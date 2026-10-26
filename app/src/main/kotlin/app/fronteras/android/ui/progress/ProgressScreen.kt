package app.fronteras.android.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fronteras.android.ui.common.EstimateLine
import app.fronteras.android.ui.common.FronterasTopBar
import app.fronteras.android.ui.common.SectionCard
import app.fronteras.android.ui.common.appContainer
import app.fronteras.core.mastery.EvidenceLevel
import app.fronteras.core.progress.TrendDirection

@Composable
fun ProgressScreen(onBack: () -> Unit) {
    val c = appContainer()
    val data by c.repository.data.collectAsState()
    val report = remember(data) { c.repository.progress(data) } ?: return
    val mastery = remember(data) { c.repository.mastery(report.target, data) }
    Scaffold(topBar = { FronterasTopBar("Progress", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard("Skills") {
                    report.skills.forEach { t ->
                        EstimateLine(t.skill.label, t.current)
                        if (t.direction != TrendDirection.INSUFFICIENT_DATA) Text(t.explanation, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                SectionCard("What you can do") {
                    Text("Each objective moves up only with evidence: practising, remembering later, using it unprompted, then doing it with little help.", style = MaterialTheme.typography.bodySmall)
                    EvidenceLevel.entries.drop(1).forEach { lvl -> Text("${lvl.label}: ${report.evidenceCounts[lvl] ?: 0}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            items(mastery.values.filter { it.level > EvidenceLevel.NONE }.sortedByDescending { it.level }, key = { it.competencyId }) { st ->
                val comp = c.repository.graph[st.competencyId]
                SectionCard(comp?.title ?: st.competencyId) {
                    Text("${st.level.label} — ${st.level.meaning}", style = MaterialTheme.typography.bodyMedium)
                    st.evidence.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    st.nextRequirement?.let { Text("Next: $it", style = MaterialTheme.typography.labelMedium) }
                }
            }
            if (report.examples.isNotEmpty()) {
                item {
                    SectionCard("Real improvements") {
                        report.examples.forEach { e -> Text("Before: “${e.before}”\nLater: “${e.after}”", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (report.selfConfidence.isNotEmpty()) {
                item {
                    SectionCard("How confident you've felt") {
                        Text("Your own ratings (1–5), shown separately from assessed skills.", style = MaterialTheme.typography.bodySmall)
                        Text(report.selfConfidence.takeLast(10).joinToString("  ") { it.rating.toString() }, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            item { SectionCard("About these numbers") { report.caveats.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) } } }
        }
    }
}

@Composable
fun PathScreen(onBack: () -> Unit, onStart: (competencyId: String, minutes: Int) -> Unit) {
    val c = appContainer()
    val data by c.repository.data.collectAsState()
    val path = remember(data) { c.repository.path(data) } ?: return
    val minutes = data.profile?.active?.dailyMinutes?.coerceAtMost(20) ?: 10
    Scaffold(topBar = { FronterasTopBar("My path", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SectionCard(path.targetOutcome) {
                    Text("Baseline: ${path.baseline}", style = MaterialTheme.typography.bodyMedium)
                    Text("Done when: ${path.completionCriteria}", style = MaterialTheme.typography.bodyMedium)
                    Text("Rough estimate: about ${path.estimatedDays} day(s) at your daily time. This is a planning aid, not a promise.", style = MaterialTheme.typography.bodySmall)
                    Text(path.reviewNote, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(path.steps.withIndex().toList(), key = { it.value.competencyId }) { (i, s) ->
                SectionCard("${i + 1}. ${s.title}") {
                    Text(s.canDo, style = MaterialTheme.typography.bodyMedium)
                    Text("${s.status.label} · ${if (s.estimatedMinutes == 0) "done" else "~${s.estimatedMinutes} min left"} · ${s.why}", style = MaterialTheme.typography.labelMedium)
                    path.milestones.firstOrNull { it.afterStep == i }?.let { Text("Milestone: ${it.assessment}", style = MaterialTheme.typography.bodySmall) }
                    if (s.estimatedMinutes > 0) androidx.compose.material3.TextButton(onClick = { onStart(s.competencyId, minutes) }) { Text("Practise this") }
                }
            }
        }
    }
}
