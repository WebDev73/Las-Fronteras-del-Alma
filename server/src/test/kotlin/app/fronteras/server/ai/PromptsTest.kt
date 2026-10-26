package app.fronteras.server.ai

import app.fronteras.core.conversation.Speaker
import app.fronteras.core.conversation.WireTurn
import app.fronteras.server.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptsTest {
    private fun assertAlternatesAndEndsWithUser(msgs: List<Pair<Speaker, String>>) {
        assertEquals(Speaker.LEARNER, msgs.first().first)
        assertEquals(Speaker.LEARNER, msgs.last().first)
        msgs.zipWithNext().forEach { (a, b) -> assertTrue(a.first != b.first, "roles alternate") }
    }

    @Test
    fun `brief first, partner opening as assistant, learner last with tags`() {
        val msgs = Prompts.partnerMessages(TestData.partner(help = true))
        assertAlternatesAndEndsWithUser(msgs)
        assertEquals(3, msgs.size)
        assertTrue("Mexican Spanish (es-MX)" in msgs[0].second)
        assertTrue("tl, en" in msgs[0].second, "support languages are not assumed to be English only")
        assertTrue(msgs.last().second.startsWith("[speech-recognised, LOW confidence 0.42; learner asked for help]"))
    }

    @Test
    fun `opening request and dangling partner turn still end with the user`() {
        assertAlternatesAndEndsWithUser(Prompts.partnerMessages(TestData.partner(turns = emptyList())))
        val msgs = Prompts.partnerMessages(TestData.partner(turns = listOf(WireTurn(Speaker.LEARNER, "Hola"), WireTurn(Speaker.PARTNER, "¿Qué tal?"))))
        assertAlternatesAndEndsWithUser(msgs)
        assertTrue("Hola" in msgs.first().second, "first learner turn merges into the brief message")
    }

    @Test
    fun `system prompts are static`() {
        assertTrue(Prompts.PARTNER_SYSTEM.length > 1000 && !Prompts.PARTNER_SYSTEM.contains("es-MX"))
        assertTrue("TRANSCRIPT" in Prompts.analysisPrompt(TestData.analysis()))
    }
}
