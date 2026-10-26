package app.fronteras.server

import app.fronteras.server.ai.ClaudeTutorModel
import app.fronteras.server.ai.UnconfiguredTutorModel
import app.fronteras.server.auth.DevTokenVerifier
import app.fronteras.server.sync.SyncStore
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

fun main() {
    val log = LoggerFactory.getLogger("fronteras")
    val config = ServerConfig.fromEnv()
    val tutor = if (config.aiConfigured) {
        ClaudeTutorModel(
            AnthropicOkHttpClient.builder().apiKey(config.anthropicApiKey!!).build(),
            config.model,
            ClaudeTutorModel.effort(config.turnEffort),
            ClaudeTutorModel.effort(config.analysisEffort),
        )
    } else {
        log.warn("ANTHROPIC_API_KEY is not set: AI conversation and analysis are disabled. The app will use offline practice.")
        UnconfiguredTutorModel("AI is not configured on this server.")
    }
    if (config.devTokens.isEmpty()) log.warn("FRONTERAS_DEV_TOKENS is empty: no client can authenticate.")
    val deps = Dependencies(config, tutor, DevTokenVerifier(config.devTokens), sync = SyncStore(config.dataDir))
    log.info("Starting Fronteras server on port {} (AI: {})", config.port, if (tutor.available) tutor.name else "disabled")
    embeddedServer(Netty, port = config.port) { fronterasModule(deps) }.start(wait = true)
}
