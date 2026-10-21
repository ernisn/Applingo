package io.github.ernisn.applingo.ui

import android.app.Application
import android.app.LocaleConfig
import android.app.LocaleManager
import android.os.LocaleList
import android.os.SystemClock
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.ernisn.applingo.AppContainer
import io.github.ernisn.applingo.ApplingoApp
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.core.LanguageGroup
import io.github.ernisn.applingo.core.LocaleCatalog
import io.github.ernisn.applingo.core.LocaleTags
import io.github.ernisn.applingo.core.Search
import io.github.ernisn.applingo.data.ActivityEntry
import io.github.ernisn.applingo.data.AppExtras
import io.github.ernisn.applingo.data.AppInfo
import io.github.ernisn.applingo.data.AppSettings
import io.github.ernisn.applingo.data.AppSort
import io.github.ernisn.applingo.data.AppTypeFilter
import io.github.ernisn.applingo.data.ApplyResult
import io.github.ernisn.applingo.data.CheckSummary
import io.github.ernisn.applingo.data.DesiredLocale
import io.github.ernisn.applingo.data.ListPreferences
import io.github.ernisn.applingo.data.Pinyin
import io.github.ernisn.applingo.data.UserMessage
import io.github.ernisn.applingo.data.loadAppExtras
import io.github.ernisn.applingo.shizuku.ServiceUnavailableException
import io.github.ernisn.applingo.shizuku.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class LocaleStatus {
    /** No per-app locale: the app follows the system. */
    None,
    /** The system has exactly what Applingo recorded. */
    Applied,
    /** Recorded, but not confirmed with the system yet (Shizuku not connected). */
    Pending,
    /** The system differs from the record; the next check will repair it (or report it). */
    Mismatch,
}

data class AppRow(
    val app: AppInfo,
    /** The chain the user chose, or what the system has when Applingo has no record yet. */
    val locales: List<String>,
    val systemLocales: List<String>?,
    val status: LocaleStatus,
    val setAt: Long,
    internal val searchTarget: Search.Target,
)

data class AppListState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val rows: List<AppRow> = emptyList(),
    val query: String = "",
    val typeFilter: AppTypeFilter = AppTypeFilter.Launchable,
    val preferences: ListPreferences = ListPreferences(),
    val shizuku: ShizukuState = ShizukuState.Checking,
    val selection: Set<String> = emptySet(),
)

/** One app Applingo keeps a record for; [row] is null when the app isn't installed. */
data class ManagedApp(
    val packageName: String,
    val label: String,
    val locales: List<String>,
    val status: LocaleStatus,
    val row: AppRow?,
)

/** A change counting down to being written; [startedAt] is in [SystemClock.uptimeMillis] time. */
data class PendingChange(val id: Long, val text: String, val startedAt: Long, val durationMillis: Long)

data class ManagedState(
    val apps: List<ManagedApp> = emptyList(),
    val activity: List<ActivityEntry> = emptyList(),
    val lastCheck: CheckSummary? = null,
    val checking: Boolean = false,
    val shizuku: ShizukuState = ShizukuState.Checking,
)

class MainViewModel(private val app: Application, private val container: AppContainer) : ViewModel() {

    private val locales = container.locales
    private val shizuku = container.shizuku

    private val query = MutableStateFlow("")
    /** The filter picked in the list this session; until then the one from settings. */
    private val chosenFilter = MutableStateFlow<AppTypeFilter?>(null)
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    private val localMessages = Channel<UserMessage>(Channel.BUFFERED)
    private val pinyinCache = ConcurrentHashMap<String, List<String>>()

    private val _pendingChange = MutableStateFlow<PendingChange?>(null)
    private var pendingWrite: (suspend () -> Unit)? = null
    private var pendingUndo: (() -> Unit)? = null
    private var pendingTimer: Job? = null
    private var lastPendingId = 0L

    val messages: Flow<UserMessage> = merge(locales.messages, localMessages.receiveAsFlow())

    /** The language change waiting out its undo period, if any. */
    val pendingChange: StateFlow<PendingChange?> = _pendingChange.asStateFlow()

