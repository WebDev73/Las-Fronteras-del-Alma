package app.fronteras.core.srs

import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

@Serializable
enum class Rating(val value: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

/** Memory state of one retrieval track (recognition or production) of a review item. */
@Serializable
data class MemoryState(
    /** Days until retrievability falls to 90%. */
    val stability: Double,
    /** 1 (easy) .. 10 (hard). */
    val difficulty: Double,
    val lastReviewAt: Long,
    val dueAt: Long,
    val reps: Int = 1,
    val lapses: Int = 0,
)

/**
 * FSRS-4.5 scheduler (Free Spaced Repetition Scheduler, open-spaced-repetition project).
 * Default weights are the published FSRS-4.5 defaults; they can be replaced with
 * per-learner optimised weights once enough review logs exist.
 *
 * Only used for card-like items. Conversational competence is tracked separately by
 * [app.fronteras.core.mastery.MasteryEvaluator].
 */
class Fsrs(
    private val w: DoubleArray = DEFAULT_WEIGHTS,
    val desiredRetention: Double = 0.9,
    val maximumIntervalDays: Double = 365.0,
) {
    init {
        require(w.size == 17) { "FSRS-4.5 requires 17 weights" }
        require(desiredRetention in 0.7..0.97) { "desiredRetention out of sensible range" }
    }

    fun retrievability(elapsedDays: Double, stability: Double): Double =
        (1.0 + FACTOR * max(0.0, elapsedDays) / stability).pow(DECAY)

    fun intervalDays(stability: Double): Double {
        val raw = stability / FACTOR * (desiredRetention.pow(1.0 / DECAY) - 1.0)
        return raw.coerceIn(1.0, maximumIntervalDays)
    }

    fun initial(rating: Rating, now: Long): MemoryState {
        val s = initialStability(rating)
        val d = initialDifficulty(rating)
        // A failed first attempt is re-shown soon (10 minutes) rather than after a day.
        val due = if (rating == Rating.AGAIN) now + 10 * MINUTE else now + days(intervalDays(s))
        return MemoryState(s, d, now, due, reps = 1, lapses = if (rating == Rating.AGAIN) 1 else 0)
    }

    fun review(state: MemoryState, rating: Rating, now: Long): MemoryState {
        val elapsed = max(0.0, (now - state.lastReviewAt) / DAY_MS.toDouble())
        val r = retrievability(elapsed, state.stability)
        val d = nextDifficulty(state.difficulty, rating)
        val s = if (rating == Rating.AGAIN) {
            min(state.stability, forgetStability(state.difficulty, state.stability, r))
        } else {
            recallStability(state.difficulty, state.stability, r, rating)
        }
        val due = if (rating == Rating.AGAIN) now + 10 * MINUTE else now + days(intervalDays(s))
        return MemoryState(
            stability = s,
            difficulty = d,
            lastReviewAt = now,
            dueAt = due,
            reps = state.reps + 1,
            lapses = state.lapses + if (rating == Rating.AGAIN) 1 else 0,
        )
    }

    fun initialStability(rating: Rating): Double = max(0.1, w[rating.value - 1])

    fun initialDifficulty(rating: Rating): Double = clampD(w[4] - (rating.value - 3) * w[5])

    private fun nextDifficulty(d: Double, rating: Rating): Double {
        val next = d - w[6] * (rating.value - 3)
        // Mean reversion towards the "Easy" initial difficulty.
        return clampD(w[7] * initialDifficulty(Rating.EASY) + (1 - w[7]) * next)
    }

    private fun recallStability(d: Double, s: Double, r: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.HARD) w[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) w[16] else 1.0
        return s * (1 + exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp((1 - r) * w[10]) - 1) * hardPenalty * easyBonus)
    }

    private fun forgetStability(d: Double, s: Double, r: Double): Double =
        w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp((1 - r) * w[14])

    private fun clampD(d: Double) = d.coerceIn(1.0, 10.0)

    private fun days(d: Double): Long = (d * DAY_MS).roundToLong()

    companion object {
        const val DECAY = -0.5
        const val FACTOR = 19.0 / 81.0
        const val DAY_MS = 86_400_000L
        const val MINUTE = 60_000L

        val DEFAULT_WEIGHTS = doubleArrayOf(
            0.4872, 1.4003, 3.7145, 13.8206, 5.1618, 1.2298, 0.8975, 0.031,
            1.6474, 0.1367, 1.0461, 2.1072, 0.0793, 0.3246, 1.587, 0.2272, 2.8755,
        )
    }
}
