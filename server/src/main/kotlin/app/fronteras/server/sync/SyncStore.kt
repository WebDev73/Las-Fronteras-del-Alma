package app.fronteras.server.sync

import app.fronteras.core.mastery.EvidenceEvent
import app.fronteras.core.profile.LearnerProfile
import app.fronteras.core.srs.ReviewItem
import app.fronteras.core.srs.ReviewScheduler
import app.fronteras.core.storage.FronterasJson
import app.fronteras.core.sync.ChangeType
import app.fronteras.core.sync.ConflictResolver
import app.fronteras.core.sync.PushResult
import app.fronteras.core.sync.SyncChange
import app.fronteras.core.sync.SyncPullResponse
import app.fronteras.core.sync.SyncPushResponse
import app.fronteras.core.sync.SyncRecord
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-user sync storage. Applies the same deterministic merge rules as the client
 * ([ConflictResolver]) so both sides converge; idempotency keys make retries safe.
 * File-backed for a single instance; swap for a database in multi-instance deployments.
 */
class SyncStore(private val dir: File?, private val resolver: ConflictResolver = ConflictResolver(ReviewScheduler())) {
    @Serializable
    private data class UserState(
        val version: Long = 0,
        val records: Map<String, SyncRecord> = emptyMap(),
        val appliedKeys: List<String> = emptyList(),
    )

    private val states = ConcurrentHashMap<String, UserState>()
    private val locks = ConcurrentHashMap<String, Any>()
    private val json = FronterasJson

    fun push(userId: String, changes: List<SyncChange>): SyncPushResponse = synchronized(lock(userId)) {
        var state = load(userId)
        val results = changes.map { change ->
            if (change.idempotencyKey in state.appliedKeys) {
                return@map PushResult.Duplicate(change.idempotencyKey, state.version)
            }
            if (change.payload.length > MAX_PAYLOAD) return@map PushResult.Rejected(change.idempotencyKey, "payload too large")
            val merged = try {
                merge(state, change)
            } catch (e: Exception) {
                return@map PushResult.Rejected(change.idempotencyKey, "invalid payload for ${change.type}")
            }
            val version = state.version + 1
            state = if (change.type == ChangeType.DELETE_ALL) {
                UserState(version, state.records.mapValues { (_, r) -> r.copy(deleted = true, payload = "", version = version) })
            } else {
                state.copy(version = version, records = state.records + (key(change.type, change.entityId) to SyncRecord(change.type, change.entityId, merged, version)))
            }
            state = state.copy(appliedKeys = (state.appliedKeys + change.idempotencyKey).takeLast(MAX_KEYS))
            PushResult.Accepted(change.idempotencyKey, version)
        }
        save(userId, state)
        SyncPushResponse(results, state.version)
    }

    fun pull(userId: String, since: Long, limit: Int = 500): SyncPullResponse = synchronized(lock(userId)) {
        val state = load(userId)
        val changed = state.records.values.filter { it.version > since }.sortedBy { it.version }
        SyncPullResponse(changed.take(limit), if (changed.size > limit) changed[limit - 1].version else state.version, changed.size > limit)
    }

    fun deleteUser(userId: String) = synchronized(lock(userId)) {
        states.remove(userId)
        file(userId)?.delete()
        Unit
    }

    private fun merge(state: UserState, change: SyncChange): String {
        val existing = state.records[key(change.type, change.entityId)]?.takeIf { !it.deleted && it.payload.isNotEmpty() }
        return when (change.type) {
            ChangeType.REVIEW_ITEM -> {
                val incoming = json.decodeFromString(ReviewItem.serializer(), change.payload)
                require(incoming.id == change.entityId)
                val result = existing?.let { resolver.mergeReviewItem(json.decodeFromString(ReviewItem.serializer(), it.payload), incoming) } ?: incoming
                json.encodeToString(ReviewItem.serializer(), result)
            }
            ChangeType.PROFILE -> {
                val incoming = json.decodeFromString(LearnerProfile.serializer(), change.payload)
                val result = existing?.let { resolver.mergeProfile(json.decodeFromString(LearnerProfile.serializer(), it.payload), incoming) } ?: incoming
                json.encodeToString(LearnerProfile.serializer(), result)
            }
            ChangeType.EVIDENCE_EVENT -> {
                val e = json.decodeFromString(EvidenceEvent.serializer(), change.payload)
                require(e.id == change.entityId)
                existing?.payload ?: change.payload // append-only: first write wins
            }
            ChangeType.SESSION_SUMMARY -> { json.parseToJsonElement(change.payload); change.payload }
            ChangeType.DELETE_ALL -> ""
        }
    }

    private fun key(type: ChangeType, id: String) = "${type.name}:$id"
    private fun lock(userId: String) = locks.computeIfAbsent(userId) { Any() }
    private fun safeName(userId: String) = userId.replace(Regex("[^A-Za-z0-9_-]"), "_")
    private fun file(userId: String) = dir?.let { File(it, "sync-${safeName(userId)}.json") }

    private fun load(userId: String): UserState = states.getOrPut(userId) {
        file(userId)?.takeIf { it.exists() }?.let { json.decodeFromString(UserState.serializer(), it.readText()) } ?: UserState()
    }

    private fun save(userId: String, state: UserState) {
        states[userId] = state
        file(userId)?.let { f ->
            f.parentFile?.mkdirs()
            val tmp = File(f.path + ".tmp")
            tmp.writeText(json.encodeToString(UserState.serializer(), state))
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    companion object {
        const val MAX_PAYLOAD = 256 * 1024
        const val MAX_KEYS = 10_000
    }
}
