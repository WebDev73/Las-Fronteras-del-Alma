package app.fronteras.core.speech

/**
 * Pure state machine for voice turn-taking (spec §6.2). Platform adapters translate
 * microphone/TTS callbacks into [VoiceEvent]s and execute the returned [VoiceEffect]s.
 */
sealed interface VoiceState {
    data object Idle : VoiceState
    data class Listening(val partial: String = "", val speechDetected: Boolean = false) : VoiceState
    /** Transcript ready for the learner to confirm or edit before sending. */
    data class Reviewing(val transcript: TranscriptionResult) : VoiceState
    data object Processing : VoiceState
    data class Speaking(val turnId: String, val text: String, val spokenChars: Int = 0) : VoiceState
    /** Learner barged in; [resumeFrom] lets the partner pick up where it stopped. */
    data class Interrupted(val turnId: String, val text: String, val resumeFrom: Int) : VoiceState
    data class Error(val error: RecognitionError, val previous: VoiceState) : VoiceState
}

sealed interface VoiceEvent {
    data object MicPressed : VoiceEvent
    data object MicReleased : VoiceEvent
    data class Recognition(val event: RecognitionEvent) : VoiceEvent
    data object ConfirmTranscript : VoiceEvent
    data class EditTranscript(val text: String) : VoiceEvent
    data object DiscardTranscript : VoiceEvent
    data object SubmittedToPartner : VoiceEvent
    data class PartnerReplyReady(val turnId: String, val text: String) : VoiceEvent
    data class SpeechProgress(val spokenChars: Int) : VoiceEvent
    data object SpeechDone : VoiceEvent
    data object Replay : VoiceEvent
    data object ResumeSpeech : VoiceEvent
    data object PartnerFailed : VoiceEvent
    data object Cancel : VoiceEvent
}

sealed interface VoiceEffect {
    data object StartRecognizer : VoiceEffect
    data object StopRecognizer : VoiceEffect
    data object CancelRecognizer : VoiceEffect
    data class Speak(val text: String, val fromChar: Int = 0) : VoiceEffect
    data object StopSpeaking : VoiceEffect
    data class Submit(val text: String, val transcript: TranscriptionResult, val edited: Boolean) : VoiceEffect
    data class RecordInterruption(val turnId: String, val spokenChars: Int) : VoiceEffect
}

data class VoiceTransition(val state: VoiceState, val effects: List<VoiceEffect> = emptyList())

