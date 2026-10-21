package io.github.ernisn.applingo.core

import java.util.Locale

/** Helpers for ordered locale chains stored as BCP 47 tags, e.g. `["ja", "en-GB"]`. */
object LocaleTags {

    /** Parses a comma-separated tag string as returned by `LocaleList.toLanguageTags()`. */
    fun parse(tags: String?): List<String> =
        tags.orEmpty().split(',').mapNotNull { canonical(it) }.distinct()

    fun join(tags: List<String>): String = tags.joinToString(",")

    /** Canonical form used for storage and comparison; null for blank or undetermined tags. */
    fun canonical(tag: String): String? {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return null
        val canonical = Locale.forLanguageTag(trimmed).toLanguageTag()
        return canonical.takeUnless { it == "und" }
    }

    fun normalize(tags: List<String>): List<String> = tags.mapNotNull { canonical(it) }.distinct()

    /** Name of the locale in its own language, e.g. "日本語" or "English (United Kingdom)". */
    fun nativeName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /** Name of the locale in [displayLocale], e.g. "Japanese" for an English UI. */
    fun localizedName(tag: String, displayLocale: Locale): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(displayLocale).replaceFirstChar { it.titlecase(displayLocale) }
    }
}
