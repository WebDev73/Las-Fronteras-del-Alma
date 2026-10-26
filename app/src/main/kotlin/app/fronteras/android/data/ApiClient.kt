package app.fronteras.android.data

import app.fronteras.core.conversation.ApiError
import app.fronteras.core.conversation.PartnerEvent
import app.fronteras.core.conversation.PartnerRequest
import app.fronteras.core.conversation.ServerCapabilities
import app.fronteras.core.conversation.SessionAnalysis
import app.fronteras.core.conversation.SessionAnalysisRequest
import app.fronteras.core.storage.FronterasJson
import app.fronteras.core.sync.SyncPullResponse
import app.fronteras.core.sync.SyncPushRequest
import app.fronteras.core.sync.SyncPushResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Where the Fronteras server is and how to authenticate. */
data class ServerEndpoint(val baseUrl: String, val token: String) {
    val configured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
}

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(val error: ApiError, val httpStatus: Int?) : ApiResult<Nothing>
}

/**
 * HTTP client for the Fronteras server. The API key for the AI provider never lives on the
 * device; the app only holds a per-user access token for this server.
 */
class ApiClient(
    private val endpoint: () -> ServerEndpoint,
    private val http: OkHttpClient = defaultClient(),
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun url(path: String) = endpoint().baseUrl.trimEnd('/') + path

    private fun request(path: String) = Request.Builder().url(url(path)).header("Authorization", "Bearer ${endpoint().token}")

    suspend fun capabilities(): ApiResult<ServerCapabilities> = call(request("/v1/capabilities").get().build(), ServerCapabilities.serializer())

    suspend fun analyze(req: SessionAnalysisRequest): ApiResult<SessionAnalysis> =
        call(request("/v1/session/analyze").post(FronterasJson.encodeToString(SessionAnalysisRequest.serializer(), req).toRequestBody(jsonType)).build(), SessionAnalysis.serializer())

    suspend fun syncPush(req: SyncPushRequest): ApiResult<SyncPushResponse> =
        call(request("/v1/sync/push").post(FronterasJson.encodeToString(SyncPushRequest.serializer(), req).toRequestBody(jsonType)).build(), SyncPushResponse.serializer())

    suspend fun syncPull(since: Long): ApiResult<SyncPullResponse> = call(request("/v1/sync/changes?since=$since").get().build(), SyncPullResponse.serializer())

    suspend fun deleteServerData(): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            http.newCall(request("/v1/account/data").delete().build()).await().use { r ->
                if (r.isSuccessful) ApiResult.Ok(Unit) else ApiResult.Err(parseError(r.body?.string(), r.code), r.code)
            }
        } catch (e: IOException) {
            ApiResult.Err(ApiError("network", "Could not reach the server.", true), null)
        }
    }

    /** Streams one partner turn over server-sent events. */
    fun partnerTurn(req: PartnerRequest): Flow<PartnerEvent> = callbackFlow {
        val body = FronterasJson.encodeToString(PartnerRequest.serializer(), req).toRequestBody(jsonType)
        val request = request("/v1/conversation/turn").header("Accept", "text/event-stream").post(body).build()
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val ev = runCatching { FronterasJson.decodeFromString(PartnerEvent.serializer(), data) }.getOrElse {
                    PartnerEvent.Failed("bad_event", "Unreadable reply from server.", retryable = true)
                }
                trySend(ev)
                if (ev is PartnerEvent.Completed || ev is PartnerEvent.Failed) close()
            }

            override fun onClosed(eventSource: EventSource) { close() }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val ev = if (response != null && !response.isSuccessful) {
                    val err = parseError(response.body?.string(), response.code)
                    PartnerEvent.Failed(err.code, err.message, err.retryable)
                } else {
                    PartnerEvent.Failed("network", "Connection lost. Your conversation is saved.", retryable = true)
                }
                trySend(ev)
                close()
            }
        }
        val source = EventSources.createFactory(http).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    private suspend fun <T> call(req: Request, serializer: KSerializer<T>): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            http.newCall(req).await().use { r ->
                val text = r.body?.string().orEmpty()
                if (r.isSuccessful) {
                    runCatching { ApiResult.Ok(FronterasJson.decodeFromString(serializer, text)) }
                        .getOrElse { ApiResult.Err(ApiError("bad_response", "Unexpected response from server.", true), r.code) }
                } else ApiResult.Err(parseError(text, r.code), r.code)
            }
        } catch (e: IOException) {
            ApiResult.Err(ApiError("network", "Could not reach the server.", true), null)
        } catch (e: IllegalArgumentException) {
            ApiResult.Err(ApiError("bad_url", "The server address is not valid.", false), null)
        }
    }

    private fun parseError(body: String?, code: Int): ApiError =
        body?.let { runCatching { FronterasJson.decodeFromString(ApiError.serializer(), it) }.getOrNull() }
            ?: ApiError(if (code == 401) "unauthorized" else "http_$code", if (code == 401) "The server rejected your access token." else "Server error ($code).", code >= 500 || code == 429)

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Turns stream; allow long gaps while the model thinks.
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
