package app.fronteras.android.data

import app.fronteras.core.assessment.CefrLevel
import app.fronteras.core.conversation.LearnerSnapshot
import app.fronteras.core.conversation.PartnerEvent
import app.fronteras.core.conversation.PartnerKind
import app.fronteras.core.conversation.PartnerRequest
import app.fronteras.core.conversation.SessionMode
import app.fronteras.core.language.LanguageTag
import app.fronteras.core.profile.FeedbackMode
import app.fronteras.core.storage.FronterasJson
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private val server = MockWebServer()
    private lateinit var api: ApiClient

    private val request = PartnerRequest(
        "r", "s", SessionMode.FREE_CONVERSATION,
        LearnerSnapshot(LanguageTag("fr-CA"), "Canadian French", LanguageTag("ar"), listOf(LanguageTag("ar")), CefrLevel.A2, true, FeedbackMode.BALANCED, emptyList()),
        turns = emptyList(),
    )

    @Before fun setUp() {
        server.start()
        api = ApiClient({ ServerEndpoint(server.url("/").toString(), "token-123") })
    }

    @After fun tearDown() = server.shutdown()

    private fun sse(vararg events: PartnerEvent) = events.joinToString("") { "data: ${FronterasJson.encodeToString(PartnerEvent.serializer(), it)}\n\n" }

    @Test fun `streams partner events and sends the bearer token`() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody(sse(PartnerEvent.TextDelta("Sal"), PartnerEvent.TextDelta("ut"), PartnerEvent.Completed("Salut", null, PartnerKind.CLOUD_AI))))
        val events = api.partnerTurn(request).toList()
        assertEquals(3, events.size)
        assertEquals("Salut", (events.last() as PartnerEvent.Completed).reply)
        val recorded = server.takeRequest()
        assertEquals("Bearer token-123", recorded.getHeader("Authorization"))
        assertEquals("/v1/conversation/turn", recorded.path)
    }

    @Test fun `http errors become typed failures`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"code":"quota_exceeded","message":"Daily limit reached","retryable":false}"""))
        val ev = api.partnerTurn(request).toList().single() as PartnerEvent.Failed
        assertEquals("quota_exceeded", ev.code)
        server.enqueue(MockResponse().setResponseCode(401))
        val caps = api.capabilities()
        assertTrue(caps is ApiResult.Err && caps.error.code == "unauthorized")
    }

    @Test fun `connection loss is retryable`() = runBlocking {
        server.shutdown()
        val ev = api.partnerTurn(request).toList().single() as PartnerEvent.Failed
        assertTrue(ev.retryable)
    }

    @Test fun `remote partner is gated on consent, connectivity and server capability`() = runBlocking {
        var consent = false
        var online = true
        val partner = RemotePartner(api, { online }, { consent }, { true })
        assertEquals(false, partner.isAvailable())
        consent = true
        assertEquals(true, partner.isAvailable())
        online = false
        assertEquals(false, partner.isAvailable())
    }
}
