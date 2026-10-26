package app.fronteras.core.feedback

import app.fronteras.core.language.LanguageTag
import kotlinx.serialization.Serializable

/**
 * Severity ladder. Lower [rank] is more serious. [ACCEPTABLE_VARIANT] is not an error:
 * valid dialectal or colloquial forms are surfaced as information, never as mistakes.
 */
@Serializable
enum class ErrorSeverity(val rank: Int, val label: String) {
    CRITICAL_MEANING(0, "Blocks understanding"),
    SIGNIFICANT(1, "Important grammar or vocabulary error"),
    UNNATURAL(2, "Understandable but unnatural"),
    MINOR_STYLE(3, "Minor stylistic preference"),
    ACCEPTABLE_VARIANT(4, "Acceptable regional or informal alternative"),
}

@Serializable
enum class ErrorCategory {
    GRAMMAR, VOCABULARY, WORD_ORDER, AGREEMENT, CONJUGATION, TENSE, ARTICLE, PREPOSITION,
    PRONUNCIATION, REGISTER, IDIOM, COLLOCATION, SPELLING, FALSE_COGNATE, OTHER,
}

@Serializable
data class TranscriptInfo(
    val recognizedText: String,
    /** 0..1 when the recogniser reports it; null when unknown. */
    val confidence: Double? = null,
    val provider: String,
    val editedByLearner: Boolean = false,
)

/**
 * How the learner will be asked to retrieve an item later. Methods are split into
 * recognition and production so multiple-choice success never counts as speaking mastery.
 */
@Serializable
enum class RetrievalMethod(val production: Boolean, val label: String) {
    RECOGNIZE(false, "Recognise the correct form"),
    TRANSLATE_TO_TARGET(true, "Translate into the target language"),
    RECALL_MISSING_WORD(true, "Recall a missing word"),
    REFORMULATE(true, "Reformulate a sentence"),
    ANSWER_UNEXPECTED(true, "Answer an unexpected question"),
    USE_IN_NEW_SCENARIO(true, "Use the phrase in a new situation"),
    EXPLAIN_CONCEPT(true, "Explain the concept in your own words"),
    CORRECT_ERROR(true, "Spot and correct the error"),
    SPEAK_ALOUD(true, "Say it aloud"),
}

/**
 * Structured analysis of one problem in one learner response (spec §7).
 */
@Serializable
data class FeedbackItem(
    val id: String,
    val sessionId: String,
    val turnId: String,
    val original: String,
    val transcript: TranscriptInfo? = null,
    /** Inferred meaning; null when it cannot reasonably be inferred. */
    val intendedMeaning: String? = null,
    /** When false the system asks the learner rather than inventing an intention. */
    val intendedMeaningConfident: Boolean = true,
    val clarificationQuestion: String? = null,
    val corrected: String? = null,
    val natural: String? = null,
    /** The exact span the issue concerns, for highlighting and review cards. */
    val focus: String? = null,
    val category: ErrorCategory,
    val severity: ErrorSeverity,
    val explanation: String,
    val explanationLanguage: LanguageTag,
    /** Contrast with a language the learner knows, when it genuinely helps. */
    val comparison: String? = null,
    val examples: List<String> = emptyList(),
    val recommendedExercise: RetrievalMethod? = null,
    /** Analyzer's suggestion; final review selection is done by [ReviewSelection]. */
    val suggestReview: Boolean = false,
    /** Analyzer confidence 0..1 in this linguistic judgement. */
    val confidence: Double,
    /** Could plausibly be a speech-recognition artefact rather than a learner error. */
    val possibleRecognitionIssue: Boolean = false,
    /** Stable key so the same underlying error across turns can be counted as recurring. */
    val patternKey: String? = null,
    val source: AnalysisSource = AnalysisSource.AI_MODEL,
)

@Serializable
enum class AnalysisSource { AI_MODEL, RULE_BASED, HUMAN }

/** Layers of feedback; the learner chooses how deep to go. */
@Serializable
enum class FeedbackDepth { QUICK, NATURAL, EXPLANATION, COMPARISON, EXAMPLES }

data class FeedbackView(
    val headline: String,
    val lines: List<Pair<String, String>>,
    val canGoDeeper: Boolean,
)

fun FeedbackItem.view(depth: FeedbackDepth): FeedbackView {
    val lines = mutableListOf<Pair<String, String>>()
    if (severity == ErrorSeverity.ACCEPTABLE_VARIANT) {
        lines += "Note" to explanation
        return FeedbackView("This is fine — a regional or informal alternative", lines, canGoDeeper = examples.isNotEmpty() && depth < FeedbackDepth.EXAMPLES)
    }
    if (!intendedMeaningConfident && clarificationQuestion != null) {
        return FeedbackView(clarificationQuestion, emptyList(), canGoDeeper = false)
    }
    lines += "You said" to original
    corrected?.let { lines += "Corrected" to it }
    if (depth >= FeedbackDepth.NATURAL) natural?.takeIf { it != corrected }?.let { lines += "More natural" to it }
    if (depth >= FeedbackDepth.EXPLANATION) lines += "Why" to explanation
    if (depth >= FeedbackDepth.COMPARISON) comparison?.let { lines += "Compared with what you know" to it }
    if (depth >= FeedbackDepth.EXAMPLES) examples.forEach { lines += "Example" to it }
    if (possibleRecognitionIssue) lines += "Heads-up" to "This may be a speech-recognition error rather than yours. Check the transcript."
    val deeper = when (depth) {
        FeedbackDepth.QUICK -> natural != null || explanation.isNotBlank()
        FeedbackDepth.NATURAL -> explanation.isNotBlank()
        FeedbackDepth.EXPLANATION -> comparison != null || examples.isNotEmpty()
        FeedbackDepth.COMPARISON -> examples.isNotEmpty()
        FeedbackDepth.EXAMPLES -> false
    }
    return FeedbackView(severity.label, lines, deeper)
}
