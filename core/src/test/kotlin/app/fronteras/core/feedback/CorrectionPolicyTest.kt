package app.fronteras.core.feedback

import app.fronteras.core.Fixtures.feedback
import app.fronteras.core.profile.FeedbackMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CorrectionPolicyTest {
    private val critical = feedback("c", ErrorSeverity.CRITICAL_MEANING)
    private val significant = feedback("s", ErrorSeverity.SIGNIFICANT)
    private val unnatural = feedback("u", ErrorSeverity.UNNATURAL)
    private val style = feedback("m", ErrorSeverity.MINOR_STYLE)
    private val variant = feedback("v", ErrorSeverity.ACCEPTABLE_VARIANT)
    private val all = listOf(critical, significant, unnatural, style, variant)

    @Test
    fun `assessment mode never interrupts`() {
        val d = CorrectionPolicy.decide(FeedbackMode.ASSESSMENT, all, helpRequested = true)
        assertTrue(d.immediate.isEmpty())
        assertEquals(5, d.deferred.size)
    }

    @Test
    fun `conversation mode only steps in when meaning breaks down`() {
        assertEquals(listOf("c"), CorrectionPolicy.decide(FeedbackMode.CONVERSATION, all).immediate.map { it.id })
        assertTrue(CorrectionPolicy.decide(FeedbackMode.CONVERSATION, listOf(significant, unnatural)).immediate.isEmpty())
        assertEquals(listOf("s"), CorrectionPolicy.decide(FeedbackMode.CONVERSATION, listOf(significant), helpRequested = true).immediate.map { it.id })
    }

    @Test
    fun `balanced mode corrects recurring significant errors`() {
        assertTrue(CorrectionPolicy.decide(FeedbackMode.BALANCED, listOf(significant)).immediate.isEmpty())
        val d = CorrectionPolicy.decide(FeedbackMode.BALANCED, listOf(significant), priorPatternCounts = mapOf("s" to 1))
        assertEquals(listOf("s"), d.immediate.map { it.id })
    }

    @Test
    fun `intensive mode caps at three and orders by severity`() {
        val d = CorrectionPolicy.decide(FeedbackMode.INTENSIVE, all.reversed())
        assertEquals(listOf("c", "s", "u"), d.immediate.map { it.id })
        assertTrue(d.deferred.any { it.id == "m" })
    }

    @Test
    fun `acceptable variants are never shown as corrections`() {
        for (mode in FeedbackMode.entries) {
            assertTrue(CorrectionPolicy.decide(mode, listOf(variant), helpRequested = true).immediate.isEmpty(), "$mode")
        }
    }

    @Test
    fun `uncertain intent asks instead of correcting`() {
        val unsure = feedback("q", ErrorSeverity.CRITICAL_MEANING, intentConfident = false)
        val d = CorrectionPolicy.decide(FeedbackMode.INTENSIVE, listOf(unsure))
        assertTrue(d.immediate.isEmpty())
        assertNotNull(d.clarification)
    }

    @Test
    fun `low confidence and possible recognition errors do not interrupt`() {
        val lowConf = feedback("l", ErrorSeverity.CRITICAL_MEANING, confidence = 0.5)
        val asr = feedback("a", ErrorSeverity.CRITICAL_MEANING, recognition = true)
        val tooLow = feedback("x", ErrorSeverity.CRITICAL_MEANING, confidence = 0.2)
        val d = CorrectionPolicy.decide(FeedbackMode.INTENSIVE, listOf(lowConf, asr, tooLow))
        assertTrue(d.immediate.isEmpty())
        assertEquals(setOf("l", "a"), d.deferred.map { it.id }.toSet(), "very low confidence items are dropped entirely")
    }

    @Test
    fun `layered views reveal more with depth`() {
        val quick = significant.view(FeedbackDepth.QUICK)
        val deep = significant.copy(comparison = "In Portuguese…", examples = listOf("Ex")).view(FeedbackDepth.EXAMPLES)
        assertTrue(quick.lines.size < deep.lines.size)
        assertTrue(quick.canGoDeeper)
        assertTrue(!deep.canGoDeeper)
    }
}
