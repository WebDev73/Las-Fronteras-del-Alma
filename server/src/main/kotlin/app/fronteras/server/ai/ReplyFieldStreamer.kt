package app.fronteras.server.ai

/**
 * Incrementally extracts the value of one top-level string field (default `reply`) from a
 * JSON object that arrives in fragments, so the learner hears the partner's reply while
 * the rest of the structured output (the analysis) is still being generated.
 */
class ReplyFieldStreamer(private val field: String = "reply") {
    private enum class State { SEEK, IN_VALUE, DONE }

    private val buffer = StringBuilder()
    private var scanPos = 0
    private var state = State.SEEK
    private var depth = 0
    private var inString = false
    private var escape = false
    private var keyStart = -1
    private var lastKey: String? = null
    private var awaitingValue = false
    private var unicode: StringBuilder? = null

    val done: Boolean get() = state == State.DONE

    /** Feed a fragment; returns newly decoded characters of the field value (possibly empty). */
    fun feed(fragment: String): String {
        if (state == State.DONE) return ""
        buffer.append(fragment)
        val out = StringBuilder()
        while (scanPos < buffer.length && state != State.DONE) {
            val c = buffer[scanPos]
            when (state) {
                State.SEEK -> seek(c)
                State.IN_VALUE -> decode(c, out)
                State.DONE -> Unit
            }
            scanPos++
        }
        return out.toString()
    }

    private fun seek(c: Char) {
        if (inString) {
            if (escape) { escape = false; return }
            when (c) {
                '\\' -> escape = true
                '"' -> {
                    inString = false
                    if (keyStart >= 0) { lastKey = buffer.substring(keyStart, scanPos); keyStart = -1 }
                }
            }
            return
        }
        when (c) {
            '{', '[' -> { depth++; awaitingValue = false }
            '}', ']' -> depth--
            ':' -> awaitingValue = depth == 1 && lastKey == field
            ',' -> { awaitingValue = false; lastKey = null }
            '"' -> {
                if (awaitingValue) { state = State.IN_VALUE; awaitingValue = false; return }
                inString = true
                // Keys only at depth 1 matter.
                keyStart = if (depth == 1) scanPos + 1 else -1
            }
        }
    }

    private fun decode(c: Char, out: StringBuilder) {
        unicode?.let { u ->
            u.append(c)
            if (u.length == 4) { out.append(u.toString().toInt(16).toChar()); unicode = null }
            return
        }
        if (escape) {
            escape = false
            when (c) {
                'n' -> out.append('\n'); 't' -> out.append('\t'); 'r' -> out.append('\r')
                'b' -> out.append('\b'); 'f' -> out.append('\u000C')
                'u' -> unicode = StringBuilder()
                else -> out.append(c)
            }
            return
        }
        when (c) {
            '\\' -> escape = true
            '"' -> state = State.DONE
            else -> out.append(c)
        }
    }
}
