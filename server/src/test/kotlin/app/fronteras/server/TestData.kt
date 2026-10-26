package app.fronteras.server

import app.fronteras.core.assessment.CefrLevel
import app.fronteras.core.conversation.LearnerSnapshot
import app.fronteras.core.conversation.PartnerRequest
import app.fronteras.core.conversation.ScenarioBrief
import app.fronteras.core.conversation.SessionAnalysisRequest
import app.fronteras.core.conversation.SessionMode
import app.fronteras.core.conversation.Speaker
import app.fronteras.core.conversation.WireTurn
import app.fronteras.core.language.LanguageTag
import app.fronteras.core.profile.FeedbackMode

object TestData {
    val learner = LearnerSnapshot(
        target = LanguageTag("es-MX"), targetName = "Mexican Spanish", explanationLanguage = LanguageTag("en"),
        supportLanguages = listOf(LanguageTag("tl"), LanguageTag("en")), estimatedLevel = CefrLevel.A2, levelUncertain = true,
        feedbackMode = FeedbackMode.BALANCED, goals = listOf("Travel"),
    )
    val scenario = ScenarioBrief("Ordering at a café", "A busy café", "a server", "a customer", "Order and pay", sensitive = false)

    fun partner(turns: List<WireTurn> = listOf(WireTurn(Speaker.PARTNER, "¡Buenas! ¿Qué le pongo?"), WireTurn(Speaker.LEARNER, "Quiero un café, por favor", 0.42)), help: Boolean = false) =
        PartnerRequest("req-1", "s-1", SessionMode.ROLE_PLAY, learner, scenario, listOf("Order food or drinks and pay politely."), listOf("¿Me trae la cuenta?"), turns, helpRequested = help)

    fun analysis() = SessionAnalysisRequest("req-2", "s-1", SessionMode.ROLE_PLAY, learner, scenario, listOf("Order food or drinks and pay politely."),
        listOf(WireTurn(Speaker.PARTNER, "¿Qué le pongo?"), WireTurn(Speaker.LEARNER, "Un café con leche")))

    const val TURN_JSON = """{"reply":"¡Claro! ¿Lo quiere \"grande\" o chico?\nTenemos de todo.","analysis":{"feedback":[{"original":"Quiero un café","natural":"¿Me da un café, por favor?","category":"REGISTER","severity":"UNNATURAL","explanation":"A question sounds more polite.","intended_meaning_confident":true,"possible_recognition_issue":false,"suggest_review":true,"confidence":0.8,"pattern_key":"es:polite-request"}],"comprehension_breakdown":false,"expressions_used":[]}}"""

    const val ANALYSIS_JSON = """{"summary":"You ordered successfully.","strengths":["Clear request"],"feedback":[],"rubric":[{"skill":"INTERACTION","score":3,"evidence":["Un café con leche"],"confidence":0.4}],"new_items":[{"content":"¿Me da…?","cue":"Can I have…?","explanation":"Polite request","kind":"chunk","example":""}],"objectives_met":["Order food or drinks and pay politely."],"next_step":"Practise paying.","limitations":["Very short sample"]}"""
}
