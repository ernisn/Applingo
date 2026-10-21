package io.github.ernisn.applingo.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconcilePolicyTest {

    private fun decide(desired: List<String>, actual: List<String>) = ReconcilePolicy.decide(desired, actual)

    @Test
    fun `matching state needs nothing`() {
        assertEquals(ReconcileAction.None, decide(emptyList(), emptyList()))
        assertEquals(ReconcileAction.None, decide(listOf("ja", "en"), listOf("ja", "en")))
    }

    @Test
    fun `cleared by the system is repaired`() {
        // What LocaleManagerService does on app update when the app has no LocaleConfig.
        assertEquals(ReconcileAction.Repair(listOf("ja")), decide(listOf("ja"), emptyList()))
    }

    @Test
    fun `trimmed by the system is repaired`() {
        // removeUnsupportedAppLocales drops unsupported entries and keeps the rest in order.
        assertEquals(ReconcileAction.Repair(listOf("ja", "en")), decide(listOf("ja", "en"), listOf("en")))
        assertEquals(
            ReconcileAction.Repair(listOf("ja", "ko", "en")),
            decide(listOf("ja", "ko", "en"), listOf("ja", "en")),
        )
    }

    @Test
    fun `changed elsewhere is adopted`() {
        assertEquals(ReconcileAction.Adopt(listOf("fr")), decide(listOf("ja"), listOf("fr")))
        // Reordering is a deliberate change, not something the system does on its own.
        assertEquals(ReconcileAction.Adopt(listOf("en", "ja")), decide(listOf("ja", "en"), listOf("en", "ja")))
    }

    @Test
    fun `existing locales without a record are imported`() {
        assertEquals(ReconcileAction.Adopt(listOf("de")), decide(emptyList(), listOf("de")))
    }
}
