package app.fronteras.core.assessment

import app.fronteras.core.content.DiagnosticBank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiagnosticTest {

    @Test
    fun `item banks are well formed`() {
        for (base in listOf("es", "fr", "en")) {
            val bank = DiagnosticBank.forBase(base)
            assertEquals(bank.size, bank.map { it.id }.toSet().size, "unique ids in $base")
            assertEquals(DiagnosticItemKind.entries.toSet(), bank.map { it.kind }.toSet(), "$base covers every item kind")
            bank.filter { !it.kind.open }.forEach { item ->
                val idx = assertNotNull(item.correctIndex, item.id)
                assertTrue(idx in item.options.indices, item.id)
                assertEquals(item.options.size, item.options.toSet().size, "distinct options in ${item.id}")
            }
            bank.filter { it.kind == DiagnosticItemKind.LISTENING_CHOICE }.forEach { assertNotNull(it.audioText) }
            bank.filter { it.kind == DiagnosticItemKind.READING_CHOICE }.forEach { assertNotNull(it.passage) }
            // Correct answer positions are varied so position gives nothing away.
            assertTrue(bank.mapNotNull { it.correctIndex }.toSet().size > 1, "$base answer positions vary")
        }
    }

    private fun run(base: String, ability: Double): DiagnosticResult {
        val bank = DiagnosticBank.forBase(base)
        val d = AdaptiveDiagnostic(bank, selfAssessed = null, now = { 0L })
        var item = d.next()
        assertEquals(DiagnosticItemKind.WARM_UP_CONVERSATION, item?.kind, "starts with a low-pressure warm-up")
        while (item != null) {
            if (item.kind.open) {
                val s = if (ability >= item.difficulty) 0.85 else 0.25
                val score = OpenResponseScore(item.kind.skills.associateWith { s }, EvidenceConditions(), "test")
                d.answerOpen(item, "respuesta", score)
            } else {
                d.answerChoice(item, if (ability >= item.difficulty) item.correctIndex else (item.correctIndex!! + 1) % item.options.size)
            }
            item = d.next()
        }
        return d.result(base)
    }

    @Test
    fun `stronger learners get higher provisional levels`() {
        val weak = run("es", 1.2)
        val strong = run("es", 4.5)
        assertTrue(strong.provisionalLevel > weak.provisionalLevel, "${strong.provisionalLevel} vs ${weak.provisionalLevel}")
        assertTrue(strong.itemsAdministered <= 16)
        assertTrue(strong.limitations.any { "not an official certification" in it })
        assertTrue(strong.explanation.first().startsWith("Based on"))
    }

    @Test
    fun `skills are reported separately`() {
        val r = run("fr", 3.0)
        assertEquals(Skill.entries.size, r.findings.size)
        assertTrue(r.findings.any { it.skill == Skill.LISTENING && it.estimate.observations > 0 })
    }

    @Test
    fun `heuristic scorer is labelled and down-weighted`() {
        val item = DiagnosticBank.spanish.first { it.kind == DiagnosticItemKind.OPEN_SPEAKING }
        val s = HeuristicOpenScorer.score("Fui a Madrid con mi hermana y visitamos muchos museos", item, EvidenceConditions())
        assertTrue(s.conditions.heuristicScoring)
        assertTrue("heuristic" in s.scorer)
    }

    @Test
    fun `open answers can be scored together at the end`() {
        val bank = DiagnosticBank.english
        val d = AdaptiveDiagnostic(bank, null, { 0L })
        val warm = d.next()!!
        d.recordDeferredOpen(warm, "Hi, I'm Ana from Manila")
        assertEquals(listOf(warm.id), d.deferredOpenResponses.map { it.first.id })
        val before = d.currentEstimates.getValue(Skill.SPEAKING)
        d.observeExternal(Skill.SPEAKING, 0.9, 2.0, EvidenceConditions(), "AI rubric")
        assertTrue(d.currentEstimates.getValue(Skill.SPEAKING).mean > before.mean)
        assertTrue(d.next()?.id != warm.id)
    }
}
