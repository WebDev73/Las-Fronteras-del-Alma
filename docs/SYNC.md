# Offline-first sync

* Every local change is queued in an outbox (`core/sync/Outbox.kt`) with an **idempotency key**
  created once and reused on every retry.
* Pending changes to the same entity coalesce. Evidence events are append-only.
* Push results: accepted, duplicate, conflict, retryable or rejected. Interrupted uploads
  return to pending with exponential backoff and jitter; the queue gives up after 12 attempts
  (visible in Settings).
* **Merge rules** (`core/sync/ConflictResolver.kt`), applied identically on server and client:
  1. Review items: union of review logs by id, then FSRS replay of the logs in time order,
     so both sides get identical state. Content edits: last writer wins. Suspension is sticky.
  2. Evidence: union by id.
  3. Profile: last writer wins per profile; targets added on any device are kept; consents
     follow the most recent decision, and on a tie, revocation wins.
  4. Deletion tombstones beat concurrent updates.
* Pull is incremental by server version and paginated.
