package app.fronteras.core.storage

import app.fronteras.core.assessment.CefrLevel
import app.fronteras.core.assessment.DiagnosticResult
import app.fronteras.core.assessment.Skill
import app.fronteras.core.assessment.SkillEstimate
import app.fronteras.core.conversation.Session
import app.fronteras.core.conversation.SessionAnalysis
import app.fronteras.core.language.LanguageTag
import app.fronteras.core.mastery.EvidenceEvent
import app.fronteras.core.privacy.RetentionPolicy
import app.fronteras.core.profile.LearnerProfile
import app.fronteras.core.profile.OnboardingDraft
import app.fronteras.core.srs.ReviewItem
import app.fronteras.core.sync.OutboxEntry
import kotlinx.serialization.Serializable

/** Point-in-time skill estimates, kept to show trends. */
@Serializable
data class EstimateSnapshot(
    val target: LanguageTag,
    val at: Long,
    val source: String,
    /** Repeatable-task id when the snapshot comes from a comparable assessment. */
    val taskId: String? = null,
    val estimates: List<SkillEstimate>,
)

/** Learner's own confidence rating; never mixed with assessed performance. */
@Serializable
data class ConfidenceReport(val target: LanguageTag, val at: Long, val sessionId: String?, val rating: Int, val note: String? = null)

@Serializable
data class AppSettings(
    val serverUrl: String? = null,
    val retention: RetentionPolicy = RetentionPolicy(),
    val installedPacks: List<String> = emptyList(),
    val voiceInputDefault: Boolean = true,
    val reviewTranscriptBeforeSend: Boolean = true,
)

/**
 * Everything stored about a learner on this device. Also the unit of export: the learner
 * can inspect, correct, export and delete all of it.
 */
@Serializable
data class LearnerData(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val profile: LearnerProfile? = null,
    val onboardingDraft: OnboardingDraft? = null,
    /** Current estimates keyed by target tag value. */
    val estimates: Map<String, List<SkillEstimate>> = emptyMap(),
    val estimateHistory: List<EstimateSnapshot> = emptyList(),
    val diagnostics: Map<String, DiagnosticResult> = emptyMap(),
    val reviewItems: List<ReviewItem> = emptyList(),
    val evidence: List<EvidenceEvent> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val analyses: Map<String, SessionAnalysis> = emptyMap(),
    val confidence: List<ConfidenceReport> = emptyList(),
    val learnerNotes: List<String> = emptyList(),
    val outbox: List<OutboxEntry> = emptyList(),
    val tombstones: Set<String> = emptySet(),
    val settings: AppSettings = AppSettings(),
) {
    fun estimatesFor(target: LanguageTag): List<SkillEstimate> = estimates[target.value].orEmpty()

    fun estimate(target: LanguageTag, skill: Skill): SkillEstimate? = estimatesFor(target).firstOrNull { it.skill == skill }

    fun sessionsFor(target: LanguageTag): List<Session> = sessions.filter { it.target == target }

    fun reviewItemsFor(target: LanguageTag): List<ReviewItem> = reviewItems.filter { it.target == target }

    /** Working level for content selection: override, else assessed estimate, else self-assessment. */
    fun workingLevel(target: LanguageTag): CefrLevel? {
        val t = profile?.target(target) ?: return null
        t.levelOverride?.let { return it }
        val core = estimatesFor(target).filter { it.skill in setOf(Skill.SPEAKING, Skill.GRAMMAR, Skill.VOCABULARY, Skill.LISTENING) }
        if (core.isNotEmpty() && core.any { it.observations > 0 }) return CefrLevel.fromScale(core.map { it.mean }.average())
        return t.selfAssessedLevel
    }

    /** How often each error pattern appeared in previous sessions (for recurrence). */
    fun historicalPatternCounts(target: LanguageTag, excludingSession: String? = null): Map<String, Int> =
        sessionsFor(target).filter { it.id != excludingSession }
            .flatMap { s -> s.allFeedback.mapNotNull { it.patternKey }.distinct() }
            .groupingBy { it }.eachCount()

    companion object {
        const val CURRENT_SCHEMA = 1
    }
}
