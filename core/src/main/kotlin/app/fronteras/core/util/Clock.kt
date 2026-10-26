package app.fronteras.core.util

/** Time source injected everywhere so scheduling and evidence rules are testable. */
fun interface Clock {
    fun nowMillis(): Long

    companion object {
        val System: Clock = Clock { java.lang.System.currentTimeMillis() }
    }
}

/** Deterministic clock for tests and replays. */
class FixedClock(var now: Long) : Clock {
    override fun nowMillis(): Long = now
    fun advanceDays(days: Double) {
        now += (days * DAY_MILLIS).toLong()
    }
    fun advanceMillis(ms: Long) {
        now += ms
    }
}

const val MINUTE_MILLIS = 60_000L
const val HOUR_MILLIS = 60 * MINUTE_MILLIS
const val DAY_MILLIS = 24 * HOUR_MILLIS

fun interface IdGenerator {
    fun newId(): String

    companion object {
        val Random: IdGenerator = IdGenerator { java.util.UUID.randomUUID().toString() }
    }
}

/** Sequential ids for deterministic tests. */
class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var next = 0
    override fun newId(): String = "$prefix-${++next}"
}
