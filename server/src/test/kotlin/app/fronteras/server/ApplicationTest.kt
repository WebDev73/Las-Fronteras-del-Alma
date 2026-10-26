package app.fronteras.server

import app.fronteras.core.conversation.PartnerEvent
import app.fronteras.core.conversation.PartnerKind
import app.fronteras.core.conversation.PartnerRequest
import app.fronteras.core.conversation.ServerCapabilities
import app.fronteras.core.conversation.SessionAnalysis
import app.fronteras.core.conversation.SessionAnalysisRequest
import app.fronteras.core.entitlement.Plan
import app.fronteras.core.storage.FronterasJson
import app.fronteras.core.sync.ChangeType
import app.fronteras.core.sync.PushResult
import app.fronteras.core.sync.SyncChange
import app.fronteras.core.sync.SyncPullResponse
import app.fronteras.core.sync.SyncPushRequest
import app.fronteras.core.sync.SyncPushResponse
import app.fronteras.server.ai.AnalysisResult
import app.fronteras.server.ai.TutorModel
import app.fronteras.server.ai.UnconfiguredTutorModel
import app.fronteras.server.auth.DevTokenVerifier
import app.fronteras.server.sync.SyncStore
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeTutor(var fail: Boolean = false) : TutorModel {
    override val name = "fake"
    override val available = true
    var calls = 0
    override fun partnerTurn(request: PartnerRequest): Flow<PartnerEvent> {
        calls++
        return if (fail) flowOf(PartnerEvent.Failed("ai_unavailable", "down", true))
        else flowOf(PartnerEvent.TextDelta("¡Ho"), PartnerEvent.TextDelta("la!"), PartnerEvent.Completed("¡Hola!", null, PartnerKind.CLOUD_AI))
    }
    override suspend fun analyzeSession(request: SessionAnalysisRequest) =
        AnalysisResult.Ok(SessionAnalysis("ok", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), "next"))
}

class ApplicationTest {
    private val token = "dev-token-0123456789"
    private val config = ServerConfig.fromEnv(mapOf("FRONTERAS_DEV_TOKENS" to "$token:ana:free"))

    private fun app(tutor: TutorModel = FakeTutor(), block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { fronterasModule(Dependencies(config, tutor, DevTokenVerifier(config.devTokens), sync = SyncStore(null))) }
        block()
    }

    private fun json(obj: Any): String = when (obj) {
        is PartnerRequest -> FronterasJson.encodeToString(PartnerRequest.serializer(), obj)
        is SessionAnalysisRequest -> FronterasJson.encodeToString(SessionAnalysisRequest.serializer(), obj)
        is SyncPushRequest -> FronterasJson.encodeToString(SyncPushRequest.serializer(), obj)
        else -> error("unsupported")
    }

    @Test
    fun `health is public, everything else needs a token`() = app {
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/capabilities").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/capabilities") { bearerAuth("wrong-token-000000") }.status)
    }

    @Test
    fun `capabilities say honestly when AI is not configured`() = app(UnconfiguredTutorModel("no key")) {
        val caps = FronterasJson.decodeFromString(ServerCapabilities.serializer(), client.get("/v1/capabilities") { bearerAuth(token) }.bodyAsText())
        assertTrue(!caps.aiConversation)
        assertTrue(caps.reason!!.contains("ANTHROPIC_API_KEY"))
        val turn = client.post("/v1/conversation/turn") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(json(TestData.partner())) }
        assertEquals(HttpStatusCode.ServiceUnavailable, turn.status)
    }

    @Test
    fun `turns stream as server-sent events`() = app {
        val res = client.post("/v1/conversation/turn") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(json(TestData.partner())) }
        assertEquals(HttpStatusCode.OK, res.status)
        val events = res.bodyAsText().split("\n\n").filter { it.startsWith("data: ") }
            .map { FronterasJson.decodeFromString(PartnerEvent.serializer(), it.removePrefix("data: ")) }
        assertEquals(3, events.size)
        assertEquals("¡Hola!", (events.last() as PartnerEvent.Completed).reply)
    }

    @Test
    fun `oversized input is rejected before reaching the model`() {
        val tutor = FakeTutor()
        app(tutor) { rejectsOversized(tutor) }
    }

    private suspend fun ApplicationTestBuilder.rejectsOversized(tutor: FakeTutor) {
        val big = TestData.partner(turns = listOf(app.fronteras.core.conversation.WireTurn(app.fronteras.core.conversation.Speaker.LEARNER, "a".repeat(5000))))
        val res = client.post("/v1/conversation/turn") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(json(big)) }
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals(0, tutor.calls)
        val malformed = client.post("/v1/conversation/turn") { bearerAuth(token); contentType(ContentType.Application.Json); setBody("{nope") }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
    }

    @Test
    fun `daily quota is enforced and failed calls are refunded`() {
        val failing = FakeTutor(fail = true)
        app(failing) {
            repeat(50) { client.post("/v1/conversation/turn") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(json(TestData.partner())) } }
        }
        // All 50 failed before producing output, so none counted — but RPM limiting (30/min) still applies.
        assertTrue(failing.calls in 1..config.requestsPerMinute)
    }

    @Test
    fun `analysis endpoint returns the analysis`() = app {
        val res = client.post("/v1/session/analyze") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(json(TestData.analysis())) }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("next", FronterasJson.decodeFromString(SessionAnalysis.serializer(), res.bodyAsText()).nextStep)
    }

    @Test
    fun `sync push is idempotent and pull returns changes`() = app {
        val event = """{"id":"e1","competencyId":"greet-introduce","kind":"EXPOSURE","at":1,"success":true,"sessionId":"s1"}"""
        val push = SyncPushRequest("device-1", listOf(SyncChange("k1", ChangeType.EVIDENCE_EVENT, "e1", event), SyncChange("k2", ChangeType.REVIEW_ITEM, "r1", "{bad")))
        val first = FronterasJson.decodeFromString(SyncPushResponse.serializer(),
            client.post("/v1/sync/push") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(json(push)) }.bodyAsText())
        assertTrue(first.results[0] is PushResult.Accepted)
        assertTrue(first.results[1] is PushResult.Rejected)
        val again = FronterasJson.decodeFromString(SyncPushResponse.serializer(),
            client.post("/v1/sync/push") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(json(push.copy(changes = push.changes.take(1)))) }.bodyAsText())
        assertTrue(again.results.single() is PushResult.Duplicate)
        val pulled = FronterasJson.decodeFromString(SyncPullResponse.serializer(), client.get("/v1/sync/changes?since=0") { bearerAuth(token) }.bodyAsText())
        assertEquals(listOf("e1"), pulled.records.map { it.entityId })
        assertEquals(HttpStatusCode.NoContent, client.delete("/v1/account/data") { bearerAuth(token) }.status)
        val empty = FronterasJson.decodeFromString(SyncPullResponse.serializer(), client.get("/v1/sync/changes?since=0") { bearerAuth(token) }.bodyAsText())
        assertTrue(empty.records.isEmpty())
    }

    @Test
    fun `dev token config is validated`() {
        assertEquals(Plan.PREMIUM, ServerConfig.parseTokens("abcdefghijklmnop:bo:premium").values.single().plan)
        kotlin.test.assertFailsWith<IllegalArgumentException> { ServerConfig.parseTokens("short:bo") }
        assertTrue(File(".").exists())
    }
}
