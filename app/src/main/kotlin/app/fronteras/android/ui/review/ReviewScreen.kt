package app.fronteras.android.ui.review

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.fronteras.android.ui.common.FronterasTopBar
import app.fronteras.android.ui.common.SectionCard
import app.fronteras.android.ui.common.appViewModel
import app.fronteras.core.feedback.RetrievalMethod
import app.fronteras.core.speech.WordMatch
import app.fronteras.core.srs.Rating

@Composable
fun ReviewScreen(onBack: () -> Unit) {
    val vm = appViewModel { ReviewViewModel(it) }
    val ui by vm.ui.collectAsState()
    Scaffold(topBar = { FronterasTopBar("Review", onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val task = ui.current
            if (ui.tasks.isEmpty()) {
                Text("Nothing to review right now.", style = MaterialTheme.typography.titleMedium)
                Text("Review items come from your own conversations. Have a conversation and the most useful corrections will appear here.")
                Button(onClick = onBack) { Text("Back") }
                return@Column
            }
            LinearProgressIndicator(progress = { ui.index.toFloat() / ui.tasks.size }, modifier = Modifier.fillMaxWidth())
            if (task == null) {
                Text("Done — ${ui.done} item(s) reviewed.", style = MaterialTheme.typography.titleMedium)
                Text("Each one is scheduled again based on how easily you remembered it.")
                Button(onClick = onBack) { Text("Finish") }
                return@Column
            }
            Text(task.reason, style = MaterialTheme.typography.labelMedium)
            Text(task.method.label, style = MaterialTheme.typography.labelLarge)
            SectionCard { Text(vm.prompt(task), style = MaterialTheme.typography.titleLarge) }
            task.item.source.context?.let { Text("From your conversation: “$it”", style = MaterialTheme.typography.bodySmall) }

            when {
                task.method == RetrievalMethod.RECOGNIZE && ui.options.isNotEmpty() && !ui.revealed ->
                    ui.options.forEach { o -> OutlinedButton(onClick = { vm.choose(o) }, modifier = Modifier.fillMaxWidth()) { Text(o) } }
                task.method == RetrievalMethod.RECOGNIZE && ui.options.isEmpty() && !ui.revealed ->
                    Button(onClick = { vm.check() }) { Text("Show answer") }
                task.method == RetrievalMethod.SPEAK_ALOUD && !ui.revealed -> SpeakAloud(ui.listening, vm)
                !ui.revealed -> {
                    OutlinedTextField(ui.answer, vm::setAnswer, label = { Text("Your answer") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::check) { Text("Check") }
                        TextButton(onClick = vm::hint) { Text("Hint") }
                        TextButton(onClick = vm::check) { Text("I don't know") }
                    }
                }
            }

            if (ui.revealed) {
                SectionCard("Answer") {
                    Text(task.item.content, style = MaterialTheme.typography.titleMedium)
                    Text(task.item.explanation, style = MaterialTheme.typography.bodyMedium)
                    task.item.examples.take(2).forEach { Text("e.g. $it", style = MaterialTheme.typography.bodySmall) }
                }
                ui.pronunciation?.let { rep ->
                    SectionCard(rep.source.label) {
                        Text(rep.measures, style = MaterialTheme.typography.bodySmall)
                        rep.intelligibility?.let { Text("Recognised as intended: ${(it * 100).toInt()}% of words", style = MaterialTheme.typography.bodyMedium) }
                        Text(rep.words.joinToString(" ") { w -> when (w.match) { WordMatch.MATCH, WordMatch.CLOSE -> w.expected ?: ""; WordMatch.MISSING -> "[${w.expected}?]"; WordMatch.UNCERTAIN -> "(${w.expected ?: w.recognized})"; WordMatch.MISMATCH -> "*${w.expected}*"; WordMatch.EXTRA -> "" } }.trim())
                        rep.limitations.forEach { Text("• $it", style = MaterialTheme.typography.labelSmall) }
                    }
                }
                when (ui.autoCorrect) {
                    true -> { Text("Correct.", style = MaterialTheme.typography.titleMedium); Grade(vm, correct = true) }
                    false -> { Text("Not quite — compare with the answer above.", style = MaterialTheme.typography.titleMedium); Grade(vm, correct = false) }
                    null -> { Text("How did you do? You're the best judge here.", style = MaterialTheme.typography.bodyMedium); SelfGrade(vm) }
                }
            }
            TextButton(onClick = vm::suspendCurrent) { Text("Stop showing me this one") }
        }
    }
}

@Composable
private fun SpeakAloud(listening: Boolean, vm: ReviewViewModel) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.listen() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = vm::speakModel) { Text("Hear it") }
        Button(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.listen()
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }, enabled = !listening) { Text(if (listening) "Listening…" else "Say it") }
        TextButton(onClick = vm::check) { Text("Skip speaking") }
    }
}

@Composable
private fun Grade(vm: ReviewViewModel, correct: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { vm.grade(correct) }) { Text("Next") }
        if (!correct) TextButton(onClick = { vm.grade(true, Rating.HARD) }) { Text("I was actually right") }
    }
}

@Composable
private fun SelfGrade(vm: ReviewViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { vm.grade(false) }) { Text("Missed") }
        OutlinedButton(onClick = { vm.grade(true, Rating.HARD) }) { Text("Hard") }
        OutlinedButton(onClick = { vm.grade(true, Rating.GOOD) }) { Text("Good") }
        OutlinedButton(onClick = { vm.grade(true, Rating.EASY) }) { Text("Easy") }
    }
}
