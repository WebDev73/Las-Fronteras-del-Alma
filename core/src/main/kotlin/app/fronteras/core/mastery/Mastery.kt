package app.fronteras.core.mastery

import app.fronteras.core.curriculum.Competency
import app.fronteras.core.util.DAY_MILLIS
import kotlinx.serialization.Serializable

/**
 * Ordered evidence levels (spec §3.4). Each requires stronger evidence than the last.
 */
@Serializable
enum class EvidenceLevel(val label: String, val meaning: String) {
    NONE("Not started", "No evidence yet."),
    EXPOSED("Exposed", "Has seen or heard the material."),
    PRACTISED("Practised", "Succeeded during guided practice."),
    RETAINED("Retained", "Succeeded again after a delay."),
    SPONTANEOUS("Applied spontaneously", "Used it unprompted in conversation."),
    INDEPENDENT("Independent", "Performed the task in conversation with little or no help."),
}

@Serializable
enum class EvidenceKind {
    /** Lesson content viewed / heard. Completion alone only ever yields EXPOSED. */
    EXPOSURE,
    GUIDED_PRACTICE,
    RETRIEVAL,
    SPONTANEOUS_USE,
    ASSESSMENT_TASK,
}

@Serializable
enum class Assistance { NONE, LOW, MODERATE, HIGH }

@Serializable
data class EvidenceEvent(
    val id: String,
    val competencyId: String,
    val kind: EvidenceKind,
    val at: Long,
    val success: Boolean,
    val sessionId: String,
    val assistance: Assistance = Assistance.NONE,
    /** Rubric average 0..4 for assessment tasks. */
    val rubricScore: Double? = null,
    /** Evidence reliability 0..1 (see EvidenceReliability). */
    val reliability: Double = 1.0,
    val note: String? = null,
)

data class MasteryStatus(
    val competencyId: String,
    val level: EvidenceLevel,
    /** Human-readable reasons for the level, citing the evidence used. */
    val evidence: List<String>,
    /** What is still needed to reach the next level. */
    val nextRequirement: String?,
    val needsRecheck: Boolean,
) {
    val mastered: Boolean get() = level == EvidenceLevel.INDEPENDENT && !needsRecheck
}

object MasteryEvaluator {
    private const val MIN_RELIABILITY = 0.5

    fun evaluate(competency: Competency, events: List<EvidenceEvent>, now: Long): MasteryStatus {
        val ev = events.filter { it.competencyId == competency.id }.sortedBy { it.at }
        val reliable = ev.filter { it.reliability >= MIN_RELIABILITY }
        val c = competency.mastery
        val reasons = mutableListOf<String>()

        val exposed = ev.isNotEmpty()
        val practiceSuccesses = reliable.filter { it.success && it.kind in setOf(EvidenceKind.GUIDED_PRACTICE, EvidenceKind.RETRIEVAL, EvidenceKind.SPONTANEOUS_USE, EvidenceKind.ASSESSMENT_TASK) }
        val firstPractice = practiceSuccesses.firstOrNull()
        val retained = firstPractice != null && practiceSuccesses.any {
            it.at - firstPractice.at >= (c.retentionDelayDays * DAY_MILLIS).toLong() && it.sessionId != firstPractice.sessionId
        }
        val spontaneous = reliable.any { it.success && it.kind == EvidenceKind.SPONTANEOUS_USE }
        val independent = reliable.filter {
            it.success && it.kind == EvidenceKind.ASSESSMENT_TASK &&
                it.assistance <= Assistance.LOW && (it.rubricScore ?: 0.0) >= c.minRubricScore
        }
        val independentOk = independent.size >= c.independentSuccesses &&
            independent.map { it.sessionId }.distinct().size >= c.distinctSessions

        val level = when {
            independentOk && retained -> EvidenceLevel.INDEPENDENT
            spontaneous && retained -> EvidenceLevel.SPONTANEOUS
            retained -> EvidenceLevel.RETAINED
            firstPractice != null -> EvidenceLevel.PRACTISED
            exposed -> EvidenceLevel.EXPOSED
            else -> EvidenceLevel.NONE
        }

        if (exposed) reasons += "${ev.size} recorded activit${if (ev.size == 1) "y" else "ies"}"
        if (firstPractice != null) reasons += "${practiceSuccesses.size} successful attempt(s) across ${practiceSuccesses.map { it.sessionId }.distinct().size} session(s)"
        if (retained) reasons += "Succeeded again at least ${c.retentionDelayDays.toInt()} days after first practice"
        if (spontaneous) reasons += "Used unprompted in conversation"
        if (independent.isNotEmpty()) reasons += "${independent.size}/${c.independentSuccesses} independent task success(es)"
        val unreliable = ev.size - reliable.size
        if (unreliable > 0) reasons += "$unreliable low-reliability observation(s) not counted (e.g. poor audio)"

        val next = when (level) {
            EvidenceLevel.NONE -> "Start a session on this objective."
            EvidenceLevel.EXPOSED -> "Succeed at it during guided practice."
            EvidenceLevel.PRACTISED -> "Succeed again in a later session, at least ${c.retentionDelayDays.toInt()} days from now."
            EvidenceLevel.RETAINED -> if (!spontaneous) "Use it in conversation without being prompted." else "Complete the assessment task with little help."
            EvidenceLevel.SPONTANEOUS -> "Complete the assessment task with little help: ${c.description}"
            EvidenceLevel.INDEPENDENT -> null
        }
        val lastIndependent = independent.maxOfOrNull { it.at }
        val needsRecheck = level == EvidenceLevel.INDEPENDENT && lastIndependent != null &&
            now - lastIndependent > competency.review.recheckAfterDays * DAY_MILLIS
        return MasteryStatus(competency.id, level, reasons, if (needsRecheck) "Re-check: it has been a while since the last independent success." else next, needsRecheck)
    }
}
