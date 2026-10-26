package app.fronteras.core.progress

import app.fronteras.core.Fixtures
import app.fronteras.core.assessment.Skill
import app.fronteras.core.assessment.SkillEstimate
import app.fronteras.core.storage.EstimateSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressTest {
    private fun snap(at: Long, mean: Double, sd: Double, task: String? = "t") =
        EstimateSnapshot(Fixtures.es, at, "x", task, listOf(SkillEstimate(Skill.SPEAKING, mean, sd, observations = 3)))

    @Test
    fun `claims improvement only beyond the uncertainty`() {
        val cur = SkillEstimate(Skill.SPEAKING, 3.5, 0.3, 5)
        assertEquals(TrendDirection.INSUFFICIENT_DATA, ProgressCalculator.trend(Skill.SPEAKING, listOf(snap(0, 2.0, 0.3)), cur).direction)
        assertEquals(TrendDirection.NO_CLEAR_CHANGE, ProgressCalculator.trend(Skill.SPEAKING, listOf(snap(0, 2.0, 0.8), snap(1, 2.6, 0.8)), cur).direction)
        assertEquals(TrendDirection.IMPROVED, ProgressCalculator.trend(Skill.SPEAKING, listOf(snap(0, 2.0, 0.3), snap(1, 3.4, 0.3)), cur).direction)
    }

    @Test
    fun `improvement examples come from real reuse`() {
        val ex = ProgressCalculator.improvementExamples(listOf("yo soy cansado" to "estoy cansado"), listOf("Hoy estoy cansado pero bien"), { it.lowercase() })
        assertEquals(1, ex.size)
    }
}
