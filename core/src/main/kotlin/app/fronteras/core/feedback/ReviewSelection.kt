package app.fronteras.core.feedback

/**
 * Chooses which session feedback becomes spaced-repetition material. Selective by design:
 * scheduling every corrected sentence produces an unmanageable queue.
 */
object ReviewSelection {
    data class Candidate(val item: FeedbackItem, val priority: Double, val reason: String)

    fun maxNewItems(dailyMinutes: Int): Int = when {
        dailyMinutes <= 5 -> 2
        dailyMinutes <= 15 -> 4
        dailyMinutes <= 30 -> 6
        else -> 8
    }

    fun select(
        feedback: List<FeedbackItem>,
        dailyMinutes: Int,
        /** Pattern keys already in the review deck. */
        alreadyScheduled: Set<String> = emptySet(),
        /** Pattern keys observed in previous sessions (recurring across sessions). */
        historicalPatternCounts: Map<String, Int> = emptyMap(),
    ): List<Candidate> {
        val sessionCounts = feedback.mapNotNull { it.patternKey }.groupingBy { it }.eachCount()
        return feedback
            .asSequence()
            .filter { it.intendedMeaningConfident && !it.possibleRecognitionIssue }
            .filter { it.confidence >= 0.6 }
            .filter { it.severity.rank <= ErrorSeverity.UNNATURAL.rank }
            .filter { it.corrected != null || it.natural != null }
            .filter { it.patternKey == null || it.patternKey !in alreadyScheduled }
            .distinctBy { it.patternKey ?: it.id }
            .map { item ->
                val key = item.patternKey
                val inSession = key?.let { sessionCounts[it] } ?: 1
                val historical = key?.let { historicalPatternCounts[it] } ?: 0
                val severityWeight = when (item.severity) {
                    ErrorSeverity.CRITICAL_MEANING -> 3.0
                    ErrorSeverity.SIGNIFICANT -> 2.0
                    else -> 1.0
                }
                val priority = severityWeight * (1 + 0.5 * (inSession - 1) + 0.75 * historical) * item.confidence +
                    if (item.suggestReview) 0.5 else 0.0
                val reason = buildString {
                    append(item.severity.label)
                    if (inSession > 1) append("; repeated $inSession× this session")
                    if (historical > 0) append("; seen in $historical earlier session(s)")
                }
                Candidate(item, priority, reason)
            }
            .sortedByDescending { it.priority }
            .take(maxNewItems(dailyMinutes))
            .toList()
    }
}
