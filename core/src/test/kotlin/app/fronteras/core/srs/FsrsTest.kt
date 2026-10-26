package app.fronteras.core.srs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FsrsTest {
    private val fsrs = Fsrs()
    private val day = Fsrs.DAY_MS

    @Test
    fun `interval equals stability at 90 percent retention`() {
        assertEquals(10.0, fsrs.intervalDays(10.0), 1e-6)
        assertEquals(0.9, fsrs.retrievability(10.0, 10.0), 1e-9)
    }

    @Test
    fun `higher desired retention gives shorter intervals`() {
        val strict = Fsrs(desiredRetention = 0.95)
        assertTrue(strict.intervalDays(10.0) < fsrs.intervalDays(10.0))
    }

    @Test
    fun `initial ratings order stability and difficulty`() {
        val s = Rating.entries.map { fsrs.initialStability(it) }
        assertEquals(s.sorted(), s)
        val d = Rating.entries.map { fsrs.initialDifficulty(it) }
        assertEquals(d.sortedDescending(), d)
    }

    @Test
    fun `successful review grows stability and failure shrinks it`() {
        val start = fsrs.initial(Rating.GOOD, 0)
        val good = fsrs.review(start, Rating.GOOD, (start.stability * day).toLong())
        assertTrue(good.stability > start.stability)
        val again = fsrs.review(good, Rating.AGAIN, good.lastReviewAt + (good.stability * day).toLong())
        assertTrue(again.stability < good.stability)
        assertEquals(1, again.lapses)
        assertTrue(again.dueAt - again.lastReviewAt < day, "failed items return within the day")
    }

    @Test
    fun `easy beats good beats hard`() {
        val start = fsrs.initial(Rating.GOOD, 0)
        val t = (3 * day)
        val hard = fsrs.review(start, Rating.HARD, t).stability
        val good = fsrs.review(start, Rating.GOOD, t).stability
        val easy = fsrs.review(start, Rating.EASY, t).stability
        assertTrue(hard < good && good < easy)
    }

    @Test
    fun `intervals are capped`() {
        val capped = Fsrs(maximumIntervalDays = 30.0)
        assertEquals(30.0, capped.intervalDays(10_000.0))
    }
}
