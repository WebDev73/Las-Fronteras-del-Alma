package app.fronteras.android.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.fronteras.core.language.LanguageTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Text-to-speech with character progress, so an interruption can record exactly how much of
 * the partner's turn was heard and resume from there.
 */
class AndroidTts(context: Context) {
    enum class Status { INITIALISING, READY, LANGUAGE_MISSING, UNAVAILABLE }

    private val _status = MutableStateFlow(Status.INITIALISING)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var offset = 0
    private var onProgress: (Int) -> Unit = {}
    private var onDone: () -> Unit = {}

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { code ->
        _status.value = if (code == TextToSpeech.SUCCESS) Status.READY else Status.UNAVAILABLE
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { onDone() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { onDone() }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) { onProgress(offset + end) }
        })
    }

    /** Selects the target variety's voice. Returns false when voice data is not installed. */
    fun setLanguage(tag: LanguageTag, rate: Float): Boolean {
        if (_status.value == Status.UNAVAILABLE) return false
        val result = tts.setLanguage(Locale.forLanguageTag(tag.value))
        tts.setSpeechRate(rate.coerceIn(0.5f, 1.5f))
        val ok = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ok) _status.value = Status.LANGUAGE_MISSING else if (_status.value == Status.LANGUAGE_MISSING) _status.value = Status.READY
        return ok
    }

    fun speak(text: String, fromChar: Int, progress: (Int) -> Unit, done: () -> Unit) {
        offset = fromChar.coerceIn(0, text.length)
        onProgress = progress
        onDone = done
        tts.speak(text.substring(offset), TextToSpeech.QUEUE_FLUSH, null, "turn-${System.nanoTime()}")
    }

    fun stop() {
        onDone = {}
        tts.stop()
    }

    fun shutdown() = tts.shutdown()
}
