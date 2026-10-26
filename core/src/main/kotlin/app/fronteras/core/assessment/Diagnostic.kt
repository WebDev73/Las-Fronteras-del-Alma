package app.fronteras.core.assessment

import app.fronteras.core.speech.PronunciationReport
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
enum class DiagnosticItemKind(val skills: Set<Skill>, val open: Boolean) {
    WARM_UP_CONVERSATION(setOf(Skill.SPEAKING, Skill.INTERACTION), true),
    LISTENING_CHOICE(setOf(Skill.LISTENING), false),
    READING_CHOICE(setOf(Skill.READING), false),
    VOCABULARY_CHOICE(setOf(Skill.VOCABULARY), false),
    GRAMMAR_CHOICE(setOf(Skill.GRAMMAR), false),
    OPEN_SPEAKING(setOf(Skill.SPEAKING, Skill.FLUENCY, Skill.GRAMMAR, Skill.VOCABULARY), true),
    PARAPHRASE(setOf(Skill.INTERACTION, Skill.VOCABULARY), true),
    READ_ALOUD(setOf(Skill.PRONUNCIATION), true),
}

@Serializable
data class DiagnosticItem(
    val id: String,
    val base: String,
    val kind: DiagnosticItemKind,
    val difficulty: Double,
    /** Target-language prompt shown or spoken. */
    val prompt: String,
    /** For listening items: text synthesised by TTS and not shown until answered. */
    val audioText: String? = null,
    /** For reading items: the passage. */
    val passage: String? = null,
    val options: List<String> = emptyList(),
    val correctIndex: Int? = null,
)

/** Score for an open response, with the conditions that bound its reliability. */
@Serializable
data class OpenResponseScore(
    val skillScores: Map<Skill, Double>,
    val conditions: EvidenceConditions,
    val scorer: String,
    val comment: String? = null,
)

@Serializable
data class DiagnosticResponse(
    val itemId: String,
    val chosenIndex: Int? = null,
    val text: String? = null,
    val correct: Boolean? = null,
    val latencyMs: Long? = null,
    val open: OpenResponseScore? = null,
    val skipped: Boolean = false,
)

@Serializable
data class SkillFinding(
    val skill: Skill,
    val estimate: SkillEstimate,
    val evidence: String,
)

@Serializable
data class DiagnosticResult(
    val base: String,
    val completedAt: Long,
    val findings: List<SkillFinding>,
    val provisionalLevel: CefrLevel,
    val provisionalRange: String,
    val explanation: List<String>,
    val limitations: List<String>,
    val itemsAdministered: Int,
)

/**
 * Short adaptive diagnostic (spec §4). Starts with a low-pressure conversational warm-up,
 * then picks, for the most uncertain skill, the unused item whose difficulty is closest to
 * the current estimate (maximum information). Stops when estimates settle or the item cap
 * is reached. Skills are reported separately; the overall level is a provisional summary.
 */
