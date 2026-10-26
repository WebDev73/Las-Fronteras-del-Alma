package app.fronteras.server.ai

import app.fronteras.core.conversation.PartnerEvent
import app.fronteras.server.TestData
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.OutputConfig
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Exercises the real Anthropic SDK against a local stand-in for the Messages API, so the
 * request we send and the streaming/parsing path are verified without network access.
 */
class ClaudeTutorModelTest {
    private lateinit var server: HttpServer
    private val bodies = mutableListOf<JsonObject>()
    private val headers = mutableListOf<Map<String, List<String>>>()
    private var respond: (JsonObject) -> Pair<Int, String> = { 200 to "" }

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/messages") { ex ->
            val body = Json.parseToJsonElement(ex.requestBody.readBytes().decodeToString()).jsonObject
            bodies += body
            headers += ex.requestHeaders.mapKeys { it.key.lowercase() }
            val (status, text) = respond(body)
            val stream = body["stream"]?.jsonPrimitive?.content == "true"
            ex.responseHeaders.add("Content-Type", if (stream && status == 200) "text/event-stream" else "application/json")
            val bytes = text.toByteArray()
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterTest
    fun stop() = server.stop(0)

    private fun model() = ClaudeTutorModel(
        AnthropicOkHttpClient.builder().apiKey("test-key").baseUrl("http://127.0.0.1:${server.address.port}").maxRetries(0).build(),
        "claude-opus-5-5", OutputConfig.Effort.LOW, OutputConfig.Effort.HIGH,
    )

    private fun sse(json: String, stop: String = "end_turn"): String {
        val events = mutableListOf<Pair<String, String>>()
        events += "message_start" to """{"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":12,"output_tokens":1}}}"""
        events += "content_block_start" to """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}"""
        events += "content_block_stop" to """{"type":"content_block_stop","index":0}"""
        events += "content_block_start" to """{"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}"""
        json.chunked(9).forEach { piece ->
            events += "content_block_delta" to """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":${JsonPrimitive(piece)}}}"""
        }
        events += "content_block_stop" to """{"type":"content_block_stop","index":1}"""
        events += "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"$stop","stop_sequence":null},"usage":{"output_tokens":40}}"""
        events += "message_stop" to """{"type":"message_stop"}"""
        return events.joinToString("") { (e, d) -> "event: $e\ndata: $d\n\n" }
    }

    @Test
    fun `turn request has the expected shape and the reply streams before completion`() = runTest {
        respond = { 200 to sse(TestData.TURN_JSON) }
        val events = model().partnerTurn(TestData.partner()).toList()

        val deltas = events.filterIsInstance<PartnerEvent.TextDelta>()
        assertTrue(deltas.size > 3, "reply is streamed in pieces")
        val done = assertIs<PartnerEvent.Completed>(events.last())
        assertEquals(deltas.joinToString("") { it.text }, done.reply)
        assertEquals("es:polite-request", done.analysis!!.feedback.single().patternKey)

        val body = bodies.single()
        assertEquals("claude-opus-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("true", body["stream"]!!.jsonPrimitive.content)
        assertEquals("default", body["fallbacks"]!!.jsonPrimitive.content)
        assertTrue("thinking" !in body, "Opus 5.5 runs adaptive thinking by default; disabling it is a 400")
        assertTrue("temperature" !in body)
        val oc = body["output_config"]!!.jsonObject
        assertEquals("low", oc["effort"]!!.jsonPrimitive.content)
        assertEquals("json_schema", oc["format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val system = body["system"]!!.jsonArray.single().jsonObject
        assertEquals("ephemeral", system["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray
        assertEquals(listOf("user", "assistant", "user"), messages.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        assertTrue(headers.single()["anthropic-beta"]!!.single().contains(ClaudeTutorModel.FALLBACK_BETA))
    }

    @Test
    fun `refusals and truncation are reported, not parsed`() = runTest {
        respond = { 200 to sse("""{"reply":"Lo sien""", stop = "refusal") }
        val refused = assertIs<PartnerEvent.Failed>(model().partnerTurn(TestData.partner()).toList().last())
        assertEquals("refused", refused.code)
        assertTrue(!refused.retryable)

        respond = { 200 to sse("""{"reply":"Hola""", stop = "max_tokens") }
        assertEquals("truncated", assertIs<PartnerEvent.Failed>(model().partnerTurn(TestData.partner()).toList().last()).code)
    }

    @Test
    fun `api errors map to retryable or permanent failures`() = runTest {
        respond = { 429 to """{"type":"error","error":{"type":"rate_limit_error","message":"slow down"}}""" }
        val rl = assertIs<PartnerEvent.Failed>(model().partnerTurn(TestData.partner()).toList().single())
        assertEquals("rate_limited", rl.code)
        assertTrue(rl.retryable)

        respond = { 401 to """{"type":"error","error":{"type":"authentication_error","message":"bad key"}}""" }
        val auth = assertIs<PartnerEvent.Failed>(model().partnerTurn(TestData.partner()).toList().single())
        assertEquals("ai_auth", auth.code)
        assertTrue(!auth.retryable)
    }

    @Test
    fun `session analysis uses structured output at high effort`() = runTest {
        val message = """{"id":"msg_2","type":"message","role":"assistant","model":"claude-opus-5-5","content":[{"type":"thinking","thinking":"","signature":"x"},{"type":"text","text":${JsonPrimitive(TestData.ANALYSIS_JSON)}}],"stop_reason":"end_turn","stop_sequence":null,"usage":{"input_tokens":50,"output_tokens":80}}"""
        respond = { 200 to message }
        val result = assertIs<AnalysisResult.Ok>(model().analyzeSession(TestData.analysis()))
        assertEquals("Practise paying.", result.analysis.nextStep)
        val body = bodies.single()
        assertEquals("high", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertTrue("stream" !in body || body["stream"]!!.jsonPrimitive.content == "false")
    }
}
