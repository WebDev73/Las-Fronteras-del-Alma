package app.fronteras.core.conversation

import app.fronteras.core.Fixtures
import app.fronteras.core.content.Curriculum
import app.fronteras.core.profile.FeedbackMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContextBuilderTest {
    private val memory = LongTermMemory(recurringErrors = listOf("ser/estar"))
    private fun session(turns: Int) = Session(
        "s", "l", Fixtures.es, SessionMode.FREE_CONVERSATION, FeedbackMode.BALANCED, startedAt = 0,
        turns = (1..turns).map { Turn("t$it", if (it % 2 == 0) Speaker.LEARNER else Speaker.PARTNER, "turn $it", it.toLong()) },
    )

    @Test
    fun `long-term memory requires consent`() {
        val b = ContextBuilder()
        val comps = listOf(Curriculum.graph["make-plans"]!!)
        val without = b.build("r", session(2), Fixtures.profile(memory = false), emptyList(), null, comps, memory, false)
        assertNull(without.memory)
        val with = b.build("r", session(2), Fixtures.profile(memory = true), emptyList(), null, comps, memory, false)
        assertEquals(memory, with.memory)
    }

    @Test
    fun `recent window is bounded and older turns are summarised`() {
        val r = ContextBuilder(maxRecentTurns = 4).build("r", session(10), Fixtures.profile(), emptyList(), null, emptyList(), null, false)
        assertEquals(4, r.turns.size)
        assertEquals("turn 10", r.turns.last().text)
        assertNotNull(r.earlierSummary)
    }

    @Test
    fun `variety expressions and notes follow the target variety`() {
        val comps = listOf(Curriculum.graph["make-plans"]!!)
        val r = ContextBuilder().build("r", session(0), Fixtures.profile(), emptyList(), null, comps, null, false)
        assertTrue(r.usefulExpressions.any { "apetece" in it }, "es-ES learner gets Spain-specific expression")
        assertTrue(r.varietyNotes.any { "coche" in it })
        assertEquals("pt-BR", r.learner.supportLanguages.first().value)
    }

    @Test
    fun `personal identifiers are redacted before leaving the device`() {
        val s = session(0).copy(turns = listOf(Turn("t", Speaker.LEARNER, "Mi correo es ana@example.com y mi móvil +34 612 345 678", 0)))
        val r = ContextBuilder().build("r", s, Fixtures.profile(), emptyList(), null, emptyList(), null, false)
        assertEquals("Mi correo es [email] y mi móvil [phone]", r.turns.single().text)
    }
}
