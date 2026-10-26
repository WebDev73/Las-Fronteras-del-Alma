package app.fronteras.server.auth

import app.fronteras.core.entitlement.Plan
import app.fronteras.server.ServerConfig
import java.security.MessageDigest

data class Principal(val userId: String, val plan: Plan)

/** Verifies bearer tokens. Production deployments plug in an OIDC/Firebase verifier here. */
fun interface TokenVerifier {
    fun verify(token: String): Principal?
}

/** Static development tokens from FRONTERAS_DEV_TOKENS, compared in constant time. */
class DevTokenVerifier(private val accounts: Map<String, ServerConfig.DevAccount>) : TokenVerifier {
    override fun verify(token: String): Principal? {
        val hit = accounts.entries.firstOrNull { MessageDigest.isEqual(it.key.toByteArray(), token.toByteArray()) } ?: return null
        return Principal(hit.value.userId, hit.value.plan)
    }
}
