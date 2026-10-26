package app.fronteras.core

import app.fronteras.core.assessment.CefrLevel
import app.fronteras.core.feedback.ErrorCategory
import app.fronteras.core.feedback.ErrorSeverity
import app.fronteras.core.feedback.FeedbackItem
import app.fronteras.core.language.LanguageTag
import app.fronteras.core.profile.Consent
import app.fronteras.core.profile.Consents
import app.fronteras.core.profile.FeedbackMode
import app.fronteras.core.profile.LearnerProfile
import app.fronteras.core.profile.LearningGoal
import app.fronteras.core.profile.TargetLanguageProfile

object Fixtures {
    val es = LanguageTag("es-ES")
    val en = LanguageTag("en")
    val pt = LanguageTag("pt-BR")

    fun profile(
        target: LanguageTag = es,
        mode: FeedbackMode = FeedbackMode.BALANCED,
        memory: Boolean = false,
        dailyMinutes: Int = 15,
        goals: List<LearningGoal> = listOf(LearningGoal.CONVERSATION),
        level: CefrLevel? = CefrLevel.A2,
    ) = LearnerProfile(
        id = "learner-1",
        interfaceLanguage = en,
        nativeLanguages = listOf(pt),
        targets = listOf(TargetLanguageProfile(target = target, selfAssessedLevel = level, goals = goals, dailyMinutes = dailyMinutes, explanationLanguage = en, feedbackMode = mode)),
        activeTarget = target,
        consents = Consents(cloudAi = Consent(true, 1), conversationMemory = Consent(memory, 1)),
    )

    fun feedback(
        id: String,
        severity: ErrorSeverity = ErrorSeverity.SIGNIFICANT,
        confidence: Double = 0.9,
        pattern: String? = id,
        category: ErrorCategory = ErrorCategory.GRAMMAR,
        intentConfident: Boolean = true,
        recognition: Boolean = false,
        corrected: String? = "Quería un café",
    ) = FeedbackItem(
        id = id, sessionId = "s1", turnId = "t1", original = "Quiero un café", corrected = corrected,
        natural = "¿Me pone un café?", category = category, severity = severity, explanation = "Polite request",
        explanationLanguage = en, confidence = confidence, patternKey = pattern, intendedMeaningConfident = intentConfident,
        clarificationQuestion = if (!intentConfident) "Did you mean you wanted a coffee?" else null,
        possibleRecognitionIssue = recognition,
    )
}
