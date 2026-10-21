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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A short, user-facing message, optionally offering to restart an app. */
data class UserMessage(val text: String, val restartPackage: String? = null)

/** Outcome of writing a choice: applied right away, or saved until Shizuku connects. */
enum class ApplyResult { Applied, Saved }

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
) {
    /** Android user the app runs as; per-app locales are per user. */
    val userId: Int = Process.myUid() / PER_USER_RANGE

    val desired: Flow<Map<String, DesiredLocale>> = store.observe(userId)

    private val _actual = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    /** Last known system per-app locales; a package missing here hasn't been read yet. */
    val actual: StateFlow<Map<String, List<String>>> = _actual.asStateFlow()

    private val _messages = Channel<UserMessage>(Channel.BUFFERED)
    val messages: Flow<UserMessage> = _messages.receiveAsFlow()

    private val reconcileRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun start() {
        scope.launch { shizuku.service.filterNotNull().collect { requestReconcile() } }
        scope.launch { reconcileRequests.conflate().collect { reconcile() } }
    }

    fun requestReconcile() {
        reconcileRequests.tryEmit(Unit)
    }

    suspend fun apply(packageName: String, locales: List<String>): ApplyResult {
        val chain = LocaleTags.normalize(locales)
        val service = shizuku.service.value
        // Without the service a reset must stay recorded, or the next check would re-import the old value.
        store.set(userId, packageName, chain, pendingReset = service == null)
        if (service == null) return ApplyResult.Saved
        withContext(Dispatchers.IO) {
            service.setApplicationLocales(packageName, userId, LocaleTags.join(chain))
            readActual(service, listOf(packageName))
        }
        return ApplyResult.Applied
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

    private suspend fun reconcile() {
        val service = shizuku.service.value ?: return
        try {
            val desired = store.current(userId)
            val packages = (apps.apps.value.orEmpty().map { it.packageName } + desired.keys).distinct()
            val actual = withContext(Dispatchers.IO) { readActual(service, packages) }

            var repaired = 0
            var adopted = 0
            for ((packageName, current) in actual) {
                val entry = desired[packageName]
                if (entry != null && entry.locales.isEmpty()) {
                    // A reset chosen while Shizuku was unavailable.
                    if (current.isNotEmpty()) {
                        withContext(Dispatchers.IO) { service.setApplicationLocales(packageName, userId, "") }
                        _actual.update { it + (packageName to emptyList()) }
                    }
                    store.set(userId, packageName, emptyList())
                    continue
                }
                val wanted = entry?.locales.orEmpty()
                when (val action = ReconcilePolicy.decide(wanted, current)) {
                    ReconcileAction.None -> Unit
                    is ReconcileAction.Repair -> {
                        withContext(Dispatchers.IO) {
                            service.setApplicationLocales(packageName, userId, LocaleTags.join(action.locales))
                        }
                        _actual.update { it + (packageName to action.locales) }
                        repaired++
                        Log.i(TAG, "Repaired $packageName: $current -> ${action.locales}")
                    }
                    is ReconcileAction.Adopt -> {
                        // Locales set elsewhere (Language Selector, system settings) were usually written
                        // with fromDelegate=true. Rewriting the same value without it takes the package off
                        // the system's reset-on-update list; the app sees no change.
                        withContext(Dispatchers.IO) {
                            service.setApplicationLocales(packageName, userId, LocaleTags.join(action.locales))
                        }
                        store.set(userId, packageName, action.locales)
                        adopted++
                        Log.i(TAG, "Adopted $packageName: $wanted -> ${action.locales}")
                    }
                }
            }
            if (repaired > 0) {
                post(context.resources.getQuantityString(R.plurals.message_repaired, repaired, repaired))
            }
            if (adopted > 0) {
                post(context.resources.getQuantityString(R.plurals.message_adopted, adopted, adopted))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reconcile failed", e)
            post(context.getString(R.string.message_reconcile_failed, e.message ?: e.toString()))
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

    private fun post(text: String, restartPackage: String? = null) {
        _messages.trySend(UserMessage(text, restartPackage))
    }

    private companion object {
        const val TAG = "LocaleController"
        const val PER_USER_RANGE = 100_000
    }
}
