package app.fronteras.core.assessment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillEstimateTest {
    private fun obs(score: Double, difficulty: Double, reliability: Double = 1.0, at: Long = 0) =
        SkillObservation(Skill.GRAMMAR, score, difficulty, reliability, at, "test")

    @Test
    fun `correct answers above level raise the estimate and reduce uncertainty`() {
        val prior = SkillEstimate.prior(Skill.GRAMMAR, CefrLevel.A2)
        val after = SkillUpdater.update(prior, obs(1.0, 3.0))
        assertTrue(after.mean > prior.mean)
        assertTrue(after.sd < prior.sd)
        assertEquals(1, after.observations)
    }

    @Test
    fun `unreliable evidence moves the estimate less`() {
        val prior = SkillEstimate.prior(Skill.GRAMMAR, CefrLevel.B1)
        val full = SkillUpdater.update(prior, obs(0.0, 2.0, 1.0))
        val weak = SkillUpdater.update(prior, obs(0.0, 2.0, 0.2))
        assertTrue(prior.mean - full.mean > prior.mean - weak.mean)
        assertEquals(prior, SkillUpdater.update(prior, obs(0.0, 2.0, 0.0)))
    }

    @Test
    fun `consistent evidence converges to the true level`() {
        var e = SkillEstimate.prior(Skill.GRAMMAR, null)
        // Simulated B2 learner: passes items below 4, fails items above.
        repeat(30) { i ->
            val d = 1.0 + (i % 6)
            e = SkillUpdater.update(e, obs(if (d < 4.0) 1.0 else if (d == 4.0) 0.5 else 0.0, d))
        }
        assertEquals(CefrLevel.B2, e.level)
        assertTrue(e.isSettled)
    }

    @Test
    fun `low ASR confidence discounts pronunciation and vocabulary evidence most`() {
        val c = EvidenceConditions(spoken = true, asrConfidence = 0.3)
        val pron = EvidenceReliability.weight(Skill.PRONUNCIATION, c)
        val inter = EvidenceReliability.weight(Skill.INTERACTION, c)
        assertTrue(pron < inter)
        assertEquals(1.0, EvidenceReliability.weight(Skill.PRONUNCIATION, c.copy(transcriptEdited = true)))
        assertEquals(0.5, EvidenceReliability.weight(Skill.GRAMMAR, EvidenceConditions(heuristicScoring = true)))
    }

    @Test
    fun `cefr scale mapping`() {
        assertEquals(CefrLevel.A1, CefrLevel.fromScale(0.2))
        assertEquals(CefrLevel.B1, CefrLevel.fromScale(3.0))
        assertEquals(CefrLevel.C2, CefrLevel.fromScale(6.8))
        val wide = SkillEstimate(Skill.GRAMMAR, 3.0, 1.0)
        assertEquals("A2–B2", wide.describeRange())
    }
}
