package io.github.ernisn.applingo.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.ernisn.applingo.core.LocaleTags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The app list's tabs. */
enum class AppTypeFilter { All, User, System, Favorites }

enum class AppSort { Name, RecentlySet, RecentlyUpdated, RecentlyInstalled }

enum class ThemeMode { System, Light, Dark }

data class ListPreferences(
    val sort: AppSort = AppSort.Name,
    /** Reverses the natural order of [sort] (A→Z for names, newest first for dates). */
    val reverseOrder: Boolean = false,
    /**
     * Only apps with a launcher icon, closest to what people think of as apps (system components have
     * none). Applies to every tab but Favorites; a search still covers every app.
     */
    val launchableOnly: Boolean = false,
    /** Hides disabled apps, likewise. */
    val enabledOnly: Boolean = false,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** Wallpaper-based colors (Monet); when off, [seedColor] generates the palette. */
    val dynamicColor: Boolean = true,
    val seedColor: Int = SEED_COLORS.first(),
    val pureBlack: Boolean = false,
    /** The device's default font instead of the condensed one. */
    val systemFont: Boolean = false,
    /** Write lost choices back automatically; when off, differences are only reported. */
    val autoRepair: Boolean = true,
    val backgroundCheck: Boolean = true,
    val backgroundCheckHours: Int = 6,
    /**
     * Rescan installed apps every time Applingo opens. Off by default: the list shows the saved snapshot
     * right away, which the background check and pull-to-refresh keep up to date.
     */
    val refreshOnLaunch: Boolean = false,
    /**
     * The app list's tab each time Applingo opens; switching it in the list lasts until then. (A stored
     * "Launchable", once a tab and now the [ListPreferences.launchableOnly] filter, reads as All.)
     */
    val startFilter: AppTypeFilter = AppTypeFilter.All,
)

/** Seed colors offered when dynamic color is off (ARGB). */
val SEED_COLORS = listOf(0xFF3F51B5, 0xFF00897B, 0xFF7CB342, 0xFFF4511E, 0xFFD81B60, 0xFF8E24AA).map { it.toInt() }

val BACKGROUND_CHECK_HOURS = listOf(3, 6, 12, 24)

private val Context.uiPreferences by preferencesDataStore(name = "ui_preferences")

class UiPreferences(context: Context) {

    private val store = context.uiPreferences

    val list: Flow<ListPreferences> = store.data.map { it.toListPreferences() }

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    val favoriteApps: Flow<Set<String>> = store.data.map { it[FAVORITE_APPS].orEmpty() }

    /** Languages starred in the picker, in the order they were starred. */
    val favoriteLocales: Flow<List<String>> = store.data.map { LocaleTags.parse(it[FAVORITE_LOCALES]) }

    suspend fun update(transform: (ListPreferences) -> ListPreferences) {
        store.edit { prefs ->
            val next = transform(prefs.toListPreferences())
            prefs[SORT] = next.sort.name
            prefs[REVERSE_ORDER] = next.reverseOrder
            prefs[LAUNCHABLE_ONLY] = next.launchableOnly
            prefs[ENABLED_ONLY] = next.enabledOnly
        }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[THEME_MODE] = next.themeMode.name
            prefs[DYNAMIC_COLOR] = next.dynamicColor
            prefs[SEED_COLOR] = next.seedColor
            prefs[PURE_BLACK] = next.pureBlack
            prefs[SYSTEM_FONT] = next.systemFont
            prefs[AUTO_REPAIR] = next.autoRepair
            prefs[BACKGROUND_CHECK] = next.backgroundCheck
            prefs[BACKGROUND_CHECK_HOURS_KEY] = next.backgroundCheckHours
            prefs[REFRESH_ON_LAUNCH] = next.refreshOnLaunch
            prefs[START_FILTER] = next.startFilter.name
        }
    }

    suspend fun toggleFavoriteApp(packageName: String) {
        store.edit { prefs ->
            val current = prefs[FAVORITE_APPS].orEmpty()
            prefs[FAVORITE_APPS] = if (packageName in current) current - packageName else current + packageName
        }
    }

    suspend fun toggleFavoriteLocale(tag: String) {
        store.edit { prefs ->
            val current = LocaleTags.parse(prefs[FAVORITE_LOCALES])
            prefs[FAVORITE_LOCALES] = LocaleTags.join(if (tag in current) current - tag else current + tag)
        }
    }

    private fun Preferences.toListPreferences(): ListPreferences {
        val defaults = ListPreferences()
        return ListPreferences(
            sort = enumOr(this[SORT], defaults.sort),
            reverseOrder = this[REVERSE_ORDER] ?: defaults.reverseOrder,
            launchableOnly = this[LAUNCHABLE_ONLY] ?: defaults.launchableOnly,
            enabledOnly = this[ENABLED_ONLY] ?: defaults.enabledOnly,
        )
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = enumOr(this[THEME_MODE], defaults.themeMode),
            dynamicColor = this[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            seedColor = this[SEED_COLOR] ?: defaults.seedColor,
            pureBlack = this[PURE_BLACK] ?: defaults.pureBlack,
            systemFont = this[SYSTEM_FONT] ?: defaults.systemFont,
            autoRepair = this[AUTO_REPAIR] ?: defaults.autoRepair,
            backgroundCheck = this[BACKGROUND_CHECK] ?: defaults.backgroundCheck,
            backgroundCheckHours = this[BACKGROUND_CHECK_HOURS_KEY]?.takeIf { it in BACKGROUND_CHECK_HOURS }
                ?: defaults.backgroundCheckHours,
            refreshOnLaunch = this[REFRESH_ON_LAUNCH] ?: defaults.refreshOnLaunch,
            startFilter = enumOr(this[START_FILTER], defaults.startFilter),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        val START_FILTER = stringPreferencesKey("start_filter")
        val SORT = stringPreferencesKey("sort")
        val REVERSE_ORDER = booleanPreferencesKey("reverse_order")
        val LAUNCHABLE_ONLY = booleanPreferencesKey("launchable_only")
        val ENABLED_ONLY = booleanPreferencesKey("enabled_only")
        val REFRESH_ON_LAUNCH = booleanPreferencesKey("refresh_on_launch")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SEED_COLOR = intPreferencesKey("seed_color")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val SYSTEM_FONT = booleanPreferencesKey("system_font")
        val AUTO_REPAIR = booleanPreferencesKey("auto_repair")
        val BACKGROUND_CHECK = booleanPreferencesKey("background_check")
        val BACKGROUND_CHECK_HOURS_KEY = intPreferencesKey("background_check_hours")
        val FAVORITE_APPS = stringSetPreferencesKey("favorite_apps")
        val FAVORITE_LOCALES = stringPreferencesKey("favorite_locales")
    }
}
