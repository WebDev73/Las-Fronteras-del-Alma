package app.fronteras.core.speech

import app.fronteras.core.language.LanguageTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TurnTakingTest {
    private val m = TurnTakingMachine()
    private fun result(text: String, conf: Double? = 0.9) = TranscriptionResult(text, conf, language = LanguageTag("es"), provider = "test", onDevice = true)

    @Test
    fun `listen, review and submit`() {
        var t = m.reduce(VoiceState.Idle, VoiceEvent.MicPressed)
        assertEquals(listOf(VoiceEffect.StartRecognizer), t.effects)
        t = m.reduce(t.state, VoiceEvent.Recognition(RecognitionEvent.Partial("hola")))
        assertEquals("hola", (t.state as VoiceState.Listening).partial)
        t = m.reduce(t.state, VoiceEvent.Recognition(RecognitionEvent.Final(result("hola que tal"))))
        assertIs<VoiceState.Reviewing>(t.state)
        t = m.reduce(t.state, VoiceEvent.EditTranscript("Hola, ¿qué tal?"))
        val submit = t.effects.single() as VoiceEffect.Submit
        assertTrue(submit.edited)
        assertIs<VoiceState.Processing>(t.state)
    }

    @Test
    fun `barge-in stops speech, records the interruption and listens`() {
        var t = m.reduce(VoiceState.Processing, VoiceEvent.PartnerReplyReady("p1", "Hola, ¿qué tal estás hoy?"))
        assertIs<VoiceState.Speaking>(t.state)
        t = m.reduce(t.state, VoiceEvent.SpeechProgress(6))
        t = m.reduce(t.state, VoiceEvent.MicPressed)
        assertIs<VoiceState.Listening>(t.state)
        assertTrue(VoiceEffect.StopSpeaking in t.effects)
        assertTrue(t.effects.contains(VoiceEffect.RecordInterruption("p1", 6)))
    }

    @Test
    fun `silence after interruption offers to resume`() {
        var t = m.reduce(VoiceState.Processing, VoiceEvent.PartnerReplyReady("p1", "Una frase bastante larga."))
        t = m.reduce(t.state, VoiceEvent.SpeechProgress(8))
        t = m.reduce(t.state, VoiceEvent.MicPressed)
        t = m.reduce(t.state, VoiceEvent.Recognition(RecognitionEvent.Error(RecognitionError.SPEECH_TIMEOUT)))
        val err = t.state as VoiceState.Error
        val prev = assertIs<VoiceState.Interrupted>(err.previous)
        t = m.reduce(prev, VoiceEvent.ResumeSpeech)
        assertEquals(VoiceEffect.Speak("Una frase bastante larga.", 8), t.effects.single())
    }

    @Test
    fun `a reply never talks over the learner`() {
        val t = m.reduce(VoiceState.Listening(speechDetected = true), VoiceEvent.PartnerReplyReady("p1", "hola"))
        assertIs<VoiceState.Listening>(t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test
    fun `empty recognition becomes a recoverable error`() {
        val t = m.reduce(VoiceState.Listening(), VoiceEvent.Recognition(RecognitionEvent.Final(result(""))))
        assertEquals(RecognitionError.NO_MATCH, (t.state as VoiceState.Error).error)
        assertIs<VoiceState.Listening>(m.reduce(t.state, VoiceEvent.MicPressed).state)
    }

    @Test
    fun `replay repeats the last partner turn`() {
        var t = m.reduce(VoiceState.Processing, VoiceEvent.PartnerReplyReady("p1", "Repite esto"))
        t = m.reduce(t.state, VoiceEvent.SpeechDone)
        t = m.reduce(t.state, VoiceEvent.Replay)
        assertTrue(t.effects.contains(VoiceEffect.Speak("Repite esto")))
    }
}
