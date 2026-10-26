package app.fronteras.core.progress

import app.fronteras.core.assessment.Skill
import app.fronteras.core.assessment.SkillEstimate
import app.fronteras.core.language.LanguageTag
import app.fronteras.core.mastery.EvidenceLevel
import app.fronteras.core.mastery.MasteryStatus
import app.fronteras.core.storage.ConfidenceReport
import app.fronteras.core.storage.EstimateSnapshot
import kotlin.math.sqrt

enum class TrendDirection { IMPROVED, NO_CLEAR_CHANGE, DECLINED, INSUFFICIENT_DATA }

data class SkillTrend(
    val skill: Skill,
    val current: SkillEstimate?,
    val direction: TrendDirection,
    val explanation: String,
)

data class ImprovementExample(val before: String, val after: String, val note: String)

data class ProgressReport(
    val target: LanguageTag,
    val skills: List<SkillTrend>,
    val evidenceCounts: Map<EvidenceLevel, Int>,
    val masteredCompetencies: List<String>,
    /** Self-reported confidence, deliberately separate from assessed performance. */
    val selfConfidence: List<ConfidenceReport>,
    val examples: List<ImprovementExample>,
    val caveats: List<String>,
)

/**
 * Builds a progress view that only claims change when the evidence supports it
 * (spec §10): trends compare like-for-like tasks and require the difference to exceed
 * the combined uncertainty. No invented percentages.
 */
object ProgressCalculator {
    /** z threshold for calling a change real. */
    private const val Z = 1.64

    fun trend(skill: Skill, history: List<EstimateSnapshot>, current: SkillEstimate?): SkillTrend {
        val points = history.mapNotNull { s -> s.estimates.firstOrNull { it.skill == skill && it.observations > 0 }?.let { s to it } }
        if (points.size < 2 || current == null) {
            return SkillTrend(skill, current, TrendDirection.INSUFFICIENT_DATA, "Not enough comparable assessments yet.")
        }
        // Prefer comparing the same repeatable task; otherwise first vs latest with a caveat.
        val byTask = points.filter { it.first.taskId != null }.groupBy { it.first.taskId }.values.firstOrNull { it.size >= 2 }
        val (first, last) = (byTask ?: points).let { it.first() to it.last() }
        val diff = last.second.mean - first.second.mean
        val se = sqrt(first.second.sd * first.second.sd + last.second.sd * last.second.sd)
        val dir = when {
            diff > Z * se -> TrendDirection.IMPROVED
            diff < -Z * se -> TrendDirection.DECLINED
            else -> TrendDirection.NO_CLEAR_CHANGE
        }
        val basis = if (byTask != null) "the same task repeated" else "different tasks (topic and difficulty may differ)"
        val text = when (dir) {
            TrendDirection.IMPROVED -> "Clear improvement from ${first.second.describeRange()} to ${last.second.describeRange()}, based on $basis."
            TrendDirection.DECLINED -> "Lower than before (${first.second.describeRange()} → ${last.second.describeRange()}), based on $basis. Unfamiliar topics or audio problems can cause this."
            TrendDirection.NO_CLEAR_CHANGE -> "No change we can confidently measure yet (currently ${last.second.describeRange()})."
            TrendDirection.INSUFFICIENT_DATA -> ""
        }
        return SkillTrend(skill, current, dir, text)
    }

    fun report(
        target: LanguageTag,
        estimates: List<SkillEstimate>,
        history: List<EstimateSnapshot>,
        mastery: Collection<MasteryStatus>,
        confidence: List<ConfidenceReport>,
        examples: List<ImprovementExample>,
    ): ProgressReport {
        val h = history.filter { it.target == target }.sortedBy { it.at }
        val trends = Skill.entries.map { s -> trend(s, h, estimates.firstOrNull { it.skill == s }) }
        val counts = mastery.groupingBy { it.level }.eachCount()
        return ProgressReport(
            target = target,
            skills = trends,
            evidenceCounts = EvidenceLevel.entries.associateWith { counts[it] ?: 0 },
            masteredCompetencies = mastery.filter { it.mastered }.map { it.competencyId },
            selfConfidence = confidence.filter { it.target == target }.sortedBy { it.at },
            examples = examples,
            caveats = listOf(
                "Levels are estimates aligned with CEFR descriptors, not certification.",
                "Ranges show uncertainty; they narrow as you practise.",
                "Your confidence ratings are shown separately and never change your assessed level.",
            ),
        )
    }

    /**
     * Finds corrected structures the learner later produced correctly — concrete evidence of improvement.
     */
    fun improvementExamples(
        corrections: List<Pair<String, String>>, // original -> corrected
        laterLearnerTexts: List<String>,
        normalize: (String) -> String,
    ): List<ImprovementExample> = corrections.mapNotNull { (orig, fixed) ->
        val key = normalize(fixed)
        if (key.length < 4) return@mapNotNull null
        laterLearnerTexts.firstOrNull { normalize(it).contains(key) }?.let { ImprovementExample(orig, it, "You used the corrected form on your own later.") }
    }.take(5)
}
