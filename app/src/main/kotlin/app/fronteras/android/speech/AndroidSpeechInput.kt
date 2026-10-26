package app.fronteras.android.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.fronteras.core.language.LanguageTag
import app.fronteras.core.speech.RecognitionError
import app.fronteras.core.speech.RecognitionEvent
import app.fronteras.core.speech.TranscriptionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Platform speech recognition. The recogniser's confidence is passed through as-is and is
 * never interpreted as a pronunciation score.
 */
class AndroidSpeechInput(private val context: Context) {
    private var active: SpeechRecognizer? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    val onDeviceAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun listen(language: LanguageTag, extendedResponseTime: Boolean, preferOffline: Boolean): Flow<RecognitionEvent> = callbackFlow {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        active = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(RecognitionEvent.Ready) }
            override fun onBeginningOfSpeech() { trySend(RecognitionEvent.SpeechStarted) }
            override fun onRmsChanged(rmsdB: Float) { trySend(RecognitionEvent.Level(rmsdB)) }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { trySend(RecognitionEvent.SpeechEnded) }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { trySend(RecognitionEvent.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                // Engines report 0 or -1 when they have no confidence estimate.
                val confidence = scores?.firstOrNull()?.takeIf { it > 0f }?.toDouble()
                trySend(RecognitionEvent.Final(TranscriptionResult(
                    text = texts.firstOrNull().orEmpty(),
                    confidence = confidence,
                    alternatives = texts.drop(1),
                    language = language,
                    provider = "android-speech",
                    onDevice = preferOffline && onDeviceAvailable,
                )))
                close()
            }

            override fun onError(error: Int) {
                trySend(RecognitionEvent.Error(map(error)))
                close()
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.value)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            if (extendedResponseTime) {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
            }
        }
        recognizer.startListening(intent)
        awaitClose {
            recognizer.destroy()
            if (active === recognizer) active = null
        }
    }.flowOn(Dispatchers.Main)

    /** Ask the recogniser to finish and deliver results now. */
    fun stop() { active?.stopListening() }

    fun cancel() { active?.cancel() }

    private fun map(code: Int): RecognitionError = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> RecognitionError.NO_MATCH
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> RecognitionError.SPEECH_TIMEOUT
        SpeechRecognizer.ERROR_AUDIO -> RecognitionError.AUDIO
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> RecognitionError.NETWORK
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> RecognitionError.PERMISSION
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> RecognitionError.BUSY
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> RecognitionError.LANGUAGE_UNAVAILABLE
        else -> RecognitionError.OTHER
    }
}
