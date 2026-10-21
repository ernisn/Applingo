package io.github.ernisn.applingo.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppTypeFilter { Launchable, User, System, All }

enum class AppSort { Name, RecentlySet, RecentlyUpdated, RecentlyInstalled }

enum class ThemeMode { System, Light, Dark }

data class ListPreferences(
    val typeFilter: AppTypeFilter = AppTypeFilter.Launchable,
    val onlyWithLocale: Boolean = false,
    val sort: AppSort = AppSort.Name,
    val withLocaleFirst: Boolean = true,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** Wallpaper-based colors (Monet); when off, [seedColor] generates the palette. */
    val dynamicColor: Boolean = true,
    val seedColor: Int = SEED_COLORS.first(),
    val pureBlack: Boolean = false,
    /** Write lost choices back automatically; when off, differences are only reported. */
    val autoRepair: Boolean = true,
    val backgroundCheck: Boolean = true,
    val backgroundCheckHours: Int = 6,
)

/** Seed colors offered when dynamic color is off (ARGB). */
val SEED_COLORS = listOf(0xFF3F51B5, 0xFF00897B, 0xFF7CB342, 0xFFF4511E, 0xFFD81B60, 0xFF8E24AA).map { it.toInt() }

val BACKGROUND_CHECK_HOURS = listOf(3, 6, 12, 24)

private val Context.uiPreferences by preferencesDataStore(name = "ui_preferences")

class UiPreferences(context: Context) {

    private val store = context.uiPreferences

    val list: Flow<ListPreferences> = store.data.map { it.toListPreferences() }

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun update(transform: (ListPreferences) -> ListPreferences) {
        store.edit { prefs ->
            val next = transform(prefs.toListPreferences())
            prefs[TYPE_FILTER] = next.typeFilter.name
            prefs[ONLY_WITH_LOCALE] = next.onlyWithLocale
            prefs[SORT] = next.sort.name
            prefs[WITH_LOCALE_FIRST] = next.withLocaleFirst
        }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[THEME_MODE] = next.themeMode.name
            prefs[DYNAMIC_COLOR] = next.dynamicColor
            prefs[SEED_COLOR] = next.seedColor
            prefs[PURE_BLACK] = next.pureBlack
            prefs[AUTO_REPAIR] = next.autoRepair
            prefs[BACKGROUND_CHECK] = next.backgroundCheck
            prefs[BACKGROUND_CHECK_HOURS_KEY] = next.backgroundCheckHours
        }
    }

    private fun Preferences.toListPreferences(): ListPreferences {
        val defaults = ListPreferences()
        return ListPreferences(
            typeFilter = enumOr(this[TYPE_FILTER], defaults.typeFilter),
            onlyWithLocale = this[ONLY_WITH_LOCALE] ?: defaults.onlyWithLocale,
            sort = enumOr(this[SORT], defaults.sort),
            withLocaleFirst = this[WITH_LOCALE_FIRST] ?: defaults.withLocaleFirst,
        )
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = enumOr(this[THEME_MODE], defaults.themeMode),
            dynamicColor = this[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            seedColor = this[SEED_COLOR] ?: defaults.seedColor,
            pureBlack = this[PURE_BLACK] ?: defaults.pureBlack,
            autoRepair = this[AUTO_REPAIR] ?: defaults.autoRepair,
            backgroundCheck = this[BACKGROUND_CHECK] ?: defaults.backgroundCheck,
            backgroundCheckHours = this[BACKGROUND_CHECK_HOURS_KEY]?.takeIf { it in BACKGROUND_CHECK_HOURS }
                ?: defaults.backgroundCheckHours,
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        val TYPE_FILTER = stringPreferencesKey("type_filter")
        val ONLY_WITH_LOCALE = booleanPreferencesKey("only_with_locale")
        val SORT = stringPreferencesKey("sort")
        val WITH_LOCALE_FIRST = booleanPreferencesKey("with_locale_first")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SEED_COLOR = intPreferencesKey("seed_color")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val AUTO_REPAIR = booleanPreferencesKey("auto_repair")
        val BACKGROUND_CHECK = booleanPreferencesKey("background_check")
        val BACKGROUND_CHECK_HOURS_KEY = intPreferencesKey("background_check_hours")
    }
}
