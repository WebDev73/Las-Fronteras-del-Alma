package app.fronteras.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fronteras.android.ui.common.FronterasTopBar
import app.fronteras.android.ui.common.SectionCard
import app.fronteras.android.ui.common.appContainer
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val c = appContainer()
    val data by c.repository.data.collectAsState()
    val scope = rememberCoroutineScope()
    var notes by rememberSaveable { mutableStateOf(data.learnerNotes.joinToString("\n")) }
    Scaffold(topBar = { FronterasTopBar("Conversations", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SectionCard("Notes for your conversation partner") {
                    Text("Used only when “Remember me” is on. One note per line, e.g. “I work as a nurse”.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    TextButton(onClick = { scope.launch { c.repository.setNotes(notes.lines()) } }) { Text("Save notes") }
                }
            }
            items(data.sessions.sortedByDescending { it.startedAt }, key = { it.id }) { s ->
                SectionCard(s.topic ?: s.mode.label) {
                    Text("${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.startedAt))} · ${s.learnerTurns.size} turns${if (s.turns.isEmpty()) " · transcript expired" else ""}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.endedAt != null) TextButton(onClick = { onOpen(s.id) }) { Text("Open review") }
                        TextButton(onClick = { scope.launch { c.repository.deleteSession(s.id) } }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
fun DeckScreen(onBack: () -> Unit) {
    val c = appContainer()
    val data by c.repository.data.collectAsState()
    val scope = rememberCoroutineScope()
    val target = data.profile?.activeTarget ?: return
    Scaffold(topBar = { FronterasTopBar("Review items", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Correct anything that's wrong, pause items you don't need, or delete them.", style = MaterialTheme.typography.bodySmall) }
            items(data.reviewItemsFor(target).sortedByDescending { it.createdAt }, key = { it.id }) { item ->
                var editing by rememberSaveable(item.id) { mutableStateOf(false) }
                var content by rememberSaveable(item.id) { mutableStateOf(item.content) }
                var cue by rememberSaveable(item.id) { mutableStateOf(item.cue) }
                var expl by rememberSaveable(item.id) { mutableStateOf(item.explanation) }
                SectionCard(item.content) {
                    Text(item.cue, style = MaterialTheme.typography.bodyMedium)
                    val next = item.nextDueAt?.let { DateFormat.getDateInstance().format(Date(it)) } ?: "new"
                    Text("${item.category.name.lowercase().replace('_', ' ')} · next: $next · seen ${item.history.size}×${if (item.suspended) " · paused" else ""}", style = MaterialTheme.typography.labelSmall)
                    if (editing) {
                        OutlinedTextField(content, { content = it }, label = { Text("Target language") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(cue, { cue = it }, label = { Text("Meaning / cue") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(expl, { expl = it }, label = { Text("Explanation") }, modifier = Modifier.fillMaxWidth())
                    }
                    Row {
                        TextButton(onClick = {
                            if (editing) scope.launch { c.repository.editReviewItem(item.id, content.trim(), cue.trim(), expl.trim()) }
                            editing = !editing
                        }) { Text(if (editing) "Save" else "Edit") }
                        TextButton(onClick = { scope.launch { c.repository.setSuspended(item.id, !item.suspended) } }) { Text(if (item.suspended) "Resume" else "Pause") }
                        TextButton(onClick = { scope.launch { c.repository.deleteReviewItem(item.id) } }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
