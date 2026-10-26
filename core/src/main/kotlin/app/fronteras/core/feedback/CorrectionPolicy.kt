package app.fronteras.core.feedback

import app.fronteras.core.profile.FeedbackMode

/**
 * Decides which feedback interrupts the conversation and which waits for the review.
 * Corrections must be useful and proportionate (spec §3.3).
 */
data class CorrectionDecision(
    /** Shown right away, alongside the partner's reply. */
    val immediate: List<FeedbackItem>,
    /** Held for the post-session review. */
    val deferred: List<FeedbackItem>,
    /** Clarification the partner should ask instead of correcting. */
    val clarification: String? = null,
)

object CorrectionPolicy {
    const val MIN_CONFIDENCE_IMMEDIATE = 0.6
    const val MIN_CONFIDENCE_RECORD = 0.35

    /**
     * @param helpRequested learner explicitly asked for help with this turn
     * @param priorPatternCounts how often each [FeedbackItem.patternKey] was already seen this session
     */
    fun decide(
        mode: FeedbackMode,
        items: List<FeedbackItem>,
        helpRequested: Boolean = false,
        priorPatternCounts: Map<String, Int> = emptyMap(),
    ): CorrectionDecision {
        val recorded = items.filter { it.confidence >= MIN_CONFIDENCE_RECORD }
        val clarification = recorded.firstOrNull { !it.intendedMeaningConfident && it.clarificationQuestion != null }?.clarificationQuestion
        // Never present uncertain-intent corrections as fact; only the question is surfaced.
        val actionable = recorded.filter { it.intendedMeaningConfident && it.severity != ErrorSeverity.ACCEPTABLE_VARIANT }
        val variants = recorded.filter { it.severity == ErrorSeverity.ACCEPTABLE_VARIANT }

        val eligible = actionable
            .filter { it.confidence >= MIN_CONFIDENCE_IMMEDIATE && !it.possibleRecognitionIssue }
            .sortedWith(compareBy<FeedbackItem> { it.severity.rank }.thenByDescending { it.confidence })

        val immediate: List<FeedbackItem> = when (mode) {
            FeedbackMode.ASSESSMENT -> emptyList()
            FeedbackMode.CONVERSATION -> when {
                helpRequested -> eligible.take(1)
                else -> eligible.filter { it.severity == ErrorSeverity.CRITICAL_MEANING }.take(1)
            }
            FeedbackMode.BALANCED -> {
                val critical = eligible.filter { it.severity == ErrorSeverity.CRITICAL_MEANING }
                val recurringSignificant = eligible.filter {
                    it.severity == ErrorSeverity.SIGNIFICANT && (it.patternKey?.let { k -> (priorPatternCounts[k] ?: 0) >= 1 } ?: false)
                }
                val pool = if (helpRequested) eligible.filter { it.severity.rank <= ErrorSeverity.UNNATURAL.rank } else critical + recurringSignificant
                pool.distinctBy { it.id }.take(if (helpRequested) 2 else 1)
            }
            FeedbackMode.INTENSIVE -> eligible.filter { it.severity.rank <= ErrorSeverity.MINOR_STYLE.rank }.take(3)
        }
        val immediateIds = immediate.map { it.id }.toSet()
        val deferred = (actionable + variants).filter { it.id !in immediateIds }
        return CorrectionDecision(immediate, deferred, clarification)
    }
}
