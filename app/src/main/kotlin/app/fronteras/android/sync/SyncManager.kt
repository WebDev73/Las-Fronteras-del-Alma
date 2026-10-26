package app.fronteras.android.sync

import app.fronteras.android.data.ApiClient
import app.fronteras.android.data.ApiResult
import app.fronteras.android.data.Credentials
import app.fronteras.android.data.LearnerRepository
import app.fronteras.core.mastery.EvidenceEvent
import app.fronteras.core.profile.LearnerProfile
import app.fronteras.core.srs.ReviewItem
import app.fronteras.core.storage.FronterasJson
import app.fronteras.core.sync.ChangeType
import app.fronteras.core.sync.ConflictResolver
import app.fronteras.core.sync.Outbox
import app.fronteras.core.sync.OutboxStatus
import app.fronteras.core.sync.SyncPushRequest
import app.fronteras.core.sync.toChange
import app.fronteras.core.util.Clock

/**
 * Pushes queued changes and pulls remote ones. Safe to interrupt at any point: entries keep
 * their idempotency keys, and merges are deterministic on both client and server.
 */
class SyncManager(
    private val repo: LearnerRepository,
    private val api: ApiClient,
    private val credentials: Credentials,
    private val clock: Clock,
) {
    private val resolver = ConflictResolver(repo.scheduler)

    sealed interface Result {
        data class Done(val pushed: Int, val pulled: Int) : Result
        data class Retry(val reason: String) : Result
        data object NotConfigured : Result
    }

    suspend fun syncOnce(): Result {
        if (!credentials.endpoint().configured) return Result.NotConfigured
        if (repo.data.value.profile?.consents?.cloudAi?.granted != true) return Result.NotConfigured
        val pushed = push() ?: return Result.Retry("push failed")
        val pulled = pull() ?: return Result.Retry("pull failed")
        return Result.Done(pushed, pulled)
    }

    private suspend fun push(): Int? {
        val now = clock.nowMillis()
        val batch = Outbox.ready(repo.data.value.outbox, now)
        if (batch.isEmpty()) return 0
        val keys = batch.map { it.idempotencyKey }.toSet()
        repo.updateData { it.copy(outbox = Outbox.markInFlight(it.outbox, keys)) }
        return when (val r = api.syncPush(SyncPushRequest(credentials.deviceId(), batch.map { it.toChange() }))) {
            is ApiResult.Ok -> {
                repo.updateData { it.copy(outbox = Outbox.prune(Outbox.applyResults(it.outbox, r.value.results, clock.nowMillis()))) }
                batch.size
            }
            is ApiResult.Err -> {
                repo.updateData { it.copy(outbox = Outbox.failAllInFlight(it.outbox, r.error.code, clock.nowMillis())) }
                null
            }
        }
    }

    private suspend fun pull(): Int? {
        var since = credentials.lastPulledVersion()
        var total = 0
        while (true) {
            val r = api.syncPull(since)
            if (r !is ApiResult.Ok) return null
            val records = r.value.records
            repo.updateData { d ->
                var data = d
                records.forEach { rec ->
                    val tomb = "${rec.type.name}:${rec.entityId}"
                    if (rec.deleted) return@forEach
                    if (tomb in data.tombstones) return@forEach
                    data = when (rec.type) {
                        ChangeType.REVIEW_ITEM -> {
                            val remote = FronterasJson.decodeFromString(ReviewItem.serializer(), rec.payload)
                            val local = data.reviewItems.firstOrNull { it.id == remote.id }
                            val merged = local?.let { resolver.mergeReviewItem(it, remote) } ?: remote
                            data.copy(reviewItems = data.reviewItems.filter { it.id != remote.id } + merged)
                        }
                        ChangeType.EVIDENCE_EVENT -> {
                            val e = FronterasJson.decodeFromString(EvidenceEvent.serializer(), rec.payload)
                            data.copy(evidence = resolver.mergeEvidence(data.evidence, listOf(e)))
                        }
                        ChangeType.PROFILE -> {
                            val remote = FronterasJson.decodeFromString(LearnerProfile.serializer(), rec.payload)
                            data.copy(profile = data.profile?.let { resolver.mergeProfile(it, remote) } ?: remote)
                        }
                        ChangeType.SESSION_SUMMARY, ChangeType.DELETE_ALL -> data
                    }
                }
                data
            }
            total += records.size
            since = r.value.serverVersion
            credentials.setLastPulledVersion(since)
            if (!r.value.hasMore) return total
        }
    }

    fun pendingCount(): Int = repo.data.value.outbox.count { it.status == OutboxStatus.PENDING || it.status == OutboxStatus.IN_FLIGHT }
    fun failedCount(): Int = repo.data.value.outbox.count { it.status == OutboxStatus.FAILED_PERMANENT }
}
