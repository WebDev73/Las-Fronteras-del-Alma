package app.fronteras.core.conversation

import app.fronteras.core.assessment.SkillEstimate
import app.fronteras.core.curriculum.Competency
import app.fronteras.core.feedback.CorrectionDecision
import app.fronteras.core.feedback.CorrectionPolicy
import app.fronteras.core.feedback.ErrorCategory
import app.fronteras.core.feedback.FeedbackItem
import app.fronteras.core.feedback.TranscriptInfo
import app.fronteras.core.profile.LearnerProfile
import app.fronteras.core.util.Clock
import app.fronteras.core.util.IdGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface EngineStatus {
    data object Idle : EngineStatus
    data object AwaitingPartner : EngineStatus
    data class Streaming(val text: String) : EngineStatus
    data class Error(val message: String, val retryable: Boolean) : EngineStatus
    data object Ended : EngineStatus
}

data class ConversationState(
    val session: Session,
    val status: EngineStatus = EngineStatus.Idle,
    val lastDecision: CorrectionDecision? = null,
    val activePartner: PartnerKind? = null,
    val notice: String? = null,
)

/**
 * Orchestrates one session: builds context, calls the partner (one call per turn that returns
 * both the reply and a light analysis), applies the correction policy and keeps the session
 * intact across failures so the learner never has to restart (spec §6.2).
 */
