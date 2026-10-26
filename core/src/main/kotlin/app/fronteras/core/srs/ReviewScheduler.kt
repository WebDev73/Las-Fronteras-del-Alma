package app.fronteras.core.srs

import app.fronteras.core.feedback.RetrievalMethod
import app.fronteras.core.util.Clock
import app.fronteras.core.util.IdGenerator
import kotlin.math.max

/** Converts an observed retrieval into an FSRS rating using outcome, effort and learner input. */
object RatingPolicy {
    fun fromOutcome(
        correct: Boolean,
        latencyMs: Long? = null,
        hintsUsed: Int = 0,
        selfReported: Rating? = null,
        production: Boolean = true,
    ): Rating {
        if (!correct) return Rating.AGAIN
        // Learner judgement is respected but cannot upgrade a hinted answer to EASY.
        if (selfReported != null) return if (hintsUsed > 0 && selfReported == Rating.EASY) Rating.GOOD else selfReported
        if (hintsUsed >= 2) return Rating.HARD
        val slow = if (production) 12_000L else 6_000L
        val fast = if (production) 4_000L else 2_000L
        return when {
            hintsUsed == 1 -> Rating.HARD
            latencyMs == null -> Rating.GOOD
            latencyMs > slow -> Rating.HARD
            latencyMs < fast -> Rating.EASY
            else -> Rating.GOOD
        }
    }
}

class ReviewScheduler(
    private val fsrs: Fsrs = Fsrs(),
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    fun record(
        item: ReviewItem,
        method: RetrievalMethod,
        rating: Rating,
        latencyMs: Long? = null,
        hintsUsed: Int = 0,
        spontaneous: Boolean = false,
    ): ReviewItem {
        val now = clock.nowMillis()
        val track = if (method.production) Track.PRODUCTION else Track.RECOGNITION
        val current = item.state(track)
        val next = if (current == null) fsrs.initial(rating, now) else fsrs.review(current, rating, now)
        val log = ReviewLog(ids.newId(), now, track, method, rating, latencyMs, hintsUsed, spontaneous)
        var updated = item.copy(history = item.history + log, updatedAt = now)
        updated = if (track == Track.PRODUCTION) updated.copy(production = next) else updated.copy(recognition = next)
        // Successful production implies recognition: seed the recognition track if absent.
        if (track == Track.PRODUCTION && rating != Rating.AGAIN && updated.recognition == null) {
            updated = updated.copy(recognition = fsrs.initial(Rating.GOOD, now))
        }
        return updated
    }

    /**
     * Rebuilds memory states from the log alone. Used after sync merges so that two devices
     * that reviewed the same item converge on identical state.
     */
    fun replay(item: ReviewItem): ReviewItem {
        var recognition: MemoryState? = null
        var production: MemoryState? = null
        val logs = item.history.distinctBy { it.id }.sortedWith(compareBy({ it.at }, { it.id }))
        for (log in logs) {
            if (log.track == Track.PRODUCTION) {
                production = production?.let { fsrs.review(it, log.rating, log.at) } ?: fsrs.initial(log.rating, log.at)
                if (log.rating != Rating.AGAIN && recognition == null) recognition = fsrs.initial(Rating.GOOD, log.at)
            } else {
                recognition = recognition?.let { fsrs.review(it, log.rating, log.at) } ?: fsrs.initial(log.rating, log.at)
            }
        }
        return item.copy(history = logs, recognition = recognition, production = production)
    }

    fun retrievability(item: ReviewItem, track: Track): Double? {
        val s = item.state(track) ?: return null
        val elapsed = max(0.0, (clock.nowMillis() - s.lastReviewAt) / Fsrs.DAY_MS.toDouble())
        return fsrs.retrievability(elapsed, s.stability)
    }
}

/** A planned review prompt. */
data class ReviewTask(val item: ReviewItem, val track: Track, val method: RetrievalMethod, val priority: Double, val reason: String)

/**
 * Builds a manageable daily queue: due items prioritised by forgetting risk, importance
 * and lapses, plus a small number of new items.
 */
class ReviewQueueBuilder(private val scheduler: ReviewScheduler, private val clock: Clock = Clock.System) {

    data class Limits(val maxReviews: Int, val maxNew: Int) {
        companion object {
            /** Roughly 20 seconds per review prompt; at most a third of study time for review. */
            fun forDailyMinutes(minutes: Int) = Limits(
                maxReviews = (minutes * 60 / 3 / 20).coerceIn(5, 60),
                maxNew = (minutes / 3).coerceIn(2, 10),
            )
        }
    }

