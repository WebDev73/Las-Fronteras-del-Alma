package app.fronteras.core.sync

import app.fronteras.core.mastery.EvidenceEvent
import app.fronteras.core.profile.LearnerProfile
import app.fronteras.core.srs.ReviewItem
import app.fronteras.core.srs.ReviewScheduler

/**
 * Deterministic conflict rules (documented in docs/SYNC.md):
 *  1. Review items: logs are append-only; merge = union of logs by id, then replay FSRS.
 *     Content edits use last-writer-wins on [ReviewItem.updatedAt]; suspension is sticky.
 *  2. Evidence events: append-only union by id.
 *  3. Profile: last-writer-wins on [LearnerProfile.updatedAt] per target-language profile,
 *     with consents resolved towards the most restrictive recent decision.
 *  4. Deletion tombstones beat any concurrent update.
 */
class ConflictResolver(private val scheduler: ReviewScheduler) {

    fun mergeReviewItem(local: ReviewItem, remote: ReviewItem): ReviewItem {
        require(local.id == remote.id)
        val newer = if (remote.updatedAt > local.updatedAt) remote else local
        val logs = (local.history + remote.history).distinctBy { it.id }
        val merged = newer.copy(
            history = logs,
            suspended = local.suspended || remote.suspended,
            updatedAt = maxOf(local.updatedAt, remote.updatedAt),
        )
        return scheduler.replay(merged)
    }

    fun mergeEvidence(local: List<EvidenceEvent>, remote: List<EvidenceEvent>): List<EvidenceEvent> =
        (local + remote).distinctBy { it.id }.sortedBy { it.at }

    fun mergeProfile(local: LearnerProfile, remote: LearnerProfile): LearnerProfile {
        val newer = if (remote.updatedAt > local.updatedAt) remote else local
        val older = if (newer === remote) local else remote
        // Keep targets added on either device; same target -> newer profile's version.
        val targets = (newer.targets + older.targets.filter { o -> newer.targets.none { it.target == o.target } })
        val consents = newer.consents.copy(
            cloudAi = latest(local.consents.cloudAi, remote.consents.cloudAi),
            audioProcessing = latest(local.consents.audioProcessing, remote.consents.audioProcessing),
            conversationMemory = latest(local.consents.conversationMemory, remote.consents.conversationMemory),
            keepRawAudio = latest(local.consents.keepRawAudio, remote.consents.keepRawAudio),
        )
        return newer.copy(targets = targets, consents = consents, updatedAt = maxOf(local.updatedAt, remote.updatedAt))
    }

    /** Most recent decision wins; on a tie, revocation wins. */
    private fun latest(a: app.fronteras.core.profile.Consent, b: app.fronteras.core.profile.Consent) = when {
        (a.decidedAt ?: 0) > (b.decidedAt ?: 0) -> a
        (b.decidedAt ?: 0) > (a.decidedAt ?: 0) -> b
        else -> if (!a.granted) a else b
    }

    fun <T> applyTombstones(items: List<T>, tombstones: Set<String>, id: (T) -> String): List<T> = items.filter { id(it) !in tombstones }
}
