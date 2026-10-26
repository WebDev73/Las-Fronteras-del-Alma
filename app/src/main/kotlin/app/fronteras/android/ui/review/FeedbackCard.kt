package app.fronteras.android.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fronteras.core.feedback.ErrorSeverity
import app.fronteras.core.feedback.FeedbackDepth
import app.fronteras.core.feedback.FeedbackItem
import app.fronteras.core.feedback.view

/** Layered feedback: quick correction first; the learner chooses how deep to go. */
@Composable
fun FeedbackCard(item: FeedbackItem, initialDepth: FeedbackDepth = FeedbackDepth.QUICK) {
    var depth by rememberSaveable(item.id) { mutableStateOf(initialDepth) }
    val v = item.view(depth)
    val colors = when (item.severity) {
        ErrorSeverity.CRITICAL_MEANING, ErrorSeverity.SIGNIFICANT -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ErrorSeverity.ACCEPTABLE_VARIANT -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        else -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    }
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(v.headline, style = MaterialTheme.typography.labelLarge)
            v.lines.forEach { (label, text) ->
                Text("$label: $text", style = MaterialTheme.typography.bodyMedium)
            }
            if (item.confidence < 0.6) Text("The tutor isn't fully sure about this one.", style = MaterialTheme.typography.labelSmall)
            if (v.canGoDeeper) TextButton(onClick = { depth = FeedbackDepth.entries[(depth.ordinal + 1).coerceAtMost(FeedbackDepth.entries.lastIndex)] }) { Text("Tell me more") }
        }
    }
}
