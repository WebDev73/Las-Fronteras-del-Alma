package app.fronteras.core.curriculum

import app.fronteras.core.assessment.CefrLevel
import app.fronteras.core.assessment.Skill
import app.fronteras.core.content.Curriculum
import app.fronteras.core.content.Scenarios
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompetencyGraphTest {
    private fun comp(id: String, vararg prereq: String) = Competency(
        id, id, id, CefrLevel.A1, CefrLevel.A2, setOf(Skill.SPEAKING), prereq.toSet(), activities = listOf(ActivityType.ROLE_PLAY),
        mastery = MasteryCriteria(description = "x"), assessment = AssessmentMethod.CONVERSATION_TASK, languages = setOf("es"),
    )

    @Test
    fun `seed curriculum is valid and ordered`() {
        val order = Curriculum.graph.topologicalOrder().map { it.id }
        Curriculum.competencies.forEach { c -> c.prerequisites.forEach { p -> assertTrue(order.indexOf(p) < order.indexOf(c.id), "$p before ${c.id}") } }
        Curriculum.competencies.forEach { c ->
            c.languages.forEach { l -> assertTrue(c.expressions[l].orEmpty().isNotEmpty(), "${c.id} has $l expressions") }
            c.scenarioIds.forEach { assertTrue(it in Scenarios.byId, "${c.id} references unknown scenario $it") }
        }
        Scenarios.all.forEach { s -> s.competencyIds.forEach { assertTrue(Curriculum.graph[it] != null, "${s.id} -> $it") } }
    }

    @Test
    fun `cycles and unknown prerequisites are rejected`() {
        assertFailsWith<IllegalStateException> { CompetencyGraph(listOf(comp("a", "b"), comp("b", "a"))) }
        assertFailsWith<IllegalArgumentException> { CompetencyGraph(listOf(comp("a", "zzz"))) }
    }

    @Test
    fun `ancestors and unlocking`() {
        val g = Curriculum.graph
        assertTrue("greet-introduce" in g.ancestors("five-minute-conversation"))
        assertTrue(g.isUnlocked("everyday-transactions") { it == "greet-introduce" })
        assertTrue(!g.isUnlocked("everyday-transactions") { false })
    }
}
