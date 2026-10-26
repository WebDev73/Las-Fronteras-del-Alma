package app.fronteras.core.srs

import app.fronteras.core.feedback.RetrievalMethod
import app.fronteras.core.language.LanguageTag
import kotlinx.serialization.Serializable

@Serializable
enum class ReviewCategory {
    VOCABULARY, FORGOTTEN_WORD, GRAMMAR_PATTERN, CORRECTED_SENTENCE, CONVERSATIONAL_CHUNK,
    IDIOM, COLLOCATION, PRONUNCIATION_TARGET, RECOGNIZED_NOT_PRODUCED,
}

@Serializable
enum class ReviewSourceType { CONVERSATION, LESSON, DIAGNOSTIC, MANUAL }

@Serializable
data class ReviewSource(
    val type: ReviewSourceType,
    val sessionId: String? = null,
    val turnId: String? = null,
    val feedbackId: String? = null,
    /** Short excerpt of where it came from, shown to the learner. */
    val context: String? = null,
)

@Serializable
enum class Track { RECOGNITION, PRODUCTION }

@Serializable
data class ReviewLog(
    val id: String,
    val at: Long,
    val track: Track,
    val method: RetrievalMethod,
    val rating: Rating,
    val latencyMs: Long? = null,
    val hintsUsed: Int = 0,
    /** Logged from a conversation where the learner used the item unprompted. */
    val spontaneous: Boolean = false,
)

/**
 * A reviewable item captured from real learning activity (spec §9). Recognition and
 * production are scheduled independently.
 */
@Serializable
data class ReviewItem(
    val id: String,
    val target: LanguageTag,
    /** Target-language content to retrieve, e.g. "me gustaría". */
    val content: String,
    /** Cue shown to the learner, in the explanation language. */
    val cue: String,
    val explanation: String,
    val explanationLanguage: LanguageTag,
    val category: ReviewCategory,
    /** Example sentence(s) in the target language. */
    val examples: List<String> = emptyList(),
    /** For CORRECT_ERROR drills: the learner's original wrong form. */
    val commonError: String? = null,
    val patternKey: String? = null,
    val competencyIds: List<String> = emptyList(),
    /** 0..1: how much this matters to the learner's goals. */
    val importance: Double = 0.5,
    val source: ReviewSource,
    val createdAt: Long,
    val recognition: MemoryState? = null,
    val production: MemoryState? = null,
    val history: List<ReviewLog> = emptyList(),
    val suspended: Boolean = false,
    val updatedAt: Long = createdAt,
) {
    val lastSuccessfulRetrievalAt: Long?
        get() = history.filter { it.rating != Rating.AGAIN }.maxOfOrNull { it.at }

    val isNew: Boolean get() = history.isEmpty()

    fun state(track: Track): MemoryState? = if (track == Track.RECOGNITION) recognition else production

    /** Next scheduled review across tracks (production preferred once recognition is established). */
    val nextDueAt: Long?
        get() = listOfNotNull(recognition?.dueAt, production?.dueAt).minOrNull()

    val totalLapses: Int get() = (recognition?.lapses ?: 0) + (production?.lapses ?: 0)
}
