package app.fronteras.core.profile

import app.fronteras.core.assessment.CefrLevel
import app.fronteras.core.language.LanguageCatalog
import app.fronteras.core.language.LanguageTag
import kotlinx.serialization.Serializable

/** Onboarding steps in order. Non-essential steps may be skipped and revised later. */
@Serializable
enum class OnboardingStep(val essential: Boolean) {
    INTERFACE_LANGUAGE(true),
    TARGET_LANGUAGE(true),
    NATIVE_LANGUAGES(true),
    OTHER_LANGUAGES(false),
    SELF_ASSESSMENT(false),
    GOALS(false),
    SITUATIONS(false),
    DAILY_TIME(false),
    EXPLANATION_LANGUAGE(false),
    CORRECTION_INTENSITY(false),
    VOICE(false),
    ACCESSIBILITY(false),
    PRIVACY(false),
}

/** Partially completed onboarding answers. Every field is optional until [OnboardingFlow.complete]. */
@Serializable
data class OnboardingDraft(
    val interfaceLanguage: LanguageTag? = null,
    val target: LanguageTag? = null,
    val nativeLanguages: List<LanguageTag> = emptyList(),
    val otherLanguages: List<KnownLanguage> = emptyList(),
    val selfAssessedLevel: CefrLevel? = null,
    val goals: List<LearningGoal> = emptyList(),
    val otherGoal: String? = null,
    val situations: List<String> = emptyList(),
    val dailyMinutes: Int? = null,
    val explanationLanguage: LanguageTag? = null,
    val feedbackMode: FeedbackMode? = null,
    val voice: VoicePreferences? = null,
    val accessibility: AccessibilityPreferences? = null,
    val consents: Consents = Consents(),
    val skipped: Set<OnboardingStep> = emptySet(),
)

sealed interface OnboardingResult {
    data class Complete(val profile: LearnerProfile) : OnboardingResult
    data class Incomplete(val missing: List<OnboardingStep>, val problems: List<String>) : OnboardingResult
}

object OnboardingFlow {
    val steps: List<OnboardingStep> = OnboardingStep.entries

    fun canSkip(step: OnboardingStep): Boolean = !step.essential

    fun isAnswered(draft: OnboardingDraft, step: OnboardingStep): Boolean = when (step) {
        OnboardingStep.INTERFACE_LANGUAGE -> draft.interfaceLanguage != null
        OnboardingStep.TARGET_LANGUAGE -> draft.target != null
        OnboardingStep.NATIVE_LANGUAGES -> draft.nativeLanguages.isNotEmpty()
        OnboardingStep.OTHER_LANGUAGES -> draft.otherLanguages.isNotEmpty()
        OnboardingStep.SELF_ASSESSMENT -> draft.selfAssessedLevel != null
        OnboardingStep.GOALS -> draft.goals.isNotEmpty()
        OnboardingStep.SITUATIONS -> draft.situations.isNotEmpty()
        OnboardingStep.DAILY_TIME -> draft.dailyMinutes != null
        OnboardingStep.EXPLANATION_LANGUAGE -> draft.explanationLanguage != null
        OnboardingStep.CORRECTION_INTENSITY -> draft.feedbackMode != null
        OnboardingStep.VOICE -> draft.voice != null
        OnboardingStep.ACCESSIBILITY -> draft.accessibility != null
        OnboardingStep.PRIVACY -> draft.consents.audioProcessing.decidedAt != null || draft.consents.cloudAi.decidedAt != null
    } || step in draft.skipped

    fun nextStep(draft: OnboardingDraft): OnboardingStep? = steps.firstOrNull { !isAnswered(draft, it) }

    fun skip(draft: OnboardingDraft, step: OnboardingStep): OnboardingDraft {
        require(canSkip(step)) { "$step is required" }
        return draft.copy(skipped = draft.skipped + step)
    }

    fun validate(draft: OnboardingDraft): List<String> = buildList {
        val target = draft.target
        if (target != null) {
            if (!LanguageCatalog.hasLearningContent(target)) add("Learning content for ${LanguageCatalog.displayName(target)} is not available yet.")
            // Learning one's own native language variety is unusual but allowed (e.g. heritage speakers) — only flag exact match.
            if (draft.nativeLanguages.any { it == target }) add("The target variety is the same as a native language.")
        }
        val minutes = draft.dailyMinutes
        if (minutes != null && minutes !in 1..240) add("Daily study time must be between 1 and 240 minutes.")
    }

    fun complete(draft: OnboardingDraft, learnerId: String, now: Long): OnboardingResult {
        val missing = steps.filter { it.essential && !isAnswered(draft, it) }
        val problems = validate(draft)
        if (missing.isNotEmpty() || problems.isNotEmpty() || draft.target == null || draft.interfaceLanguage == null) {
            return OnboardingResult.Incomplete(missing, problems)
        }
        val explanation = draft.explanationLanguage ?: defaultExplanationLanguage(draft)
        val target = TargetLanguageProfile(
            target = draft.target,
            selfAssessedLevel = draft.selfAssessedLevel,
            goals = draft.goals.ifEmpty { listOf(LearningGoal.CONVERSATION) },
            otherGoal = draft.otherGoal,
            speakingSituations = draft.situations,
            dailyMinutes = draft.dailyMinutes ?: 10,
            explanationLanguage = explanation,
            feedbackMode = draft.feedbackMode ?: FeedbackMode.BALANCED,
            voice = draft.voice ?: VoicePreferences(accent = draft.target),
            createdAt = now,
        )
        return OnboardingResult.Complete(
            LearnerProfile(
                id = learnerId,
                interfaceLanguage = draft.interfaceLanguage,
                nativeLanguages = draft.nativeLanguages.distinct(),
                otherLanguages = draft.otherLanguages.distinctBy { it.tag },
                targets = listOf(target),
                activeTarget = draft.target,
                accessibility = draft.accessibility ?: AccessibilityPreferences(),
                consents = draft.consents,
                updatedAt = now,
            ),
        )
    }

    /** Prefer the interface language if the learner knows it, otherwise their first native language. */
    private fun defaultExplanationLanguage(draft: OnboardingDraft): LanguageTag {
        val ui = draft.interfaceLanguage!!
        val known = draft.nativeLanguages + draft.otherLanguages.map { it.tag }
        return if (known.any { it.sameLanguageAs(ui) }) ui else draft.nativeLanguages.first()
    }

    /** Adds another target language to an existing profile without disturbing the others. */
    fun addTarget(profile: LearnerProfile, target: TargetLanguageProfile, now: Long, makeActive: Boolean = true): LearnerProfile {
        require(profile.target(target.target) == null) { "Already learning ${target.target}" }
        return profile.copy(
            targets = profile.targets + target,
            activeTarget = if (makeActive) target.target else profile.activeTarget,
            updatedAt = now,
        )
    }
}
