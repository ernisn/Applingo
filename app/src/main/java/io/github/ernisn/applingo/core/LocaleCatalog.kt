package io.github.ernisn.applingo.core

import java.text.Collator
import java.util.Locale

data class LocaleOption(
    val tag: String,
    val nativeName: String,
    val localizedName: String,
) {
    // The UI-language name ranks first: searching "japan" should find "Japanese" before "English (Japan)".
    val searchTarget = Search.Target(localizedName, tag, listOf(nativeName))
}

/** A language ("pt") with its regional/script variants ("pt-BR", "pt-PT", ...). */
data class LanguageGroup(
    val language: LocaleOption,
    val variants: List<LocaleOption>,
)

object LocaleCatalog {

    /** Every language the platform knows about, grouped by language and sorted for [displayLocale]. */
    fun build(displayLocale: Locale): List<LanguageGroup> {
        val collator = Collator.getInstance(displayLocale)
        return Locale.getAvailableLocales()
            .asSequence()
            .filter { it.language.isNotEmpty() && it.variant.isEmpty() && !it.hasExtensions() }
            .map { it.toLanguageTag() }
            .distinct()
            .groupBy { Locale.forLanguageTag(it).language }
            .map { (language, tags) ->
                LanguageGroup(
                    language = option(language, displayLocale),
                    variants = tags.filter { it != language }
                        .map { option(it, displayLocale) }
                        .sortedWith(compareBy(collator) { it.localizedName }),
                )
            }
            .sortedWith(compareBy(collator) { it.language.localizedName })
    }

    fun option(tag: String, displayLocale: Locale) = LocaleOption(
        tag = tag,
        nativeName = LocaleTags.nativeName(tag),
        localizedName = LocaleTags.localizedName(tag, displayLocale),
    )
}
