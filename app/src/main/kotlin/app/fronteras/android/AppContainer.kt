package app.fronteras.android

import android.content.Context
import app.fronteras.android.data.ApiClient
import app.fronteras.android.data.ApiResult
import app.fronteras.android.data.Connectivity
import app.fronteras.android.data.Credentials
import app.fronteras.android.data.LearnerRepository
import app.fronteras.android.data.RemotePartner
import app.fronteras.android.speech.AndroidSpeechInput
import app.fronteras.android.speech.AndroidTts
import app.fronteras.android.sync.SyncManager
import app.fronteras.android.sync.SyncWorker
import app.fronteras.core.content.Scenarios
import app.fronteras.core.conversation.ScriptedOfflinePartner
import app.fronteras.core.conversation.ServerCapabilities
import app.fronteras.core.srs.Fsrs
import app.fronteras.core.srs.ReviewScheduler
import app.fronteras.core.storage.FileLearnerStore
import app.fronteras.core.util.Clock
import app.fronteras.core.util.IdGenerator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Manual dependency container; one per process. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val clock: Clock = Clock.System
    val ids: IdGenerator = IdGenerator.Random
    val credentials = Credentials(context, BuildConfig.DEFAULT_SERVER_URL)
    val connectivity = Connectivity(context)
    val api = ApiClient(credentials::endpoint)
    val store = FileLearnerStore(File(context.filesDir, "learner.json"))
    val scheduler = ReviewScheduler(Fsrs(), clock, ids)
    val repository = LearnerRepository(store, clock, ids, scheduler)
    val sync = SyncManager(repository, api, credentials, clock)
    val speechInput = AndroidSpeechInput(context)
    val tts by lazy { AndroidTts(context) }

    /** Completed once stored data has been loaded. */
    val ready = CompletableDeferred<Unit>()

    private val _capabilities = MutableStateFlow<CapabilityState>(CapabilityState.Unknown)
    val capabilities: StateFlow<CapabilityState> = _capabilities.asStateFlow()

    sealed interface CapabilityState {
        data object Unknown : CapabilityState
        data object NotConfigured : CapabilityState
        data class Known(val caps: ServerCapabilities) : CapabilityState
        data class Unreachable(val message: String) : CapabilityState
    }

    suspend fun refreshCapabilities(): CapabilityState {
        val state = if (!credentials.endpoint().configured) CapabilityState.NotConfigured else when (val r = api.capabilities()) {
            is ApiResult.Ok -> CapabilityState.Known(r.value)
            is ApiResult.Err -> CapabilityState.Unreachable(r.error.message)
        }
        _capabilities.value = state
        return state
    }

    val remotePartner = RemotePartner(
        api = api,
        online = { connectivity.online.value },
        consented = { repository.data.value.profile?.consents?.cloudAi?.granted == true },
        capabilities = {
            val c = _capabilities.value
            val known = if (c is CapabilityState.Known) c else refreshCapabilities()
            known is CapabilityState.Known && known.caps.aiConversation
        },
    )

    val offlinePartner = ScriptedOfflinePartner(Scenarios.byId, Scenarios.genericPrompts)

    fun requestSync() = SyncWorker.syncSoon(appContext)
}