    val settings: StateFlow<AppSettings> =
        container.preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Every app with its locale state, rebuilt only when apps or locales change (not per keystroke). */
    val rows: StateFlow<Map<String, AppRow>> =
        combine(container.apps.apps, locales.desired, locales.actual) { apps, desired, actual ->
            apps?.associate { it.packageName to buildRow(it, desired[it.packageName], actual[it.packageName]) }
                ?: emptyMap()
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // The stored settings rather than [settings]: its placeholder would show the default filter for a
    // moment before switching to the one chosen in settings.
    private val typeFilter: Flow<AppTypeFilter> =
        combine(chosenFilter, container.preferences.settings) { chosen, settings -> chosen ?: settings.startFilter }

    val listState: StateFlow<AppListState> = combine(
        combine(container.apps.apps, rows) { apps, rows -> (apps != null) to rows },
        combine(query, typeFilter, _selection) { query, filter, selection -> Triple(query, filter, selection) },
        container.preferences.list,
        shizuku.state,
        container.apps.refreshing,
    ) { (loaded, rows), (query, filter, selection), preferences, shizukuState, refreshing ->
        AppListState(
            loading = !loaded,
            refreshing = refreshing,
            rows = filterAndSort(rows.values, query, filter, preferences),
            query = query,
            typeFilter = filter,
            preferences = preferences,
            shizuku = shizukuState,
            selection = selection,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListState())

    val managedState: StateFlow<ManagedState> = combine(
        combine(locales.desired, rows, locales.actual) { desired, rows, actual -> Triple(desired, rows, actual) },
        container.activityLog.entries,
        locales.lastCheck,
        locales.checking,
        shizuku.state,
    ) { (desired, rows, actual), activity, lastCheck, checking, shizukuState ->
        val managed = buildMap {
            desired.values.forEach { put(it.packageName, it) }
        }.keys + rows.values.filter { it.locales.isNotEmpty() }.map { it.app.packageName }
        val collator = Collator.getInstance(uiLocale())
        ManagedState(
            apps = managed.map { packageName ->
                val row = rows[packageName]
                ManagedApp(
                    packageName = packageName,
                    label = row?.app?.label ?: packageName,
                    locales = row?.locales ?: desired[packageName]?.locales.orEmpty(),
                    status = row?.status ?: if (actual.containsKey(packageName)) LocaleStatus.Applied else LocaleStatus.Pending,
                    row = row,
                )
            }.sortedWith(compareBy<ManagedApp> { it.row == null }.thenBy { it.status != LocaleStatus.Mismatch }.thenBy(collator) { it.label }),
            activity = activity,
            lastCheck = lastCheck,
            checking = checking,
            shizuku = shizukuState,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManagedState())

    /** All languages for the picker, built once on first use. */
    val catalog: StateFlow<List<LanguageGroup>?> = MutableStateFlow<List<LanguageGroup>?>(null).also { flow ->
        viewModelScope.launch(Dispatchers.Default) { flow.value = LocaleCatalog.build(uiLocale()) }
    }

    val systemLocales: List<String>
        get() = LocaleTags.parse(app.getSystemService(LocaleManager::class.java).systemLocales.toLanguageTags())

    val versionName: String = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()

    // --- App list --------------------------------------------------------------------------------

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setTypeFilter(filter: AppTypeFilter) {
        chosenFilter.value = filter
    }

    fun updatePreferences(transform: (ListPreferences) -> ListPreferences) {
        viewModelScope.launch { container.preferences.update(transform) }
    }

    fun refresh() {
        viewModelScope.launch { container.refreshApps() }
    }

    /** Called when the UI comes back to the foreground: re-check locales, and rescan apps if enabled. */
    fun onForeground() {
        val stale = System.currentTimeMillis() - container.apps.lastRefreshAt > FOREGROUND_REFRESH_MS
        when {
            !stale -> Unit
            settings.value.refreshOnLaunch -> refresh()
            else -> container.locales.requestReconcile()
        }
    }

    fun forceStop(packageName: String) {
        viewModelScope.launch {
            val text = try {
                locales.forceStop(packageName)
                app.getString(R.string.message_force_stopped)
            } catch (e: ServiceUnavailableException) {
                app.getString(R.string.message_needs_shizuku)
            } catch (e: Exception) {
                app.getString(R.string.message_force_stop_failed, e.message ?: e.toString())
            }
            localMessages.send(UserMessage(text))
        }
    }

    fun toggleSelection(packageName: String) {
        _selection.update { if (packageName in it) it - packageName else it + packageName }
    }

    fun selectAll(packageNames: Collection<String>) {
        _selection.update { it + packageNames }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    // --- Shizuku ---------------------------------------------------------------------------------

    fun requestShizukuPermission() = shizuku.requestPermission()

    fun retryShizuku() = shizuku.retry()

    fun shizukuLaunchIntent(): Intent? = shizuku.managerLaunchIntent()

    // --- Locales ---------------------------------------------------------------------------------

    suspend fun extras(packageName: String): AppExtras = loadAppExtras(app, packageName)

    /** Sets [chain] for the app (empty: back to the system language) once the undo period is over. */
    fun applyLocales(packageName: String, chain: List<String>) {
        val label = rows.value[packageName]?.app?.label ?: packageName
        afterUndoPeriod(app.getString(if (chain.isEmpty()) R.string.pending_reset else R.string.pending_apply, label)) {
            val message = try {
                when (locales.apply(packageName, chain)) {
                    ApplyResult.Applied -> UserMessage(
                        app.getString(if (chain.isEmpty()) R.string.message_reset else R.string.message_applied),
                    )
                    ApplyResult.Saved -> UserMessage(app.getString(R.string.message_saved_pending))
                }
            } catch (e: Exception) {
                UserMessage(app.getString(R.string.message_apply_failed, e.message ?: e.toString()))
            }
            localMessages.send(message)
        }
    }

    /** Applies [chain] to every selected app once the undo period is over; undo selects them again. */
    fun applyToSelection(chain: List<String>) {
        val packages = _selection.value
        clearSelection()
        val countdown = app.resources.getQuantityString(
            if (chain.isEmpty()) R.plurals.pending_batch_reset else R.plurals.pending_batch_apply,
            packages.size,
            packages.size,
        )
        afterUndoPeriod(countdown, onUndo = { _selection.update { it + packages } }) {
            val failed = locales.applyMany(packages, chain)
            val text = if (failed == 0) {
                app.resources.getQuantityString(R.plurals.message_batch_applied, packages.size, packages.size)
            } else {
                app.getString(R.string.message_batch_failed, failed, packages.size)
            }
            localMessages.send(UserMessage(text))
        }
    }

    fun restartApp(packageName: String) {
        viewModelScope.launch {
            try {
                locales.restart(packageName)
            } catch (e: ServiceUnavailableException) {
                localMessages.send(UserMessage(app.getString(R.string.message_needs_shizuku)))
            } catch (e: Exception) {
                localMessages.send(UserMessage(app.getString(R.string.message_restart_failed, e.message ?: e.toString())))
            }
        }
    }

    fun checkNow() {
        viewModelScope.launch {
            container.apps.refresh()
            val summary = locales.reconcileNow(announce = true)
            val text = when {
                summary == null -> app.getString(R.string.message_needs_shizuku)
                summary.repaired + summary.adopted + summary.mismatched + summary.failed == 0 ->
                    app.getString(R.string.message_check_ok)
                else -> null
            }
            text?.let { localMessages.send(UserMessage(it)) }
        }
    }

    fun forget(packageName: String) {
        afterUndoPeriod(app.getString(R.string.pending_forget, packageName)) { locales.forget(packageName) }
    }

    // --- Undo period -----------------------------------------------------------------------------
    // Changing, clearing or forgetting a language is written only after a short countdown, so a
    // mis-tap can be taken back before the previous choice is lost.

    private fun afterUndoPeriod(text: String, onUndo: () -> Unit = {}, write: suspend () -> Unit) {
        // One countdown at a time. The waiting change was confirmed too, so it is written now
        // rather than dropped.
        writePendingNow()
        pendingWrite = write
        pendingUndo = onUndo
        _pendingChange.value = PendingChange(++lastPendingId, text, SystemClock.uptimeMillis(), UNDO_PERIOD_MS)
        pendingTimer = viewModelScope.launch {
            delay(UNDO_PERIOD_MS)
            pendingTimer = null
            writePendingNow()
        }
    }

    fun undoPendingChange() {
        pendingTimer?.cancel()
        pendingTimer = null
        pendingWrite = null
        pendingUndo?.invoke()
        pendingUndo = null
        _pendingChange.value = null
    }

    private fun writePendingNow() {
        val write = pendingWrite ?: return
        pendingTimer?.cancel()
        pendingTimer = null
        pendingWrite = null
        pendingUndo = null
        _pendingChange.value = null
        // App-wide scope: the write goes through even if this screen is closed for good meanwhile.
        container.scope.launch { write() }
    }

    /** Leaving Applingo during a countdown writes the change instead of silently dropping it. */
    override fun onCleared() {
        writePendingNow()
    }

    fun clearActivity() {
        viewModelScope.launch { container.activityLog.clear() }
    }

    // --- Settings --------------------------------------------------------------------------------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.preferences.updateSettings(transform) }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val text = try {
                val json = locales.exportJson()
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.encodeToByteArray()) }
                }
                app.getString(R.string.message_exported)
            } catch (e: Exception) {
                app.getString(R.string.message_export_failed, e.message ?: e.toString())
            }
            localMessages.send(UserMessage(text))
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val text = try {
                val json = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
                val count = locales.importJson(json)
                app.resources.getQuantityString(R.plurals.message_imported, count, count)
            } catch (e: Exception) {
                app.getString(R.string.message_import_failed, e.message ?: e.toString())
            }
            localMessages.send(UserMessage(text))
        }
    }

    // --- Intents ---------------------------------------------------------------------------------

    fun launchIntent(packageName: String): Intent? =
        app.packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appInfoIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun systemLanguageIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // --- Applingo's own language ------------------------------------------------------------------
    // Set in-app with the public per-app API: Samsung's own region picker in system settings crashes.

    /** UI languages Applingo ships, from its generated LocaleConfig. */
    val ownLocaleOptions: List<String> by lazy {
        val collator = Collator.getInstance(Locale.ROOT)
        LocaleTags.parse(LocaleConfig(app).supportedLocales?.toLanguageTags())
            .sortedWith(compareBy(collator) { LocaleTags.nativeName(it) })
    }

    fun ownLocale(): String? =
        app.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().ifEmpty { null }

    /** Changes Applingo's UI language (null follows the system); the activity is recreated by the system. */
    fun setOwnLocale(tag: String?) {
        app.getSystemService(LocaleManager::class.java).applicationLocales =
            tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
    }

    // --- Internals -------------------------------------------------------------------------------

    private fun buildRow(app: AppInfo, desired: DesiredLocale?, actual: List<String>?): AppRow {
        val wanted = desired?.locales.orEmpty()
        val pendingReset = desired != null && wanted.isEmpty()
        val shown = if (pendingReset) emptyList() else wanted.ifEmpty { actual.orEmpty() }
        val status = when {
            pendingReset -> if (actual.isNullOrEmpty()) LocaleStatus.None else LocaleStatus.Pending
            wanted.isEmpty() -> if (actual.isNullOrEmpty()) LocaleStatus.None else LocaleStatus.Applied
            actual == null -> LocaleStatus.Pending
            actual == wanted -> LocaleStatus.Applied
            else -> LocaleStatus.Mismatch
        }
        val ui = uiLocale()
        val localeTerms = shown.flatMap { listOf(it, LocaleTags.nativeName(it), LocaleTags.localizedName(it, ui)) }
        val pinyin = pinyinCache.getOrPut(app.label) { Pinyin.spellings(app.label) }
        return AppRow(
            app = app,
            locales = shown,
            systemLocales = actual,
            status = status,
            setAt = desired?.updatedAt ?: 0,
            searchTarget = Search.Target(app.label, app.packageName, localeTerms, pinyin),
        )
    }

    private fun filterAndSort(
        rows: Collection<AppRow>,
        query: String,
        typeFilter: AppTypeFilter,
        preferences: ListPreferences,
    ): List<AppRow> {
        val keywords = Search.keywords(query)
        val collator = Collator.getInstance(uiLocale())
        val byName = compareBy<AppRow, String>(collator) { it.app.label }

        if (keywords.isNotEmpty()) {
            // Searching always covers every app, so nothing is hidden behind the type filter.
            return rows.mapNotNull { row -> Search.score(keywords, row.searchTarget)?.let { row to it } }
                .sortedWith(compareByDescending<Pair<AppRow, Int>> { it.second }.thenBy(collator) { it.first.app.label })
                .map { it.first }
        }

        val sortOrder = when (preferences.sort) {
            AppSort.Name -> byName
            AppSort.RecentlySet -> compareByDescending<AppRow> { it.setAt }.then(byName)
            AppSort.RecentlyUpdated -> compareByDescending<AppRow> { it.app.lastUpdateTime }.then(byName)
            AppSort.RecentlyInstalled -> compareByDescending<AppRow> { it.app.firstInstallTime }.then(byName)
        }
        val directed = if (preferences.reverseOrder) sortOrder.reversed() else sortOrder
        val ordering = if (preferences.withLocaleFirst) {
            compareBy<AppRow> { it.locales.isEmpty() }.then(directed)
        } else {
            directed
        }
        return rows.filter { row ->
            when (typeFilter) {
                AppTypeFilter.Launchable -> row.app.isLaunchable
                AppTypeFilter.User -> !row.app.isSystem
                AppTypeFilter.System -> row.app.isSystem
                AppTypeFilter.All -> true
            }
        }.sortedWith(ordering)
    }

    private fun uiLocale(): Locale = app.resources.configuration.locales[0]

    companion object {
        private const val FOREGROUND_REFRESH_MS = 30_000L
        private const val UNDO_PERIOD_MS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ApplingoApp
                MainViewModel(app, app.container)
            }
        }
    }
}