class ConversationEngine(
    initial: Session,
    private val profile: LearnerProfile,
    private val estimates: List<SkillEstimate>,
    private val scenario: Scenario?,
    private val competencies: List<Competency>,
    private val memory: LongTermMemory?,
    private val primary: ConversationPartner,
    private val fallback: ConversationPartner? = null,
    private val contextBuilder: ContextBuilder = ContextBuilder(),
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
    private val maxAutoRetries: Int = 2,
    private val retryBaseDelayMs: Long = 600,
) {
    private val _state = MutableStateFlow(ConversationState(initial))
    val state: StateFlow<ConversationState> = _state.asStateFlow()
    private val mutex = Mutex()
    private var usingFallback = false

    val session: Session get() = _state.value.session

    /** Lets the partner open the conversation (scenario opening line or greeting). */
    suspend fun open() = mutex.withLock {
        if (session.turns.isEmpty()) requestPartnerTurn(helpRequested = false)
    }

    suspend fun submitLearnerTurn(
        text: String,
        inputMode: InputMode = InputMode.TEXT,
        transcript: TranscriptInfo? = null,
        helpRequested: Boolean = false,
        responseLatencyMs: Long? = null,
    ) = mutex.withLock {
        require(session.isActive) { "Session has ended" }
        val clean = text.trim()
        if (clean.isEmpty()) return@withLock
        val turn = Turn(
            id = ids.newId(), speaker = Speaker.LEARNER, text = clean, at = clock.nowMillis(),
            inputMode = inputMode, transcript = transcript, helpRequested = helpRequested,
            responseLatencyMs = responseLatencyMs, status = TurnStatus.PENDING,
        )
        _state.update { it.copy(session = it.session.copy(turns = it.session.turns + turn), notice = null) }
        requestPartnerTurn(helpRequested)
    }

    /** Retries the partner for the last pending learner turn without losing anything. */
    suspend fun retry() = mutex.withLock {
        val last = session.turns.lastOrNull()
        if (last != null && last.speaker == Speaker.LEARNER && last.status != TurnStatus.COMPLETE) {
            setTurnStatus(last.id, TurnStatus.PENDING)
            requestPartnerTurn(last.helpRequested)
        } else if (session.turns.isEmpty()) {
            requestPartnerTurn(false)
        }
    }

    /** Learner fixes a transcription of a turn the partner has not answered yet. */
    suspend fun editPendingTurn(turnId: String, newText: String) = mutex.withLock {
        _state.update { s ->
            s.copy(session = s.session.copy(turns = s.session.turns.map {
                if (it.id == turnId && it.status != TurnStatus.COMPLETE) {
                    it.copy(text = newText.trim(), transcript = it.transcript?.copy(editedByLearner = true))
                } else it
            }))
        }
    }

    /** Records that the learner interrupted partner speech at [spokenUpTo] characters. */
    fun markInterrupted(partnerTurnId: String, spokenUpTo: Int) {
        _state.update { s ->
            s.copy(session = s.session.copy(turns = s.session.turns.map {
                if (it.id == partnerTurnId) it.copy(status = TurnStatus.INTERRUPTED, spokenUpTo = spokenUpTo) else it
            }))
        }
    }

    fun end(): Session {
        _state.update { it.copy(session = it.session.copy(endedAt = clock.nowMillis()), status = EngineStatus.Ended) }
        return session
    }

    private suspend fun choosePartner(): ConversationPartner? {
        if (!usingFallback && primary.isAvailable()) return primary
        val fb = fallback
        if (fb != null && fb.isAvailable()) {
            if (!usingFallback) {
                usingFallback = true
                _state.update { it.copy(notice = "Connection to the AI partner is unavailable. Continuing with the ${fb.kind.label}.") }
            }
            return fb
        }
        return if (primary.isAvailable()) primary else null
    }

    private suspend fun requestPartnerTurn(helpRequested: Boolean) {
        var attempt = 0
        while (true) {
            val partner = choosePartner()
            if (partner == null) {
                failPending("No conversation partner is available right now. Your conversation is saved; try again when you're back online.", retryable = true)
                return
            }
            _state.update { it.copy(status = EngineStatus.AwaitingPartner, activePartner = partner.kind) }
            val request = contextBuilder.build(ids.newId(), session, profile, estimates, scenario, competencies, memory, helpRequested)
            var outcome: PartnerEvent? = null
            val buffer = StringBuilder()
            partner.respond(request)
                .catch { e -> outcome = PartnerEvent.Failed("transport", e.message ?: "Network error", retryable = true) }
                .collect { ev ->
                    when (ev) {
                        is PartnerEvent.TextDelta -> {
                            buffer.append(ev.text)
                            _state.update { it.copy(status = EngineStatus.Streaming(buffer.toString())) }
                        }
                        is PartnerEvent.Completed, is PartnerEvent.Failed -> outcome = ev
                    }
                }
            when (val result = outcome) {
                is PartnerEvent.Completed -> {
                    complete(result, helpRequested)
                    return
                }
                is PartnerEvent.Failed -> {
                    if (result.retryable && attempt < maxAutoRetries) {
                        attempt++
                        delay(retryBaseDelayMs * (1L shl (attempt - 1)))
                        continue
                    }
                    if (partner === primary && fallback != null && !usingFallback && result.retryable) {
                        usingFallback = true
                        _state.update { it.copy(notice = "The AI partner isn't responding. Continuing with the ${fallback.kind.label}.") }
                        attempt = 0
                        continue
                    }
                    failPending(result.message, result.retryable)
                    return
                }
                else -> {
                    failPending("The partner ended without a reply.", retryable = true)
                    return
                }
            }
        }
    }

    private fun complete(result: PartnerEvent.Completed, helpRequested: Boolean) {
        val now = clock.nowMillis()
        val s = session
        val learnerTurn = s.turns.lastOrNull()?.takeIf { it.speaker == Speaker.LEARNER }
        val feedback = learnerTurn?.let { turn -> result.analysis?.feedback.orEmpty().map { toFeedbackItem(it, turn) } }.orEmpty()
        val priorCounts = s.allFeedback.mapNotNull { it.patternKey }.groupingBy { it }.eachCount()
        val decision = CorrectionPolicy.decide(s.feedbackMode, feedback, helpRequested, priorCounts)
        val partnerTurn = Turn(ids.newId(), Speaker.PARTNER, result.reply.trim(), now)
        _state.update { st ->
            st.copy(
                session = st.session.copy(
                    turns = st.session.turns.map { if (it.id == learnerTurn?.id) it.copy(status = TurnStatus.COMPLETE) else it } + partnerTurn,
                    shownFeedback = st.session.shownFeedback + decision.immediate,
                    deferredFeedback = st.session.deferredFeedback + decision.deferred,
                    partnerKind = st.session.partnerKind ?: result.partnerKind,
                ),
                status = EngineStatus.Idle,
                lastDecision = decision,
                activePartner = result.partnerKind,
            )
        }
    }

    private fun toFeedbackItem(w: WireFeedback, turn: Turn): FeedbackItem {
        val asr = turn.transcript?.takeUnless { it.editedByLearner }?.confidence
        // Client-side guard: low recogniser confidence makes form-level judgements suspect.
        val recognitionSuspect = w.possibleRecognitionIssue || (asr != null && asr < 0.6 &&
            w.category in setOf(ErrorCategory.PRONUNCIATION, ErrorCategory.VOCABULARY, ErrorCategory.SPELLING))
        return FeedbackItem(
            id = ids.newId(), sessionId = session.id, turnId = turn.id, original = w.original.ifBlank { turn.text },
            transcript = turn.transcript, intendedMeaning = w.intendedMeaning, intendedMeaningConfident = w.intendedMeaningConfident,
            clarificationQuestion = w.clarificationQuestion, corrected = w.corrected, natural = w.natural, focus = w.focus,
            category = w.category, severity = w.severity, explanation = w.explanation,
            explanationLanguage = profile.active.explanationLanguage, comparison = w.comparison, examples = w.examples,
            recommendedExercise = w.recommendedExercise, suggestReview = w.suggestReview,
            confidence = w.confidence.coerceIn(0.0, 1.0), possibleRecognitionIssue = recognitionSuspect, patternKey = w.patternKey,
        )
    }

    private fun failPending(message: String, retryable: Boolean) {
        val last = session.turns.lastOrNull()
        if (last != null && last.speaker == Speaker.LEARNER && last.status == TurnStatus.PENDING) setTurnStatus(last.id, TurnStatus.FAILED)
        _state.update { it.copy(status = EngineStatus.Error(message, retryable)) }
    }

    private fun setTurnStatus(id: String, status: TurnStatus) {
        _state.update { s -> s.copy(session = s.session.copy(turns = s.session.turns.map { if (it.id == id) it.copy(status = status) else it })) }
    }
}
