package app.fronteras.android.ui.conversation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.fronteras.android.ui.common.FronterasTopBar
import app.fronteras.android.ui.common.SectionCard
import app.fronteras.android.ui.common.appViewModel
import app.fronteras.android.ui.review.FeedbackCard
import app.fronteras.core.conversation.EngineStatus
import app.fronteras.core.conversation.Speaker
import app.fronteras.core.conversation.Turn
import app.fronteras.core.conversation.TurnStatus
import app.fronteras.core.speech.VoiceEvent
import app.fronteras.core.speech.VoiceState

@Composable
fun ConversationScreen(scenarioId: String?, competencyId: String?, minutes: Int, onBack: () -> Unit, onEnded: (String) -> Unit) {
    val vm = appViewModel(key = "conv-$scenarioId-$competencyId") { ConversationViewModel(it, scenarioId, competencyId, minutes) }
    val state by vm.state.collectAsState()
    val extras by vm.extras.collectAsState()
    val voice by vm.voiceState.collectAsState()
    val session = state.session
    var confirmEnd by remember { mutableStateOf(false) }
    LaunchedEffect(extras.endedSessionId) { extras.endedSessionId?.let(onEnded) }

    Scaffold(
        topBar = {
            FronterasTopBar(vm.scenario?.title ?: vm.competency?.title ?: "Conversation", onBack = { if (session.learnerTurns.isEmpty()) onBack() else confirmEnd = true }, actions = {
                TextButton(onClick = { confirmEnd = true }, enabled = !extras.ending) { Text("End") }
            })
        },
        bottomBar = {
            InputBar(
                voiceMode = extras.voiceMode, voiceAvailable = extras.voiceAvailable, voice = voice,
                busy = state.status is EngineStatus.AwaitingPartner || state.status is EngineStatus.Streaming,
                onSend = { vm.submit(it) }, onHelp = { vm.askForHelp(it) }, onVoiceMode = vm::setVoiceMode, onVoice = vm::voiceEvent,
            )
        },
    ) { padding ->
        val list = rememberLazyListState()
        LaunchedEffect(session.turns.size, state.status) { if (session.turns.isNotEmpty()) list.animateScrollToItem(session.turns.size + 1) }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.activePartner?.let { Text(it.label, style = MaterialTheme.typography.labelMedium) }
                    vm.scenario?.disclaimer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (vm.competency != null || vm.scenario != null) {
                        SectionCard(vm.competency?.canDo ?: vm.scenario?.learnerGoal) {
                            vm.scenario?.let { Text("${it.setting} You're ${it.learnerRole}; I'm ${it.partnerRole}.", style = MaterialTheme.typography.bodySmall) }
                            if (vm.expressions.isNotEmpty()) {
                                Text("Useful expressions", style = MaterialTheme.typography.labelLarge)
                                vm.expressions.take(6).forEach { e -> Text("• ${e.text} — ${e.gloss}", style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }
            items(session.turns, key = { it.id }) { t ->
                TurnBubble(t, onReplay = { vm.replay(t.text) }, onRetry = vm::retry, onEdit = { text -> vm.editPending(t.id, text) })
                session.shownFeedback.filter { it.turnId == t.id }.forEach { f -> FeedbackCard(f) }
            }
            item {
                when (val s = state.status) {
                    is EngineStatus.AwaitingPartner -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp)); Text("…", style = MaterialTheme.typography.bodyLarge)
                    }
                    is EngineStatus.Streaming -> Bubble(s.text, mine = false)
                    is EngineStatus.Error -> Column {
                        Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        if (s.retryable) TextButton(onClick = { vm.retry() }) { Text("Try again") }
                    }
                    else -> Unit
                }
                state.lastDecision?.clarification?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (extras.ending) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End this conversation?") },
            text = { Text("You'll get a short review. Nothing you said is lost.") },
            confirmButton = { Button(onClick = { confirmEnd = false; if (session.learnerTurns.isEmpty()) onBack() else vm.end() }) { Text("End and review") } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep talking") } },
        )
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean, modifier: Modifier = Modifier) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier.widthIn(max = 320.dp),
        ) { Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun TurnBubble(t: Turn, onReplay: () -> Unit, onRetry: () -> Unit, onEdit: (String) -> Unit) {
    val mine = t.speaker == Speaker.LEARNER
    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Bubble(t.text, mine)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!mine) IconButton(onClick = onReplay) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Replay") }
            t.transcript?.let { tr ->
                Text(if (tr.editedByLearner) "edited transcript" else tr.confidence?.let { c -> if (c < 0.6) "recognised with low confidence" else "recognised speech" } ?: "recognised speech",
                    style = MaterialTheme.typography.labelSmall)
            }
            if (t.status == TurnStatus.INTERRUPTED) Text("interrupted", style = MaterialTheme.typography.labelSmall)
        }
        if (mine && t.status == TurnStatus.FAILED) {
            var editing by rememberSaveable(t.id) { mutableStateOf(false) }
            var text by rememberSaveable(t.id) { mutableStateOf(t.text) }
            Row { TextButton(onClick = onRetry) { Text("Send again") }; TextButton(onClick = { editing = !editing }) { Text("Fix transcript") } }
            if (editing) {
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { onEdit(text); editing = false; onRetry() }) { Text("Save and send") }
            }
        }
    }
}

