package app.fronteras.server.ai

import app.fronteras.core.conversation.PartnerEvent
import app.fronteras.core.conversation.PartnerKind
import app.fronteras.core.conversation.PartnerRequest
import app.fronteras.core.conversation.SessionAnalysisRequest
import app.fronteras.core.conversation.Speaker
import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Claude-backed tutor using the official Anthropic Java SDK.
 *
 * - Structured outputs (`output_config.format`) guarantee parseable turn and analysis JSON.
 * - The turn call streams; the `reply` field is extracted as it arrives so the app can start
 *   text-to-speech before the analysis is finished.
 * - Static system prompts with prompt caching; the session brief is the stable first message.
 * - Server-side refusal fallbacks are enabled (`fallbacks: "default"`); refusals that still
 *   occur are reported, never retried blindly.
 */
class ClaudeTutorModel(
    private val client: AnthropicClient,
    private val model: String,
    private val turnEffort: OutputConfig.Effort,
    private val analysisEffort: OutputConfig.Effort,
) : TutorModel {
    private val log = LoggerFactory.getLogger(ClaudeTutorModel::class.java)
    override val name: String = model
    override val available = true

    private fun MessageCreateParams.Builder.withDefaults(): MessageCreateParams.Builder = this
        .model(model)
        .cacheControl(CacheControlEphemeral.builder().build())
        // Server-side refusal fallback, routed by refusal category.
        .putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
        .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))

    private fun systemBlock(text: String) = listOf(
        TextBlockParam.builder().text(text).cacheControl(CacheControlEphemeral.builder().build()).build(),
    )

    private fun format(schema: Map<String, Any>) = JsonOutputFormat.builder()
        .schema(JsonOutputFormat.Schema.builder().apply { schema.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) } }.build())
        .build()

    fun buildTurnParams(request: PartnerRequest): MessageCreateParams {
        val b = MessageCreateParams.builder()
            .withDefaults()
            .maxTokens(16_000L)
            .systemOfTextBlockParams(systemBlock(Prompts.PARTNER_SYSTEM))
            .outputConfig(OutputConfig.builder().effort(turnEffort).format(format(Schemas.partnerTurn)).build())
        Prompts.partnerMessages(request).forEach { (role, text) ->
            if (role == Speaker.LEARNER) b.addUserMessage(text) else b.addAssistantMessage(text)
        }
        return b.build()
    }

    fun buildAnalysisParams(request: SessionAnalysisRequest): MessageCreateParams = MessageCreateParams.builder()
        .withDefaults()
        .maxTokens(16_000L)
        .systemOfTextBlockParams(systemBlock(Prompts.ANALYSIS_SYSTEM))
        .outputConfig(OutputConfig.builder().effort(analysisEffort).format(format(Schemas.sessionAnalysis)).build())
        .addUserMessage(Prompts.analysisPrompt(request))
        .build()

    override fun partnerTurn(request: PartnerRequest): Flow<PartnerEvent> = flow {
        val params = buildTurnParams(request)
        val accumulator = MessageAccumulator.create()
        val streamer = ReplyFieldStreamer()
        try {
            client.messages().createStreaming(params).use { response ->
                val it = response.stream().iterator()
                while (it.hasNext()) {
                    val event = accumulator.accumulate(it.next())
                    val delta = event.contentBlockDelta().flatMap { d -> d.delta().text() }.orElse(null)
                    if (delta != null) {
                        val piece = streamer.feed(delta.text())
                        if (piece.isNotEmpty()) emit(PartnerEvent.TextDelta(piece))
                    }
                }
            }
            val message = accumulator.message()
            emit(completeTurn(message))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // client went away; stop streaming
        } catch (e: Exception) {
            emit(failure(e))
        }
    }.flowOn(Dispatchers.IO)

    private fun completeTurn(message: Message): PartnerEvent {
        refusalOrTruncation(message)?.let { return it }
        val text = finalText(message)
        return try {
            val (reply, analysis) = Schemas.parseTurn(text)
            if (reply.isBlank()) PartnerEvent.Failed("empty_reply", "The partner returned an empty reply.", retryable = true)
            else PartnerEvent.Completed(reply, analysis, PartnerKind.CLOUD_AI)
        } catch (e: Exception) {
            log.warn("Unparseable turn output ({} chars)", text.length)
            PartnerEvent.Failed("bad_output", "The partner's reply could not be read.", retryable = true)
        }
    }

    override suspend fun analyzeSession(request: SessionAnalysisRequest): AnalysisResult = withContext(Dispatchers.IO) {
        try {
            val message = client.messages().create(buildAnalysisParams(request))
            when (val problem = refusalOrTruncation(message)) {
                is PartnerEvent.Failed -> AnalysisResult.Failed(problem.code, problem.message, problem.retryable)
                else -> AnalysisResult.Ok(Schemas.parseAnalysis(finalText(message)))
            }
        } catch (e: kotlinx.serialization.SerializationException) {
            AnalysisResult.Failed("bad_output", "The analysis could not be read.", true)
        } catch (e: Exception) {
            val f = failure(e)
            AnalysisResult.Failed(f.code, f.message, f.retryable)
        }
    }

    private fun finalText(message: Message): String =
        message.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }.joinToString("")

    private fun refusalOrTruncation(message: Message): PartnerEvent.Failed? {
        val stop = message.stopReason().orElse(null)
        return when (stop) {
            StopReason.REFUSAL -> PartnerEvent.Failed("refused", "The AI declined to continue this conversation. Try a different topic.", retryable = false)
            StopReason.MAX_TOKENS, StopReason.MODEL_CONTEXT_WINDOW_EXCEEDED -> PartnerEvent.Failed("truncated", "The reply was cut off.", retryable = true)
            else -> null
        }
    }

    private fun failure(e: Exception): PartnerEvent.Failed {
        // Most specific first; retryable = rate limits, server errors and network problems.
        log.warn("Claude request failed: {}", e.javaClass.simpleName)
        return when (e) {
            is RateLimitException -> PartnerEvent.Failed("rate_limited", "The AI service is busy. Retrying shortly.", true)
            is UnauthorizedException, is PermissionDeniedException -> PartnerEvent.Failed("ai_auth", "The server's AI credentials were rejected.", false)
            is BadRequestException -> PartnerEvent.Failed("bad_request", "The AI service rejected the request.", false)
            is InternalServerException -> PartnerEvent.Failed("ai_unavailable", "The AI service is temporarily unavailable.", true)
            is AnthropicServiceException -> PartnerEvent.Failed("ai_error", "The AI service returned an error (${e.statusCode()}).", e.statusCode() >= 500 || e.statusCode() == 429 || e.statusCode() == 408)
            is AnthropicIoException -> PartnerEvent.Failed("network", "Could not reach the AI service.", true)
            else -> PartnerEvent.Failed("internal", "Unexpected error.", true)
        }
    }

    companion object {
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        fun effort(name: String): OutputConfig.Effort = when (name.lowercase()) {
            "low" -> OutputConfig.Effort.LOW
            "medium" -> OutputConfig.Effort.MEDIUM
            "high" -> OutputConfig.Effort.HIGH
            "xhigh" -> OutputConfig.Effort.XHIGH
            "max" -> OutputConfig.Effort.MAX
            else -> throw IllegalArgumentException("Unknown effort '$name'")
        }
    }
}
