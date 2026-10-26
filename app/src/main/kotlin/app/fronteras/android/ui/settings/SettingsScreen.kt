package app.fronteras.android.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.fronteras.android.AppContainer
import app.fronteras.android.data.ApiResult
import app.fronteras.android.sync.SyncManager
import app.fronteras.android.ui.common.FronterasTopBar
import app.fronteras.android.ui.common.SectionCard
import app.fronteras.android.ui.common.appContainer
import app.fronteras.android.ui.onboarding.Accessibility
import app.fronteras.android.ui.onboarding.ConsentSwitch
import app.fronteras.android.ui.onboarding.FeedbackModeChoice
import app.fronteras.android.ui.onboarding.LevelChoice
import app.fronteras.core.language.LanguageCatalog
import app.fronteras.core.profile.Consent
import app.fronteras.core.profile.OnboardingFlow
import app.fronteras.core.profile.TargetLanguageProfile
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(onBack: () -> Unit, onHistory: () -> Unit, onDeck: () -> Unit, onErased: () -> Unit) {
    val c = appContainer()
    val data by c.repository.data.collectAsState()
    val caps by c.capabilities.collectAsState()
    val profile = data.profile ?: return
    val t = profile.active
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val now = { c.clock.nowMillis() }

    Scaffold(topBar = { FronterasTopBar("Settings", onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard("Languages") {
                Text("Learning now: ${LanguageCatalog.displayName(profile.activeTarget)}", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    profile.targets.forEach { tp ->
                        FilterChip(selected = tp.target == profile.activeTarget, onClick = { scope.launch { c.repository.updateProfile { it.copy(activeTarget = tp.target) } } },
                            label = { Text(LanguageCatalog.displayName(tp.target)) })
                    }
                }
                var adding by rememberSaveable { mutableStateOf(false) }
                if (!adding) TextButton(onClick = { adding = true }) { Text("Learn another language") }
                else LanguageCatalog.variants.filter { v -> LanguageCatalog.hasLearningContent(v.tag) && profile.target(v.tag) == null }.forEach { v ->
                    TextButton(onClick = {
                        adding = false
                        scope.launch { c.repository.updateProfile { OnboardingFlow.addTarget(it, TargetLanguageProfile(v.tag, explanationLanguage = t.explanationLanguage, dailyMinutes = t.dailyMinutes, createdAt = now()), now()) } }
                    }) { Text(v.displayName) }
                }
            }
            SectionCard("Corrections") {
                FeedbackModeChoice(t.feedbackMode) { m -> scope.launch { c.repository.updateProfile { p -> p.updateTarget(p.activeTarget, now()) { it.copy(feedbackMode = m) } } } }
            }
            SectionCard("Daily time") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 20, 30, 60).forEach { m ->
                        FilterChip(selected = t.dailyMinutes == m, onClick = { scope.launch { c.repository.updateProfile { p -> p.updateTarget(p.activeTarget, now()) { it.copy(dailyMinutes = m) } } } }, label = { Text("$m") })
                    }
                }
            }
            SectionCard("Level") {
                val override = t.levelOverride
                Text(override?.let { "You set your level to ${it.name}. Lessons still adapt to what you do." } ?: "Your level is estimated from what you do.", style = MaterialTheme.typography.bodyMedium)
                var editing by rememberSaveable { mutableStateOf(false) }
                if (editing) LevelChoice(override) { l -> editing = false; scope.launch { c.repository.setLevelOverride(l) } }
                else TextButton(onClick = { editing = true }) { Text("Change") }
            }
            SectionCard("Accessibility") {
                Accessibility(profile.accessibility) { a -> scope.launch { c.repository.updateProfile { it.copy(accessibility = a) } } }
                Row {
                    Text("Check the transcript before sending speech", Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = data.settings.reviewTranscriptBeforeSend, onCheckedChange = { v -> scope.launch { c.repository.updateSettings { it.copy(reviewTranscriptBeforeSend = v) } } })
                }
            }
            SectionCard("Privacy") {
                ConsentSwitch("Speech recognition", "Turns your voice into text using your device's speech service. Recordings are not saved by this app.", profile.consents.audioProcessing) { g ->
                    scope.launch { c.repository.updateProfile { it.copy(consents = it.consents.copy(audioProcessing = Consent(g, now()))) } }
                }
                ConsentSwitch("AI conversation partner", "Sends conversation text to the Fronteras server and AI provider. Off = review and scripted offline practice only.", profile.consents.cloudAi) { g ->
                    scope.launch { c.repository.updateProfile { it.copy(consents = it.consents.copy(cloudAi = Consent(g, now()))) } }
                }
                ConsentSwitch("Remember me across conversations", "Shares recurring difficulties, recent vocabulary and your notes with the partner.", profile.consents.conversationMemory) { g ->
                    scope.launch {
                        c.repository.updateProfile { it.copy(consents = it.consents.copy(conversationMemory = Consent(g, now()))) }
                        c.repository.updateSettings { it.copy(retention = it.retention.copy(conversationMemory = g)) }
                    }
                }
                Text("Keep conversation transcripts for", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 90, 180, null).forEach { d ->
                        FilterChip(selected = data.settings.retention.transcriptDays == d, onClick = {
                            scope.launch { c.repository.updateSettings { it.copy(retention = it.retention.copy(transcriptDays = d)) }; c.repository.enforceRetention() }
                        }, label = { Text(d?.let { "$it days" } ?: "Until I delete") })
                    }
                }
                Text("Learning results (review items, progress) are kept when transcripts expire.", style = MaterialTheme.typography.bodySmall)
            }
            ServerSection(c, caps)
            OfflineSection(c)
            DataSection(c, onHistory, onDeck, onErased)
        }
    }
}

