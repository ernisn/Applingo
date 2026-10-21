package io.github.ernisn.applingo.core

import java.text.Normalizer
import java.util.Locale

/**
 * Forgiving app search: case-, accent- and width-insensitive, whitespace-tolerant, multi-keyword.
 * Every keyword must match the label, the package name or one of the extra terms (e.g. locale names).
 */
object Search {

    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespace = Regex("\\s+")
    private val wordSeparators = Regex("[\\s._\\-()（）·]+")

    /** NFKD folds full-width forms ("ｗｘ" → "wx") and splits accents off so they can be dropped. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .replace(whitespace, " ")
            .trim()

    fun keywords(query: String): List<String> =
        normalize(query).split(' ').filter { it.isNotEmpty() }

    /** Pre-normalized searchable fields of one item. */
    class Target(label: String, packageName: String, extraTerms: List<String> = emptyList()) {
        val label = normalize(label)
        val labelWords = this.label.split(wordSeparators).filter { it.isNotEmpty() }
        val packageName = normalize(packageName)
        val packageSegments = this.packageName.split('.')
        val extraTerms = extraTerms.map(::normalize)
    }

    /** Relevance score, or null when some keyword matches nothing. Higher is better. */
    fun score(keywords: List<String>, target: Target): Int? {
        if (keywords.isEmpty()) return 0
        var total = 0
        for (keyword in keywords) {
            total += keywordScore(keyword, target) ?: return null
        }
        if (target.label == keywords.joinToString(" ")) total += 1000
        return total
    }

    private fun keywordScore(keyword: String, target: Target): Int? = when {
        target.label.startsWith(keyword) -> 100
        target.labelWords.any { it.startsWith(keyword) } -> 80
        target.label.contains(keyword) -> 60
        target.packageSegments.any { it.startsWith(keyword) } -> 50
        target.packageName.contains(keyword) -> 40
        target.extraTerms.any { it.contains(keyword) } -> 30
        else -> null
    }
}
