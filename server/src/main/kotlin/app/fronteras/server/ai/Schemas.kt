package app.fronteras.server.ai

import app.fronteras.core.assessment.Skill
import app.fronteras.core.conversation.ExtractedItem
import app.fronteras.core.conversation.RubricScore
import app.fronteras.core.conversation.SessionAnalysis
import app.fronteras.core.conversation.TurnAnalysis
import app.fronteras.core.conversation.WireFeedback
import app.fronteras.core.feedback.ErrorCategory
import app.fronteras.core.feedback.ErrorSeverity
import app.fronteras.core.feedback.RetrievalMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON Schemas for structured outputs, and the DTOs the model's JSON decodes into. */
object Schemas {
    private fun str() = mapOf("type" to "string")
    private fun bool() = mapOf("type" to "boolean")
    private fun num() = mapOf("type" to "number")
    private fun arr(items: Map<String, Any>) = mapOf("type" to "array", "items" to items)
    private fun enumOf(values: List<String>) = mapOf("type" to "string", "enum" to values)
    private fun obj(props: Map<String, Any>, required: List<String> = props.keys.toList()) =
        mapOf("type" to "object", "properties" to props, "required" to required, "additionalProperties" to false)

    val feedbackItem: Map<String, Any> = obj(
        linkedMapOf(
            "original" to str(),
            "focus" to str(),
            "corrected" to str(),
            "natural" to str(),
            "category" to enumOf(ErrorCategory.entries.map { it.name }),
            "severity" to enumOf(ErrorSeverity.entries.map { it.name }),
            "explanation" to str(),
            "comparison" to str(),
            "examples" to arr(str()),
            "intended_meaning" to str(),
            "intended_meaning_confident" to bool(),
            "clarification_question" to str(),
            "possible_recognition_issue" to bool(),
            "pattern_key" to str(),
            "recommended_exercise" to enumOf(RetrievalMethod.entries.map { it.name }),
            "suggest_review" to bool(),
            "confidence" to num(),
        ),
        required = listOf("original", "category", "severity", "explanation", "intended_meaning_confident", "possible_recognition_issue", "suggest_review", "confidence"),
    )

    /** `reply` is first so it can be streamed to the learner before the analysis is complete. */
    val partnerTurn: Map<String, Any> = obj(
        linkedMapOf(
            "reply" to str(),
            "analysis" to obj(linkedMapOf(
                "feedback" to arr(feedbackItem),
                "comprehension_breakdown" to bool(),
                "expressions_used" to arr(str()),
            )),
        ),
    )

    val sessionAnalysis: Map<String, Any> = obj(
        linkedMapOf(
            "summary" to str(),
            "strengths" to arr(str()),
            "feedback" to arr(feedbackItem),
            "rubric" to arr(obj(linkedMapOf(
                "skill" to enumOf(Skill.entries.map { it.name }),
                "score" to num(),
                "evidence" to arr(str()),
                "confidence" to num(),
            ))),
            "new_items" to arr(obj(linkedMapOf(
                "content" to str(), "cue" to str(), "explanation" to str(),
                "kind" to enumOf(listOf("word", "chunk", "idiom", "collocation", "grammar", "recognized")),
                "example" to str(),
            ), required = listOf("content", "cue", "explanation", "kind"))),
            "objectives_met" to arr(str()),
            "next_step" to str(),
            "limitations" to arr(str()),
        ),
    )

    val json = Json { ignoreUnknownKeys = true; isLenient = false; explicitNulls = false }

    @Serializable
    data class ModelFeedback(
        val original: String,
        val focus: String? = null,
        val corrected: String? = null,
        val natural: String? = null,
        val category: ErrorCategory,
        val severity: ErrorSeverity,
        val explanation: String,
        val comparison: String? = null,
        val examples: List<String> = emptyList(),
        @SerialName("intended_meaning") val intendedMeaning: String? = null,
        @SerialName("intended_meaning_confident") val intendedMeaningConfident: Boolean = true,
        @SerialName("clarification_question") val clarificationQuestion: String? = null,
        @SerialName("possible_recognition_issue") val possibleRecognitionIssue: Boolean = false,
        @SerialName("pattern_key") val patternKey: String? = null,
        @SerialName("recommended_exercise") val recommendedExercise: RetrievalMethod? = null,
        @SerialName("suggest_review") val suggestReview: Boolean = false,
        val confidence: Double,
    ) {
        fun toWire() = WireFeedback(
            original = original, focus = focus.blankToNull(), corrected = corrected.blankToNull(), natural = natural.blankToNull(),
            category = category, severity = severity, explanation = explanation, comparison = comparison.blankToNull(),
            examples = examples.filter { it.isNotBlank() }.take(3), intendedMeaning = intendedMeaning.blankToNull(),
            intendedMeaningConfident = intendedMeaningConfident, clarificationQuestion = clarificationQuestion.blankToNull(),
            possibleRecognitionIssue = possibleRecognitionIssue, patternKey = patternKey.blankToNull(),
            recommendedExercise = recommendedExercise, suggestReview = suggestReview, confidence = confidence.coerceIn(0.0, 1.0),
        )
    }

    @Serializable
    data class ModelTurnAnalysis(
        val feedback: List<ModelFeedback> = emptyList(),
        @SerialName("comprehension_breakdown") val comprehensionBreakdown: Boolean = false,
        @SerialName("expressions_used") val expressionsUsed: List<String> = emptyList(),
    )

    @Serializable
    data class ModelTurn(val reply: String, val analysis: ModelTurnAnalysis? = null)

    @Serializable
    data class ModelRubric(val skill: Skill, val score: Double, val evidence: List<String> = emptyList(), val confidence: Double)

    @Serializable
    data class ModelItem(val content: String, val cue: String, val explanation: String, val kind: String, val example: String? = null)

    @Serializable
    data class ModelAnalysis(
        val summary: String,
        val strengths: List<String> = emptyList(),
        val feedback: List<ModelFeedback> = emptyList(),
        val rubric: List<ModelRubric> = emptyList(),
        @SerialName("new_items") val newItems: List<ModelItem> = emptyList(),
        @SerialName("objectives_met") val objectivesMet: List<String> = emptyList(),
        @SerialName("next_step") val nextStep: String = "",
        val limitations: List<String> = emptyList(),
    )

    fun parseTurn(text: String): Pair<String, TurnAnalysis?> {
        val t = json.decodeFromString(ModelTurn.serializer(), text)
        val a = t.analysis?.let { TurnAnalysis(it.feedback.take(3).map(ModelFeedback::toWire), it.comprehensionBreakdown, it.expressionsUsed) }
        return t.reply.trim() to a
    }

    fun parseAnalysis(text: String): SessionAnalysis {
        val a = json.decodeFromString(ModelAnalysis.serializer(), text)
        return SessionAnalysis(
            summary = a.summary,
            strengths = a.strengths,
            feedback = a.feedback.take(6).map(ModelFeedback::toWire),
            rubric = a.rubric.map { RubricScore(it.skill, it.score.coerceIn(0.0, 4.0), it.evidence.take(2), it.confidence.coerceIn(0.0, 1.0)) },
            newItems = a.newItems.take(5).map { ExtractedItem(it.content, it.cue, it.explanation, it.kind, it.example.blankToNull()) },
            objectivesMet = a.objectivesMet,
            nextStep = a.nextStep,
            limitations = a.limitations,
        )
    }

    private fun String?.blankToNull() = this?.takeIf { it.isNotBlank() }
}
