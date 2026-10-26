package app.fronteras.core.profile

import app.fronteras.core.assessment.CefrLevel
import app.fronteras.core.language.LanguageTag
import kotlinx.serialization.Serializable

@Serializable
enum class LearningGoal(val label: String) {
    CONVERSATION("Everyday conversation"),
    WORK("Work and professional life"),
    TRAVEL("Travel"),
    EDUCATION("Study and education"),
    IMMIGRATION("Settling in a new country"),
    RELATIONSHIPS("Family, partner and friends"),
    EXAMINATION("Preparing for an examination"),
    CULTURAL_INTEGRATION("Cultural integration"),
    OTHER("Something else"),
}

/** How and when corrections are shown. See [app.fronteras.core.feedback.CorrectionPolicy]. */
@Serializable
enum class FeedbackMode(val label: String, val description: String) {
    CONVERSATION("Conversation", "Keep the conversation flowing; only step in when meaning breaks down."),
    BALANCED("Balanced", "Correct important errors without overwhelming you."),
    INTENSIVE("Intensive", "Detailed feedback after each response."),
    ASSESSMENT("Assessment", "Minimal help until the assessment is finished."),
}

@Serializable
data class KnownLanguage(
    val tag: LanguageTag,
    /** Self-reported; null when the learner prefers not to say. */
    val level: CefrLevel? = null,
)

@Serializable
data class VoicePreferences(
    val voiceName: String? = null,
    /** 1.0 is the engine's normal rate. */
    val speakingRate: Float = 1.0f,
    /** Accent/variety to synthesise, defaults to the target variety. */
    val accent: LanguageTag? = null,
)

@Serializable
data class AccessibilityPreferences(
    val largeText: Boolean = false,
    val reducedMotion: Boolean = false,
    val alwaysShowCaptions: Boolean = true,
    /** Extra time before the microphone treats silence as end of turn. */
    val extendedResponseTime: Boolean = false,
    val preferTextInput: Boolean = false,
)

@Serializable
data class Consent(val granted: Boolean = false, val decidedAt: Long? = null)

/**
 * Explicit, revocable consents. Nothing is sent to cloud AI or retained beyond the
 * session without the matching consent.
 */
@Serializable
data class Consents(
    /** Microphone audio may be processed for speech recognition. */
    val audioProcessing: Consent = Consent(),
    /** Learner text/transcripts may be sent to the cloud AI service. */
    val cloudAi: Consent = Consent(),
    /** Long-term conversational memory across sessions. */
    val conversationMemory: Consent = Consent(),
    /** Raw audio may be kept (bounded by retention policy) for replay. */
    val keepRawAudio: Consent = Consent(),
)

@Serializable
data class TargetLanguageProfile(
    /** Target variety, e.g. `fr-CA`. */
    val target: LanguageTag,
    val selfAssessedLevel: CefrLevel? = null,
    /** Learner override of the provisional level; adaptation continues regardless. */
    val levelOverride: CefrLevel? = null,
    val goals: List<LearningGoal> = listOf(LearningGoal.CONVERSATION),
    val otherGoal: String? = null,
    val speakingSituations: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val dailyMinutes: Int = 10,
    val explanationLanguage: LanguageTag,
    val feedbackMode: FeedbackMode = FeedbackMode.BALANCED,
    val voice: VoicePreferences = VoicePreferences(),
    val createdAt: Long = 0L,
)

@Serializable
data class LearnerProfile(
    val id: String,
    val displayName: String? = null,
    val interfaceLanguage: LanguageTag,
    /** One or more native languages. */
    val nativeLanguages: List<LanguageTag>,
    val otherLanguages: List<KnownLanguage> = emptyList(),
    val targets: List<TargetLanguageProfile>,
    val activeTarget: LanguageTag,
    val accessibility: AccessibilityPreferences = AccessibilityPreferences(),
    val consents: Consents = Consents(),
    val updatedAt: Long = 0L,
) {
    init {
        require(nativeLanguages.isNotEmpty()) { "At least one native language is required" }
        require(targets.isNotEmpty()) { "At least one target language is required" }
        require(targets.any { it.target == activeTarget }) { "Active target must be one of the targets" }
        require(targets.map { it.target }.toSet().size == targets.size) { "Duplicate target language" }
    }

    val active: TargetLanguageProfile get() = targets.first { it.target == activeTarget }

    fun target(tag: LanguageTag): TargetLanguageProfile? = targets.firstOrNull { it.target == tag }

    /** Languages the learner can draw on for explanations and contrastive notes. */
    val supportLanguages: List<LanguageTag>
        get() = (nativeLanguages + otherLanguages.filter { (it.level ?: CefrLevel.B2) >= CefrLevel.B1 }.map { it.tag }).distinct()

    fun updateTarget(tag: LanguageTag, now: Long, transform: (TargetLanguageProfile) -> TargetLanguageProfile): LearnerProfile =
        copy(targets = targets.map { if (it.target == tag) transform(it) else it }, updatedAt = now)
}
