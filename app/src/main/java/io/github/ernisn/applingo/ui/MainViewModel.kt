package io.github.ernisn.applingo.ui

import android.app.Application
import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.ernisn.applingo.ApplingoApp
import io.github.ernisn.applingo.AppContainer
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.core.LanguageGroup
import io.github.ernisn.applingo.core.LocaleCatalog
import io.github.ernisn.applingo.core.LocaleTags
import io.github.ernisn.applingo.core.Search
import io.github.ernisn.applingo.data.AppExtras
import io.github.ernisn.applingo.data.AppInfo
import io.github.ernisn.applingo.data.AppSort
import io.github.ernisn.applingo.data.AppTypeFilter
import io.github.ernisn.applingo.data.ApplyResult
import io.github.ernisn.applingo.data.DesiredLocale
import io.github.ernisn.applingo.data.ListPreferences
import io.github.ernisn.applingo.data.UserMessage
import io.github.ernisn.applingo.data.loadAppExtras
import io.github.ernisn.applingo.shizuku.ServiceUnavailableException
import io.github.ernisn.applingo.shizuku.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

enum class LocaleStatus {
    /** No per-app locale: the app follows the system. */
    None,
    /** The system has exactly what Applingo recorded. */
    Applied,
    /** Recorded, but not confirmed with the system yet (Shizuku not connected). */
    Pending,
    /** The system differs from the record; the next check will repair it. */
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
    val preferences: ListPreferences = ListPreferences(),
    val shizuku: ShizukuState = ShizukuState.Checking,
)

class MainViewModel(private val app: Application, private val container: AppContainer) : ViewModel() {

    private val locales = container.locales
    private val shizuku = container.shizuku

    private val query = MutableStateFlow("")
    private val localMessages = Channel<UserMessage>(Channel.BUFFERED)

    val messages: Flow<UserMessage> = merge(locales.messages, localMessages.receiveAsFlow())

    /** Every app with its locale state, rebuilt only when apps or locales change (not per keystroke). */
    val rows: StateFlow<Map<String, AppRow>> =
        combine(container.apps.apps, locales.desired, locales.actual) { apps, desired, actual ->
            apps?.associate { it.packageName to buildRow(it, desired[it.packageName], actual[it.packageName]) }
                ?: emptyMap()
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val listState: StateFlow<AppListState> = combine(
        combine(container.apps.apps, rows) { apps, rows -> (apps != null) to rows },
        query,
        container.preferences.list,
        shizuku.state,
        container.apps.refreshing,
    ) { (loaded, rows), query, preferences, shizukuState, refreshing ->
        AppListState(
            loading = !loaded,
            refreshing = refreshing,
            rows = filterAndSort(rows.values, query, preferences),
            query = query,
            preferences = preferences,
            shizuku = shizukuState,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListState())

    /** All languages for the picker, built once on first use. */
    val catalog: StateFlow<List<LanguageGroup>?> = MutableStateFlow<List<LanguageGroup>?>(null).also { flow ->
        viewModelScope.launch(Dispatchers.Default) { flow.value = LocaleCatalog.build(uiLocale()) }
    }

    val systemLocales: List<String>
        get() = LocaleTags.parse(app.getSystemService(LocaleManager::class.java).systemLocales.toLanguageTags())

    fun onQueryChange(value: String) {
        // Single-line search: pasted line breaks become spaces instead of breaking the match.
        query.value = value.replace(Regex("[\\r\\n]+"), " ")
    }

    fun updatePreferences(transform: (ListPreferences) -> ListPreferences) {
        viewModelScope.launch { container.preferences.update(transform) }
    }

    fun refresh() {
        viewModelScope.launch { container.refreshApps() }
    }

    /** Called when the UI comes to the foreground: pick up installs/updates and re-check locales. */
    fun onForeground() {
        if (System.currentTimeMillis() - container.apps.lastRefreshAt > FOREGROUND_REFRESH_MS) refresh()
    }

    fun requestShizukuPermission() = shizuku.requestPermission()

    fun retryShizuku() = shizuku.retry()

    fun shizukuLaunchIntent(): Intent? = shizuku.managerLaunchIntent()

    suspend fun extras(packageName: String): AppExtras = loadAppExtras(app, packageName)

    fun applyLocales(packageName: String, chain: List<String>) {
        viewModelScope.launch {
            val message = try {
                when (locales.apply(packageName, chain)) {
                    ApplyResult.Applied -> if (chain.isEmpty()) {
                        UserMessage(app.getString(R.string.message_reset), restartPackage = packageName)
                    } else {
                        UserMessage(app.getString(R.string.message_applied), restartPackage = packageName)
                    }
                    ApplyResult.Saved -> UserMessage(app.getString(R.string.message_saved_pending))
                }
            } catch (e: Exception) {
                UserMessage(app.getString(R.string.message_apply_failed, e.message ?: e.toString()))
            }
            localMessages.send(message)
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

    fun launchIntent(packageName: String): Intent? =
        app.packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appInfoIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun systemLanguageIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

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
        return AppRow(
            app = app,
            locales = shown,
            systemLocales = actual,
            status = status,
            setAt = desired?.updatedAt ?: 0,
            searchTarget = Search.Target(app.label, app.packageName, localeTerms),
        )
    }

    private fun filterAndSort(rows: Collection<AppRow>, query: String, preferences: ListPreferences): List<AppRow> {
        val keywords = Search.keywords(query)
        val collator = Collator.getInstance(uiLocale())
        val byName = compareBy<AppRow, String>(collator) { it.app.label }
        val visible = rows.filter { !preferences.onlyWithLocale || it.locales.isNotEmpty() }

        if (keywords.isNotEmpty()) {
            // Searching always covers every app, so nothing is hidden behind the type filter.
            return visible.mapNotNull { row -> Search.score(keywords, row.searchTarget)?.let { row to it } }
                .sortedWith(compareByDescending<Pair<AppRow, Int>> { it.second }.thenBy(collator) { it.first.app.label })
                .map { it.first }
        }

        val sortOrder = when (preferences.sort) {
            AppSort.Name -> byName
            AppSort.RecentlySet -> compareByDescending<AppRow> { it.setAt }.then(byName)
            AppSort.RecentlyUpdated -> compareByDescending<AppRow> { it.app.lastUpdateTime }.then(byName)
            AppSort.RecentlyInstalled -> compareByDescending<AppRow> { it.app.firstInstallTime }.then(byName)
        }
        val ordering = if (preferences.withLocaleFirst) {
            compareBy<AppRow> { it.locales.isEmpty() }.then(sortOrder)
        } else {
            sortOrder
        }
        return visible.filter { row ->
            when (preferences.typeFilter) {
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

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ApplingoApp
                MainViewModel(app, app.container)
            }
        }
    }
}
