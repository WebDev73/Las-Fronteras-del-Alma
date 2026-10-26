package app.fronteras.android.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fronteras.android.AppContainer
import app.fronteras.android.data.ApiResult
import app.fronteras.android.speech.VoiceController
import app.fronteras.core.content.Scenarios
import app.fronteras.core.conversation.ContextBuilder
import app.fronteras.core.conversation.ConversationEngine
import app.fronteras.core.conversation.ConversationState
import app.fronteras.core.conversation.EngineStatus
import app.fronteras.core.conversation.InputMode
import app.fronteras.core.conversation.Scenario
import app.fronteras.core.conversation.SessionAnalysis
import app.fronteras.core.conversation.SessionAnalysisRequest
import app.fronteras.core.conversation.SessionMode
import app.fronteras.core.conversation.Speaker
import app.fronteras.core.conversation.WireTurn
import app.fronteras.core.curriculum.Competency
import app.fronteras.core.feedback.TranscriptInfo
import app.fronteras.core.planning.SessionPlan
import app.fronteras.core.planning.SessionPlanner
import app.fronteras.core.speech.VoiceEvent
import app.fronteras.core.speech.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConversationExtras(
    val voiceMode: Boolean,
    val voiceAvailable: Boolean,
    val ending: Boolean = false,
    val endedSessionId: String? = null,
    val endNote: String? = null,
)

