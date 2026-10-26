package app.fronteras.server

import app.fronteras.core.conversation.PartnerRequest
import app.fronteras.core.conversation.SessionAnalysisRequest
import app.fronteras.core.conversation.WireTurn
import app.fronteras.core.sync.SyncPushRequest

class ValidationException(message: String) : RuntimeException(message)

/** Input bounds: protect cost, latency and the model from oversized or malformed input. */
object Validation {
    const val MAX_TURN_CHARS = 2_000
    const val MAX_TURNS_PER_TURN_REQUEST = 40
    const val MAX_TURNS_PER_ANALYSIS = 200
    const val MAX_SHORT_TEXT = 500

    private fun check(cond: Boolean, msg: String) { if (!cond) throw ValidationException(msg) }

    private fun turns(t: List<WireTurn>, max: Int) {
        check(t.size <= max, "Too many turns (max $max)")
        t.forEach { check(it.text.length <= MAX_TURN_CHARS, "A turn exceeds $MAX_TURN_CHARS characters") }
    }

    fun partner(r: PartnerRequest) {
        turns(r.turns, MAX_TURNS_PER_TURN_REQUEST)
        check(r.objectives.size <= 10 && r.usefulExpressions.size <= 20 && r.varietyNotes.size <= 20, "Too many objectives or expressions")
        check((r.objectives + r.usefulExpressions + r.varietyNotes).all { it.length <= MAX_SHORT_TEXT }, "Brief entry too long")
        check((r.earlierSummary?.length ?: 0) <= 2_000, "Summary too long")
        r.memory?.let { m -> check((m.recurringErrors + m.recentVocabulary + m.reviewPriorities + m.notes).let { it.size <= 40 && it.all { s -> s.length <= MAX_SHORT_TEXT } }, "Memory too large") }
        r.scenario?.let { s -> check(listOf(s.title, s.setting, s.partnerRole, s.learnerRole, s.learnerGoal).all { it.length <= MAX_SHORT_TEXT }, "Scenario text too long") }
    }

    fun analysis(r: SessionAnalysisRequest) {
        turns(r.turns, MAX_TURNS_PER_ANALYSIS)
        check(r.turns.any { it.speaker == app.fronteras.core.conversation.Speaker.LEARNER }, "No learner turns to analyse")
        check(r.objectives.size <= 10 && r.alreadyGiven.size <= 50, "Too many objectives")
    }

    fun sync(r: SyncPushRequest) {
        check(r.changes.size <= 200, "Too many changes in one push (max 200)")
        check(r.deviceId.length in 1..100, "Invalid device id")
    }
}