@Composable
private fun ServerSection(c: AppContainer, caps: AppContainer.CapabilityState) {
    val scope = rememberCoroutineScope()
    val endpoint = remember { c.credentials.endpoint() }
    var url by rememberSaveable { mutableStateOf(endpoint.baseUrl) }
    var token by rememberSaveable { mutableStateOf(endpoint.token) }
    var syncMsg by remember { mutableStateOf<String?>(null) }
    SectionCard("Server") {
        Text("AI conversation runs on a Fronteras server you or your organisation operates. The AI provider's key stays on that server, never on this phone.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(url, { url = it }, label = { Text("Server address (https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(token, { token = it }, label = { Text("Access token") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { c.credentials.save(url, token); scope.launch { c.refreshCapabilities() } }) { Text("Save and test") }
            OutlinedButton(onClick = {
                scope.launch {
                    syncMsg = when (val r = c.sync.syncOnce()) {
                        is SyncManager.Result.Done -> "Synced: sent ${r.pushed}, received ${r.pulled}."
                        is SyncManager.Result.Retry -> "Sync will retry automatically (${r.reason})."
                        SyncManager.Result.NotConfigured -> "Sync needs a server and AI/cloud consent."
                    }
                }
            }) { Text("Sync now") }
        }
        Text(when (caps) {
            AppContainer.CapabilityState.Unknown -> "Not checked yet."
            AppContainer.CapabilityState.NotConfigured -> "No server configured."
            is AppContainer.CapabilityState.Unreachable -> "Can't reach the server: ${caps.message}"
            is AppContainer.CapabilityState.Known -> if (caps.caps.aiConversation) "Connected. AI conversation available (${caps.caps.turnsUsedToday ?: 0}/${caps.caps.dailyTurnLimit ?: "∞"} turns today)."
                else "Connected, but ${caps.caps.reason ?: "AI is not available"}"
        }, style = MaterialTheme.typography.bodyMedium)
        Text("Waiting to sync: ${c.sync.pendingCount()}${c.sync.failedCount().takeIf { it > 0 }?.let { " · couldn't sync: $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
        syncMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun OfflineSection(c: AppContainer) {
    val context = LocalContext.current
    SectionCard("Offline") {
        Text("Works offline: your review deck, lessons and expressions, scripted practice conversations, progress history, read-aloud practice (if your device recognises speech offline).", style = MaterialTheme.typography.bodyMedium)
        Text("Needs a connection: AI conversation partner, detailed session analysis, sync.", style = MaterialTheme.typography.bodyMedium)
        Text("On-device speech recognition: ${if (c.speechInput.onDeviceAvailable) "available" else "not available on this device"}", style = MaterialTheme.typography.bodySmall)
        Text("Offline AI conversation isn't offered: no on-device model has been validated for this app yet.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = {
            try { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: ActivityNotFoundException) {}
        }) { Text("Manage offline voices") }
    }
}

@Composable
private fun DataSection(c: AppContainer, onHistory: () -> Unit, onDeck: () -> Unit, onErased: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    SectionCard("Your data") {
        Text("Everything the app knows about you is on this device. You can look at it, correct it, take a copy, or delete it.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onHistory) { Text("Conversations") }
            OutlinedButton(onClick = onDeck) { Text("Review items") }
        }
        OutlinedButton(onClick = {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "fronteras-export.json").apply { writeText(c.repository.exportJson()) }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".exports", file)
            val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Export my data"))
        }) { Text("Export everything (JSON)") }
        Button(onClick = { confirm = true }) { Text("Delete everything") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete all your data?") },
            text = { Text("This removes your profile, conversations, review items and progress from this device, and asks the server to delete its copy. It can't be undone.") },
            confirmButton = {
                Button(onClick = {
                    confirm = false
                    scope.launch {
                        val serverOk = if (c.credentials.endpoint().configured) c.api.deleteServerData() is ApiResult.Ok else true
                        c.repository.eraseAll()
                        c.credentials.setLastPulledVersion(0)
                        if (!serverOk) message = "Deleted on this device. The server couldn't be reached; delete again when online."
                        else onErased()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