class ConversationViewModel(
    private val c: AppContainer,
    scenarioId: String?,
    competencyId: String?,
    minutes: Int,
) : ViewModel() {
    private val repo = c.repository
    private val profile = requireNotNull(repo.profile)
    val competency: Competency? = competencyId?.let { repo.graph[it] }
    val scenario: Scenario? = (scenarioId ?: competency?.scenarioIds?.firstOrNull())?.let { Scenarios.byId[it] }
    private val comps = listOfNotNull(competency) + scenario?.competencyIds.orEmpty().filter { it != competency?.id }.mapNotNull { repo.graph[it] }
    private val mode = when {
        competency != null && scenario?.mode != SessionMode.FREE_CONVERSATION -> SessionMode.GUIDED
        scenario != null -> scenario.mode
        else -> SessionMode.FREE_CONVERSATION
    }
    val plan: SessionPlan = if (mode == SessionMode.GUIDED && competency != null) SessionPlanner.guided(competency, minutes) else SessionPlanner.free(minutes)
    val expressions = comps.flatMap { cmp -> cmp.expressions[profile.activeTarget.base].orEmpty().filter { it.variants.isEmpty() || profile.activeTarget.value in it.variants } }.distinctBy { it.text }

    private val engine = ConversationEngine(
        initial = repo.newSession(mode, scenario, comps, minutes),
        profile = profile,
        estimates = repo.data.value.estimatesFor(profile.activeTarget),
        scenario = scenario,
        competencies = comps,
        memory = repo.memory(profile.activeTarget),
        primary = c.remotePartner,
        fallback = c.offlinePartner,
        clock = c.clock,
        ids = c.ids,
    )
    val state: StateFlow<ConversationState> = engine.state

    private val voiceAllowed = profile.consents.audioProcessing.granted && c.speechInput.available
    private val _extras = MutableStateFlow(ConversationExtras(voiceMode = voiceAllowed && !profile.accessibility.preferTextInput, voiceAvailable = voiceAllowed))
    val extras: StateFlow<ConversationExtras> = _extras.asStateFlow()

    private var lastPartnerSpokenId: String? = null
    private var partnerFinishedAt: Long? = null

    val voice: VoiceController? = if (voiceAllowed) VoiceController(
        scope = viewModelScope,
        input = c.speechInput,
        tts = c.tts,
        language = profile.activeTarget,
        extendedResponseTime = profile.accessibility.extendedResponseTime,
        preferOffline = !c.connectivity.online.value,
        reviewBeforeSend = repo.data.value.settings.reviewTranscriptBeforeSend,
        onSubmit = { text, t, edited -> submit(text, InputMode.VOICE, TranscriptInfo(t.text, t.confidence, t.provider, editedByLearner = edited)) },
        onInterruption = { id, chars -> engine.markInterrupted(id, chars) },
    ) else null

    val voiceState: StateFlow<VoiceState> = voice?.state ?: MutableStateFlow(VoiceState.Idle)

    init {
        c.tts.setLanguage(profile.active.voice.accent ?: profile.activeTarget, profile.active.voice.speakingRate)
        viewModelScope.launch {
            engine.open()
            onPartnerUpdate()
        }
    }

    private fun onPartnerUpdate() {
        val s = engine.session
        viewModelScope.launch { repo.saveInProgress(s) }
        val last = s.turns.lastOrNull() ?: return
        if (last.speaker == Speaker.PARTNER && last.id != lastPartnerSpokenId) {
            lastPartnerSpokenId = last.id
            partnerFinishedAt = c.clock.nowMillis()
            if (_extras.value.voiceMode) voice?.send(VoiceEvent.PartnerReplyReady(last.id, last.text))
        }
        if (engine.state.value.status is EngineStatus.Error) voice?.send(VoiceEvent.PartnerFailed)
    }

    fun submit(text: String, inputMode: InputMode = InputMode.TEXT, transcript: TranscriptInfo? = null, help: Boolean = false) {
        val latency = partnerFinishedAt?.let { c.clock.nowMillis() - it }
        viewModelScope.launch {
            voice?.send(VoiceEvent.SubmittedToPartner)
            engine.submitLearnerTurn(text, inputMode, transcript, help, latency)
            onPartnerUpdate()
        }
    }

    fun askForHelp(question: String) = submit(question.ifBlank { "?" }, help = true)

    fun retry() = viewModelScope.launch { engine.retry(); onPartnerUpdate() }

    fun editPending(turnId: String, text: String) = viewModelScope.launch { engine.editPendingTurn(turnId, text) }

    fun setVoiceMode(on: Boolean) {
        if (!on) voice?.send(VoiceEvent.Cancel)
        _extras.value = _extras.value.copy(voiceMode = on && voiceAllowed)
    }

    fun voiceEvent(e: VoiceEvent) = voice?.send(e)

    fun replay(text: String) {
        c.tts.speak(text, 0, {}, {})
    }

    /** Ends the session, runs one batched analysis if possible, and updates the learner model. */
    fun end() {
        if (_extras.value.ending) return
        _extras.value = _extras.value.copy(ending = true)
        voice?.release()
        viewModelScope.launch {
            val session = engine.end()
            var note: String? = null
            val analysis: SessionAnalysis? = if (session.learnerTurns.isEmpty()) null
            else if (!profile.consents.cloudAi.granted || !c.connectivity.online.value || !c.credentials.endpoint().configured) {
                note = "This session was saved. A detailed AI analysis needs a connection and AI consent."
                null
            } else {
                val req = SessionAnalysisRequest(
                    requestId = c.ids.newId(), sessionId = session.id, mode = session.mode,
                    learner = ContextBuilder().snapshot(profile, repo.data.value.estimatesFor(profile.activeTarget)),
                    scenario = scenario?.let { app.fronteras.core.conversation.ScenarioBrief(it.title, it.setting, it.partnerRole, it.learnerRole, it.learnerGoal, it.sensitive) },
                    objectives = comps.map { it.canDo },
                    turns = session.turns.filter { it.speaker != Speaker.SYSTEM }.map { WireTurn(it.speaker, app.fronteras.core.privacy.Redactor().redact(it.text), it.transcript?.takeUnless { t -> t.editedByLearner }?.confidence) },
                    alreadyGiven = session.shownFeedback.mapNotNull { it.corrected ?: it.natural },
                )
                when (val r = c.api.analyze(req)) {
                    is ApiResult.Ok -> r.value
                    is ApiResult.Err -> { note = "The detailed analysis couldn't be made (${r.error.message}). Your session and in-conversation feedback are saved."; null }
                }
            }
            repo.finishSession(session, analysis)
            c.requestSync()
            _extras.value = _extras.value.copy(ending = false, endedSessionId = session.id, endNote = note)
        }
    }

    // In-progress sessions are already saved after every turn (see onPartnerUpdate).
    override fun onCleared() {
        voice?.release()
    }
}
