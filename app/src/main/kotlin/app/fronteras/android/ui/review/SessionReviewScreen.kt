package app.fronteras.android.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fronteras.android.ui.common.FronterasTopBar
import app.fronteras.android.ui.common.SectionCard
import app.fronteras.android.ui.common.appContainer
import app.fronteras.core.feedback.ErrorSeverity
import kotlinx.coroutines.launch

@Composable
fun SessionReviewScreen(sessionId: String, onReview: () -> Unit, onHome: () -> Unit) {
    val c = appContainer()
    val data by c.repository.data.collectAsState()
    val session = data.sessions.firstOrNull { it.id == sessionId } ?: return
    val analysis = data.analyses[sessionId]
    val newItems = data.reviewItems.filter { it.source.sessionId == sessionId }
    val scope = rememberCoroutineScope()
    var confidence by rememberSaveable { mutableIntStateOf(data.confidence.lastOrNull { it.sessionId == sessionId }?.rating ?: 0) }
    val feedback = session.deferredFeedback.filter { it.intendedMeaningConfident }.sortedBy { it.severity.rank }

    Scaffold(topBar = { FronterasTopBar("Session review", onBack = onHome) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard(analysis?.summary ?: "You spoke ${session.learnerTurns.size} time(s).") {
                    analysis?.strengths?.forEach { Text("✓ $it", style = MaterialTheme.typography.bodyMedium) }
                    if (analysis == null) Text("A detailed AI analysis wasn't available for this session; the corrections made during the conversation are below.", style = MaterialTheme.typography.bodySmall)
                    session.partnerKind?.let { Text("Partner: ${it.label}", style = MaterialTheme.typography.labelSmall) }
                }
            }
            if (feedback.isNotEmpty()) {
                item { Text("Worth looking at", style = MaterialTheme.typography.titleMedium) }
                items(feedback.filter { it.severity != ErrorSeverity.ACCEPTABLE_VARIANT }.take(6), key = { it.id }) { FeedbackCard(it) }
                val variants = feedback.filter { it.severity == ErrorSeverity.ACCEPTABLE_VARIANT }
                if (variants.isNotEmpty()) {
                    item { Text("These were fine", style = MaterialTheme.typography.titleMedium) }
                    items(variants, key = { it.id }) { FeedbackCard(it) }
                }
            }
            analysis?.rubric?.takeIf { it.isNotEmpty() }?.let { rubric ->
                item {
                    SectionCard("How it went, by skill") {
                        Text("Scored 0–4 against your current level band. These are estimates for practice, not certification.", style = MaterialTheme.typography.bodySmall)
                        rubric.forEach { r ->
                            val conf = when { r.confidence >= 0.7 -> "fairly confident"; r.confidence >= 0.4 -> "somewhat confident"; else -> "low confidence" }
                            Text("${r.skill.label}: ${"%.1f".format(r.score)}/4 ($conf)", style = MaterialTheme.typography.bodyMedium)
                            r.evidence.firstOrNull()?.let { Text("  “$it”", style = MaterialTheme.typography.bodySmall) }
                        }
                        analysis?.limitations.orEmpty().forEach { Text("Note: $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (newItems.isNotEmpty()) {
                item {
                    SectionCard("Added to your review (${newItems.size})") {
                        Text("Only a few items, chosen because they matter most.", style = MaterialTheme.typography.bodySmall)
                        newItems.forEach { Text("• ${it.content} — ${it.cue}", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            item {
                SectionCard("How confident did you feel?") {
                    Text("This is your own view and is kept separate from your assessed level.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { n ->
                            FilterChip(selected = confidence == n, onClick = { confidence = n; scope.launch { c.repository.recordConfidence(sessionId, n, null) } }, label = { Text("$n") })
                        }
                    }
                }
            }
            analysis?.nextStep?.takeIf { it.isNotBlank() }?.let { item { SectionCard("Next step") { Text(it) } } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (newItems.isNotEmpty()) Button(onClick = onReview) { Text("Practise these now") }
                    OutlinedButton(onClick = onHome) { Text("Done") }
                }
            }
        }
    }
}
