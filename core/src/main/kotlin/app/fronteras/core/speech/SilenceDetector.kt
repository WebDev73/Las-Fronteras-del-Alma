package app.fronteras.core.speech

/**
 * Energy-based end-of-turn detector with configurable thresholds, used when the platform
 * recogniser does not expose its own endpointing or when an extended response time is
 * requested for accessibility. Feed it one RMS level (dBFS) per audio frame.
 */
class SilenceDetector(
    private val speechThresholdDb: Float = -40f,
    /** Silence after speech that ends the turn. */
    private val endSilenceMs: Long = 1_200,
    /** Silence before any speech after which we give up listening. */
    private val noSpeechTimeoutMs: Long = 8_000,
    /** Speech must persist this long to count (filters clicks and coughs). */
    private val minSpeechMs: Long = 150,
) {
    enum class Signal { NONE, SPEECH_STARTED, END_OF_TURN, NO_SPEECH_TIMEOUT }

    private var elapsed = 0L
    private var speechRun = 0L
    private var silenceRun = 0L
    private var speaking = false
    private var done = false

    fun reset() {
        elapsed = 0; speechRun = 0; silenceRun = 0; speaking = false; done = false
    }

    fun onFrame(rmsDb: Float, frameMs: Long): Signal {
        if (done) return Signal.NONE
        elapsed += frameMs
        val loud = rmsDb >= speechThresholdDb
        if (loud) {
            speechRun += frameMs
            silenceRun = 0
            if (!speaking && speechRun >= minSpeechMs) {
                speaking = true
                return Signal.SPEECH_STARTED
            }
        } else {
            speechRun = 0
            silenceRun += frameMs
            if (speaking && silenceRun >= endSilenceMs) {
                done = true
                return Signal.END_OF_TURN
            }
            if (!speaking && elapsed >= noSpeechTimeoutMs) {
                done = true
                return Signal.NO_SPEECH_TIMEOUT
            }
        }
        return Signal.NONE
    }

    companion object {
        fun forPreferences(extendedResponseTime: Boolean) =
            if (extendedResponseTime) SilenceDetector(endSilenceMs = 2_500, noSpeechTimeoutMs = 15_000) else SilenceDetector()
    }
}
