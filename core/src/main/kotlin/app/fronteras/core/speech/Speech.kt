package app.fronteras.core.speech

import app.fronteras.core.language.LanguageTag
import kotlinx.serialization.Serializable

@Serializable
data class WordTiming(val word: String, val startMs: Long, val endMs: Long, val confidence: Double? = null)

@Serializable
data class TranscriptionResult(
    val text: String,
    /** Recogniser confidence, not a pronunciation score. */
    val confidence: Double?,
    val alternatives: List<String> = emptyList(),
    val words: List<WordTiming> = emptyList(),
    val language: LanguageTag,
    val provider: String,
    val onDevice: Boolean,
)

sealed interface RecognitionEvent {
    data object Ready : RecognitionEvent
    data object SpeechStarted : RecognitionEvent
    data class Partial(val text: String) : RecognitionEvent
    data class Level(val rmsDb: Float) : RecognitionEvent
    data object SpeechEnded : RecognitionEvent
    data class Final(val result: TranscriptionResult) : RecognitionEvent
    data class Error(val reason: RecognitionError, val detail: String? = null) : RecognitionEvent
}

enum class RecognitionError(val learnerMessage: String, val retryable: Boolean) {
    NO_MATCH("I didn't catch that. Try again, or type your answer.", true),
    SPEECH_TIMEOUT("I didn't hear anything. Tap the microphone when you're ready.", true),
    AUDIO("There's a problem with the microphone.", true),
    NETWORK("Speech recognition needs a connection right now. You can type instead.", true),
    PERMISSION("Microphone permission is needed for voice practice.", false),
    LANGUAGE_UNAVAILABLE("Speech recognition isn't available for this language on this device. You can type instead.", false),
    BUSY("The recogniser is busy. Try again in a moment.", true),
    OTHER("Something went wrong with speech recognition.", true),
}

/** What a pronunciation result is based on — always shown with the result. */
@Serializable
enum class PronunciationSource(val label: String) {
    PROVIDER_SCORE("Score from a pronunciation-assessment provider"),
    APP_ESTIMATE("Estimate by this app from speech recognition"),
    UNAVAILABLE("Pronunciation assessment unavailable"),
}

@Serializable
enum class WordMatch { MATCH, CLOSE, MISMATCH, MISSING, EXTRA, UNCERTAIN }

@Serializable
data class WordPronunciation(
    val expected: String?,
    val recognized: String?,
    val match: WordMatch,
    val timing: WordTiming? = null,
    val providerScore: Double? = null,
)

@Serializable
data class PronunciationReport(
    val source: PronunciationSource,
    /** Plain statement of what was measured. */
    val measures: String,
    val limitations: List<String>,
    val words: List<WordPronunciation> = emptyList(),
    /** Share of expected words recognised as intended (app estimate) or provider intelligibility. */
    val intelligibility: Double? = null,
    val wordsPerMinute: Double? = null,
    val longPauses: Int? = null,
    val phonemeLevel: Boolean = false,
    val targetVariety: LanguageTag? = null,
)

/** Integration point for a real pronunciation-assessment provider (phoneme-level). */
interface PronunciationAssessor {
    val name: String
    suspend fun isAvailable(language: LanguageTag): Boolean
    suspend fun assess(audioPcm16k: ByteArray, expectedText: String, language: LanguageTag): PronunciationReport
}
