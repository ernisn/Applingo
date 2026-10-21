package io.github.ernisn.applingo.data

import android.icu.text.Transliterator

/**
 * Pinyin spellings of Chinese app names for search, using the platform's ICU transliterator.
 * "微信" → ["weixin", "wx"]; "QQ音乐" → ["qqyinle", "qqyinyue", "qqyl", "qqyy"] (non-Han letters are
 * kept as they are; common polyphonic characters get their other readings too).
 */
object Pinyin {

    // Same transform AOSP's contacts provider uses for pinyin (HanziToPinyin); null if the platform lacks it.
    private val transliterator: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Han-Latin; Latin-Ascii; Any-Lower") }.getOrNull()
    }
    private val lock = Any()

    /** Other readings of polyphonic characters that often appear in app names (ICU picks just one). */
    private val alternatives: Map<Int, List<String>> = mapOf(
        '乐' to listOf("yue", "le"),
        '行' to listOf("hang", "xing"),
        '长' to listOf("chang", "zhang"),
        '重' to listOf("zhong", "chong"),
        '传' to listOf("chuan", "zhuan"),
        '调' to listOf("tiao", "diao"),
        '藏' to listOf("cang", "zang"),
        '都' to listOf("du", "dou"),
        '还' to listOf("hai", "huan"),
        '和' to listOf("he", "huo"),
        '地' to listOf("di", "de"),
        '的' to listOf("de", "di"),
        '单' to listOf("dan", "shan"),
        '朝' to listOf("chao", "zhao"),
        '曾' to listOf("zeng", "ceng"),
        '解' to listOf("jie", "xie"),
        '着' to listOf("zhe", "zhao", "zhuo"),
        '觉' to listOf("jue", "jiao"),
        '系' to listOf("xi", "ji"),
        '便' to listOf("bian", "pian"),
        '差' to listOf("cha", "chai"),
        '率' to listOf("lv", "shuai"),
        '量' to listOf("liang"),
        '省' to listOf("sheng", "xing"),
        '参' to listOf("can", "shen"),
        '区' to listOf("qu", "ou"),
        '车' to listOf("che", "ju"),
        '弹' to listOf("dan", "tan"),
        '盛' to listOf("sheng", "cheng"),
        '校' to listOf("xiao", "jiao"),
        '期' to listOf("qi", "ji"),
        '会' to listOf("hui", "kuai"),
        '空' to listOf("kong"),
        '大' to listOf("da", "dai"),
        '石' to listOf("shi", "dan"),
        '音' to listOf("yin"),
    ).mapKeys { it.key.code }

    private const val MAX_VARIANTS = 16

    fun spellings(text: String): List<String> {
        if (text.none { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) return emptyList()
        val transliterator = transliterator ?: return emptyList()

        var full = listOf("")
        var initials = listOf("")
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            i += Character.charCount(codePoint)
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                val icu = synchronized(lock) {
                    transliterator.transliterate(String(Character.toChars(codePoint)))
                }.filter { it.isLetter() }
                val readings = (listOf(icu) + alternatives[codePoint].orEmpty()).filter { it.isNotEmpty() }.distinct()
                if (readings.isEmpty()) continue
                full = full.flatMap { prefix -> readings.map { prefix + it } }.take(MAX_VARIANTS)
                initials = initials.flatMap { prefix -> readings.map { prefix + it.first() } }.distinct().take(MAX_VARIANTS)
            } else if (Character.isLetterOrDigit(codePoint)) {
                val kept = String(Character.toChars(codePoint)).lowercase()
                full = full.map { it + kept }
                initials = initials.map { it + kept }
            }
        }
        return (full + initials).filter { it.isNotEmpty() }.distinct()
    }
}
