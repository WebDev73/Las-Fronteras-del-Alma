package app.fronteras.core.conversation

import app.fronteras.core.assessment.SkillEstimate
import app.fronteras.core.curriculum.Competency
import app.fronteras.core.language.LanguageCatalog
import app.fronteras.core.language.VariantGuidance
import app.fronteras.core.privacy.Redactor
import app.fronteras.core.profile.LearnerProfile

/**
 * Assembles the three context levels (spec §6.4) into a bounded [PartnerRequest]:
 *  - turn context: the learner's latest turn;
 *  - session context: scenario, objectives, recent dialogue, earlier summary;
 *  - long-term context: recurring errors, vocabulary, review priorities — only with consent.
 */
class ContextBuilder(
    private val maxRecentTurns: Int = 12,
    private val redactor: Redactor = Redactor(),
) {
    fun snapshot(profile: LearnerProfile, estimates: List<SkillEstimate>): LearnerSnapshot {
        val t = profile.active
        val speaking = estimates.filter { it.skill.name in setOf("SPEAKING", "GRAMMAR", "VOCABULARY", "LISTENING") }
        val level = t.levelOverride ?: if (speaking.isNotEmpty()) app.fronteras.core.assessment.CefrLevel.fromScale(speaking.map { it.mean }.average()) else t.selfAssessedLevel
        return LearnerSnapshot(
            target = t.target,
            targetName = LanguageCatalog.displayName(t.target),
            explanationLanguage = t.explanationLanguage,
            supportLanguages = profile.supportLanguages,
            estimatedLevel = level,
            levelUncertain = speaking.isEmpty() || speaking.any { !it.isSettled },
            feedbackMode = t.feedbackMode,
            goals = t.goals.map { it.label } + listOfNotNull(t.otherGoal),
            interests = t.interests,
        )
    }

    fun build(
        requestId: String,
        session: Session,
        profile: LearnerProfile,
        estimates: List<SkillEstimate>,
        scenario: Scenario?,
        competencies: List<Competency>,
        memory: LongTermMemory?,
        helpRequested: Boolean,
    ): PartnerRequest {
        val dialogue = session.turns.filter { it.speaker != Speaker.SYSTEM && it.status != TurnStatus.FAILED }
        val recent = dialogue.takeLast(maxRecentTurns)
        val older = dialogue.dropLast(maxRecentTurns)
        val base = session.target.base
        val memoryAllowed = profile.consents.conversationMemory.granted
        return PartnerRequest(
            requestId = requestId,
            sessionId = session.id,
            mode = session.mode,
            learner = snapshot(profile, estimates),
            scenario = scenario?.let { ScenarioBrief(it.title, it.setting, it.partnerRole, it.learnerRole, it.learnerGoal, it.sensitive) },
            objectives = competencies.map { it.canDo },
            usefulExpressions = competencies.flatMap { c ->
                c.expressions[base].orEmpty().filter { it.variants.isEmpty() || session.target.value in it.variants }.map { it.text }
            }.distinct().take(12),
            turns = recent.map { WireTurn(it.speaker, redactor.redact(it.text), it.transcript?.takeUnless { t -> t.editedByLearner }?.confidence) },
            earlierSummary = if (older.isEmpty()) null else summarize(older, session),
            memory = if (memoryAllowed) memory else null,
            helpRequested = helpRequested,
            varietyNotes = VariantGuidance.forBase(base).mapNotNull { n ->
                VariantGuidance.preferredForm(n, session.target)?.let { "${n.meaning}: prefer '$it' (${n.kind.name.lowercase().replace('_', ' ')})" }
            },
        )
    }

    /** Deterministic summary of trimmed turns; avoids an extra model call. */
    private fun summarize(older: List<Turn>, session: Session): String {
        val learner = older.count { it.speaker == Speaker.LEARNER }
        val topics = session.topic?.let { " Topic: $it." } ?: ""
        val corrected = session.shownFeedback.filter { f -> older.any { it.id == f.turnId } }
            .mapNotNull { it.corrected }.take(3)
        val corr = if (corrected.isEmpty()) "" else " Corrections already given: ${corrected.joinToString("; ")}."
        return "${older.size} earlier turns ($learner from the learner).$topics$corr"
    }
}
