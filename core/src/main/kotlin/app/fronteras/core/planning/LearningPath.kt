package app.fronteras.core.planning

import app.fronteras.core.assessment.CefrLevel
import app.fronteras.core.curriculum.Competency
import app.fronteras.core.curriculum.CompetencyGraph
import app.fronteras.core.mastery.EvidenceLevel
import app.fronteras.core.mastery.MasteryStatus
import app.fronteras.core.profile.LearningGoal
import app.fronteras.core.profile.TargetLanguageProfile
import kotlinx.serialization.Serializable
import kotlin.math.ceil

@Serializable
data class PathStep(
    val competencyId: String,
    val title: String,
    val canDo: String,
    val status: EvidenceLevel,
    /** Remaining practice estimate in minutes — a planning aid, not a promise. */
    val estimatedMinutes: Int,
    val why: String,
)

@Serializable
data class Milestone(val title: String, val afterStep: Int, val assessment: String)

@Serializable
data class LearningPath(
    val targetOutcome: String,
    val baseline: String,
    val steps: List<PathStep>,
    val milestones: List<Milestone>,
    val estimatedDays: Int,
    val reviewNote: String,
    val completionCriteria: String,
)

/**
 * Generates a personalised path (spec §10) from goals, prerequisites, current mastery,
 * working level and daily time. Mastered steps are kept (for visibility) but cost nothing.
 */
object LearningPathGenerator {
    fun outcomeFor(goals: List<LearningGoal>): Pair<String, String> = when (goals.firstOrNull()) {
        LearningGoal.WORK -> "Hold a five-minute conversation about your work." to "job-interview"
        LearningGoal.TRAVEL -> "Handle an unfamiliar travel situation on your own." to "travel-problem"
        LearningGoal.EXAMINATION -> "Complete a structured oral mock examination." to "five-minute-conversation"
        LearningGoal.EDUCATION -> "Express and defend an opinion." to "express-opinion"
        LearningGoal.IMMIGRATION -> "Handle everyday appointments and services with confidence." to "healthcare-visit"
        LearningGoal.RELATIONSHIPS, LearningGoal.CULTURAL_INTEGRATION -> "Chat naturally with the people in your life." to "natural-expressions"
        LearningGoal.CONVERSATION, LearningGoal.OTHER, null -> "Hold a five-minute conversation with limited help." to "five-minute-conversation"
    }

    fun generate(
        target: TargetLanguageProfile,
        graph: CompetencyGraph,
        mastery: Map<String, MasteryStatus>,
        workingLevel: CefrLevel?,
        baselineDescription: String,
    ): LearningPath {
        val (outcome, goalCompetency) = outcomeFor(target.goals)
        val base = target.target.base
        val level = workingLevel ?: CefrLevel.A1
        val available = graph.forLanguage(base)
        val needed = (graph.ancestors(goalCompetency) + goalCompetency)
        // Goal chain first, then other relevant competencies at or near the learner's level.
        val related = available.filter { c ->
            c.id !in needed && (c.goals.any { it in target.goals }) && c.minLevel <= level.next() && c.maxLevel >= level
        }.map { it.id }
        val ordered = available.filter { it.id in needed || it.id in related }
        val steps = ordered.map { c ->
            val status = mastery[c.id]
            val lvl = status?.level ?: EvidenceLevel.NONE
            PathStep(c.id, c.title, c.canDo, lvl, remainingMinutes(c, lvl, level), reasonFor(c, needed.contains(c.id), level))
        }
        val remaining = steps.sumOf { it.estimatedMinutes }
        val practicePerDay = (target.dailyMinutes * 2 / 3).coerceAtLeast(3) // a third is reserved for review
        val milestones = steps.withIndex()
            .filter { (i, s) -> (i + 1) % 3 == 0 || s.competencyId == goalCompetency }
            .map { (i, s) -> Milestone("Milestone ${i / 3 + 1}: ${s.title}", i, graph[s.competencyId]?.mastery?.description ?: s.canDo) }
            .distinctBy { it.afterStep }
        return LearningPath(
            targetOutcome = outcome,
            baseline = baselineDescription,
            steps = steps,
            milestones = milestones,
            estimatedDays = ceil(remaining / practicePerDay.toDouble()).toInt(),
            reviewNote = "About a third of each day's time goes to review of items drawn from your own conversations.",
            completionCriteria = graph[goalCompetency]?.mastery?.description ?: outcome,
        )
    }

    private fun CefrLevel.next(): CefrLevel = CefrLevel.entries.getOrElse(ordinal + 1) { this }

    private fun remainingMinutes(c: Competency, lvl: EvidenceLevel, learner: CefrLevel): Int {
        val factor = when (lvl) {
            EvidenceLevel.INDEPENDENT -> 0.0
            EvidenceLevel.SPONTANEOUS -> 0.25
            EvidenceLevel.RETAINED -> 0.4
            EvidenceLevel.PRACTISED -> 0.6
            EvidenceLevel.EXPOSED -> 0.9
            EvidenceLevel.NONE -> 1.0
        }
        // Material below the learner's level usually goes faster.
        val levelFactor = if (c.maxLevel < learner) 0.5 else if (c.minLevel > learner) 1.4 else 1.0
        return (c.typicalPracticeMinutes * factor * levelFactor).toInt()
    }

    private fun reasonFor(c: Competency, onGoalChain: Boolean, level: CefrLevel): String = when {
        onGoalChain -> "Needed for your goal."
        c.minLevel > level -> "A stretch at your current level."
        else -> "Relevant to your goals at your level."
    }
}
