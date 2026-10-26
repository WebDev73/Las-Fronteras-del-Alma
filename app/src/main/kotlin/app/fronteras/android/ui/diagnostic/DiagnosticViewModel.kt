package app.fronteras.android.ui.diagnostic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fronteras.android.AppContainer
import app.fronteras.android.data.ApiResult
import app.fronteras.core.assessment.AdaptiveDiagnostic
import app.fronteras.core.assessment.CefrLevel
import app.fronteras.core.assessment.DiagnosticItem
import app.fronteras.core.assessment.DiagnosticItemKind
import app.fronteras.core.assessment.DiagnosticResult
import app.fronteras.core.assessment.EvidenceConditions
import app.fronteras.core.assessment.HeuristicOpenScorer
import app.fronteras.core.content.DiagnosticBank
import app.fronteras.core.conversation.ContextBuilder
import app.fronteras.core.conversation.SessionAnalysisRequest
import app.fronteras.core.conversation.SessionMode
import app.fronteras.core.conversation.Speaker
import app.fronteras.core.conversation.WireTurn
import app.fronteras.core.speech.ReadAloudEstimator
import app.fronteras.core.speech.RecognitionEvent
import app.fronteras.core.speech.TranscriptionResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiagnosticUi(
    val item: DiagnosticItem? = null,
    val index: Int = 0,
    val answerText: String = "",
    val transcript: TranscriptionResult? = null,
    val listening: Boolean = false,
    val partial: String = "",
    val speechError: String? = null,
    val scoring: Boolean = false,
    val result: DiagnosticResult? = null,
    val scorerNote: String? = null,
    val itemStartedAt: Long = System.currentTimeMillis(),
)

class DiagnosticViewModel(private val c: AppContainer) : ViewModel() {
    private val profile = requireNotNull(c.repository.profile)
    private val target = profile.activeTarget
    private val engine = AdaptiveDiagnostic(DiagnosticBank.forBase(target.base), profile.active.selfAssessedLevel, c.clock::nowMillis)
    private val _ui = MutableStateFlow(DiagnosticUi(item = engine.next()))
    val ui: StateFlow<DiagnosticUi> = _ui.asStateFlow()
    private var listenJob: Job? = null
    private var asrConfidences = mutableListOf<Double>()

    val speakingRate = profile.active.voice.speakingRate

    fun speak(text: String) {
        c.tts.setLanguage(profile.active.voice.accent ?: target, speakingRate)
        c.tts.speak(text, 0, {}, {})
    }

    fun setText(t: String) { _ui.value = _ui.value.copy(answerText = t) }

    fun listen() {
        listenJob?.cancel()
        _ui.value = _ui.value.copy(listening = true, partial = "", speechError = null)
        listenJob = viewModelScope.launch {
            c.speechInput.listen(target, profile.accessibility.extendedResponseTime, preferOffline = !c.connectivity.online.value).collect { ev ->
                when (ev) {
                    is RecognitionEvent.Partial -> _ui.value = _ui.value.copy(partial = ev.text)
                    is RecognitionEvent.Final -> _ui.value = _ui.value.copy(listening = false, answerText = ev.result.text, transcript = ev.result, partial = "")
                    is RecognitionEvent.Error -> _ui.value = _ui.value.copy(listening = false, speechError = ev.reason.learnerMessage)
                    else -> Unit
                }
            }
        }
    }

    fun stopListening() = c.speechInput.stop()

    fun choose(index: Int?) {
        val item = _ui.value.item ?: return
        engine.answerChoice(item, index, c.clock.nowMillis() - _ui.value.itemStartedAt)
        advance()
    }

    fun submitOpen() {
        val ui = _ui.value
        val item = ui.item ?: return
        if (item.kind == DiagnosticItemKind.READ_ALOUD) {
            val t = ui.transcript ?: return
            engine.answerReadAloud(item, ReadAloudEstimator.estimate(item.prompt, t, profile.active.voice.accent ?: target), t.confidence)
        } else {
            if (ui.answerText.isBlank()) return
            ui.transcript?.confidence?.let { asrConfidences += it }
            engine.recordDeferredOpen(item, ui.answerText.trim())
        }
        advance()
    }

    fun skip() {
        _ui.value.item?.let { engine.skip(it) }
        advance()
    }

    /** Ends early; whatever has been observed so far is used. */
    fun finishNow() = finish()

    private fun advance() {
        val next = engine.next()
        if (next == null) finish() else _ui.value = DiagnosticUi(item = next, index = _ui.value.index + 1)
    }

    private fun finish() {
        _ui.value = _ui.value.copy(item = null, scoring = true)
        viewModelScope.launch {
            val note = scoreOpenResponses()
            val result = engine.result(target.base)
            c.repository.saveDiagnostic(target, result)
            _ui.value = _ui.value.copy(scoring = false, result = result, scorerNote = note)
        }
    }

    /** One batched AI call for all open answers when allowed and available; otherwise the labelled heuristic. */
    private suspend fun scoreOpenResponses(): String? {
        val open = engine.deferredOpenResponses
        if (open.isEmpty()) return null
        val avgAsr = asrConfidences.takeIf { it.isNotEmpty() }?.average()
        val spoken = avgAsr != null
        val aiAllowed = profile.consents.cloudAi.granted && c.connectivity.online.value && c.credentials.endpoint().configured
        if (aiAllowed) {
            val snapshot = ContextBuilder().snapshot(profile, emptyList())
            val turns = open.flatMap { (item, text) -> listOf(WireTurn(Speaker.PARTNER, item.prompt), WireTurn(Speaker.LEARNER, text, avgAsr)) }
            val req = SessionAnalysisRequest(c.ids.newId(), "diagnostic", SessionMode.MOCK_EXAM, snapshot, null, emptyList(), turns)
            val r = c.api.analyze(req)
            if (r is ApiResult.Ok && r.value.rubric.isNotEmpty()) {
                val difficulty = open.map { it.first.difficulty }.average()
                r.value.rubric.forEach { rs ->
                    val conf = EvidenceConditions(spoken = spoken, asrConfidence = avgAsr)
                    // Rubric confidence scales reliability via a fractional observation.
                    engine.observeExternal(rs.skill, rs.score / 4.0, difficulty, conf.copy(heuristicScoring = rs.confidence < 0.5), "open answers scored by AI rubric (${rs.score}/4)")
                }
                return "Your spoken and written answers were scored by the AI tutor."
            }
        }
        open.forEach { (item, text) ->
            val s = HeuristicOpenScorer.score(text, item, EvidenceConditions(spoken = spoken, asrConfidence = avgAsr))
            engine.answerOpen(item, text, s)
        }
        return "Your open answers were estimated offline from length and variety only, so they count for less. Speaking estimates will improve after a few conversations."
    }

    fun override(level: CefrLevel?) {
        viewModelScope.launch { c.repository.setLevelOverride(level) }
    }

    override fun onCleared() {
        listenJob?.cancel()
        c.tts.stop()
    }
}
