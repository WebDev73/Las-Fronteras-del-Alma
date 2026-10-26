package app.fronteras.server.quota

import app.fronteras.core.entitlement.Entitlements
import app.fronteras.core.entitlement.Plan
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-user daily fair-use quota for AI calls (UTC days). In-memory: a multi-instance
 * deployment must move this to a shared store (see docs/DEPLOYMENT.md).
 */
class UsageMeter(private val today: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) }) {
    enum class Kind { TURN, ANALYSIS }

    private data class Key(val user: String, val day: LocalDate, val kind: Kind)
    private val counts = ConcurrentHashMap<Key, Int>()

    fun limit(plan: Plan, kind: Kind): Int = Entitlements.limits(plan).let { if (kind == Kind.TURN) it.aiTurnsPerDay else it.sessionAnalysesPerDay }

    fun used(user: String, kind: Kind): Int = counts[Key(user, today(), kind)] ?: 0

    /** Atomically reserves one unit; false when the daily limit is reached. */
    fun tryConsume(user: String, plan: Plan, kind: Kind): Boolean {
        val key = Key(user, today(), kind)
        var ok = false
        counts.compute(key) { _, v ->
            val cur = v ?: 0
            if (cur < limit(plan, kind)) { ok = true; cur + 1 } else cur
        }
        prune()
        return ok
    }

    /** Gives a unit back when the call failed before producing anything. */
    fun refund(user: String, kind: Kind) {
        counts.computeIfPresent(Key(user, today(), kind)) { _, v -> (v - 1).coerceAtLeast(0) }
    }

    private fun prune() {
        val d = today()
        counts.keys.removeIf { it.day.isBefore(d.minusDays(1)) }
    }
}
