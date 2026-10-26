package app.fronteras.android

import android.app.Application
import app.fronteras.android.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FronterasApp : Application() {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            container.repository.load()
            container.ready.complete(Unit)
            container.refreshCapabilities()
        }
        SyncWorker.schedulePeriodic(this)
    }
}
