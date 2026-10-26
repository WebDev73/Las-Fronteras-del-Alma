package app.fronteras.core.privacy

import kotlinx.serialization.Serializable

/**
 * Removes common personal identifiers from text before it leaves the device for cloud AI.
 * Language practice rarely needs a real phone number or card number; placeholders keep
 * the sentence grammatical for analysis.
 */
class Redactor(private val enabled: Boolean = true) {
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val card = Regex("\\b(?:\\d[ -]?){13,19}\\b")
    private val phone = Regex("(?<![\\w])\\+?\\d[\\d ().-]{7,}\\d(?![\\w])")
    private val iban = Regex("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b")

    fun redact(text: String): String {
        if (!enabled) return text
        return text
            .replace(email, "[email]")
            .replace(iban, "[account]")
            .replace(card, "[number]")
            .replace(phone, "[phone]")
    }
}

/**
 * Retention defaults are conservative: raw audio is not kept at all unless the learner
 * opts in, and then only for [rawAudioHours].
 */
@Serializable
data class RetentionPolicy(
    val keepRawAudio: Boolean = false,
    val rawAudioHours: Int = 24,
    /** Null keeps transcripts until the learner deletes them. */
    val transcriptDays: Int? = 180,
    val conversationMemory: Boolean = false,
) {
    fun rawAudioExpired(createdAt: Long, now: Long): Boolean =
        !keepRawAudio || now - createdAt > rawAudioHours * 3_600_000L

    fun transcriptExpired(endedAt: Long, now: Long): Boolean =
        transcriptDays != null && now - endedAt > transcriptDays * 86_400_000L
}
