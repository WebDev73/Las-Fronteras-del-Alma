package app.fronteras.core.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncChange(
    val idempotencyKey: String,
    val type: ChangeType,
    val entityId: String,
    /** JSON of the entity (ReviewItem, LearnerProfile, EvidenceEvent ...). */
    val payload: String,
)

@Serializable
data class SyncPushRequest(val deviceId: String, val changes: List<SyncChange>)

@Serializable
data class SyncPushResponse(val results: List<PushResult>, val serverVersion: Long)

@Serializable
data class SyncRecord(val type: ChangeType, val entityId: String, val payload: String, val version: Long, val deleted: Boolean = false)

@Serializable
data class SyncPullResponse(val records: List<SyncRecord>, val serverVersion: Long, val hasMore: Boolean = false)

fun OutboxEntry.toChange() = SyncChange(idempotencyKey, type, entityId, payload)
