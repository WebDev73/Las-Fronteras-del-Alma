package app.fronteras.core.planning

import app.fronteras.core.conversation.SessionMode
import app.fronteras.core.curriculum.ActivityType
import app.fronteras.core.curriculum.Competency
import app.fronteras.core.mastery.EvidenceLevel
import app.fronteras.core.mastery.MasteryStatus
import kotlinx.serialization.Serializable

@Serializable
enum class RecommendationKind { DIAGNOSTIC, REVIEW, GUIDED_SESSION, TARGETED_PRACTICE, FREE_CONVERSATION, REASSESS }

@Serializable
data class Recommendation(
    val kind: RecommendationKind,
    val title: String,
    val reason: String,
    val minutes: Int,
    val competencyId: String? = null,
    val scenarioId: String? = null,
    val patternKey: String? = null,
)

/**
 * Chooses the next step of the loop ASSESS → CONVERSE → ANALYZE → EXPLAIN → PRACTISE →
 * REVIEW → REASSESS from current evidence.
 */
object NextStepRecommender {
    fun recommend(
        hasDiagnostic: Boolean,
        dueReviews: Int,
        path: LearningPath,
        mastery: Map<String, MasteryStatus>,
        competencies: Map<String, Competency>,
        recurringPatterns: List<Pair<String, Int>>,
        dailyMinutes: Int,
        sessionsSinceReassessment: Int,
    ): List<Recommendation> {
        val out = mutableListOf<Recommendation>()
        if (!hasDiagnostic) {
            out += Recommendation(RecommendationKind.DIAGNOSTIC, "Find your starting point", "A short, low-pressure check so lessons fit you. You can skip it.", 8)
        }
        if (dueReviews >= 5) {
            out += Recommendation(RecommendationKind.REVIEW, "Review $dueReviews items", "These come from your own conversations and are due before you forget them.",
                minutes = (dueReviews * 20 / 60).coerceIn(2, dailyMinutes))
        }
        recurringPatterns.firstOrNull { it.second >= 3 }?.let { (key, count) ->
            out += Recommendation(RecommendationKind.TARGETED_PRACTICE, "Targeted practice", "This pattern came up in $count sessions. A short focused drill will help.", 5, patternKey = key)
        }
        mastery.values.firstOrNull { it.needsRecheck }?.let { st ->
            competencies[st.competencyId]?.let { c ->
                out += Recommendation(RecommendationKind.REASSESS, "Re-check: ${c.title}", "It's been a while since you last did this independently.", 10, c.id, c.scenarioIds.firstOrNull())
            }
        }
        val next = path.steps.firstOrNull { it.status < EvidenceLevel.INDEPENDENT }
        if (next != null) {
            val c = competencies[next.competencyId]
            val (title, reason) = when (next.status) {
                EvidenceLevel.NONE, EvidenceLevel.EXPOSED -> "Start: ${next.title}" to "Next on your path. ${next.why}"
                EvidenceLevel.PRACTISED -> "Practise again: ${next.title}" to "A second session after a short gap shows whether it stuck."
                EvidenceLevel.RETAINED -> "Use it in conversation: ${next.title}" to "You remember it; now try using it without prompts."
                else -> "Show you can do it: ${next.title}" to "Try the task with little help: ${c?.mastery?.description ?: next.canDo}"
            }
            out += Recommendation(RecommendationKind.GUIDED_SESSION, title, reason, minOf(dailyMinutes, 20), next.competencyId, c?.scenarioIds?.firstOrNull())
        }
        if (sessionsSinceReassessment >= 10 && hasDiagnostic) {
            out += Recommendation(RecommendationKind.REASSESS, "Repeat the check-in", "Comparing the same task over time shows real progress.", 8)
        }
        out += Recommendation(RecommendationKind.FREE_CONVERSATION, "Free conversation", "Talk about anything you like. No lesson, no script.", minOf(dailyMinutes, 10), scenarioId = "free-chat")
        return out.distinctBy { it.kind to it.competencyId }
    }
}

/** One step of a guided session (spec §5.4). */
@Serializable
data class SessionStep(val kind: SessionStepKind, val title: String, val minutes: Int, val activity: ActivityType? = null)

@Serializable
enum class SessionStepKind { OBJECTIVE, CONTEXT_AND_EXPRESSIONS, ACTIVITY, CORRECTIONS, REVIEW_SUMMARY, RETRIEVAL, NEXT_STEP }

@Serializable
data class SessionPlan(val competencyId: String?, val mode: SessionMode, val totalMinutes: Int, val steps: List<SessionStep>)

object SessionPlanner {
    /** Session length options offered in the app. */
    val lengths = listOf(5, 10, 20, 40)

    fun guided(c: Competency, minutes: Int): SessionPlan {
        val activity = c.activities.firstOrNull { it in setOf(ActivityType.ROLE_PLAY, ActivityType.GUIDED_DIALOGUE, ActivityType.STORYTELLING, ActivityType.DEBATE, ActivityType.FREE_CONVERSATION) }
            ?: ActivityType.GUIDED_DIALOGUE
        val steps = when {
            minutes <= 5 -> listOf(
                SessionStep(SessionStepKind.OBJECTIVE, c.canDo, 0),
                SessionStep(SessionStepKind.CONTEXT_AND_EXPRESSIONS, "Two useful expressions", 1, ActivityType.CONTEXT_AND_EXPRESSIONS),
                SessionStep(SessionStepKind.ACTIVITY, "Quick exchange", 3, activity),
                SessionStep(SessionStepKind.REVIEW_SUMMARY, "One thing to remember", 1),
            )
            else -> {
                val core = (minutes * 0.55).toInt().coerceAtLeast(3)
                val context = (minutes * 0.15).toInt().coerceAtLeast(1)
                val retrieval = (minutes * 0.15).toInt().coerceAtLeast(1)
                val review = (minutes - core - context - retrieval).coerceAtLeast(1)
                listOf(
                    SessionStep(SessionStepKind.OBJECTIVE, c.canDo, 0),
                    SessionStep(SessionStepKind.CONTEXT_AND_EXPRESSIONS, "Context and useful expressions", context, ActivityType.CONTEXT_AND_EXPRESSIONS),
                    SessionStep(SessionStepKind.ACTIVITY, activityTitle(activity), core, activity),
                    SessionStep(SessionStepKind.CORRECTIONS, "Selected corrections", 0),
                    SessionStep(SessionStepKind.REVIEW_SUMMARY, "Session review", review),
                    SessionStep(SessionStepKind.RETRIEVAL, "Retrieval practice on weak points", retrieval, ActivityType.RETRIEVAL_PRACTICE),
                    SessionStep(SessionStepKind.NEXT_STEP, "Your next step", 0),
                )
            }
        }
        return SessionPlan(c.id, SessionMode.GUIDED, minutes, steps)
    }

    /** Free conversation is never forced into the guided structure. */
    fun free(minutes: Int) = SessionPlan(null, SessionMode.FREE_CONVERSATION, minutes, listOf(SessionStep(SessionStepKind.ACTIVITY, "Free conversation", minutes, ActivityType.FREE_CONVERSATION)))

    private fun activityTitle(a: ActivityType) = when (a) {
        ActivityType.ROLE_PLAY -> "Role-play"
        ActivityType.STORYTELLING -> "Tell the story"
        ActivityType.DEBATE -> "Debate"
        ActivityType.FREE_CONVERSATION -> "Conversation"
        else -> "Guided dialogue"
    }
}
