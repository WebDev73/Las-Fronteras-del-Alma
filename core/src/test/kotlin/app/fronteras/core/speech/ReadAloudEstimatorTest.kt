package app.fronteras.core.speech

import app.fronteras.core.language.LanguageTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadAloudEstimatorTest {
    private val expected = "Buenos días, quería reservar una mesa para dos personas."
    private fun r(text: String, conf: Double? = 0.9, words: List<WordTiming> = emptyList()) =
        TranscriptionResult(text, conf, words = words, language = LanguageTag("es-ES"), provider = "test", onDevice = true)

    @Test
    fun `perfect reading is fully intelligible and labelled as an estimate`() {
        val rep = ReadAloudEstimator.estimate(expected, r("buenos días quería reservar una mesa para dos personas"), LanguageTag("es-ES"))
        assertEquals(1.0, rep.intelligibility)
        assertEquals(PronunciationSource.APP_ESTIMATE, rep.source)
        assertTrue(!rep.phonemeLevel)
        assertTrue(rep.limitations.any { "phonemes" in it })
    }

    @Test
    fun `missing and close words are distinguished`() {
        val rep = ReadAloudEstimator.estimate(expected, r("buenos dias queria reservar mesa para dos personas"), null)
        assertTrue(rep.words.any { it.expected == "una" && it.match == WordMatch.MISSING })
        assertTrue(rep.words.any { it.expected == "días" && it.match == WordMatch.CLOSE })
        assertEquals(8.0 / 9.0, rep.intelligibility!!, 1e-9)
    }

    @Test
    fun `low recogniser confidence yields uncertainty, not blame`() {
        val rep = ReadAloudEstimator.estimate(expected, r("buenos tías quería reservar luna mesa para dos personas", conf = 0.4), null)
        assertNull(rep.intelligibility)
        assertTrue(rep.words.none { it.match == WordMatch.MISMATCH })
    }

    @Test
    fun `speech rate and pauses from word timings`() {
        val words = listOf(WordTiming("a", 0, 400), WordTiming("b", 500, 900), WordTiming("c", 2500, 3000), WordTiming("d", 3100, 3600))
        val rep = ReadAloudEstimator.estimate("a b c d", r("a b c d", words = words), null)
        assertEquals(1, rep.longPauses)
        assertEquals(4 / (3.6 / 60), rep.wordsPerMinute!!, 0.01)
    }
}
