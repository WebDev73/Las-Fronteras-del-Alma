package app.fronteras.android.ui.diagnostic

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.fronteras.android.ui.common.EstimateLine
import app.fronteras.android.ui.common.FronterasTopBar
import app.fronteras.android.ui.common.SectionCard
import app.fronteras.android.ui.common.appViewModel
import app.fronteras.android.ui.onboarding.LevelChoice
import app.fronteras.core.assessment.DiagnosticItem
import app.fronteras.core.assessment.DiagnosticItemKind

private fun instruction(kind: DiagnosticItemKind) = when (kind) {
    DiagnosticItemKind.WARM_UP_CONVERSATION -> "Let's start easy. Answer in any way you can — a few words is fine."
    DiagnosticItemKind.LISTENING_CHOICE -> "Listen, then choose the answer."
    DiagnosticItemKind.READING_CHOICE -> "Read the text, then choose the answer."
    DiagnosticItemKind.VOCABULARY_CHOICE -> "Choose the word that fits best."
    DiagnosticItemKind.GRAMMAR_CHOICE -> "Choose the form that fits best."
    DiagnosticItemKind.OPEN_SPEAKING -> "Answer in your own words. Speaking is best; typing also works."
    DiagnosticItemKind.PARAPHRASE -> "Explain it another way — this shows how you get around missing words."
    DiagnosticItemKind.READ_ALOUD -> "Read this sentence aloud."
}

@Composable
fun DiagnosticScreen(onDone: () -> Unit) {
    val vm = appViewModel { DiagnosticViewModel(it) }
    val ui by vm.ui.collectAsState()
    Scaffold(topBar = { FronterasTopBar(if (ui.result != null) "Your starting point" else "Quick check-in", actions = {
        if (ui.result == null && ui.item != null) TextButton(onClick = vm::finishNow) { Text("Finish early") }
    }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                ui.scoring -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(); Text("Looking at your answers…")
                }
                ui.result != null -> ResultView(ui, vm, onDone)
                ui.item != null -> ItemView(ui.item!!, ui, vm)
            }
        }
    }
}

@Composable
private fun ItemView(item: DiagnosticItem, ui: DiagnosticUi, vm: DiagnosticViewModel) {
    Text("Question ${ui.index + 1}", style = MaterialTheme.typography.labelLarge)
    Text(instruction(item.kind), style = MaterialTheme.typography.bodyMedium)
    when (item.kind) {
        DiagnosticItemKind.LISTENING_CHOICE -> {
            var plays by remember(item.id) { mutableIntStateOf(0) }
            FilledTonalButton(onClick = { plays++; vm.speak(item.audioText!!) }, enabled = plays < 3) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null); Text(if (plays == 0) "  Play" else "  Play again (${3 - plays} left)")
            }
            Text(item.prompt, style = MaterialTheme.typography.titleMedium)
            Choices(item, vm)
        }
        DiagnosticItemKind.READING_CHOICE -> {
            SectionCard { Text(item.passage.orEmpty(), style = MaterialTheme.typography.bodyLarge) }
            Text(item.prompt, style = MaterialTheme.typography.titleMedium)
            Choices(item, vm)
        }
        DiagnosticItemKind.VOCABULARY_CHOICE, DiagnosticItemKind.GRAMMAR_CHOICE -> {
            Text(item.prompt, style = MaterialTheme.typography.titleLarge)
            Choices(item, vm)
        }
        else -> OpenAnswer(item, ui, vm)
    }
}

@Composable
private fun Choices(item: DiagnosticItem, vm: DiagnosticViewModel) {
    item.options.forEachIndexed { i, o -> OutlinedButton(onClick = { vm.choose(i) }, modifier = Modifier.fillMaxWidth()) { Text(o) } }
    TextButton(onClick = { vm.choose(null) }) { Text("I don't know") }
}

@Composable
private fun OpenAnswer(item: DiagnosticItem, ui: DiagnosticUi, vm: DiagnosticViewModel) {
    val context = LocalContext.current
    var denied by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.listen() else denied = true }
    SectionCard { Text(item.prompt, style = MaterialTheme.typography.titleLarge) }
    if (item.kind != DiagnosticItemKind.READ_ALOUD) {
        FilledTonalButton(onClick = { vm.speak(item.prompt) }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null); Text("  Hear it") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ui.listening) {
            Button(onClick = vm::stopListening) { Icon(Icons.Filled.Stop, contentDescription = null); Text("  Done speaking") }
            Text(ui.partial.ifEmpty { "Listening…" }, style = MaterialTheme.typography.bodyMedium)
        } else {
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.listen()
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Icon(Icons.Filled.Mic, contentDescription = null); Text("  Speak") }
        }
    }
    if (denied) Text("Microphone permission was refused. You can type your answer instead.", style = MaterialTheme.typography.bodySmall)
    ui.speechError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    if (item.kind != DiagnosticItemKind.READ_ALOUD) {
        OutlinedTextField(ui.answerText, vm::setText, label = { Text(if (ui.transcript != null) "What we heard — fix it if needed" else "Or type your answer") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
    } else if (ui.transcript != null) {
        Text("We heard: “${ui.transcript.text}”", style = MaterialTheme.typography.bodyMedium)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = vm::skip) { Text("Skip") }
        Button(onClick = vm::submitOpen, enabled = if (item.kind == DiagnosticItemKind.READ_ALOUD) ui.transcript != null else ui.answerText.isNotBlank()) { Text("Next") }
    }
}

@Composable
private fun ResultView(ui: DiagnosticUi, vm: DiagnosticViewModel, onDone: () -> Unit) {
    val r = ui.result!!
    SectionCard("Provisional level: ${r.provisionalRange}") {
        r.explanation.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        ui.scorerNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    SectionCard("By skill") {
        r.findings.forEach { f -> EstimateLine(f.skill.label, f.estimate) }
    }
    SectionCard("Evidence") { r.findings.filter { !it.evidence.startsWith("Not assessed") }.forEach { Text("${it.skill.label}: ${it.evidence}", style = MaterialTheme.typography.bodySmall) } }
    SectionCard("Good to know") { r.limitations.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) } }
    var overriding by rememberSaveable { mutableStateOf(false) }
    if (overriding) {
        SectionCard("Choose your level") {
            LevelChoice(null) { l -> vm.override(l); overriding = false }
        }
    } else TextButton(onClick = { overriding = true }) { Text("This doesn't seem right — set my level myself") }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
}
