package app.fronteras.core.conversation

import app.fronteras.core.assessment.CefrLevel
import app.fronteras.core.assessment.Skill
import app.fronteras.core.feedback.ErrorCategory
import app.fronteras.core.feedback.ErrorSeverity
import app.fronteras.core.feedback.RetrievalMethod
import app.fronteras.core.language.LanguageTag
import app.fronteras.core.profile.FeedbackMode
import kotlinx.serialization.Serializable

/*
 * Wire contract between the Android client and the Fronteras server. Kept in :core so
 * both sides compile against the same types.
 */

@Serializable
data class LearnerSnapshot(
    val target: LanguageTag,
    val targetName: String,
    val explanationLanguage: LanguageTag,
    val supportLanguages: List<LanguageTag>,
    val estimatedLevel: CefrLevel?,
    val levelUncertain: Boolean,
    val feedbackMode: FeedbackMode,
    val goals: List<String>,
    val interests: List<String> = emptyList(),
)

/** Long-term memory, included only with the learner's conversation-memory consent. */
@Serializable
data class LongTermMemory(
    val recurringErrors: List<String> = emptyList(),
    val recentVocabulary: List<String> = emptyList(),
    val reviewPriorities: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
)

@Serializable
data class ScenarioBrief(
    val title: String,
    val setting: String,
    val partnerRole: String,
    val learnerRole: String,
    val learnerGoal: String,
    val sensitive: Boolean,
)

@Serializable
data class WireTurn(val speaker: Speaker, val text: String, val asrConfidence: Double? = null)

@Serializable
data class PartnerRequest(
    val requestId: String,
    val sessionId: String,
    val mode: SessionMode,
    val learner: LearnerSnapshot,
    val scenario: ScenarioBrief? = null,
    val objectives: List<String> = emptyList(),
    val usefulExpressions: List<String> = emptyList(),
    /** Recent turns, oldest first. The last is the learner turn to answer (or empty to open). */
    val turns: List<WireTurn>,
    /** Summary of older turns when the window was trimmed. */
    val earlierSummary: String? = null,
    val memory: LongTermMemory? = null,
    val helpRequested: Boolean = false,
    /** Variety notes so the partner respects the learner's target variety. */
    val varietyNotes: List<String> = emptyList(),
)

@Serializable
data class WireFeedback(
    val original: String,
    val focus: String? = null,
    val corrected: String? = null,
    val natural: String? = null,
    val category: ErrorCategory,
    val severity: ErrorSeverity,
    val explanation: String,
    val comparison: String? = null,
    val examples: List<String> = emptyList(),
    val intendedMeaning: String? = null,
    val intendedMeaningConfident: Boolean = true,
    val clarificationQuestion: String? = null,
    val possibleRecognitionIssue: Boolean = false,
    val patternKey: String? = null,
    val recommendedExercise: RetrievalMethod? = null,
    val suggestReview: Boolean = false,
    val confidence: Double,
)

@Serializable
data class TurnAnalysis(
    val feedback: List<WireFeedback> = emptyList(),
    /** Communication broke down; partner should repair rather than continue. */
    val comprehensionBreakdown: Boolean = false,
    /** Expressions from the objectives the learner used unprompted. */
    val expressionsUsed: List<String> = emptyList(),
)

/** Streaming events from the server for one partner turn. */
@Serializable
sealed interface PartnerEvent {
    @Serializable
    data class TextDelta(val text: String) : PartnerEvent

    @Serializable
    data class Completed(val reply: String, val analysis: TurnAnalysis?, val partnerKind: PartnerKind) : PartnerEvent

    @Serializable
    data class Failed(val code: String, val message: String, val retryable: Boolean) : PartnerEvent
}

@Serializable
data class RubricScore(
    val skill: Skill,
    /** 0..4 against the framework-aligned descriptor. */
    val score: Double,
    val evidence: List<String>,
    val confidence: Double,
)

@Serializable
data class ExtractedItem(
    val content: String,
    val cue: String,
    val explanation: String,
    val kind: String,
    val example: String? = null,
)

@Serializable
data class SessionAnalysisRequest(
    val requestId: String,
    val sessionId: String,
    val mode: SessionMode,
    val learner: LearnerSnapshot,
    val scenario: ScenarioBrief? = null,
    val objectives: List<String>,
    val turns: List<WireTurn>,
    /** Feedback already given during the session, to avoid repeating it. */
    val alreadyGiven: List<String> = emptyList(),
)

@Serializable
data class SessionAnalysis(
    val summary: String,
    val strengths: List<String>,
    val feedback: List<WireFeedback>,
    val rubric: List<RubricScore>,
    val newItems: List<ExtractedItem>,
    val objectivesMet: List<String>,
    val nextStep: String,
    /** Analyzer's own caveats (short sample, topic familiarity...). */
    val limitations: List<String> = emptyList(),
)

@Serializable
data class ServerCapabilities(
    val aiConversation: Boolean,
    val sessionAnalysis: Boolean,
    val pronunciationScoring: Boolean,
    val reason: String? = null,
    val model: String? = null,
    val dailyTurnLimit: Int? = null,
    val turnsUsedToday: Int? = null,
)

@Serializable
data class ApiError(val code: String, val message: String, val retryable: Boolean = false)
