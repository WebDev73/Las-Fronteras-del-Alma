package app.fronteras.server.ai

import app.fronteras.core.conversation.PartnerEvent
import app.fronteras.core.conversation.PartnerRequest
import app.fronteras.core.conversation.SessionAnalysis
import app.fronteras.core.conversation.SessionAnalysisRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The server's AI boundary. One call per learner turn (reply + light analysis) and one
 * batched call per session (full analysis) — never one call per responsibility.
 */
interface TutorModel {
    val name: String
    val available: Boolean
    fun partnerTurn(request: PartnerRequest): Flow<PartnerEvent>
    suspend fun analyzeSession(request: SessionAnalysisRequest): AnalysisResult
}

sealed interface AnalysisResult {
    data class Ok(val analysis: SessionAnalysis) : AnalysisResult
    data class Failed(val code: String, val message: String, val retryable: Boolean) : AnalysisResult
}

/** Used when no API key is configured. It says so; it never pretends. */
class UnconfiguredTutorModel(private val reason: String) : TutorModel {
    override val name = "none"
    override val available = false
    override fun partnerTurn(request: PartnerRequest): Flow<PartnerEvent> =
        flowOf(PartnerEvent.Failed("ai_not_configured", reason, retryable = false))
    override suspend fun analyzeSession(request: SessionAnalysisRequest) = AnalysisResult.Failed("ai_not_configured", reason, false)
}
