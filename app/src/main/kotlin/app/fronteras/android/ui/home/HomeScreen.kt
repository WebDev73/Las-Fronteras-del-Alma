package app.fronteras.android.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fronteras.android.AppContainer
import app.fronteras.android.ui.common.FronterasTopBar
import app.fronteras.android.ui.common.OfflineBanner
import app.fronteras.android.ui.common.appContainer
import app.fronteras.core.language.LanguageCatalog
import app.fronteras.core.planning.Recommendation
import app.fronteras.core.planning.RecommendationKind

sealed interface HomeAction {
    data object Diagnostic : HomeAction
    data object Review : HomeAction
    data class Conversation(val scenarioId: String?, val competencyId: String?, val minutes: Int) : HomeAction
    data object Scenarios : HomeAction
    data object Path : HomeAction
    data object Progress : HomeAction
    data object Settings : HomeAction
}

@Composable
fun HomeScreen(onAction: (HomeAction) -> Unit) {
    val c = appContainer()
    val data by c.repository.data.collectAsState()
    val online by c.connectivity.online.collectAsState()
    val caps by c.capabilities.collectAsState()
    LaunchedEffect(online) { if (online) c.refreshCapabilities() }
    val profile = data.profile ?: return
    val recs = remember(data) { c.repository.recommendations(data) }
    val due = remember(data) { c.repository.dueCount(data) }
    val aiOk = (caps as? AppContainer.CapabilityState.Known)?.caps?.aiConversation == true && profile.consents.cloudAi.granted
    val reason = when {
        !profile.consents.cloudAi.granted -> "AI conversation is off (Settings → Privacy). Review and scripted practice still work."
        caps is AppContainer.CapabilityState.NotConfigured -> "No server is set up yet (Settings → Server). Review and scripted practice still work."
        caps is AppContainer.CapabilityState.Known -> (caps as AppContainer.CapabilityState.Known).caps.reason
        caps is AppContainer.CapabilityState.Unreachable -> (caps as AppContainer.CapabilityState.Unreachable).message
        else -> null
    }

    Scaffold(topBar = {
        FronterasTopBar(LanguageCatalog.displayName(profile.activeTarget), actions = {
            IconButton(onClick = { onAction(HomeAction.Progress) }) { Icon(Icons.Filled.BarChart, contentDescription = "Progress") }
            IconButton(onClick = { onAction(HomeAction.Settings) }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        })
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OfflineBanner(online, aiOk, reason) }
            item {
                Text("Next steps", style = MaterialTheme.typography.titleLarge)
                Text("Chosen from what you've actually done — not just what you've completed.", style = MaterialTheme.typography.bodySmall)
            }
            items(recs, key = { "${it.kind}-${it.competencyId}-${it.patternKey}" }) { r -> RecommendationCard(r) { onAction(toAction(r)) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { onAction(HomeAction.Scenarios) }, label = { Text("All conversations") })
                    AssistChip(onClick = { onAction(HomeAction.Review) }, label = { Text(if (due > 0) "Review ($due due)" else "Review") })
                    AssistChip(onClick = { onAction(HomeAction.Path) }, label = { Text("My path") })
                }
            }
        }
    }
}

private fun toAction(r: Recommendation): HomeAction = when (r.kind) {
    RecommendationKind.DIAGNOSTIC -> HomeAction.Diagnostic
    RecommendationKind.REVIEW -> HomeAction.Review
    RecommendationKind.TARGETED_PRACTICE -> HomeAction.Review
    RecommendationKind.GUIDED_SESSION, RecommendationKind.REASSESS ->
        if (r.competencyId == null && r.kind == RecommendationKind.REASSESS) HomeAction.Diagnostic else HomeAction.Conversation(r.scenarioId, r.competencyId, r.minutes)
    RecommendationKind.FREE_CONVERSATION -> HomeAction.Conversation("free-chat", null, r.minutes)
}

@Composable
private fun RecommendationCard(r: Recommendation, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium)
                Text(r.reason, style = MaterialTheme.typography.bodyMedium)
                Text("About ${r.minutes} min", style = MaterialTheme.typography.labelMedium)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}
