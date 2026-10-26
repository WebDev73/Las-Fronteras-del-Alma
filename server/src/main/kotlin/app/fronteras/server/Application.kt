package app.fronteras.server

import app.fronteras.core.conversation.ApiError
import app.fronteras.core.conversation.PartnerEvent
import app.fronteras.core.conversation.PartnerRequest
import app.fronteras.core.conversation.ServerCapabilities
import app.fronteras.core.conversation.SessionAnalysisRequest
import app.fronteras.core.storage.FronterasJson
import app.fronteras.core.sync.SyncPushRequest
import app.fronteras.server.ai.AnalysisResult
import app.fronteras.server.ai.TutorModel
import app.fronteras.server.auth.Principal
import app.fronteras.server.auth.TokenVerifier
import app.fronteras.server.quota.UsageMeter
import app.fronteras.server.sync.SyncStore
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.cacheControl
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8
import kotlin.time.Duration.Companion.seconds

data class Dependencies(
    val config: ServerConfig,
    val tutor: TutorModel,
    val verifier: TokenVerifier,
    val usage: UsageMeter = UsageMeter(),
    val sync: SyncStore,
)

private val AI = RateLimitName("ai")

fun Application.fronterasModule(deps: Dependencies) {
    install(ContentNegotiation) { json(FronterasJson) }
    install(CallLogging) {
        // Metadata only. Request bodies (learner speech) are never logged.
        format { call -> "${call.request.local.method.value} ${call.request.local.uri} -> ${call.response.status()?.value}" }
    }
    install(StatusPages) {
        exception<ValidationException> { call, e -> call.respond(HttpStatusCode.BadRequest, ApiError("invalid_request", e.message ?: "Invalid request")) }
        exception<io.ktor.server.plugins.BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("invalid_request", "Malformed request body")) }
        exception<Throwable> { call, _ -> call.respond(HttpStatusCode.InternalServerError, ApiError("internal", "Unexpected server error", retryable = true)) }
        status(HttpStatusCode.TooManyRequests) { call, _ -> call.respond(HttpStatusCode.TooManyRequests, ApiError("rate_limited", "Too many requests. Slow down a little.", retryable = true)) }
    }
    install(Authentication) {
        bearer("api") {
            authenticate { credential -> deps.verifier.verify(credential.token) }
        }
    }
    install(RateLimit) {
        register(AI) {
            rateLimiter(limit = deps.config.requestsPerMinute, refillPeriod = 60.seconds)
            requestKey { call -> call.principal<Principal>()?.userId ?: "anonymous" }
        }
    }

    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }

        authenticate("api") {
            get("/v1/capabilities") {
                val p = call.user()
                call.respond(
                    ServerCapabilities(
                        aiConversation = deps.tutor.available,
                        sessionAnalysis = deps.tutor.available,
                        pronunciationScoring = false,
                        reason = if (deps.tutor.available) null else "AI is not configured on this server (ANTHROPIC_API_KEY is not set).",
                        model = deps.tutor.name.takeIf { deps.tutor.available },
                        dailyTurnLimit = deps.usage.limit(p.plan, UsageMeter.Kind.TURN),
                        turnsUsedToday = deps.usage.used(p.userId, UsageMeter.Kind.TURN),
                    ),
                )
            }

            rateLimit(AI) {
                post("/v1/conversation/turn") {
                    val p = call.user()
                    val req = call.receive<PartnerRequest>()
                    Validation.partner(req)
                    if (!deps.tutor.available) {
                        return@post call.respond(HttpStatusCode.ServiceUnavailable, ApiError("ai_not_configured", "AI conversation is not configured on this server."))
                    }
                    if (!deps.usage.tryConsume(p.userId, p.plan, UsageMeter.Kind.TURN)) {
                        return@post call.respond(HttpStatusCode.TooManyRequests, ApiError("quota_exceeded", "You've reached today's AI conversation limit. Offline practice and review are still available."))
                    }
                    call.response.cacheControl(CacheControl.NoCache(null))
                    call.response.header("X-Accel-Buffering", "no")
                    call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                        var produced = false
                        deps.tutor.partnerTurn(req).collect { ev ->
                            if (ev is PartnerEvent.TextDelta || ev is PartnerEvent.Completed) produced = true
                            if (ev is PartnerEvent.Failed && !produced) deps.usage.refund(p.userId, UsageMeter.Kind.TURN)
                            writeStringUtf8("data: ${FronterasJson.encodeToString(PartnerEvent.serializer(), ev)}\n\n")
                            flush()
                        }
                    }
                }

                post("/v1/session/analyze") {
                    val p = call.user()
                    val req = call.receive<SessionAnalysisRequest>()
                    Validation.analysis(req)
                    if (!deps.tutor.available) {
                        return@post call.respond(HttpStatusCode.ServiceUnavailable, ApiError("ai_not_configured", "Session analysis is not configured on this server."))
                    }
                    if (!deps.usage.tryConsume(p.userId, p.plan, UsageMeter.Kind.ANALYSIS)) {
                        return@post call.respond(HttpStatusCode.TooManyRequests, ApiError("quota_exceeded", "You've reached today's session-analysis limit."))
                    }
                    when (val r = deps.tutor.analyzeSession(req)) {
                        is AnalysisResult.Ok -> call.respond(r.analysis)
                        is AnalysisResult.Failed -> {
                            deps.usage.refund(p.userId, UsageMeter.Kind.ANALYSIS)
                            call.respond(if (r.retryable) HttpStatusCode.ServiceUnavailable else HttpStatusCode.UnprocessableEntity, ApiError(r.code, r.message, r.retryable))
                        }
                    }
                }
            }

            post("/v1/sync/push") {
                val p = call.user()
                val req = call.receive<SyncPushRequest>()
                Validation.sync(req)
                call.respond(deps.sync.push(p.userId, req.changes))
            }

            get("/v1/sync/changes") {
                val p = call.user()
                val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                call.respond(deps.sync.pull(p.userId, since))
            }

            delete("/v1/account/data") {
                deps.sync.deleteUser(call.user().userId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun ApplicationCall.user(): Principal = principal<Principal>() ?: error("authenticate block guarantees a principal")

