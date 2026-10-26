package app.fronteras.core.conversation

import app.fronteras.core.assessment.CefrLevel
import app.fronteras.core.assessment.EvidenceConditions
import app.fronteras.core.assessment.EvidenceReliability
import app.fronteras.core.assessment.SkillEstimate
import app.fronteras.core.assessment.SkillObservation
import app.fronteras.core.assessment.SkillUpdater
import app.fronteras.core.curriculum.Competency
import app.fronteras.core.feedback.ErrorCategory
import app.fronteras.core.feedback.FeedbackItem
import app.fronteras.core.feedback.RetrievalMethod
import app.fronteras.core.feedback.ReviewSelection
import app.fronteras.core.mastery.Assistance
import app.fronteras.core.mastery.EvidenceEvent
import app.fronteras.core.mastery.EvidenceKind
import app.fronteras.core.srs.Rating
import app.fronteras.core.srs.ReviewCategory
import app.fronteras.core.srs.ReviewItem
import app.fronteras.core.srs.ReviewScheduler
import app.fronteras.core.srs.ReviewSource
import app.fronteras.core.srs.ReviewSourceType
import app.fronteras.core.storage.EstimateSnapshot
import app.fronteras.core.storage.LearnerData
import app.fronteras.core.util.IdGenerator
import java.text.Normalizer

/** Outcome of closing a session: what changed in the learner model and why. */
data class SessionOutcome(
    val session: Session,
    val analysis: SessionAnalysis?,
    val newReviewItems: List<ReviewItem>,
    val evidence: List<EvidenceEvent>,
    val updatedEstimates: List<SkillEstimate>,
    val reviewReasons: Map<String, String>,
    val notes: List<String>,
)

/**
 * The ANALYZE → PRACTISE → REVIEW part of the loop: turns a finished session (and the
 * optional batched AI analysis) into review items, evidence events and skill updates.
 * Deterministic processing is used where it suffices (expression matching, counting).
 */