@Composable
private fun InputBar(
    voiceMode: Boolean, voiceAvailable: Boolean, voice: VoiceState, busy: Boolean,
    onSend: (String) -> Unit, onHelp: (String) -> Unit, onVoiceMode: (Boolean) -> Unit, onVoice: (VoiceEvent) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    var micDenied by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) onVoice(VoiceEvent.MicPressed) else micDenied = true }
    fun mic() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) onVoice(VoiceEvent.MicPressed)
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (micDenied) Text("Microphone permission is off. You can type, or allow it in system settings.", style = MaterialTheme.typography.bodySmall)
            if (voiceMode) {
                when (voice) {
                    is VoiceState.Reviewing -> {
                        var edit by remember(voice.transcript) { mutableStateOf(voice.transcript.text) }
                        Text("Is this what you said?", style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(edit, { edit = it }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onVoice(VoiceEvent.EditTranscript(edit)) }) { Text("Send") }
                            TextButton(onClick = { onVoice(VoiceEvent.MicPressed) }) { Text("Say it again") }
                            TextButton(onClick = { onVoice(VoiceEvent.DiscardTranscript) }) { Text("Discard") }
                        }
                    }
                    is VoiceState.Error -> {
                        Text(voice.error.learnerMessage, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = ::mic) { Text("Try again") }
                            if (voice.previous is VoiceState.Interrupted) TextButton(onClick = { onVoice(VoiceEvent.ResumeSpeech) }) { Text("Let it finish") }
                            TextButton(onClick = { onVoiceMode(false) }) { Text("Type instead") }
                        }
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val listening = voice is VoiceState.Listening
                        FilledIconButton(
                            onClick = { if (listening) onVoice(VoiceEvent.MicReleased) else mic() },
                            enabled = !busy || voice is VoiceState.Speaking,
                            modifier = Modifier.size(64.dp),
                        ) { Icon(if (listening) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = if (listening) "Stop and send" else "Speak") }
                        Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                            Text(
                                when (voice) {
                                    is VoiceState.Listening -> voice.partial.ifEmpty { "Listening…" }
                                    is VoiceState.Speaking -> "Speaking — tap the mic to interrupt"
                                    is VoiceState.Processing -> "Thinking…"
                                    is VoiceState.Interrupted -> "Paused"
                                    else -> "Tap to speak"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (voice is VoiceState.Speaking || voice is VoiceState.Idle) IconButton(onClick = { onVoice(VoiceEvent.Replay) }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Replay last reply") }
                        IconButton(onClick = { onVoiceMode(false) }) { Icon(Icons.Filled.Keyboard, contentDescription = "Switch to typing") }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(text, { text = it.take(2000) }, modifier = Modifier.weight(1f), placeholder = { Text("Type your reply") }, maxLines = 4)
                    IconButton(onClick = { onHelp(text); text = "" }, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Ask for help") }
                    IconButton(onClick = { onSend(text); text = "" }, enabled = text.isNotBlank() && !busy) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
                    if (voiceAvailable) IconButton(onClick = { onVoiceMode(true) }) { Icon(Icons.Filled.Mic, contentDescription = "Switch to speaking") }
                }
            }
        }
    }
}