class AdaptiveDiagnostic(
    private val bank: List<DiagnosticItem>,
    selfAssessed: CefrLevel?,
    private val now: () -> Long,
    private val maxItems: Int = 16,
    private val settledSd: Double = 0.75,
    skills: Set<Skill> = Skill.entries.toSet(),
) {
    private val estimates: MutableMap<Skill, SkillEstimate> =
        skills.associateWith { SkillEstimate.prior(it, selfAssessed, now()) }.toMutableMap()
    private val responses = mutableListOf<DiagnosticResponse>()
    private val used = mutableSetOf<String>()
    private val evidenceCount = mutableMapOf<Skill, MutableList<String>>()

    val administered: Int get() = responses.size
    val currentEstimates: Map<Skill, SkillEstimate> get() = estimates.toMap()

    fun next(): DiagnosticItem? {
        if (responses.size >= maxItems) return null
        if (responses.isEmpty()) {
            bank.filter { it.kind == DiagnosticItemKind.WARM_UP_CONVERSATION }.minByOrNull { it.difficulty }?.let { return it }
        }
        val candidates = bank.filter { it.id !in used }
        val bySkill = estimates.values
            .filter { e -> !isSettled(e) && candidates.any { e.skill in it.kind.skills } }
            .sortedByDescending { it.sd }
        val skill = bySkill.firstOrNull()?.skill ?: return null
        val est = estimates.getValue(skill)
        return candidates.filter { skill in it.kind.skills }
            // Prefer items primarily about this skill, then closest difficulty.
            .minWithOrNull(compareBy<DiagnosticItem>({ if (it.kind.skills.size == 1) 0 else 1 }, { abs(it.difficulty - est.mean) }))
    }

    private fun isSettled(e: SkillEstimate) = e.sd <= settledSd

    fun answerChoice(item: DiagnosticItem, chosenIndex: Int?, latencyMs: Long? = null) {
        require(!item.kind.open && item.correctIndex != null)
        val correct = chosenIndex == item.correctIndex
        record(DiagnosticResponse(item.id, chosenIndex = chosenIndex, correct = correct, latencyMs = latencyMs, skipped = chosenIndex == null))
        // "I don't know" is valid evidence of not knowing, scored as incorrect.
        item.kind.skills.forEach { s -> observe(s, if (correct) 1.0 else 0.0, item, EvidenceConditions(), if (correct) "answered a ${CefrLevel.fromScale(item.difficulty)} item correctly" else "missed a ${CefrLevel.fromScale(item.difficulty)} item") }
    }

    fun answerOpen(item: DiagnosticItem, text: String, score: OpenResponseScore) {
        require(item.kind.open)
        record(DiagnosticResponse(item.id, text = text, open = score))
        score.skillScores.forEach { (s, v) ->
            if (s in estimates) observe(s, v, item, score.conditions, "open response scored ${"%.0f".format(v * 100)}% by ${score.scorer}")
        }
    }

    fun answerReadAloud(item: DiagnosticItem, report: PronunciationReport, asrConfidence: Double?) {
        require(item.kind == DiagnosticItemKind.READ_ALOUD)
        record(DiagnosticResponse(item.id, text = report.words.joinToString(" ") { it.recognized ?: "" }))
        val intel = report.intelligibility ?: return // uncertain recording: no evidence either way
        observe(Skill.PRONUNCIATION, intel, item, EvidenceConditions(spoken = true, asrConfidence = asrConfidence, heuristicScoring = report.source != app.fronteras.core.speech.PronunciationSource.PROVIDER_SCORE),
            "read-aloud: ${"%.0f".format(intel * 100)}% of words recognised as intended (${report.source.label.lowercase()})")
    }

    /**
     * Records an open response whose scoring is deferred, so all open answers can be scored
     * together at the end (one AI call instead of one per item).
     */
    fun recordDeferredOpen(item: DiagnosticItem, text: String) {
        require(item.kind.open)
        record(DiagnosticResponse(item.id, text = text))
    }

    /** Applies a score from an external scorer (e.g. a batched AI rubric) to one skill. */
    fun observeExternal(skill: Skill, score: Double, difficulty: Double, conditions: EvidenceConditions, note: String) {
        val est = estimates[skill] ?: return
        val w = EvidenceReliability.weight(skill, conditions)
        estimates[skill] = SkillUpdater.update(est, SkillObservation(skill, score.coerceIn(0.0, 1.0), difficulty, w, now(), "diagnostic:external"))
        evidenceCount.getOrPut(skill) { mutableListOf() } += note
    }

    val deferredOpenResponses: List<Pair<DiagnosticItem, String>>
        get() = responses.filter { it.open == null && it.text != null && !it.skipped }
            .mapNotNull { r -> bank.firstOrNull { it.id == r.itemId && it.kind.open && it.kind != DiagnosticItemKind.READ_ALOUD }?.let { it to r.text!! } }

    fun skip(item: DiagnosticItem) {
        record(DiagnosticResponse(item.id, skipped = true))
    }

    private fun record(r: DiagnosticResponse) {
        responses += r
        used += r.itemId
    }

    private fun observe(skill: Skill, score: Double, item: DiagnosticItem, c: EvidenceConditions, note: String) {
        val est = estimates[skill] ?: return
        val w = EvidenceReliability.weight(skill, c)
        estimates[skill] = SkillUpdater.update(est, SkillObservation(skill, score, item.difficulty, w, now(), "diagnostic:${item.id}"))
        evidenceCount.getOrPut(skill) { mutableListOf() } += note
    }

    fun result(base: String): DiagnosticResult {
        val findings = estimates.values.sortedBy { it.skill.ordinal }.map { e ->
            val notes = evidenceCount[e.skill].orEmpty()
            val evidence = if (notes.isEmpty()) "Not assessed in this diagnostic; estimate is your self-assessment or a default."
            else "${notes.size} observation(s): ${notes.take(3).joinToString("; ")}${if (notes.size > 3) "…" else ""}"
            SkillFinding(e.skill, e, evidence)
        }
        val core = findings.filter { evidenceCount[it.skill].orEmpty().isNotEmpty() }
            .ifEmpty { findings }
        // Precision-weighted mean of assessed skills.
        val weights = core.map { 1.0 / (it.estimate.sd * it.estimate.sd) }
        val mean = core.zip(weights).sumOf { (f, w) -> f.estimate.mean * w } / weights.sum()
        val sd = kotlin.math.sqrt(1.0 / weights.sum()).coerceAtLeast(0.35)
        val lo = CefrLevel.fromScale(mean - 1.28 * sd)
        val hi = CefrLevel.fromScale(mean + 1.28 * sd)
        val strongest = core.maxByOrNull { it.estimate.mean }
        val weakest = core.minByOrNull { it.estimate.mean }
        val explanation = buildList {
            add("Based on $administered response(s) across ${core.size} skill area(s).")
            if (strongest != null && weakest != null && strongest.skill != weakest.skill) {
                add("Strongest: ${strongest.skill.label} (${strongest.estimate.describeRange()}). Most room to grow: ${weakest.skill.label} (${weakest.estimate.describeRange()}).")
            }
            findings.filter { !it.estimate.isSettled }.takeIf { it.isNotEmpty() }?.let {
                add("Still uncertain: ${it.joinToString { f -> f.skill.label }}. Lessons will keep adjusting as we see more.")
            }
        }
        val limitations = listOf(
            "This is a short, provisional estimate aligned with CEFR descriptors — not an official certification.",
            "Open responses were scored automatically; recognition errors and unfamiliar topics can lower the estimate.",
            "You can change this level at any time. The app keeps adapting to what you actually do.",
        )
        return DiagnosticResult(base, now(), findings, CefrLevel.fromScale(mean), if (lo == hi) lo.name else "${lo.name}–${hi.name}", explanation, limitations, administered)
    }
}

/**
 * Offline, clearly-labelled fallback scorer for open responses: uses length and lexical
 * variety only. Its evidence carries half weight ([EvidenceConditions.heuristicScoring]).
 */
object HeuristicOpenScorer {
    fun score(text: String, item: DiagnosticItem, conditions: EvidenceConditions): OpenResponseScore {
        val words = text.split(Regex("\\s+")).filter { it.any(Char::isLetter) }
        val unique = words.map { it.lowercase().trim(',', '.', '!', '?', '¿', '¡') }.toSet().size
        val expectedWords = (8 * item.difficulty).coerceAtLeast(5.0)
        val lengthScore = (words.size / expectedWords).coerceIn(0.0, 1.0)
        val variety = if (words.isEmpty()) 0.0 else (unique.toDouble() / words.size).coerceIn(0.0, 1.0)
        val s = (0.7 * lengthScore + 0.3 * variety).coerceIn(0.0, 1.0)
        val scores = item.kind.skills.filter { it == Skill.SPEAKING || it == Skill.FLUENCY || it == Skill.INTERACTION }.associateWith { s }
        return OpenResponseScore(scores, conditions.copy(heuristicScoring = true), "offline heuristic (length and variety only)")
    }
}
