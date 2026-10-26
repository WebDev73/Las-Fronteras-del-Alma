package app.fronteras.android.speech

import app.fronteras.core.language.LanguageTag
import app.fronteras.core.speech.TranscriptionResult
import app.fronteras.core.speech.TurnTakingMachine
import app.fronteras.core.speech.VoiceEffect
import app.fronteras.core.speech.VoiceEvent
import app.fronteras.core.speech.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Executes the effects of the core [TurnTakingMachine] against Android speech services. */
class VoiceController(
    private val scope: CoroutineScope,
    private val input: AndroidSpeechInput,
    private val tts: AndroidTts,
    private val language: LanguageTag,
    private val extendedResponseTime: Boolean,
    private val preferOffline: Boolean,
    reviewBeforeSend: Boolean,
    private val onSubmit: (text: String, transcript: TranscriptionResult, edited: Boolean) -> Unit,
    private val onInterruption: (turnId: String, spokenChars: Int) -> Unit,
) {
    private val machine = TurnTakingMachine(reviewTranscriptBeforeSend = reviewBeforeSend)
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()
    private var listenJob: Job? = null

    fun send(event: VoiceEvent) {
        val t = machine.reduce(_state.value, event)
        _state.value = t.state
        t.effects.forEach(::run)
    }

    private fun run(effect: VoiceEffect) {
        when (effect) {
            VoiceEffect.StartRecognizer -> {
                listenJob?.cancel()
                listenJob = scope.launch {
                    input.listen(language, extendedResponseTime, preferOffline).collect { send(VoiceEvent.Recognition(it)) }
                }
            }
            VoiceEffect.StopRecognizer -> input.stop()
            VoiceEffect.CancelRecognizer -> { input.cancel(); listenJob?.cancel() }
            is VoiceEffect.Speak -> tts.speak(effect.text, effect.fromChar,
                progress = { chars -> scope.launch { send(VoiceEvent.SpeechProgress(chars)) } },
                done = { scope.launch { send(VoiceEvent.SpeechDone) } })
            VoiceEffect.StopSpeaking -> tts.stop()
            is VoiceEffect.Submit -> onSubmit(effect.text, effect.transcript, effect.edited)
            is VoiceEffect.RecordInterruption -> onInterruption(effect.turnId, effect.spokenChars)
        }
    }

    fun release() {
        input.cancel()
        tts.stop()
        listenJob?.cancel()
    }
}
