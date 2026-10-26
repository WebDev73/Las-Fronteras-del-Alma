package app.fronteras.core.sync

import app.fronteras.core.Fixtures
import app.fronteras.core.feedback.RetrievalMethod
import app.fronteras.core.srs.Fsrs
import app.fronteras.core.srs.Rating
import app.fronteras.core.srs.ReviewCategory
import app.fronteras.core.srs.ReviewItem
import app.fronteras.core.srs.ReviewScheduler
import app.fronteras.core.srs.ReviewSource
import app.fronteras.core.srs.ReviewSourceType
import app.fronteras.core.util.FixedClock
import app.fronteras.core.util.SequentialIds
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutboxTest {
    private fun entry(key: String, type: ChangeType = ChangeType.REVIEW_ITEM, entity: String = "r1", at: Long = 0) =
        OutboxEntry(key, type, entity, "{}", at)

    @Test
    fun `pending changes to one entity coalesce but evidence is append-only`() {
        var q = Outbox.enqueue(emptyList(), entry("k1"))
        q = Outbox.enqueue(q, entry("k2"))
        assertEquals(listOf("k2"), q.map { it.idempotencyKey })
        q = Outbox.enqueue(q, entry("e1", ChangeType.EVIDENCE_EVENT, "c"))
        q = Outbox.enqueue(q, entry("e2", ChangeType.EVIDENCE_EVENT, "c"))
        assertEquals(3, q.size)
    }

    @Test
    fun `interrupted uploads retry with the same key and back off`() {
        var q = listOf(entry("k1"), entry("k2"), entry("k3"))
        q = Outbox.markInFlight(q, setOf("k1", "k2", "k3"))
        q = Outbox.applyResults(q, listOf(PushResult.Accepted("k1", 1), PushResult.Rejected("k3", "bad")), now = 1000, random = Random(1))
        val k2 = q.first { it.idempotencyKey == "k2" }
        assertEquals(OutboxStatus.PENDING, k2.status)
        assertEquals(1, k2.attempts)
        assertTrue(k2.nextAttemptAt > 1000)
        assertEquals(OutboxStatus.FAILED_PERMANENT, q.first { it.idempotencyKey == "k3" }.status)
        assertEquals(listOf("k2"), Outbox.prune(q).map { it.idempotencyKey } - "k3")
        assertTrue(Outbox.ready(q, now = 1000).isEmpty(), "not ready until backoff elapses")
        assertEquals(listOf("k2"), Outbox.ready(q, now = k2.nextAttemptAt).map { it.idempotencyKey })
    }

    @Test
    fun `gives up after max attempts`() {
        val policy = BackoffPolicy(maxAttempts = 2)
        var q = listOf(entry("k"))
        repeat(2) { q = Outbox.applyResults(Outbox.markInFlight(q, setOf("k")), listOf(PushResult.Retryable("k", "503")), 0, policy) }
        assertEquals(OutboxStatus.FAILED_PERMANENT, q.single().status)
    }

    @Test
    fun `backoff grows and is capped`() {
        val p = BackoffPolicy(baseMs = 1000, maxMs = 10_000, jitter = 0.0)
        assertEquals(2000, p.delayFor(1))
        assertEquals(4000, p.delayFor(2))
        assertEquals(10_000, p.delayFor(10))
    }

    @Test
    fun `review items merged from two devices converge`() {
        val clock = FixedClock(0)
        val scheduler = ReviewScheduler(Fsrs(), clock, SequentialIds("x"))
        val base = ReviewItem("r", Fixtures.es, "vale", "OK", "x", Fixtures.en, ReviewCategory.VOCABULARY, source = ReviewSource(ReviewSourceType.MANUAL), createdAt = 0)
        val a = ReviewScheduler(Fsrs(), FixedClock(86_400_000), SequentialIds("a")).record(base, RetrievalMethod.RECOGNIZE, Rating.GOOD)
        val b = ReviewScheduler(Fsrs(), FixedClock(2 * 86_400_000L), SequentialIds("b")).record(base, RetrievalMethod.TRANSLATE_TO_TARGET, Rating.HARD)
        val resolver = ConflictResolver(scheduler)
        val ab = resolver.mergeReviewItem(a, b)
        val ba = resolver.mergeReviewItem(b, a)
        assertEquals(2, ab.history.size)
        assertEquals(ab.production, ba.production)
        assertEquals(ab.recognition, ba.recognition)
    }

    @Test
    fun `profile merge keeps targets from both devices and honours latest consent`() {
        val resolver = ConflictResolver(ReviewScheduler())
        val local = Fixtures.profile().copy(updatedAt = 10)
        val remoteBase = Fixtures.profile(target = app.fronteras.core.language.LanguageTag("fr-CA"))
        val remote = remoteBase.copy(updatedAt = 20, consents = remoteBase.consents.copy(cloudAi = app.fronteras.core.profile.Consent(false, 30)))
        val merged = resolver.mergeProfile(local, remote)
        assertEquals(2, merged.targets.size)
        assertTrue(!merged.consents.cloudAi.granted)
    }
}
