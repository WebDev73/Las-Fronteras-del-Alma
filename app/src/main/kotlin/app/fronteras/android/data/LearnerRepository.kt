package app.fronteras.android.data

import app.fronteras.core.assessment.CefrLevel
import app.fronteras.core.assessment.DiagnosticResult
import app.fronteras.core.content.Curriculum
import app.fronteras.core.content.Scenarios
import app.fronteras.core.conversation.LongTermMemory
import app.fronteras.core.conversation.PostSessionProcessor
import app.fronteras.core.conversation.Scenario
import app.fronteras.core.conversation.Session
import app.fronteras.core.conversation.SessionAnalysis
import app.fronteras.core.conversation.SessionMode
import app.fronteras.core.conversation.SessionOutcome
import app.fronteras.core.curriculum.Competency
import app.fronteras.core.feedback.RetrievalMethod
import app.fronteras.core.language.LanguageTag
import app.fronteras.core.mastery.EvidenceEvent
import app.fronteras.core.mastery.MasteryEvaluator
import app.fronteras.core.mastery.MasteryStatus
import app.fronteras.core.planning.LearningPath
import app.fronteras.core.planning.LearningPathGenerator
import app.fronteras.core.planning.NextStepRecommender
import app.fronteras.core.planning.Recommendation
import app.fronteras.core.profile.LearnerProfile
import app.fronteras.core.profile.OnboardingDraft
import app.fronteras.core.profile.OnboardingFlow
import app.fronteras.core.profile.OnboardingResult
import app.fronteras.core.progress.ProgressCalculator
import app.fronteras.core.progress.ProgressReport
import app.fronteras.core.srs.Rating
import app.fronteras.core.srs.ReviewItem
import app.fronteras.core.srs.ReviewQueueBuilder
import app.fronteras.core.srs.ReviewScheduler
import app.fronteras.core.srs.ReviewTask
import app.fronteras.core.storage.AppSettings
import app.fronteras.core.storage.ConfidenceReport
import app.fronteras.core.storage.EstimateSnapshot
import app.fronteras.core.storage.FronterasJson
import app.fronteras.core.storage.LearnerData
import app.fronteras.core.storage.LearnerStore
import app.fronteras.core.sync.ChangeType
import app.fronteras.core.sync.Outbox
import app.fronteras.core.sync.OutboxEntry
import app.fronteras.core.util.Clock
import app.fronteras.core.util.IdGenerator
import kotlinx.coroutines.flow.StateFlow

/**
 * Application-level operations over the learner's data. All learning logic lives in :core;
 * this class wires it to storage and queues changes for sync.
 */
