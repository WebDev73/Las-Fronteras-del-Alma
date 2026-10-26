package app.fronteras.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals

class SilenceDetectorTest {
    private fun feed(d: SilenceDetector, db: Float, ms: Long, frame: Long = 50): List<SilenceDetector.Signal> =
        (0 until ms / frame).map { d.onFrame(db, frame) }.filter { it != SilenceDetector.Signal.NONE }

    @Test
    fun `detects speech then end of turn`() {
        val d = SilenceDetector(endSilenceMs = 1000)
        assertEquals(listOf(SilenceDetector.Signal.SPEECH_STARTED), feed(d, -20f, 500))
        assertEquals(emptyList(), feed(d, -60f, 900), "short pause is not the end of the turn")
        assertEquals(emptyList(), feed(d, -20f, 200))
        assertEquals(listOf(SilenceDetector.Signal.END_OF_TURN), feed(d, -60f, 1100))
    }

    @Test
    fun `clicks are ignored and silence times out`() {
        val d = SilenceDetector(noSpeechTimeoutMs = 2000)
        assertEquals(emptyList(), feed(d, -10f, 100)) // 100ms burst < minSpeech
        assertEquals(listOf(SilenceDetector.Signal.NO_SPEECH_TIMEOUT), feed(d, -70f, 2000))
    }

    @Test
    fun `extended response time waits longer`() {
        val d = SilenceDetector.forPreferences(extendedResponseTime = true)
        feed(d, -20f, 400)
        assertEquals(emptyList(), feed(d, -60f, 2000))
        assertEquals(listOf(SilenceDetector.Signal.END_OF_TURN), feed(d, -60f, 600))
    }
}
