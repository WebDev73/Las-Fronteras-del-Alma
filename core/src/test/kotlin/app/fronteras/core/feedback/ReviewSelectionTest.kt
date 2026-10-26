package app.fronteras.core.feedback

import app.fronteras.core.Fixtures.feedback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewSelectionTest {
    @Test
    fun `selection is capped and excludes noise`() {
        val items = (1..20).map { feedback("f$it") } +
            feedback("style", ErrorSeverity.MINOR_STYLE) +
            feedback("variant", ErrorSeverity.ACCEPTABLE_VARIANT) +
            feedback("asr", recognition = true) +
            feedback("unsure", intentConfident = false) +
            feedback("lowconf", confidence = 0.4)
        val picked = ReviewSelection.select(items, dailyMinutes = 10)
        assertEquals(ReviewSelection.maxNewItems(10), picked.size)
        val ids = picked.map { it.item.id }.toSet()
        assertTrue(ids.none { it in setOf("style", "variant", "asr", "unsure", "lowconf") })
    }

    @Test
    fun `recurring and already scheduled patterns are handled`() {
        val repeated = listOf(feedback("a1", pattern = "ser-estar"), feedback("a2", pattern = "ser-estar"), feedback("b", pattern = "por-para"))
        val picked = ReviewSelection.select(repeated + feedback("c", pattern = "gender"), 30, alreadyScheduled = setOf("gender"),
            historicalPatternCounts = mapOf("por-para" to 3))
        assertEquals(listOf("por-para", "ser-estar"), picked.map { it.item.patternKey })
        assertTrue(picked.first().reason.contains("earlier session"))
    }
}
