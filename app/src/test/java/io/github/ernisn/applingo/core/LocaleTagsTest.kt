package io.github.ernisn.applingo.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleTagsTest {

    @Test
    fun `parses and canonicalizes tag lists`() {
        assertEquals(listOf("ja", "en-GB"), LocaleTags.parse("ja,EN-gb"))
        assertEquals(listOf("zh-Hans-CN"), LocaleTags.parse(" zh-hans-cn "))
    }

    @Test
    fun `empty and duplicate entries are dropped`() {
        assertEquals(emptyList<String>(), LocaleTags.parse(""))
        assertEquals(emptyList<String>(), LocaleTags.parse(null))
        assertEquals(listOf("ja", "en"), LocaleTags.parse("ja,,en,ja"))
    }
}
