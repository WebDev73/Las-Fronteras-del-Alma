package app.fronteras.core.curriculum

import app.fronteras.core.assessment.CefrLevel
import app.fronteras.core.assessment.Skill
import app.fronteras.core.profile.LearningGoal
import kotlinx.serialization.Serializable

@Serializable
enum class ActivityType {
    CONTEXT_AND_EXPRESSIONS, GUIDED_DIALOGUE, ROLE_PLAY, FREE_CONVERSATION, LISTENING_TASK,
    PRONUNCIATION_DRILL, GRAMMAR_FOCUS, RETRIEVAL_PRACTICE, STORYTELLING, DEBATE,
}

@Serializable
enum class AssessmentMethod {
    /** Repeatable conversational task scored on the rubric dimensions. */
    CONVERSATION_TASK,
    /** Short structured oral task (e.g. narrate for 3 minutes). */
    ORAL_PRODUCTION_TASK,
    /** Listen and respond appropriately. */
    LISTEN_AND_RESPOND,
    /** Retrieval of specific forms (only proves recognition/production of forms). */
    TARGETED_RETRIEVAL,
}

/**
 * What must be observed before a competency counts as mastered. Completion of a lesson
 * never satisfies these on its own.
 */
@Serializable
data class MasteryCriteria(
    /** Independent successes required (assessment-task or unassisted conversation). */
    val independentSuccesses: Int = 2,
    /** Successes must span at least this many distinct sessions. */
    val distinctSessions: Int = 2,
    /** Minimum days between first practice and a retained success. */
    val retentionDelayDays: Double = 2.0,
    /** Rubric average (0..4) needed on the assessment task. */
    val minRubricScore: Double = 2.5,
    val description: String,
)

@Serializable
data class ReviewPolicy(
    /** Re-check interval after mastery; mastered competencies can decay. */
    val recheckAfterDays: Int = 30,
)

@Serializable
data class Expression(
    val text: String,
    val gloss: String,
    val register: String? = null,
    /** Restrict to specific varieties (empty = all). */
    val variants: List<String> = emptyList(),
)

/**
 * A measurable learning objective (spec §5.1). Definitions are language-independent;
 * expressions are supplied per target language.
 */
@Serializable
data class Competency(
    val id: String,
    val title: String,
    val canDo: String,
    val minLevel: CefrLevel,
    val maxLevel: CefrLevel,
    val skills: Set<Skill>,
    val prerequisites: Set<String> = emptySet(),
    val goals: Set<LearningGoal> = emptySet(),
    val activities: List<ActivityType>,
    val mastery: MasteryCriteria,
    val review: ReviewPolicy = ReviewPolicy(),
    val assessment: AssessmentMethod,
    /** Expressions keyed by base language code. */
    val expressions: Map<String, List<Expression>> = emptyMap(),
    val scenarioIds: List<String> = emptyList(),
    /** Base languages with content for this competency. */
    val languages: Set<String>,
    /** Typical guided practice needed, in minutes (planning estimate, not a promise). */
    val typicalPracticeMinutes: Int = 30,
)

class CompetencyGraph(competencies: List<Competency>) {
    val all: Map<String, Competency> = competencies.associateBy { it.id }

    init {
        require(all.size == competencies.size) { "Duplicate competency ids" }
        competencies.forEach { c ->
            c.prerequisites.forEach { p -> require(p in all) { "${c.id} requires unknown prerequisite $p" } }
            require(c.minLevel <= c.maxLevel) { "${c.id}: minLevel > maxLevel" }
        }
        topologicalOrder() // throws on cycles
    }

    operator fun get(id: String): Competency? = all[id]

    fun forLanguage(base: String): List<Competency> = topologicalOrder().filter { base in it.languages }

    /** Kahn's algorithm; stable by level then id so paths are deterministic. */
    fun topologicalOrder(): List<Competency> {
        val indegree = all.values.associate { it.id to it.prerequisites.size }.toMutableMap()
        val ready = java.util.PriorityQueue(compareBy<Competency>({ it.minLevel }, { it.id }))
        all.values.filter { indegree[it.id] == 0 }.forEach(ready::add)
        val dependents = all.values.flatMap { c -> c.prerequisites.map { it to c } }.groupBy({ it.first }, { it.second })
        val out = mutableListOf<Competency>()
        while (ready.isNotEmpty()) {
            val c = ready.poll()
            out += c
            dependents[c.id].orEmpty().forEach { d ->
                indegree[d.id] = indegree.getValue(d.id) - 1
                if (indegree[d.id] == 0) ready.add(d)
            }
        }
        check(out.size == all.size) { "Competency prerequisites contain a cycle" }
        return out
    }

    /** All transitive prerequisites of [id]. */
    fun ancestors(id: String): Set<String> {
        val seen = mutableSetOf<String>()
        val stack = ArrayDeque(all[id]?.prerequisites.orEmpty())
        while (stack.isNotEmpty()) {
            val p = stack.removeLast()
            if (seen.add(p)) stack.addAll(all[p]?.prerequisites.orEmpty())
        }
        return seen
    }

    fun isUnlocked(id: String, mastered: (String) -> Boolean): Boolean =
        all[id]?.prerequisites?.all(mastered) ?: false
}
