package io.github.ernisn.applingo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {

    private val maps = Search.Target("Google Maps", "com.google.android.apps.maps", listOf("ja", "日本語", "Japanese"))
    private val chrome = Search.Target("Chrome", "com.android.chrome")

    private fun score(query: String, target: Search.Target) = Search.score(Search.keywords(query), target)

    @Test
    fun `stray whitespace and line breaks do not break a match`() {
        assertNotNull(score("maps ", maps))
        assertNotNull(score("  maps\n", maps))
        assertEquals(listOf("goo", "map"), Search.keywords(" goo \t map \r\n"))
    }

    @Test
    fun `case, accents and full-width characters are ignored`() {
        assertNotNull(score("MAPS", maps))
        assertNotNull(score("ｍａｐｓ", maps))
        assertEquals("cafe", Search.normalize("Café"))
    }

    @Test
    fun `every keyword must match somewhere`() {
        assertNotNull(score("goo map", maps))
        assertNull(score("goo chrome", maps))
    }

    @Test
    fun `package names and locale terms match`() {
        assertNotNull(score("apps.maps", maps))
        assertNotNull(score("日本語", maps))
        assertNotNull(score("japanese", maps))
        assertNull(score("japanese", chrome))
    }

    @Test
    fun `label matches rank above package matches`() {
        val labelHit = score("chrome", chrome)!!
        val packageHit = score("android", chrome)!!
        assertTrue(labelHit > packageHit)
    }

    @Test
    fun `empty query matches everything`() {
        assertEquals(0, score("   ", chrome))
    }
}
