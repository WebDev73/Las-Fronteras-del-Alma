package app.fronteras.core.sync

import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.random.Random

@Serializable
enum class OutboxStatus { PENDING, IN_FLIGHT, DONE, FAILED_PERMANENT }

@Serializable
enum class ChangeType { PROFILE, REVIEW_ITEM, EVIDENCE_EVENT, SESSION_SUMMARY, DELETE_ALL }

/**
 * A queued change. [idempotencyKey] is generated once and reused on every retry, so a
 * request that succeeded on the server but whose response was lost is not applied twice.
 */
@Serializable
data class OutboxEntry(
    val idempotencyKey: String,
    val type: ChangeType,
    val entityId: String,
    val payload: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val nextAttemptAt: Long = createdAt,
    val status: OutboxStatus = OutboxStatus.PENDING,
    val lastError: String? = null,
)

@Serializable
data class BackoffPolicy(
    val baseMs: Long = 5_000,
    val maxMs: Long = 6 * 3_600_000L,
    val maxAttempts: Int = 12,
    val jitter: Double = 0.2,
) {
    fun delayFor(attempt: Int, random: Random = Random.Default): Long {
        val exp = baseMs * (1L shl min(attempt, 20))
        val capped = min(exp, maxMs)
        val j = 1.0 + (random.nextDouble() * 2 - 1) * jitter
        return (capped * j).toLong().coerceAtLeast(baseMs)
    }
}

@Serializable
sealed interface PushResult {
    @Serializable data class Accepted(val key: String, val serverVersion: Long) : PushResult
    /** Server had already applied this idempotency key. */
    @Serializable data class Duplicate(val key: String, val serverVersion: Long) : PushResult
    /** Server holds a newer version; client must merge [serverPayload] and re-enqueue. */
    @Serializable data class Conflict(val key: String, val serverPayload: String, val serverVersion: Long) : PushResult
    @Serializable data class Retryable(val key: String, val reason: String) : PushResult
    @Serializable data class Rejected(val key: String, val reason: String) : PushResult
}

/** Pure operations on the outbox list; persistence is the caller's concern. */
object Outbox {
    /**
     * Enqueue a change. Pending changes to the same entity are coalesced (latest payload
     * wins) except append-only types, which are kept individually.
     */
    fun enqueue(entries: List<OutboxEntry>, entry: OutboxEntry): List<OutboxEntry> {
        if (entry.type == ChangeType.EVIDENCE_EVENT) return entries + entry
        val coalesced = entries.filterNot {
            it.status == OutboxStatus.PENDING && it.type == entry.type && it.entityId == entry.entityId
        }
        return coalesced + entry
    }

    fun ready(entries: List<OutboxEntry>, now: Long, batchSize: Int = 50): List<OutboxEntry> =
        entries.filter { it.status == OutboxStatus.PENDING && it.nextAttemptAt <= now }
            .sortedBy { it.createdAt }
            .take(batchSize)

    fun markInFlight(entries: List<OutboxEntry>, keys: Set<String>): List<OutboxEntry> =
        entries.map { if (it.idempotencyKey in keys) it.copy(status = OutboxStatus.IN_FLIGHT) else it }

    /**
     * Applies push results. Entries still IN_FLIGHT without a result (interrupted upload)
     * return to PENDING with backoff, keeping their idempotency key.
     */
    fun applyResults(
        entries: List<OutboxEntry>,
        results: List<PushResult>,
        now: Long,
        policy: BackoffPolicy = BackoffPolicy(),
        random: Random = Random.Default,
    ): List<OutboxEntry> {
        val byKey = results.associateBy { keyOf(it) }
        return entries.map { e ->
            if (e.status != OutboxStatus.IN_FLIGHT) return@map e
            when (val r = byKey[e.idempotencyKey]) {
                is PushResult.Accepted, is PushResult.Duplicate -> e.copy(status = OutboxStatus.DONE, lastError = null)
                is PushResult.Conflict -> e.copy(status = OutboxStatus.DONE, lastError = "conflict merged")
                is PushResult.Rejected -> e.copy(status = OutboxStatus.FAILED_PERMANENT, lastError = r.reason)
                is PushResult.Retryable -> backoff(e, r.reason, now, policy, random)
                null -> backoff(e, "interrupted", now, policy, random)
            }
        }
    }

    fun failAllInFlight(entries: List<OutboxEntry>, reason: String, now: Long, policy: BackoffPolicy = BackoffPolicy()): List<OutboxEntry> =
        entries.map { if (it.status == OutboxStatus.IN_FLIGHT) backoff(it, reason, now, policy, Random.Default) else it }

    fun prune(entries: List<OutboxEntry>): List<OutboxEntry> = entries.filter { it.status != OutboxStatus.DONE }

    private fun backoff(e: OutboxEntry, reason: String, now: Long, policy: BackoffPolicy, random: Random): OutboxEntry {
        val attempts = e.attempts + 1
        return if (attempts >= policy.maxAttempts) {
            e.copy(status = OutboxStatus.FAILED_PERMANENT, attempts = attempts, lastError = "gave up after $attempts attempts: $reason")
        } else {
            e.copy(status = OutboxStatus.PENDING, attempts = attempts, nextAttemptAt = now + policy.delayFor(attempts, random), lastError = reason)
        }
    }

    private fun keyOf(r: PushResult) = when (r) {
        is PushResult.Accepted -> r.key
        is PushResult.Duplicate -> r.key
        is PushResult.Conflict -> r.key
        is PushResult.Retryable -> r.key
        is PushResult.Rejected -> r.key
    }
}