class TurnTakingMachine(
    /** Learners can confirm/edit the transcript before it is sent. */
    private val reviewTranscriptBeforeSend: Boolean = true,
    /** Barge-in: pressing the mic while the partner speaks stops speech and listens. */
    private val allowInterruption: Boolean = true,
) {
    private var lastSpoken: VoiceState.Speaking? = null

    fun reduce(state: VoiceState, event: VoiceEvent): VoiceTransition = when (event) {
        VoiceEvent.MicPressed -> when (state) {
            is VoiceState.Idle, is VoiceState.Error, is VoiceState.Interrupted, is VoiceState.Reviewing ->
                VoiceTransition(VoiceState.Listening(), listOf(VoiceEffect.StartRecognizer))
            is VoiceState.Speaking -> if (allowInterruption) {
                VoiceTransition(
                    VoiceState.Listening(),
                    listOf(VoiceEffect.StopSpeaking, VoiceEffect.RecordInterruption(state.turnId, state.spokenChars), VoiceEffect.StartRecognizer),
                )
            } else VoiceTransition(state)
            else -> VoiceTransition(state)
        }
        VoiceEvent.MicReleased -> if (state is VoiceState.Listening) VoiceTransition(state, listOf(VoiceEffect.StopRecognizer)) else VoiceTransition(state)
        is VoiceEvent.Recognition -> onRecognition(state, event.event)
        VoiceEvent.ConfirmTranscript -> if (state is VoiceState.Reviewing) {
            VoiceTransition(VoiceState.Processing, listOf(VoiceEffect.Submit(state.transcript.text, state.transcript, edited = false)))
        } else VoiceTransition(state)
        is VoiceEvent.EditTranscript -> if (state is VoiceState.Reviewing) {
            VoiceTransition(VoiceState.Processing, listOf(VoiceEffect.Submit(event.text, state.transcript, edited = event.text.trim() != state.transcript.text.trim())))
        } else VoiceTransition(state)
        VoiceEvent.DiscardTranscript -> if (state is VoiceState.Reviewing) VoiceTransition(VoiceState.Idle) else VoiceTransition(state)
        VoiceEvent.SubmittedToPartner -> VoiceTransition(VoiceState.Processing)
        is VoiceEvent.PartnerReplyReady -> {
            val speaking = VoiceState.Speaking(event.turnId, event.text)
            lastSpoken = speaking
            // Never talk over the learner: a reply arriving while they speak waits.
            if (state is VoiceState.Listening) VoiceTransition(state) else VoiceTransition(speaking, listOf(VoiceEffect.Speak(event.text)))
        }
        is VoiceEvent.SpeechProgress -> if (state is VoiceState.Speaking) {
            val s = state.copy(spokenChars = event.spokenChars); lastSpoken = s; VoiceTransition(s)
        } else VoiceTransition(state)
        VoiceEvent.SpeechDone -> if (state is VoiceState.Speaking) VoiceTransition(VoiceState.Idle) else VoiceTransition(state)
        VoiceEvent.Replay -> lastSpoken?.let { VoiceTransition(it.copy(spokenChars = 0), listOf(VoiceEffect.StopSpeaking, VoiceEffect.Speak(it.text))) }
            ?: VoiceTransition(state)
        VoiceEvent.ResumeSpeech -> if (state is VoiceState.Interrupted) {
            VoiceTransition(VoiceState.Speaking(state.turnId, state.text, state.resumeFrom), listOf(VoiceEffect.Speak(state.text, state.resumeFrom)))
        } else VoiceTransition(state)
        VoiceEvent.PartnerFailed -> VoiceTransition(VoiceState.Idle)
        VoiceEvent.Cancel -> VoiceTransition(VoiceState.Idle, listOf(VoiceEffect.CancelRecognizer, VoiceEffect.StopSpeaking))
    }

    private fun onRecognition(state: VoiceState, ev: RecognitionEvent): VoiceTransition {
        if (state !is VoiceState.Listening) return VoiceTransition(state)
        return when (ev) {
            RecognitionEvent.Ready, is RecognitionEvent.Level, RecognitionEvent.SpeechEnded -> VoiceTransition(state)
            RecognitionEvent.SpeechStarted -> VoiceTransition(state.copy(speechDetected = true))
            is RecognitionEvent.Partial -> VoiceTransition(state.copy(partial = ev.text, speechDetected = true))
            is RecognitionEvent.Final -> {
                if (ev.result.text.isBlank()) {
                    VoiceTransition(VoiceState.Error(RecognitionError.NO_MATCH, VoiceState.Idle))
                } else if (reviewTranscriptBeforeSend) {
                    VoiceTransition(VoiceState.Reviewing(ev.result))
                } else {
                    VoiceTransition(VoiceState.Processing, listOf(VoiceEffect.Submit(ev.result.text, ev.result, edited = false)))
                }
            }
            is RecognitionEvent.Error -> {
                // If the learner interrupted the partner and then said nothing, offer to resume.
                val interrupted = lastSpoken
                val previous = if (interrupted != null && interrupted.spokenChars in 1 until interrupted.text.length) {
                    VoiceState.Interrupted(interrupted.turnId, interrupted.text, interrupted.spokenChars)
                } else VoiceState.Idle
                VoiceTransition(VoiceState.Error(ev.reason, previous))
            }
        }
    }
}
