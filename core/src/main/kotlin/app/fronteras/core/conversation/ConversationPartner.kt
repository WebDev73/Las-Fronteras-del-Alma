package app.fronteras.core.conversation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Produces partner turns. Implementations: the cloud partner (via the Fronteras server),
 * an on-device model when one is installed and validated, and [ScriptedOfflinePartner].
 */
interface ConversationPartner {
    val kind: PartnerKind

    /** Whether the partner can currently serve requests (network, configuration, quota). */
    suspend fun isAvailable(): Boolean

    fun respond(request: PartnerRequest): Flow<PartnerEvent>
}

/**
 * Offline fallback. Explicitly NOT generative AI: it walks through curated prompts for the
 * scenario so the learner can keep speaking practice going without a connection. It never
 * produces corrections, because it cannot analyse language.
 */
class ScriptedOfflinePartner(
    private val scenarios: Map<String, Scenario>,
    private val genericPrompts: Map<String, List<String>>,
) : ConversationPartner {
    override val kind = PartnerKind.SCRIPTED_OFFLINE

    override suspend fun isAvailable(): Boolean = true

    override fun respond(request: PartnerRequest): Flow<PartnerEvent> = flow {
        val base = request.learner.target.base
        val scenario = scenarios.values.firstOrNull { it.title == request.scenario?.title }
        val prompts = scenario?.offlinePrompts?.get(base).orEmpty().ifEmpty { genericPrompts[base].orEmpty() }
        if (prompts.isEmpty()) {
            emit(PartnerEvent.Failed("offline_content_missing", "No offline practice content for this language.", retryable = false))
            return@flow
        }
        val partnerTurns = request.turns.count { it.speaker == Speaker.PARTNER }
        val opening = scenario?.openings?.get(base)
        val text = when {
            partnerTurns == 0 && opening != null -> opening
            else -> prompts[(partnerTurns - if (opening != null) 1 else 0).coerceAtLeast(0) % prompts.size]
        }
        emit(PartnerEvent.Completed(text, analysis = null, partnerKind = kind))
    }
}
