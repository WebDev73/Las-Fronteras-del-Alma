package app.fronteras.core.planning

import app.fronteras.core.Fixtures
import app.fronteras.core.assessment.CefrLevel
import app.fronteras.core.content.Curriculum
import app.fronteras.core.mastery.EvidenceLevel
import app.fronteras.core.mastery.MasteryStatus
import app.fronteras.core.profile.LearningGoal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanningTest {
    private val graph = Curriculum.graph

    @Test
    fun `work goal path follows prerequisites to the interview`() {
        val t = Fixtures.profile(goals = listOf(LearningGoal.WORK)).active
        val path = LearningPathGenerator.generate(t, graph, emptyMap(), CefrLevel.A2, "A2 provisional")
        val ids = path.steps.map { it.competencyId }
        assertTrue("job-interview" in ids)
        assertTrue(ids.indexOf("describe-work") < ids.indexOf("job-interview"))
        assertTrue(ids.indexOf("greet-introduce") < ids.indexOf("describe-work"))
        assertTrue(path.estimatedDays > 0)
        assertTrue(path.milestones.isNotEmpty())
    }

    @Test
    fun `mastered steps cost nothing`() {
        val t = Fixtures.profile().active
        val m = mapOf("greet-introduce" to MasteryStatus("greet-introduce", EvidenceLevel.INDEPENDENT, emptyList(), null, false))
        val path = LearningPathGenerator.generate(t, graph, m, CefrLevel.A2, "")
        assertEquals(0, path.steps.first { it.competencyId == "greet-introduce" }.estimatedMinutes)
    }

    @Test
    fun `recommendations follow the learning loop`() {
        val t = Fixtures.profile().active
        val path = LearningPathGenerator.generate(t, graph, emptyMap(), CefrLevel.A2, "")
        val first = NextStepRecommender.recommend(false, 0, path, emptyMap(), graph.all, emptyList(), 15, 0)
        assertEquals(RecommendationKind.DIAGNOSTIC, first.first().kind)
        val later = NextStepRecommender.recommend(true, 12, path, emptyMap(), graph.all, listOf("ser-estar" to 4), 15, 0)
        assertEquals(listOf(RecommendationKind.REVIEW, RecommendationKind.TARGETED_PRACTICE, RecommendationKind.GUIDED_SESSION, RecommendationKind.FREE_CONVERSATION), later.map { it.kind })
    }

    @Test
    fun `session plans fit the requested length`() {
        val c = graph["make-plans"]!!
        for (m in SessionPlanner.lengths) {
            val plan = SessionPlanner.guided(c, m)
            assertTrue(plan.steps.sumOf { it.minutes } <= m + 1, "plan for $m min")
        }
        assertEquals(7, SessionPlanner.guided(c, 20).steps.size)
        assertEquals(1, SessionPlanner.free(10).steps.size)
    }
}
