package io.github.ernisn.applingo.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppTypeFilter { Launchable, User, System, All }

enum class AppSort { Name, RecentlySet, RecentlyUpdated, RecentlyInstalled }

data class ListPreferences(
    val typeFilter: AppTypeFilter = AppTypeFilter.Launchable,
    val onlyWithLocale: Boolean = false,
    val sort: AppSort = AppSort.Name,
    val withLocaleFirst: Boolean = true,
)

private val Context.uiPreferences by preferencesDataStore(name = "ui_preferences")

class UiPreferences(context: Context) {

    private val store = context.uiPreferences

    val list: Flow<ListPreferences> = store.data.map { it.toListPreferences() }

    suspend fun update(transform: (ListPreferences) -> ListPreferences) {
        store.edit { prefs ->
            val next = transform(prefs.toListPreferences())
            prefs[TYPE_FILTER] = next.typeFilter.name
            prefs[ONLY_WITH_LOCALE] = next.onlyWithLocale
            prefs[SORT] = next.sort.name
            prefs[WITH_LOCALE_FIRST] = next.withLocaleFirst
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

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        val TYPE_FILTER = stringPreferencesKey("type_filter")
        val ONLY_WITH_LOCALE = booleanPreferencesKey("only_with_locale")
        val SORT = stringPreferencesKey("sort")
        val WITH_LOCALE_FIRST = booleanPreferencesKey("with_locale_first")
    }
}
