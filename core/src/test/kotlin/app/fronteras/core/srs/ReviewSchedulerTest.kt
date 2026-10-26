package app.fronteras.core.srs

import app.fronteras.core.Fixtures
import app.fronteras.core.feedback.RetrievalMethod
import app.fronteras.core.util.FixedClock
import app.fronteras.core.util.SequentialIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewSchedulerTest {
    private val clock = FixedClock(1_000_000_000L)
    private val scheduler = ReviewScheduler(Fsrs(), clock, SequentialIds("log"))

    private fun item(id: String, importance: Double = 0.5, category: ReviewCategory = ReviewCategory.VOCABULARY) = ReviewItem(
        id = id, target = Fixtures.es, content = "me gustaría", cue = "I would like", explanation = "conditional",
        explanationLanguage = Fixtures.en, category = category, examples = listOf("Me gustaría un té."),
        importance = importance, source = ReviewSource(ReviewSourceType.CONVERSATION, "s1"), createdAt = clock.now,
    )

    @Test
    fun `recognition success does not create a production track`() {
        val r = scheduler.record(item("a"), RetrievalMethod.RECOGNIZE, Rating.GOOD)
        assertNotNull(r.recognition)
        assertNull(r.production, "multiple-choice recall must not count as production")
    }

    @Test
    fun `production success seeds recognition`() {
        val r = scheduler.record(item("a"), RetrievalMethod.TRANSLATE_TO_TARGET, Rating.GOOD)
        assertNotNull(r.production)
        assertNotNull(r.recognition)
        assertEquals(1, r.history.size)
        assertEquals(clock.now, r.lastSuccessfulRetrievalAt)
    }

    @Test
    fun `replay of shuffled logs converges`() {
        var a = item("a")
        a = scheduler.record(a, RetrievalMethod.RECOGNIZE, Rating.GOOD)
        clock.advanceDays(2.0)
        a = scheduler.record(a, RetrievalMethod.RECALL_MISSING_WORD, Rating.HARD)
        clock.advanceDays(5.0)
        a = scheduler.record(a, RetrievalMethod.RECALL_MISSING_WORD, Rating.GOOD)
        val shuffled = a.copy(history = a.history.reversed(), recognition = null, production = null)
        val replayed = scheduler.replay(shuffled)
        assertEquals(a.production!!.stability, replayed.production!!.stability, 1e-9)
        assertEquals(a.recognition!!.dueAt, replayed.recognition!!.dueAt)
    }

    @Test
    fun `queue respects limits and prioritises forgotten important items`() {
        val items = (1..20).map { i ->
            var it = item("i$i", importance = if (i == 7) 1.0 else 0.3)
            it = scheduler.record(it, RetrievalMethod.RECOGNIZE, Rating.GOOD)
            it
        } + (1..5).map { item("new$it") }
        clock.advanceDays(30.0)
        val queue = ReviewQueueBuilder(scheduler, clock).build(items, ReviewQueueBuilder.Limits(maxReviews = 10, maxNew = 2))
        assertEquals(12, queue.size)
        assertEquals("i7", queue.first().item.id)
        assertEquals(2, queue.count { it.item.isNew })
    }

    @Test
    fun `retrieval method moves towards open use as stability grows`() {
        val qb = ReviewQueueBuilder(scheduler, clock)
        val weak = item("w").copy(production = MemoryState(1.0, 5.0, 0, 0))
        val strong = item("s").copy(production = MemoryState(60.0, 5.0, 0, 0))
        assertEquals(RetrievalMethod.RECALL_MISSING_WORD, qb.methodFor(weak, Track.PRODUCTION))
        assertEquals(RetrievalMethod.USE_IN_NEW_SCENARIO, qb.methodFor(strong, Track.PRODUCTION))
        assertEquals(RetrievalMethod.RECOGNIZE, qb.methodFor(strong, Track.RECOGNITION))
    }

    @Test
    fun `rating policy`() {
        assertEquals(Rating.AGAIN, RatingPolicy.fromOutcome(false))
        assertEquals(Rating.HARD, RatingPolicy.fromOutcome(true, hintsUsed = 1))
        assertEquals(Rating.EASY, RatingPolicy.fromOutcome(true, latencyMs = 1_000))
        assertEquals(Rating.HARD, RatingPolicy.fromOutcome(true, latencyMs = 20_000))
        assertEquals(Rating.GOOD, RatingPolicy.fromOutcome(true, hintsUsed = 1, selfReported = Rating.EASY))
        assertTrue(Rating.entries.size == 4)
    }
}
