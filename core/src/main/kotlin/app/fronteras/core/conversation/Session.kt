package app.fronteras.core.conversation

import app.fronteras.core.feedback.FeedbackItem
import app.fronteras.core.feedback.TranscriptInfo
import app.fronteras.core.language.LanguageTag
import app.fronteras.core.profile.FeedbackMode
import kotlinx.serialization.Serializable

@Serializable
enum class SessionMode(val label: String) {
    FREE_CONVERSATION("Free conversation"),
    ROLE_PLAY("Role-play"),
    GUIDED("Guided session"),
    INTERVIEW("Interview simulation"),
    DEBATE("Debate and opinion"),
    STORYTELLING("Storytelling"),
    MOCK_EXAM("Oral mock exam"),
    DRILL("Structured drill"),
}

@Serializable
enum class Speaker { LEARNER, PARTNER, SYSTEM }

@Serializable
enum class InputMode { TEXT, VOICE }

@Serializable
enum class TurnStatus { COMPLETE, PENDING, FAILED, INTERRUPTED }

@Serializable
data class Turn(
    val id: String,
    val speaker: Speaker,
    val text: String,
    val at: Long,
    val inputMode: InputMode = InputMode.TEXT,
    val transcript: TranscriptInfo? = null,
    val helpRequested: Boolean = false,
    /** Time from partner finishing to learner starting to answer, when measured. */
    val responseLatencyMs: Long? = null,
    val status: TurnStatus = TurnStatus.COMPLETE,
    /** For partner turns cut off by the learner: how much was spoken. */
    val spokenUpTo: Int? = null,
)

@Serializable
data class Session(
    val id: String,
    val learnerId: String,
    val target: LanguageTag,
    val mode: SessionMode,
    val feedbackMode: FeedbackMode,
    val scenarioId: String? = null,
    val competencyIds: List<String> = emptyList(),
    val topic: String? = null,
    val plannedMinutes: Int? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val turns: List<Turn> = emptyList(),
    val shownFeedback: List<FeedbackItem> = emptyList(),
    val deferredFeedback: List<FeedbackItem> = emptyList(),
    /** Which partner produced the replies; shown to the learner. */
    val partnerKind: PartnerKind? = null,
) {
    val learnerTurns: List<Turn> get() = turns.filter { it.speaker == Speaker.LEARNER && it.status == TurnStatus.COMPLETE }
    val allFeedback: List<FeedbackItem> get() = shownFeedback + deferredFeedback
    val isActive: Boolean get() = endedAt == null
}

/** Honest label for whoever is generating partner turns. */
@Serializable
enum class PartnerKind(val label: String) {
    CLOUD_AI("AI conversation partner (online)"),
    ON_DEVICE_AI("AI conversation partner (on-device)"),
    SCRIPTED_OFFLINE("Scripted practice partner (offline — not AI)"),
}
