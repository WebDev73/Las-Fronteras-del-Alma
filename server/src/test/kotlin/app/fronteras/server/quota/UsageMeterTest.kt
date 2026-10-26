package app.fronteras.server.quota

import app.fronteras.core.entitlement.Plan
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsageMeterTest {
    @Test
    fun `limits per user per day with refunds`() {
        var day = LocalDate.of(2026, 1, 1)
        val m = UsageMeter { day }
        val limit = m.limit(Plan.FREE, UsageMeter.Kind.ANALYSIS)
        repeat(limit) { assertTrue(m.tryConsume("ana", Plan.FREE, UsageMeter.Kind.ANALYSIS)) }
        assertFalse(m.tryConsume("ana", Plan.FREE, UsageMeter.Kind.ANALYSIS))
        assertTrue(m.tryConsume("bo", Plan.FREE, UsageMeter.Kind.ANALYSIS), "users are independent")
        m.refund("ana", UsageMeter.Kind.ANALYSIS)
        assertTrue(m.tryConsume("ana", Plan.FREE, UsageMeter.Kind.ANALYSIS))
        day = day.plusDays(1)
        assertEquals(0, m.used("ana", UsageMeter.Kind.ANALYSIS))
        assertTrue(m.limit(Plan.PREMIUM, UsageMeter.Kind.TURN) > m.limit(Plan.FREE, UsageMeter.Kind.TURN))
    }
}
