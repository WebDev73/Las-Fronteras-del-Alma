package app.fronteras.core.mastery

import app.fronteras.core.content.Curriculum
import app.fronteras.core.util.DAY_MILLIS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MasteryEvaluatorTest {
    private val c = Curriculum.graph["greet-introduce"]!!
    private var n = 0
    private fun ev(kind: EvidenceKind, day: Double, session: String, success: Boolean = true, rubric: Double? = null,
                   assistance: Assistance = Assistance.NONE, reliability: Double = 1.0) =
        EvidenceEvent("e${++n}", c.id, kind, (day * DAY_MILLIS).toLong(), success, session, assistance, rubric, reliability)

    private fun level(vararg events: EvidenceEvent, now: Double = 10.0) = MasteryEvaluator.evaluate(c, events.toList(), (now * DAY_MILLIS).toLong())

    @Test
    fun `completing a lesson only proves exposure`() {
        val s = level(ev(EvidenceKind.EXPOSURE, 0.0, "s1"), ev(EvidenceKind.EXPOSURE, 1.0, "s2"))
        assertEquals(EvidenceLevel.EXPOSED, s.level)
        assertFalse(s.mastered)
    }

    @Test
    fun `progression requires delay, spontaneity and independent tasks`() {
        val practice = ev(EvidenceKind.GUIDED_PRACTICE, 0.0, "s1")
        assertEquals(EvidenceLevel.PRACTISED, level(practice).level)
        assertEquals(EvidenceLevel.PRACTISED, level(practice, ev(EvidenceKind.RETRIEVAL, 0.5, "s2")).level, "too soon to count as retained")
        val retained = ev(EvidenceKind.RETRIEVAL, 3.0, "s2")
        assertEquals(EvidenceLevel.RETAINED, level(practice, retained).level)
        val spont = ev(EvidenceKind.SPONTANEOUS_USE, 4.0, "s3")
        assertEquals(EvidenceLevel.SPONTANEOUS, level(practice, retained, spont).level)
        val t1 = ev(EvidenceKind.ASSESSMENT_TASK, 5.0, "s4", rubric = 3.0)
        val t2 = ev(EvidenceKind.ASSESSMENT_TASK, 6.0, "s5", rubric = 3.0, assistance = Assistance.LOW)
        val full = level(practice, retained, spont, t1, t2)
        assertEquals(EvidenceLevel.INDEPENDENT, full.level)
        assertTrue(full.mastered)
        assertTrue(full.evidence.isNotEmpty())
    }

    @Test
    fun `heavily assisted or low-reliability tasks do not count`() {
        val practice = ev(EvidenceKind.GUIDED_PRACTICE, 0.0, "s1")
        val retained = ev(EvidenceKind.RETRIEVAL, 3.0, "s2")
        val helped = ev(EvidenceKind.ASSESSMENT_TASK, 5.0, "s3", rubric = 3.5, assistance = Assistance.HIGH)
        val noisy = ev(EvidenceKind.ASSESSMENT_TASK, 6.0, "s4", rubric = 3.5, reliability = 0.2)
        val s = level(practice, retained, helped, noisy)
        assertEquals(EvidenceLevel.RETAINED, s.level)
        assertTrue(s.evidence.any { "low-reliability" in it })
    }

    @Test
    fun `mastery needs a recheck after long inactivity`() {
        val events = arrayOf(
            ev(EvidenceKind.GUIDED_PRACTICE, 0.0, "s1"), ev(EvidenceKind.RETRIEVAL, 3.0, "s2"),
            ev(EvidenceKind.ASSESSMENT_TASK, 5.0, "s3", rubric = 3.0), ev(EvidenceKind.ASSESSMENT_TASK, 6.0, "s4", rubric = 3.0),
        )
        assertTrue(level(*events, now = 10.0).mastered)
        val later = level(*events, now = 60.0)
        assertTrue(later.needsRecheck)
        assertFalse(later.mastered)
    }
}
