package app.fronteras.android.ui.scenarios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fronteras.android.ui.common.FronterasTopBar
import app.fronteras.android.ui.common.appContainer
import app.fronteras.core.planning.SessionPlanner

@Composable
fun ScenariosScreen(onBack: () -> Unit, onStart: (scenarioId: String, minutes: Int) -> Unit) {
    val c = appContainer()
    val data by c.repository.data.collectAsState()
    val profile = data.profile ?: return
    val level = data.workingLevel(profile.activeTarget)
    val scenarios = remember(data) { c.repository.scenariosFor(profile.activeTarget, level) }
    var minutes by rememberSaveable { mutableIntStateOf(10) }
    Scaffold(topBar = { FronterasTopBar("Conversations", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Session length", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SessionPlanner.lengths.forEach { m -> FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") }) }
                }
            }
            items(scenarios, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth().clickable { onStart(s.id, minutes) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(s.title, style = MaterialTheme.typography.titleMedium)
                        Text(s.learnerGoal, style = MaterialTheme.typography.bodyMedium)
                        Text("${s.mode.label} · ${s.minLevel.name}–${s.maxLevel.name}${if (s.sensitive) " · fictional" else ""}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
