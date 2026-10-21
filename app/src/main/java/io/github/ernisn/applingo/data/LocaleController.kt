package io.github.ernisn.applingo.data

import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.core.LocaleTags
import io.github.ernisn.applingo.core.ReconcileAction
import io.github.ernisn.applingo.core.ReconcilePolicy
import io.github.ernisn.applingo.service.IApplingoService
import io.github.ernisn.applingo.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A short, user-facing message, optionally offering to restart an app. */
data class UserMessage(val text: String, val restartPackage: String? = null)

/** Outcome of writing a choice: applied right away, or saved until Shizuku connects. */
enum class ApplyResult { Applied, Saved }

/** Result of one check of all apps against Applingo's record. */
data class CheckSummary(
    val time: Long,
    val repaired: Int,
    val adopted: Int,
    val mismatched: Int,
    val failed: Int,
)

@Serializable
private data class ExportFile(
    val format: String = EXPORT_FORMAT,
    val version: Int = 1,
    val exportedAt: Long,
    val apps: List<ExportedApp>,
)

@Serializable
private data class ExportedApp(val packageName: String, val locales: List<String>)

private const val EXPORT_FORMAT = "applingo-locales"

/**
 * Owns the per-app locale state: Applingo's record of what the user chose ([desired]), what the
 * system currently has ([actual]), and keeping the two in line (see [ReconcilePolicy]).
 */