class LearnerRepository(
    private val store: LearnerStore,
    private val clock: Clock,
    private val ids: IdGenerator,
    val scheduler: ReviewScheduler,
) {
    val data: StateFlow<LearnerData> = store.data
    private val processor = PostSessionProcessor(scheduler, ids)
    val graph = Curriculum.graph

    suspend fun load() {
        store.load()
        enforceRetention()
    }

    private val current: LearnerData get() = data.value
    val profile: LearnerProfile? get() = current.profile

    // ---- onboarding & profile ----

    suspend fun saveDraft(draft: OnboardingDraft) { store.update { it.copy(onboardingDraft = draft) } }

    suspend fun completeOnboarding(draft: OnboardingDraft): OnboardingResult {
        val result = OnboardingFlow.complete(draft, current.profile?.id ?: ids.newId(), clock.nowMillis())
        if (result is OnboardingResult.Complete) {
            store.update { d -> d.copy(profile = result.profile, onboardingDraft = null, settings = d.settings.copy(retention = d.settings.retention.copy(conversationMemory = result.profile.consents.conversationMemory.granted))) }
            enqueueProfile()
        } else saveDraft(draft)
        return result
    }

    suspend fun updateProfile(transform: (LearnerProfile) -> LearnerProfile) {
        store.update { d -> d.profile?.let { d.copy(profile = transform(it).copy(updatedAt = clock.nowMillis())) } ?: d }
        enqueueProfile()
    }

    suspend fun setLevelOverride(level: CefrLevel?) {
        val p = profile ?: return
        updateProfile { it.updateTarget(p.activeTarget, clock.nowMillis()) { t -> t.copy(levelOverride = level) } }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) { store.update { it.copy(settings = transform(it.settings)) } }

    // ---- diagnostic ----

    suspend fun saveDiagnostic(target: LanguageTag, result: DiagnosticResult) {
        val now = clock.nowMillis()
        val estimates = result.findings.map { it.estimate }
        store.update { d ->
            d.copy(
                diagnostics = d.diagnostics + (target.value to result),
                estimates = d.estimates + (target.value to estimates),
                estimateHistory = d.estimateHistory + EstimateSnapshot(target, now, "diagnostic", "diagnostic-${target.base}", estimates),
            )
        }
    }

    // ---- planning ----

    fun competenciesFor(target: LanguageTag): List<Competency> = graph.forLanguage(target.base)

    fun mastery(target: LanguageTag, d: LearnerData = current): Map<String, MasteryStatus> {
        val now = clock.nowMillis()
        val ev = d.evidence.filter { e -> d.sessions.any { it.id == e.sessionId && it.target == target } || e.sessionId.startsWith("review:${target.value}") }
        return competenciesFor(target).associate { c -> c.id to MasteryEvaluator.evaluate(c, ev, now) }
    }

    fun path(d: LearnerData = current): LearningPath? {
        val p = d.profile ?: return null
        val target = p.activeTarget
        val level = d.workingLevel(target)
        val baseline = d.diagnostics[target.value]?.let { "Provisional ${it.provisionalRange} from your check-in" }
            ?: level?.let { "Starting from ${it.name} (self-assessed)" } ?: "No baseline yet"
        return LearningPathGenerator.generate(p.active, graph, mastery(target, d), level, baseline)
    }

    fun recommendations(d: LearnerData = current): List<Recommendation> {
        val p = d.profile ?: return emptyList()
        val target = p.activeTarget
        val path = path(d) ?: return emptyList()
        val counts = d.historicalPatternCounts(target).entries.sortedByDescending { it.value }.map { it.key to it.value }
        val lastDiag = d.diagnostics[target.value]?.completedAt ?: 0
        return NextStepRecommender.recommend(
            hasDiagnostic = d.diagnostics.containsKey(target.value),
            dueReviews = dueCount(d),
            path = path,
            mastery = mastery(target, d),
            competencies = graph.all,
            recurringPatterns = counts,
            dailyMinutes = p.active.dailyMinutes,
            sessionsSinceReassessment = d.sessionsFor(target).count { (it.endedAt ?: 0) > lastDiag },
        )
    }

    fun scenariosFor(target: LanguageTag, level: CefrLevel?): List<Scenario> =
        Scenarios.all.filter { s -> s.offlinePrompts.containsKey(target.base) }
            .sortedBy { s -> level?.let { if (it in s.minLevel..s.maxLevel) 0 else 1 } ?: 0 }

    // ---- review ----

    private fun queueBuilder() = ReviewQueueBuilder(scheduler, clock)

    fun dueCount(d: LearnerData = current): Int {
        val p = d.profile ?: return 0
        val now = clock.nowMillis()
        return d.reviewItemsFor(p.activeTarget).count { !it.suspended && !it.isNew && (it.nextDueAt ?: Long.MAX_VALUE) <= now }
    }

    fun reviewQueue(d: LearnerData = current): List<ReviewTask> {
        val p = d.profile ?: return emptyList()
        val goalComps = path(d)?.steps?.map { it.competencyId }?.toSet().orEmpty()
        return queueBuilder().build(d.reviewItemsFor(p.activeTarget), ReviewQueueBuilder.Limits.forDailyMinutes(p.active.dailyMinutes), goalComps)
    }

    suspend fun recordReview(item: ReviewItem, method: RetrievalMethod, rating: Rating, latencyMs: Long?, hints: Int) {
        val updated = scheduler.record(item, method, rating, latencyMs, hints)
        store.update { d -> d.copy(reviewItems = d.reviewItems.map { if (it.id == item.id) updated else it }) }
        enqueue(ChangeType.REVIEW_ITEM, updated.id, FronterasJson.encodeToString(ReviewItem.serializer(), updated))
    }

    suspend fun editReviewItem(id: String, content: String, cue: String, explanation: String) {
        val now = clock.nowMillis()
        store.update { d -> d.copy(reviewItems = d.reviewItems.map { if (it.id == id) it.copy(content = content, cue = cue, explanation = explanation, updatedAt = now) else it }) }
        current.reviewItems.firstOrNull { it.id == id }?.let { enqueue(ChangeType.REVIEW_ITEM, id, FronterasJson.encodeToString(ReviewItem.serializer(), it)) }
    }

    suspend fun setSuspended(id: String, suspended: Boolean) {
        store.update { d -> d.copy(reviewItems = d.reviewItems.map { if (it.id == id) it.copy(suspended = suspended, updatedAt = clock.nowMillis()) else it }) }
        current.reviewItems.firstOrNull { it.id == id }?.let { enqueue(ChangeType.REVIEW_ITEM, id, FronterasJson.encodeToString(ReviewItem.serializer(), it)) }
    }

    suspend fun deleteReviewItem(id: String) {
        store.update { d -> d.copy(reviewItems = d.reviewItems.filter { it.id != id }, tombstones = d.tombstones + "REVIEW_ITEM:$id") }
    }

    // ---- sessions ----

    fun newSession(mode: SessionMode, scenario: Scenario?, competencies: List<Competency>, minutes: Int?): Session {
        val p = requireNotNull(profile)
        return Session(
            id = ids.newId(), learnerId = p.id, target = p.activeTarget, mode = mode, feedbackMode = p.active.feedbackMode,
            scenarioId = scenario?.id, competencyIds = competencies.map { it.id }, topic = scenario?.title, plannedMinutes = minutes,
            startedAt = clock.nowMillis(),
        )
    }

    /** Saves an in-progress session so a crash or app switch never loses it. */
    suspend fun saveInProgress(session: Session) {
        store.update { d -> d.copy(sessions = d.sessions.filter { it.id != session.id } + session) }
    }

    suspend fun finishSession(session: Session, analysis: SessionAnalysis?): SessionOutcome {
        val scenario = session.scenarioId?.let { Scenarios.byId[it] }
        val comps = session.competencyIds.mapNotNull { graph[it] }
        val now = clock.nowMillis()
        val outcome = processor.process(current, session, analysis, comps, scenario, now)
        store.update { processor.apply(it, outcome, now) }
        outcome.newReviewItems.forEach { enqueue(ChangeType.REVIEW_ITEM, it.id, FronterasJson.encodeToString(ReviewItem.serializer(), it)) }
        outcome.evidence.forEach { enqueue(ChangeType.EVIDENCE_EVENT, it.id, FronterasJson.encodeToString(EvidenceEvent.serializer(), it)) }
        return outcome
    }

    fun session(id: String): Session? = current.sessions.firstOrNull { it.id == id }

    suspend fun deleteSession(id: String) {
        store.update { d -> d.copy(sessions = d.sessions.filter { it.id != id }, analyses = d.analyses - id) }
    }

    /** Long-term context for the partner; only used when the learner enabled memory. */
    fun memory(target: LanguageTag, d: LearnerData = current): LongTermMemory? {
        if (d.profile?.consents?.conversationMemory?.granted != true) return null
        val recurring = d.sessionsFor(target).flatMap { it.allFeedback }.filter { it.patternKey != null }
            .groupBy { it.patternKey }.entries.sortedByDescending { it.value.size }.take(5)
            .map { (_, items) -> items.last().let { f -> "${f.explanation} (e.g. '${f.corrected ?: f.natural ?: f.original}')" } }
        val vocab = d.reviewItemsFor(target).sortedByDescending { it.createdAt }.take(8).map { it.content }
        val priorities = reviewQueue(d).take(5).map { it.item.content }
        return LongTermMemory(recurring, vocab, priorities, d.learnerNotes.take(5))
    }

    suspend fun recordConfidence(sessionId: String?, rating: Int, note: String?) {
        val p = profile ?: return
        store.update { it.copy(confidence = it.confidence + ConfidenceReport(p.activeTarget, clock.nowMillis(), sessionId, rating.coerceIn(1, 5), note)) }
    }

    suspend fun setNotes(notes: List<String>) { store.update { it.copy(learnerNotes = notes.map(String::trim).filter(String::isNotEmpty).take(20)) } }

    fun progress(d: LearnerData = current): ProgressReport? {
        val p = d.profile ?: return null
        val t = p.activeTarget
        val sessions = d.sessionsFor(t).sortedBy { it.startedAt }
        val corrections = sessions.flatMap { s -> s.allFeedback.mapNotNull { f -> f.corrected?.let { f.original to it }?.let { pair -> s.startedAt to pair } } }
        val examples = ProgressCalculator.improvementExamples(
            corrections.map { it.second },
            sessions.drop(1).flatMap { s -> s.learnerTurns.filter { turn -> corrections.any { it.first < s.startedAt } }.map { it.text } },
            { PostSessionProcessor.normalize(it) },
        )
        return ProgressCalculator.report(t, d.estimatesFor(t), d.estimateHistory, mastery(t, d).values, d.confidence, examples)
    }

    // ---- privacy ----

    fun exportJson(): String = store.exportJson()

    suspend fun eraseAll() = store.eraseAll()

    /** Applies the retention policy: drops expired transcripts but keeps their learning outcomes. */
    suspend fun enforceRetention() {
        val now = clock.nowMillis()
        store.update { d ->
            val policy = d.settings.retention
            d.copy(sessions = d.sessions.map { s ->
                val ended = s.endedAt
                if (ended != null && policy.transcriptExpired(ended, now) && s.turns.isNotEmpty()) s.copy(turns = emptyList()) else s
            })
        }
    }

    // ---- sync ----

    private suspend fun enqueueProfile() {
        val p = profile ?: return
        enqueue(ChangeType.PROFILE, p.id, FronterasJson.encodeToString(LearnerProfile.serializer(), p))
    }

    private suspend fun enqueue(type: ChangeType, entityId: String, payload: String) {
        val entry = OutboxEntry(ids.newId(), type, entityId, payload, clock.nowMillis())
        store.update { it.copy(outbox = Outbox.enqueue(it.outbox, entry)) }
    }

    suspend fun updateData(transform: (LearnerData) -> LearnerData) = store.update(transform)
}
