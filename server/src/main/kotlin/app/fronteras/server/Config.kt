package app.fronteras.server

import java.io.File

/**
 * Server configuration, read from the environment. Nothing secret is ever logged.
 */
data class ServerConfig(
    val port: Int,
    val anthropicApiKey: String?,
    val model: String,
    val turnEffort: String,
    val analysisEffort: String,
    /** `token:userId[:plan]` pairs for development. Replace with a real identity provider in production. */
    val devTokens: Map<String, DevAccount>,
    val dataDir: File,
    val requestsPerMinute: Int,
) {
    data class DevAccount(val userId: String, val plan: app.fronteras.core.entitlement.Plan)

    val aiConfigured: Boolean get() = !anthropicApiKey.isNullOrBlank()

    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig = ServerConfig(
            port = env["PORT"]?.toIntOrNull() ?: 8080,
            anthropicApiKey = env["ANTHROPIC_API_KEY"]?.takeIf { it.isNotBlank() },
            model = env["FRONTERAS_MODEL"] ?: "claude-opus-5-5",
            turnEffort = env["FRONTERAS_TURN_EFFORT"] ?: "low",
            analysisEffort = env["FRONTERAS_ANALYSIS_EFFORT"] ?: "high",
            devTokens = parseTokens(env["FRONTERAS_DEV_TOKENS"].orEmpty()),
            dataDir = File(env["FRONTERAS_DATA_DIR"] ?: "server/data"),
            requestsPerMinute = env["FRONTERAS_RPM"]?.toIntOrNull() ?: 30,
        )

        fun parseTokens(raw: String): Map<String, DevAccount> = raw.split(',')
            .map { it.trim() }.filter { it.isNotEmpty() }
            .associate { entry ->
                val parts = entry.split(':')
                require(parts.size in 2..3 && parts[0].length >= 16) { "FRONTERAS_DEV_TOKENS entries must be token:userId[:plan] with tokens of 16+ characters" }
                val plan = parts.getOrNull(2)?.let { app.fronteras.core.entitlement.Plan.valueOf(it.uppercase()) } ?: app.fronteras.core.entitlement.Plan.FREE
                parts[0] to DevAccount(parts[1], plan)
            }
    }
}