class LocaleController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val shizuku: ShizukuManager,
    private val apps: AppRepository,
    private val store: DesiredLocaleStore,
    private val preferences: UiPreferences,
    private val log: ActivityLog,
) {
    /** Android user the app runs as; per-app locales are per user. */
    val userId: Int = Process.myUid() / PER_USER_RANGE

    val desired: Flow<Map<String, DesiredLocale>> = store.observe(userId)

    private val _actual = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    /** Last known system per-app locales; a package missing here hasn't been read yet. */
    val actual: StateFlow<Map<String, List<String>>> = _actual.asStateFlow()

    private val _lastCheck = MutableStateFlow<CheckSummary?>(null)
    val lastCheck: StateFlow<CheckSummary?> = _lastCheck.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _messages = Channel<UserMessage>(Channel.BUFFERED)
    val messages: Flow<UserMessage> = _messages.receiveAsFlow()

    private val reconcileRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val reconcileMutex = Mutex()
    /** Mismatches already reported while auto-repair is off, so each is logged once. */
    private val reportedMismatches = mutableSetOf<String>()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun start() {
        scope.launch { shizuku.service.filterNotNull().collect { requestReconcile() } }
        scope.launch { reconcileRequests.conflate().collect { reconcileNow(announce = true) } }
    }

    fun requestReconcile() {
        reconcileRequests.tryEmit(Unit)
    }

    suspend fun apply(packageName: String, locales: List<String>): ApplyResult {
        val chain = LocaleTags.normalize(locales)
        val service = shizuku.service.value
        val before = _actual.value[packageName].orEmpty()
        // Without the service a reset must stay recorded, or the next check would re-import the old value.
        store.set(userId, packageName, chain, pendingReset = service == null)
        if (service == null) return ApplyResult.Saved
        try {
            withContext(Dispatchers.IO) {
                service.setApplicationLocales(packageName, userId, LocaleTags.join(chain))
                readActual(service, listOf(packageName))
            }
        } catch (e: Exception) {
            log.add(entry(ActivityKind.Failed, packageName, before, chain, e.message ?: e.toString()))
            throw e
        }
        log.add(entry(if (chain.isEmpty()) ActivityKind.Reset else ActivityKind.Applied, packageName, before, chain))
        return ApplyResult.Applied
    }

    /** Applies one chain to several apps; returns how many failed. */
    suspend fun applyMany(packageNames: Collection<String>, locales: List<String>): Int {
        var failed = 0
        for (packageName in packageNames) {
            try {
                apply(packageName, locales)
            } catch (e: Exception) {
                failed++
            }
        }
        return failed
    }

    /** Drops the record of an app (e.g. one that was uninstalled) without touching the system. */
    suspend fun forget(packageName: String) {
        store.set(userId, packageName, emptyList())
    }

    suspend fun forceStop(packageName: String) = withContext(Dispatchers.IO) {
        shizuku.awaitService().forceStopPackage(packageName, userId)
    }

    /** Force-stops the app so it picks up its new locales, then launches it again if it can be. */
    suspend fun restart(packageName: String) {
        forceStop(packageName)
        context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let(context::startActivity)
    }

    suspend fun exportJson(): String {
        val entries = store.current(userId).values
            .filter { it.locales.isNotEmpty() }
            .sortedBy { it.packageName }
            .map { ExportedApp(it.packageName, it.locales) }
        return json.encodeToString(ExportFile(exportedAt = System.currentTimeMillis(), apps = entries))
    }

    /** Records and applies every app in an exported file; returns how many apps it contained. */
    suspend fun importJson(text: String): Int {
        val file = json.decodeFromString<ExportFile>(text)
        require(file.format == EXPORT_FORMAT) { "Not an Applingo export" }
        val service = shizuku.service.value
        val logged = mutableListOf<ActivityEntry>()
        for (app in file.apps) {
            val chain = LocaleTags.normalize(app.locales)
            if (chain.isEmpty()) continue
            store.set(userId, app.packageName, chain)
            if (service != null) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        service.setApplicationLocales(app.packageName, userId, LocaleTags.join(chain))
                    }
                }
            }
            logged += entry(ActivityKind.Imported, app.packageName, emptyList(), chain)
        }
        log.add(logged)
        requestReconcile()
        return logged.size
    }

    /**
     * Compares every app with the record and acts per [ReconcilePolicy]. Runs one at a time; safe to
     * call from the background worker. Returns null when the Shizuku service isn't available.
     */
    suspend fun reconcileNow(announce: Boolean): CheckSummary? = reconcileMutex.withLock {
        val service = shizuku.service.value ?: return null
        _checking.value = true
        try {
            val autoRepair = preferences.settings.first().autoRepair
            val desired = store.current(userId)
            val packages = (apps.apps.value.orEmpty().map { it.packageName } + desired.keys).distinct()
            val actual = withContext(Dispatchers.IO) { readActual(service, packages) }

            var repaired = 0
            var adopted = 0
            var mismatched = 0
            var failed = 0
            val logged = mutableListOf<ActivityEntry>()

            suspend fun write(packageName: String, locales: List<String>): Boolean = try {
                withContext(Dispatchers.IO) {
                    service.setApplicationLocales(packageName, userId, LocaleTags.join(locales))
                }
                _actual.update { it + (packageName to locales) }
                true
            } catch (e: Exception) {
                failed++
                logged += entry(ActivityKind.Failed, packageName, actual[packageName].orEmpty(), locales, e.message)
                false
            }

            for ((packageName, current) in actual) {
                val record = desired[packageName]
                if (record != null && record.locales.isEmpty()) {
                    // A reset chosen while Shizuku was unavailable.
                    if (current.isEmpty() || write(packageName, emptyList())) {
                        store.set(userId, packageName, emptyList())
                        if (current.isNotEmpty()) logged += entry(ActivityKind.Reset, packageName, current, emptyList())
                    }
                    continue
                }
                val wanted = record?.locales.orEmpty()
                when (val action = ReconcilePolicy.decide(wanted, current)) {
                    ReconcileAction.None -> reportedMismatches.removeAll { it.startsWith("$packageName|") }
                    is ReconcileAction.Repair -> if (autoRepair) {
                        if (write(packageName, action.locales)) {
                            repaired++
                            logged += entry(ActivityKind.Repaired, packageName, current, action.locales)
                            Log.i(TAG, "Repaired $packageName: $current -> ${action.locales}")
                        }
                    } else {
                        mismatched++
                        if (reportedMismatches.add("$packageName|$current")) {
                            logged += entry(ActivityKind.Mismatch, packageName, current, action.locales)
                        }
                    }
                    is ReconcileAction.Adopt -> {
                        // Locales set elsewhere (Language Selector, system settings) were usually written
                        // with fromDelegate=true. Rewriting the same value without it takes the package off
                        // the system's reset-on-update list; the app sees no change.
                        write(packageName, action.locales)
                        store.set(userId, packageName, action.locales)
                        adopted++
                        logged += entry(ActivityKind.Adopted, packageName, wanted, action.locales)
                        Log.i(TAG, "Adopted $packageName: $wanted -> ${action.locales}")
                    }
                }
            }
            log.add(logged)

            if (announce) {
                if (repaired > 0) post(context.resources.getQuantityString(R.plurals.message_repaired, repaired, repaired))
                if (adopted > 0) post(context.resources.getQuantityString(R.plurals.message_adopted, adopted, adopted))
            }
            CheckSummary(System.currentTimeMillis(), repaired, adopted, mismatched, failed).also { _lastCheck.value = it }
        } catch (e: Exception) {
            Log.w(TAG, "Reconcile failed", e)
            if (announce) post(context.getString(R.string.message_reconcile_failed, e.message ?: e.toString()))
            null
        } finally {
            _checking.value = false
        }
    }

    /** Reads the system locales of [packages] in one IPC and merges them into [actual]. */
    private fun readActual(service: IApplingoService, packages: List<String>): Map<String, List<String>> {
        if (packages.isEmpty()) return emptyMap()
        val result = service.getApplicationLocales(packages.toTypedArray(), userId)
        val read = buildMap {
            packages.forEachIndexed { i, packageName ->
                val tags = result.getOrNull(i) ?: return@forEachIndexed
                put(packageName, LocaleTags.parse(tags))
            }
        }
        _actual.update { it + read }
        return read
    }

    private fun entry(
        kind: ActivityKind,
        packageName: String,
        from: List<String>,
        to: List<String>,
        message: String? = null,
    ) = ActivityEntry(
        time = System.currentTimeMillis(),
        kind = kind,
        packageName = packageName,
        label = apps.apps.value?.firstOrNull { it.packageName == packageName }?.label ?: packageName,
        from = from,
        to = to,
        message = message,
    )

    private fun post(text: String, restartPackage: String? = null) {
        _messages.trySend(UserMessage(text, restartPackage))
    }

    private companion object {
        const val TAG = "LocaleController"
        const val PER_USER_RANGE = 100_000
    }
}
