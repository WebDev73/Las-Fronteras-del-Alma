package app.fronteras.core.conversation

import app.fronteras.core.Fixtures
import app.fronteras.core.content.Curriculum
import app.fronteras.core.content.Scenarios
import app.fronteras.core.feedback.ErrorCategory
import app.fronteras.core.feedback.ErrorSeverity
import app.fronteras.core.feedback.TranscriptInfo
import app.fronteras.core.profile.FeedbackMode
import app.fronteras.core.util.FixedClock
import app.fronteras.core.util.SequentialIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakePartner(
    var available: Boolean = true,
    val script: MutableList<(PartnerRequest) -> List<PartnerEvent>> = mutableListOf(),
) : ConversationPartner {
    override val kind = PartnerKind.CLOUD_AI
    val requests = mutableListOf<PartnerRequest>()
    override suspend fun isAvailable() = available
    override fun respond(request: PartnerRequest): Flow<PartnerEvent> = flow {
        requests += request
        val step = if (script.isNotEmpty()) script.removeAt(0) else { _ -> listOf(PartnerEvent.Completed("¡Genial!", null, kind)) }
        step(request).forEach { emit(it) }
    }
}

class ConversationEngineTest {
    private val clock = FixedClock(1_000)
    private val scenario = Scenarios.byId.getValue("cafe-order")
    private val comps = listOf(Curriculum.graph["everyday-transactions"]!!)

    private fun session(mode: FeedbackMode = FeedbackMode.BALANCED) =
        Session("s1", "learner-1", Fixtures.es, SessionMode.ROLE_PLAY, mode, scenario.id, comps.map { it.id }, startedAt = 0)

    private fun engine(primary: ConversationPartner, fallback: ConversationPartner? = null, mode: FeedbackMode = FeedbackMode.BALANCED) =
        ConversationEngine(session(mode), Fixtures.profile(mode = mode), emptyList(), scenario, comps, null, primary, fallback,
            clock = clock, ids = SequentialIds(), retryBaseDelayMs = 1)

    private val wireCritical = WireFeedback(
        original = "Quiero café caliente frío", corrected = "Quiero un café frío", category = ErrorCategory.VOCABULARY,
        severity = ErrorSeverity.CRITICAL_MEANING, explanation = "Contradiction", confidence = 0.9, patternKey = "contradiction",
    )

    @Test
    fun `streams a reply and applies the correction policy`() = runTest {
        val partner = FakePartner(script = mutableListOf(
            { _ -> listOf(PartnerEvent.TextDelta("¡Bue"), PartnerEvent.TextDelta("nas!"), PartnerEvent.Completed("¡Buenas! ¿Qué le pongo?", null, PartnerKind.CLOUD_AI)) },
            { _ -> listOf(PartnerEvent.Completed("¿Frío o caliente?", TurnAnalysis(listOf(wireCritical, wireCritical.copy(severity = ErrorSeverity.MINOR_STYLE, patternKey = "style"))), PartnerKind.CLOUD_AI)) },
        ))
        val e = engine(partner)
        e.open()
        assertEquals(1, e.session.turns.size)
        e.submitLearnerTurn("Quiero café caliente frío")
        val s = e.session
        assertEquals(3, s.turns.size)
        assertTrue(s.turns.all { it.status == TurnStatus.COMPLETE })
        assertEquals(listOf("contradiction"), s.shownFeedback.map { it.patternKey })
        assertEquals(listOf("style"), s.deferredFeedback.map { it.patternKey })
        assertEquals(PartnerKind.CLOUD_AI, s.partnerKind)
        assertEquals(scenario.title, partner.requests.last().scenario?.title)
    }

    @Test
    fun `transient failures are retried without losing the turn`() = runTest {
        val partner = FakePartner(script = mutableListOf(
            { _ -> listOf(PartnerEvent.Failed("503", "busy", true)) },
            { _ -> listOf(PartnerEvent.Completed("Vale.", null, PartnerKind.CLOUD_AI)) },
        ))
        val e = engine(partner)
        e.submitLearnerTurn("Hola")
        assertEquals(listOf(Speaker.LEARNER, Speaker.PARTNER), e.session.turns.map { it.speaker })
        assertIs<EngineStatus.Idle>(e.state.value.status)
    }

    @Test
    fun `persistent failure keeps the session and allows manual retry`() = runTest {
        val fail: (PartnerRequest) -> List<PartnerEvent> = { _ -> listOf(PartnerEvent.Failed("503", "busy", true)) }
        val partner = FakePartner(script = mutableListOf(fail, fail, fail))
        val e = engine(partner)
        e.submitLearnerTurn("Hola")
        val err = assertIs<EngineStatus.Error>(e.state.value.status)
        assertTrue(err.retryable)
        assertEquals(TurnStatus.FAILED, e.session.turns.single().status)
        e.retry()
        assertEquals(TurnStatus.COMPLETE, e.session.turns.first().status)
        assertEquals(2, e.session.turns.size)
    }

    @Test
    fun `falls back to the scripted offline partner and says so`() = runTest {
        val primary = FakePartner(available = false)
        val offline = ScriptedOfflinePartner(Scenarios.byId, Scenarios.genericPrompts)
        val e = engine(primary, offline)
        e.open()
        assertEquals(scenario.openings["es"], e.session.turns.single().text)
        assertNotNull(e.state.value.notice)
        assertEquals(PartnerKind.SCRIPTED_OFFLINE, e.session.partnerKind)
        e.submitLearnerTurn("Un café, por favor")
        assertEquals(scenario.offlinePrompts["es"]!!.first(), e.session.turns.last().text)
        assertTrue(e.session.allFeedback.isEmpty(), "the scripted partner never invents corrections")
    }

    @Test
    fun `low ASR confidence marks vocabulary findings as possible recognition issues`() = runTest {
        val partner = FakePartner(script = mutableListOf({ _ -> listOf(PartnerEvent.Completed("¿Perdón?", TurnAnalysis(listOf(wireCritical)), PartnerKind.CLOUD_AI)) }))
        val e = engine(partner, mode = FeedbackMode.INTENSIVE)
        e.submitLearnerTurn("Quiero café caliente frío", InputMode.VOICE, TranscriptInfo("Quiero café caliente frío", 0.4, "test"))
        assertTrue(e.session.shownFeedback.isEmpty())
        assertTrue(e.session.deferredFeedback.single().possibleRecognitionIssue)
        assertEquals(0.4, partner.requests.single().turns.last().asrConfidence)
    }

    @Test
    fun `editing a pending transcript marks it as learner-edited`() = runTest {
        val fail: (PartnerRequest) -> List<PartnerEvent> = { _ -> listOf(PartnerEvent.Failed("x", "down", false)) }
        val e = engine(FakePartner(script = mutableListOf(fail)))
        e.submitLearnerTurn("ola", InputMode.VOICE, TranscriptInfo("ola", 0.5, "test"))
        val id = e.session.turns.single().id
        e.editPendingTurn(id, "Hola")
        val t = e.session.turns.single()
        assertEquals("Hola", t.text)
        assertTrue(t.transcript!!.editedByLearner)
        assertNull(e.session.endedAt)
        e.end()
        assertNotNull(e.session.endedAt)
    }
}
