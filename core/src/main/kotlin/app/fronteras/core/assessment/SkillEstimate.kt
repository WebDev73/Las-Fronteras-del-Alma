package app.fronteras.core.assessment

import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A Gaussian belief about one ability on the CEFR-aligned scale.
 *
 * Updated with a Rasch-style (1PL logistic) approximation: each scored observation has a
 * difficulty on the same scale and a reliability weight. Low-reliability evidence (poor
 * audio, low ASR confidence, unfamiliar topic) moves the estimate less instead of being
 * mistaken for inability.
 */
@Serializable
data class SkillEstimate(
    val skill: Skill,
    val mean: Double,
    val sd: Double,
    val observations: Int = 0,
    val updatedAt: Long = 0L,
) {
    val level: CefrLevel get() = CefrLevel.fromScale(mean)

    /** Range of levels compatible with the estimate at ~80% (±1.28 sd). */
    val plausibleRange: ClosedRange<CefrLevel>
        get() = CefrLevel.fromScale(mean - 1.28 * sd)..CefrLevel.fromScale(mean + 1.28 * sd)

    val isSettled: Boolean get() = sd <= SETTLED_SD

    fun describeRange(): String {
        val r = plausibleRange
        return if (r.start == r.endInclusive) r.start.name else "${r.start.name}–${r.endInclusive.name}"
    }

    companion object {
        const val PRIOR_SD = 1.6
        const val SETTLED_SD = 0.55
        const val MIN_SD = 0.25
        const val DISCRIMINATION = 1.7

        fun prior(skill: Skill, selfAssessed: CefrLevel?, now: Long = 0L): SkillEstimate =
            SkillEstimate(
                skill = skill,
                mean = selfAssessed?.scaleCentre ?: 1.5,
                // Self-assessment is informative but not trusted much.
                sd = if (selfAssessed != null) 1.2 else PRIOR_SD,
                updatedAt = now,
            )
    }
}

/**
 * One scored piece of evidence about a skill.
 * @param score 0..1 (partial credit allowed)
 * @param difficulty item/task difficulty on the CEFR scale
 * @param reliability 0..1 weight; see [EvidenceReliability]
 */
@Serializable
data class SkillObservation(
    val skill: Skill,
    val score: Double,
    val difficulty: Double,
    val reliability: Double,
    val at: Long,
    val source: String,
)

object SkillUpdater {
    /** Variance inflation per idle day, so long-unseen skills become uncertain again. */
    private const val DRIFT_VARIANCE_PER_DAY = 0.0025

    fun expectedScore(ability: Double, difficulty: Double): Double =
        1.0 / (1.0 + exp(-SkillEstimate.DISCRIMINATION * (ability - difficulty)))

    fun update(estimate: SkillEstimate, obs: SkillObservation): SkillEstimate {
        require(obs.skill == estimate.skill) { "Observation for ${obs.skill} applied to ${estimate.skill}" }
        val w = obs.reliability.coerceIn(0.0, 1.0)
        if (w == 0.0) return estimate
        val idleDays = max(0.0, (obs.at - estimate.updatedAt) / 86_400_000.0)
        val priorVar = estimate.sd * estimate.sd + if (estimate.observations > 0) idleDays * DRIFT_VARIANCE_PER_DAY else 0.0
        val a = SkillEstimate.DISCRIMINATION
        val p = expectedScore(estimate.mean, obs.difficulty)
        val info = a * a * p * (1 - p)
        val postVar = 1.0 / (1.0 / priorVar + w * info)
        val mean = estimate.mean + postVar * w * a * (obs.score.coerceIn(0.0, 1.0) - p)
        return estimate.copy(
            mean = mean.coerceIn(CefrLevel.SCALE_MIN, CefrLevel.SCALE_MAX),
            sd = max(SkillEstimate.MIN_SD, min(SkillEstimate.PRIOR_SD, sqrt(postVar))),
            observations = estimate.observations + 1,
            updatedAt = obs.at,
        )
    }
}

/**
 * Conditions that make an observation less trustworthy as evidence of ability.
 * Distinguishes genuine inability from recognition errors, noise, topic unfamiliarity
 * and momentary hesitation.
 */
@Serializable
data class EvidenceConditions(
    val spoken: Boolean = false,
    /** Speech-recogniser confidence 0..1, when the provider reports it. */
    val asrConfidence: Double? = null,
    val noisyAudio: Boolean = false,
    val unfamiliarTopic: Boolean = false,
    /** Learner edited the transcript; reflects what they meant, not what was recognised. */
    val transcriptEdited: Boolean = false,
    /** Scored by a heuristic rather than deterministic key or calibrated model. */
    val heuristicScoring: Boolean = false,
)

object EvidenceReliability {
    fun weight(skill: Skill, c: EvidenceConditions): Double {
        var w = 1.0
        if (c.spoken) {
            val asr = c.asrConfidence
            // Recognition errors must not be read as pronunciation or vocabulary failures.
            if (asr != null && !c.transcriptEdited && asr < 0.75) {
                w *= when (skill) {
                    Skill.PRONUNCIATION, Skill.VOCABULARY, Skill.GRAMMAR -> max(0.15, asr)
                    else -> max(0.4, asr)
                }
            }
            if (c.noisyAudio) w *= if (skill == Skill.PRONUNCIATION) 0.3 else 0.6
        }
        if (c.unfamiliarTopic && skill in setOf(Skill.VOCABULARY, Skill.FLUENCY, Skill.SPEAKING)) w *= 0.7
        if (c.heuristicScoring) w *= 0.5
        return w.coerceIn(0.0, 1.0)
    }
}
