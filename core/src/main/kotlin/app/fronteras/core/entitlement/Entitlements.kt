package app.fronteras.core.entitlement

import kotlinx.serialization.Serializable

/**
 * Plan limits. Billing itself is intentionally not implemented yet (spec §13: monetisation
 * comes after the core experience is reliable); these limits exist now because cloud AI
 * has a real cost and the server must enforce a fair-use quota.
 */
@Serializable
enum class Plan { FREE, PREMIUM }

@Serializable
data class PlanLimits(
    val aiTurnsPerDay: Int,
    val sessionAnalysesPerDay: Int,
    val offlinePacks: Int,
)

object Entitlements {
    fun limits(plan: Plan): PlanLimits = when (plan) {
        Plan.FREE -> PlanLimits(aiTurnsPerDay = 40, sessionAnalysesPerDay = 3, offlinePacks = 1)
        Plan.PREMIUM -> PlanLimits(aiTurnsPerDay = 400, sessionAnalysesPerDay = 30, offlinePacks = 10)
    }

    /** Features that are never paywalled: learning must remain possible offline and for free. */
    val alwaysFree = listOf("onboarding", "diagnostic", "review", "offline-practice", "data-export", "data-deletion")
}