    fun build(items: List<ReviewItem>, limits: Limits, goalCompetencies: Set<String> = emptySet()): List<ReviewTask> {
        val now = clock.nowMillis()
        val active = items.filter { !it.suspended }
        val due = active.filter { !it.isNew && (it.nextDueAt ?: Long.MAX_VALUE) <= now }
            .map { item -> planExisting(item, now, goalCompetencies) }
            .sortedByDescending { it.priority }
            .take(limits.maxReviews)
        val fresh = active.filter { it.isNew }
            .sortedWith(compareByDescending<ReviewItem> { it.importance + goalBoost(it, goalCompetencies) }.thenBy { it.createdAt })
            .take(limits.maxNew)
            .map { ReviewTask(it, Track.RECOGNITION, firstMethod(it), it.importance, "New item") }
        return due + fresh
    }

    private fun goalBoost(item: ReviewItem, goals: Set<String>) = if (item.competencyIds.any { it in goals }) 0.3 else 0.0

    private fun planExisting(item: ReviewItem, now: Long, goals: Set<String>): ReviewTask {
        val prodDue = item.production?.let { it.dueAt <= now } ?: (item.recognition != null)
        val track = if (prodDue) Track.PRODUCTION else Track.RECOGNITION
        val r = scheduler.retrievability(item, track) ?: 0.5
        val priority = (1 - r) * (0.5 + item.importance + goalBoost(item, goals)) * (1 + 0.25 * item.totalLapses)
        val reason = buildString {
            append(if (track == Track.PRODUCTION) "Practise producing it" else "Check you recognise it")
            if (item.totalLapses > 0) append(" · forgotten ${item.totalLapses}×")
        }
        return ReviewTask(item, track, methodFor(item, track), priority, reason)
    }

    private fun firstMethod(item: ReviewItem): RetrievalMethod =
        if (item.category == ReviewCategory.CORRECTED_SENTENCE && item.commonError != null) RetrievalMethod.CORRECT_ERROR else RetrievalMethod.RECOGNIZE

    /** Vary retrieval: move from easier cued recall towards open use as stability grows. */
    fun methodFor(item: ReviewItem, track: Track): RetrievalMethod {
        if (track == Track.RECOGNITION) return RetrievalMethod.RECOGNIZE
        val s = item.production?.stability ?: 0.0
        val recentMethods = item.history.takeLast(2).map { it.method }.toSet()
        val ladder = when (item.category) {
            ReviewCategory.PRONUNCIATION_TARGET -> listOf(RetrievalMethod.SPEAK_ALOUD)
            ReviewCategory.CORRECTED_SENTENCE, ReviewCategory.GRAMMAR_PATTERN ->
                listOf(RetrievalMethod.CORRECT_ERROR, RetrievalMethod.REFORMULATE, RetrievalMethod.TRANSLATE_TO_TARGET, RetrievalMethod.USE_IN_NEW_SCENARIO)
            ReviewCategory.CONVERSATIONAL_CHUNK, ReviewCategory.IDIOM, ReviewCategory.COLLOCATION ->
                listOf(RetrievalMethod.RECALL_MISSING_WORD, RetrievalMethod.TRANSLATE_TO_TARGET, RetrievalMethod.USE_IN_NEW_SCENARIO, RetrievalMethod.ANSWER_UNEXPECTED)
            else -> listOf(RetrievalMethod.RECALL_MISSING_WORD, RetrievalMethod.TRANSLATE_TO_TARGET, RetrievalMethod.SPEAK_ALOUD, RetrievalMethod.USE_IN_NEW_SCENARIO)
        }.filter { it != RetrievalMethod.CORRECT_ERROR || item.commonError != null }
            .filter { it != RetrievalMethod.RECALL_MISSING_WORD || item.examples.isNotEmpty() }
            .ifEmpty { listOf(RetrievalMethod.TRANSLATE_TO_TARGET) }
        val stage = when {
            s < 3 -> 0
            s < 10 -> 1
            s < 30 -> 2
            else -> 3
        }.coerceAtMost(ladder.lastIndex)
        val preferred = ladder[stage]
        return if (preferred in recentMethods && ladder.size > 1) ladder[(stage + 1) % ladder.size] else preferred
    }
}
