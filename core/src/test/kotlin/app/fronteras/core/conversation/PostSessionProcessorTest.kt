package app.fronteras.core.conversation

import app.fronteras.core.Fixtures
import app.fronteras.core.assessment.Skill
import app.fronteras.core.content.Curriculum
import app.fronteras.core.content.Scenarios
import app.fronteras.core.feedback.ErrorCategory
import app.fronteras.core.feedback.ErrorSeverity
import app.fronteras.core.mastery.EvidenceKind
import app.fronteras.core.profile.FeedbackMode
import app.fronteras.core.srs.ReviewScheduler
import app.fronteras.core.storage.LearnerData
import app.fronteras.core.util.FixedClock
import app.fronteras.core.util.SequentialIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostSessionProcessorTest {
    private val clock = FixedClock(10_000)
    private val processor = PostSessionProcessor(ReviewScheduler(clock = clock, ids = SequentialIds("log")), SequentialIds("ps"))
    private val comp = Curriculum.graph["make-plans"]!!
    private val scenario = Scenarios.byId.getValue("weekend-plans")

    private fun session(vararg learner: String) = Session(
        "s1", "learner-1", Fixtures.es, SessionMode.ROLE_PLAY, FeedbackMode.BALANCED, scenario.id, listOf(comp.id), startedAt = 0,
        turns = learner.flatMapIndexed { i, t -> listOf(Turn("p$i", Speaker.PARTNER, "¿Y?", i * 2L), Turn("l$i", Speaker.LEARNER, t, i * 2L + 1)) },
    )

    private val analysis = SessionAnalysis(
        summary = "Good", strengths = listOf("Polite refusals"),
        feedback = listOf(
            WireFeedback("Yo soy cansado", corrected = "Estoy cansado", category = ErrorCategory.GRAMMAR, severity = ErrorSeverity.SIGNIFICANT,
                explanation = "Temporary states use estar.", confidence = 0.9, patternKey = "ser-estar"),
            WireFeedback("vale", category = ErrorCategory.OTHER, severity = ErrorSeverity.ACCEPTABLE_VARIANT, explanation = "Fine in Spain.", confidence = 0.9),
        ),
        rubric = listOf(RubricScore(Skill.INTERACTION, 3.0, listOf("negotiated a time"), 0.8), RubricScore(Skill.GRAMMAR, 2.0, listOf("ser/estar"), 0.8)),
        newItems = listOf(ExtractedItem("¿Te viene bien…?", "Does … suit you?", "Asking if a time works", "chunk", "¿Te viene bien el sábado?")),
        objectivesMet = listOf(comp.canDo),
        nextStep = "Practise ser/estar",
    )

    @Test
    fun `turns a session into review items, evidence and skill updates`() {
        val data = LearnerData(profile = Fixtures.profile())
        val s = session("Hola, yo soy cansado hoy", "¿Qué tal si vamos al cine?", "Me encantaría, pero el domingo no puedo", "¿Quedamos a las ocho?")
        val out = processor.process(data, s, analysis, listOf(comp), scenario, clock.now)

        val contents = out.newReviewItems.map { it.content }
        assertTrue("Estoy cansado" in contents)
        assertTrue("¿Te viene bien…?" in contents)
        assertTrue(out.newReviewItems.none { it.content == "vale" }, "acceptable variants are not review material")

        val kinds = out.evidence.map { it.kind }.toSet()
        assertTrue(EvidenceKind.EXPOSURE in kinds)
        assertTrue(EvidenceKind.SPONTANEOUS_USE in kinds, "learner used ¿Qué tal si…? unprompted")
        assertTrue(kinds.contains(EvidenceKind.ASSESSMENT_TASK) || kinds.contains(EvidenceKind.GUIDED_PRACTICE))

        val interaction = out.updatedEstimates.first { it.skill == Skill.INTERACTION }
        assertEquals(1, interaction.observations)

        val applied = processor.apply(data, out, clock.now)
        assertEquals(1, applied.sessions.size)
        assertEquals(1, applied.estimateHistory.size)
        assertTrue(applied.analyses.containsKey("s1"))
    }

    @Test
    fun `short conversations produce exposure only`() {
        val data = LearnerData(profile = Fixtures.profile())
        val out = processor.process(data, session("Hola"), analysis, listOf(comp), scenario, clock.now)
        assertEquals(setOf(EvidenceKind.EXPOSURE), out.evidence.map { it.kind }.toSet())
        assertTrue(out.notes.any { "Short conversation" in it })
    }

    @Test
    fun `works offline without an analysis`() {
        val data = LearnerData(profile = Fixtures.profile())
        val out = processor.process(data, session("a", "b", "c"), null, listOf(comp), scenario, clock.now)
        assertTrue(out.newReviewItems.isEmpty())
        assertTrue(out.updatedEstimates.isEmpty())
    }
}