class PostSessionProcessor(
    private val scheduler: ReviewScheduler,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    fun process(
        data: LearnerData,
        session: Session,
        analysis: SessionAnalysis?,
        competencies: List<Competency>,
        scenario: Scenario?,
        now: Long,
    ): SessionOutcome {
        val profile = requireNotNull(data.profile)
        val target = profile.target(session.target) ?: profile.active
        val notes = mutableListOf<String>()
        val learnerTurns = session.learnerTurns

        // 1. Feedback: session feedback + analysis findings not already given.
        val analysisFeedback = analysis?.feedback.orEmpty().mapNotNull { w ->
            val turn = learnerTurns.firstOrNull { normalize(it.text).contains(normalize(w.original)) } ?: learnerTurns.lastOrNull() ?: return@mapNotNull null
            FeedbackItem(
                id = ids.newId(), sessionId = session.id, turnId = turn.id, original = w.original, transcript = turn.transcript,
                intendedMeaning = w.intendedMeaning, intendedMeaningConfident = w.intendedMeaningConfident, clarificationQuestion = w.clarificationQuestion,
                corrected = w.corrected, natural = w.natural, focus = w.focus, category = w.category, severity = w.severity, explanation = w.explanation,
                explanationLanguage = target.explanationLanguage, comparison = w.comparison, examples = w.examples, recommendedExercise = w.recommendedExercise,
                suggestReview = w.suggestReview, confidence = w.confidence, possibleRecognitionIssue = w.possibleRecognitionIssue, patternKey = w.patternKey,
            )
        }
        val existingKeys = session.allFeedback.mapNotNull { it.patternKey }.toSet()
        val allFeedback = session.allFeedback + analysisFeedback.filter { it.patternKey == null || it.patternKey !in existingKeys }

        // 2. Review selection (selective) + extracted items.
        val scheduledKeys = data.reviewItemsFor(session.target).mapNotNull { it.patternKey }.toSet()
        val selected = ReviewSelection.select(allFeedback, target.dailyMinutes, scheduledKeys, data.historicalPatternCounts(session.target, session.id))
        val reasons = mutableMapOf<String, String>()
        val fromFeedback = selected.map { cand ->
            val f = cand.item
            val item = ReviewItem(
                id = ids.newId(), target = session.target,
                content = f.corrected ?: f.natural!!,
                cue = f.intendedMeaning ?: f.explanation,
                explanation = f.explanation, explanationLanguage = f.explanationLanguage,
                category = when (f.category) {
                    ErrorCategory.VOCABULARY, ErrorCategory.FALSE_COGNATE -> ReviewCategory.VOCABULARY
                    ErrorCategory.IDIOM -> ReviewCategory.IDIOM
                    ErrorCategory.COLLOCATION -> ReviewCategory.COLLOCATION
                    ErrorCategory.PRONUNCIATION -> ReviewCategory.PRONUNCIATION_TARGET
                    else -> ReviewCategory.CORRECTED_SENTENCE
                },
                examples = f.examples, commonError = f.original, patternKey = f.patternKey,
                competencyIds = session.competencyIds, importance = (cand.priority / 6.0).coerceIn(0.2, 1.0),
                source = ReviewSource(ReviewSourceType.CONVERSATION, session.id, f.turnId, f.id, f.original), createdAt = now,
            )
            reasons[item.id] = cand.reason
            item
        }
        val maxExtracted = (ReviewSelection.maxNewItems(target.dailyMinutes) - fromFeedback.size).coerceAtLeast(1)
        val existingContent = data.reviewItemsFor(session.target).map { normalize(it.content) }.toSet()
        val extracted = analysis?.newItems.orEmpty()
            .filter { normalize(it.content) !in existingContent }
            .take(maxExtracted)
            .map { x ->
                ReviewItem(
                    id = ids.newId(), target = session.target, content = x.content, cue = x.cue, explanation = x.explanation,
                    explanationLanguage = target.explanationLanguage,
                    category = when (x.kind.lowercase()) {
                        "idiom" -> ReviewCategory.IDIOM
                        "collocation" -> ReviewCategory.COLLOCATION
                        "chunk", "phrase", "expression" -> ReviewCategory.CONVERSATIONAL_CHUNK
                        "grammar" -> ReviewCategory.GRAMMAR_PATTERN
                        "recognized" -> ReviewCategory.RECOGNIZED_NOT_PRODUCED
                        else -> ReviewCategory.VOCABULARY
                    },
                    examples = listOfNotNull(x.example), competencyIds = session.competencyIds, importance = 0.5,
                    source = ReviewSource(ReviewSourceType.CONVERSATION, session.id, context = x.example), createdAt = now,
                ).also { reasons[it.id] = "New expression from this conversation" }
            }

        // 3. Spontaneous use of existing review items counts as a successful production retrieval.
        val learnerText = learnerTurns.joinToString(" ") { normalize(it.text) }
        val updatedExisting = data.reviewItemsFor(session.target).filter { it.production != null || it.recognition != null }
            .filter { normalize(it.content).length >= 4 && learnerText.contains(normalize(it.content).trimEnd('…', '.')) }
            .map { scheduler.record(it, RetrievalMethod.USE_IN_NEW_SCENARIO, Rating.GOOD, spontaneous = true) }
        if (updatedExisting.isNotEmpty()) notes += "You used ${updatedExisting.size} item(s) from your review deck without being prompted."

        // 4. Evidence for competencies.
        val events = mutableListOf<EvidenceEvent>()
        val avgAsr = learnerTurns.mapNotNull { it.transcript?.takeUnless { t -> t.editedByLearner }?.confidence }.takeIf { it.isNotEmpty() }?.average()
        val reliability = if (avgAsr != null && avgAsr < 0.6) 0.4 else 1.0
        val helpCount = learnerTurns.count { it.helpRequested }
        val assistance = when {
            session.feedbackMode == app.fronteras.core.profile.FeedbackMode.ASSESSMENT && helpCount == 0 -> Assistance.NONE
            helpCount == 0 && session.shownFeedback.size <= 1 -> Assistance.LOW
            helpCount <= 2 -> Assistance.MODERATE
            else -> Assistance.HIGH
        }
        val rubricAvg = analysis?.rubric?.takeIf { it.isNotEmpty() }?.map { it.score }?.average()
        val usedExpressionsByComp = competencies.associate { c ->
            c.id to c.expressions[session.target.base].orEmpty().filter { e ->
                val key = normalize(e.text).replace("…", "").trim()
                key.length >= 3 && learnerText.contains(key)
            }
        }
        for (c in competencies) {
            events += EvidenceEvent(ids.newId(), c.id, EvidenceKind.EXPOSURE, now, success = true, sessionId = session.id)
            if (learnerTurns.size < 3) continue
            val met = analysis?.objectivesMet?.any { it.equals(c.canDo, ignoreCase = true) || it.equals(c.id, true) || it.equals(c.title, true) } ?: false
            if (met) {
                val kind = if (session.mode == SessionMode.MOCK_EXAM || session.feedbackMode == app.fronteras.core.profile.FeedbackMode.ASSESSMENT || assistance <= Assistance.LOW) EvidenceKind.ASSESSMENT_TASK else EvidenceKind.GUIDED_PRACTICE
                events += EvidenceEvent(ids.newId(), c.id, kind, now, success = true, sessionId = session.id, assistance = assistance,
                    rubricScore = rubricAvg, reliability = reliability, note = "Objective met per session analysis")
            }
            val used = usedExpressionsByComp[c.id].orEmpty()
            if (used.isNotEmpty() && session.mode != SessionMode.DRILL) {
                events += EvidenceEvent(ids.newId(), c.id, EvidenceKind.SPONTANEOUS_USE, now, success = true, sessionId = session.id,
                    assistance = assistance, reliability = reliability, note = "Used: ${used.joinToString { it.text }}")
            }
        }
        if (reliability < 1.0) notes += "Speech recognition confidence was low in this session, so it counts less towards your progress."

        // 5. Skill estimates from rubric scores (task difficulty = scenario level).
        val taskLevel = scenario?.let { (it.minLevel.scaleCentre + it.maxLevel.scaleCentre) / 2 } ?: (data.workingLevel(session.target) ?: CefrLevel.A2).scaleCentre
        val estimates = data.estimatesFor(session.target).associateBy { it.skill }.toMutableMap()
        analysis?.rubric.orEmpty().forEach { r ->
            val prior = estimates[r.skill] ?: SkillEstimate.prior(r.skill, target.selfAssessedLevel, now)
            val conditions = EvidenceConditions(spoken = learnerTurns.any { it.inputMode == InputMode.VOICE }, asrConfidence = avgAsr)
            val w = EvidenceReliability.weight(r.skill, conditions) * r.confidence.coerceIn(0.0, 1.0) * if (learnerTurns.size < 4) 0.5 else 1.0
            estimates[r.skill] = SkillUpdater.update(prior, SkillObservation(r.skill, r.score / 4.0, taskLevel, w, now, "session:${session.id}"))
        }
        if (learnerTurns.size < 4 && analysis != null) notes += "Short conversation: its scores carry less weight."

        val finished = session.copy(deferredFeedback = session.deferredFeedback + analysisFeedback.filter { it.patternKey == null || it.patternKey !in existingKeys })
        return SessionOutcome(finished, analysis, fromFeedback + extracted + updatedExisting, events, estimates.values.toList(), reasons, notes)
    }

    /** Applies an outcome to the stored learner data. */
    fun apply(data: LearnerData, outcome: SessionOutcome, now: Long): LearnerData {
        val s = outcome.session
        val replacedIds = outcome.newReviewItems.map { it.id }.toSet()
        val estimates = data.estimates + (s.target.value to outcome.updatedEstimates)
        val history = if (outcome.analysis?.rubric.isNullOrEmpty()) data.estimateHistory
        else data.estimateHistory + EstimateSnapshot(s.target, now, "session:${s.id}", s.scenarioId, outcome.updatedEstimates)
        return data.copy(
            sessions = data.sessions.filter { it.id != s.id } + s,
            analyses = outcome.analysis?.let { data.analyses + (s.id to it) } ?: data.analyses,
            reviewItems = data.reviewItems.filter { it.id !in replacedIds } + outcome.newReviewItems,
            evidence = data.evidence + outcome.evidence,
            estimates = estimates,
            estimateHistory = history,
        )
    }

    companion object {
        fun normalize(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFC)
            .replace(Regex("[¿¡!?.,;:«»\"“”()]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
