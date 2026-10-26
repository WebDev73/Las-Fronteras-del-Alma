package app.fronteras.core.language

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LanguageTest {
    @Test
    fun `tags parse base and region`() {
        assertEquals("es", LanguageTag("es-419").base)
        assertEquals("CA", LanguageTag("fr-CA").region)
        assertTrue(LanguageTag("pt-BR").sameLanguageAs(LanguageTag("pt-PT")))
        assertFailsWith<IllegalArgumentException> { LanguageTag("Spanish") }
    }

    @Test
    fun `variant guidance respects the chosen variety`() {
        val bus = VariantGuidance.notes.first { it.id == "pt-bus" }
        assertEquals("autocarro", VariantGuidance.preferredForm(bus, LanguageTag("pt-PT")))
        assertEquals("ônibus", VariantGuidance.preferredForm(bus, LanguageTag("pt-BR")))
        val pc = VariantGuidance.notes.first { it.id == "es-computer" }
        assertEquals("computadora", VariantGuidance.preferredForm(pc, LanguageTag("es-MX")), "Latin American varieties fall back to es-419")
        assertTrue(VariantGuidance.notes.all { it.reviewStatus == ReviewStatus.CURATED_DRAFT })
    }

    @Test
    fun `catalog knows which targets have content`() {
        assertTrue(LanguageCatalog.hasLearningContent(LanguageTag("fr-CA")))
        assertTrue(!LanguageCatalog.hasLearningContent(LanguageTag("ja")))
        assertEquals("Canadian French (Québec)", LanguageCatalog.displayName(LanguageTag("fr-CA")))
    }
}
