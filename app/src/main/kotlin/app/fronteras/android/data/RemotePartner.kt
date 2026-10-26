package app.fronteras.android.data

import app.fronteras.core.conversation.ConversationPartner
import app.fronteras.core.conversation.PartnerEvent
import app.fronteras.core.conversation.PartnerKind
import app.fronteras.core.conversation.PartnerRequest
import kotlinx.coroutines.flow.Flow

/**
 * AI conversation partner served by the Fronteras server. Only used when the learner has
 * consented to cloud AI, the device is online, and the server reports AI as configured.
 */
class RemotePartner(
    private val api: ApiClient,
    private val online: () -> Boolean,
    private val consented: () -> Boolean,
    private val capabilities: suspend () -> Boolean,
) : ConversationPartner {
    override val kind = PartnerKind.CLOUD_AI

    override suspend fun isAvailable(): Boolean = consented() && online() && capabilities()

    override fun respond(request: PartnerRequest): Flow<PartnerEvent> = api.partnerTurn(request)
}
