package app.fronteras.core.speech

import app.fronteras.core.language.LanguageTag
import java.text.Normalizer

/**
 * App-generated, deliberately modest pronunciation estimate for read-aloud tasks when no
 * phoneme-level provider is configured. It compares what the recogniser heard with the
 * expected text. It cannot tell a mispronunciation from a recognition error, so low-confidence
 * words are reported as UNCERTAIN and the report states its limitations.
 */
object ReadAloudEstimator {
    private const val LONG_PAUSE_MS = 1_000L

    fun estimate(expected: String, result: TranscriptionResult, variety: LanguageTag?): PronunciationReport {
        val exp = tokens(expected)
        val got = tokens(result.text)
        val aligned = align(exp, got)
        val lowConfidence = (result.confidence ?: 1.0) < 0.6
        val words = aligned.map { (e, g) ->
            val match = when {
                e != null && g != null && e == g -> WordMatch.MATCH
                e != null && g != null && editDistance(e, g) <= maxOf(1, e.length / 4) -> WordMatch.CLOSE
                lowConfidence -> WordMatch.UNCERTAIN
                e != null && g != null -> WordMatch.MISMATCH
                e != null -> WordMatch.MISSING
                else -> WordMatch.EXTRA
            }
            WordPronunciation(e, g, match)
        }
        val expectedCount = words.count { it.expected != null }
        val ok = words.count { it.match == WordMatch.MATCH || it.match == WordMatch.CLOSE }
        val timings = result.words
        val wpm = if (timings.size >= 3) {
            val dur = (timings.last().endMs - timings.first().startMs).coerceAtLeast(1)
            timings.size / (dur / 60_000.0)
        } else null
        val pauses = if (timings.size >= 2) timings.zipWithNext().count { (a, b) -> b.startMs - a.endMs > LONG_PAUSE_MS } else null
        val limitations = buildList {
            add("Based on automatic speech recognition, which can mishear correct pronunciation.")
            add("Does not measure individual sounds (phonemes), stress, rhythm or intonation.")
            if (lowConfidence) add("The recogniser was not confident about this recording, so differences are marked uncertain.")
            if (timings.isEmpty()) add("Speech rate and pauses are unavailable: the recogniser did not provide word timings.")
            add("A different accent is not an error. Intelligibility is what this estimate approximates.")
        }
        return PronunciationReport(
            source = PronunciationSource.APP_ESTIMATE,
            measures = "How many of the expected words the speech recogniser understood as intended.",
            limitations = limitations,
            words = words,
            intelligibility = if (expectedCount == 0 || lowConfidence) null else ok.toDouble() / expectedCount,
            wordsPerMinute = wpm,
            longPauses = pauses,
            phonemeLevel = false,
            targetVariety = variety,
        )
    }

    fun tokens(text: String): List<String> =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFC)
            .replace(Regex("[^\\p{L}\\p{N}'’ -]"), " ")
            .split(Regex("[\\s-]+"))
            .map { it.trim('\'', '’') }
            .filter { it.isNotBlank() }

    /** Needleman–Wunsch style alignment returning (expected, recognised) pairs. */
    fun align(a: List<String>, b: List<String>): List<Pair<String?, String?>> {
        val n = a.size; val m = b.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val sub = if (a[i - 1] == b[j - 1]) 0 else if (editDistance(a[i - 1], b[j - 1]) <= maxOf(1, a[i - 1].length / 4)) 1 else 2
            dp[i][j] = minOf(dp[i - 1][j - 1] + sub, dp[i - 1][j] + 1, dp[i][j - 1] + 1)
        }
        val out = ArrayDeque<Pair<String?, String?>>()
        var i = n; var j = m
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                val sub = if (a[i - 1] == b[j - 1]) 0 else if (editDistance(a[i - 1], b[j - 1]) <= maxOf(1, a[i - 1].length / 4)) 1 else 2
                if (dp[i][j] == dp[i - 1][j - 1] + sub) { out.addFirst(a[i - 1] to b[j - 1]); i--; j--; continue }
            }
            if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) { out.addFirst(a[i - 1] to null); i-- } else { out.addFirst(null to b[j - 1]); j-- }
        }
        return out.toList()
    }

    fun editDistance(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }
}
