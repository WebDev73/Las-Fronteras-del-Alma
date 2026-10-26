package app.fronteras.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.fronteras.android.ui.FronterasNavHost
import app.fronteras.android.ui.theme.FronterasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FronterasApp).container
        setContent { Root(container) }
    }
}

@Composable
private fun Root(container: AppContainer) {
    var ready by remember { mutableStateOf(container.ready.isCompleted) }
    LaunchedEffect(Unit) { container.ready.await(); ready = true }
    val data by container.repository.data.collectAsState()
    FronterasTheme(largeText = data.profile?.accessibility?.largeText == true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else {
                // Decide the start destination once; later changes navigate explicitly.
                val startOnboarding = remember { container.repository.data.value.profile == null }
                FronterasNavHost(startOnboarding = startOnboarding)
            }
        }
    }
}
