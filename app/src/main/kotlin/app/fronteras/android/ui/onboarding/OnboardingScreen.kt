package app.fronteras.android.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.fronteras.android.ui.common.FronterasTopBar
import app.fronteras.android.ui.common.appViewModel
import app.fronteras.core.assessment.CefrLevel
import app.fronteras.core.language.LanguageCatalog
import app.fronteras.core.language.LanguageTag
import app.fronteras.core.profile.AccessibilityPreferences
import app.fronteras.core.profile.Consent
import app.fronteras.core.profile.FeedbackMode
import app.fronteras.core.profile.KnownLanguage
import app.fronteras.core.profile.LearningGoal
import app.fronteras.core.profile.OnboardingDraft
import app.fronteras.core.profile.OnboardingStep
import app.fronteras.core.profile.VoicePreferences

private fun title(step: OnboardingStep) = when (step) {
    OnboardingStep.INTERFACE_LANGUAGE -> "App language"
    OnboardingStep.TARGET_LANGUAGE -> "What do you want to learn?"
    OnboardingStep.NATIVE_LANGUAGES -> "Your native language(s)"
    OnboardingStep.OTHER_LANGUAGES -> "Other languages you know"
    OnboardingStep.SELF_ASSESSMENT -> "How well do you know it now?"
    OnboardingStep.GOALS -> "Why are you learning?"
    OnboardingStep.SITUATIONS -> "Where do you want to speak it?"
    OnboardingStep.DAILY_TIME -> "Time per day"
    OnboardingStep.EXPLANATION_LANGUAGE -> "Explanations in…"
    OnboardingStep.CORRECTION_INTENSITY -> "How should I correct you?"
    OnboardingStep.VOICE -> "Voice and speed"
    OnboardingStep.ACCESSIBILITY -> "Accessibility"
    OnboardingStep.PRIVACY -> "Privacy and your data"
}

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val vm = appViewModel { OnboardingViewModel(it.repository) }
    val draft by vm.draft.collectAsState()
    val step by vm.step.collectAsState()
    val problems by vm.problems.collectAsState()
    val now = remember { System.currentTimeMillis() }

    Scaffold(
        topBar = { FronterasTopBar(title(step), onBack = if (step.ordinal > 0) vm::back else null) },
        bottomBar = {
            Column(Modifier.padding(16.dp).imePadding()) {
                problems.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (!step.essential) TextButton(onClick = { vm.skip(onDone) }) { Text("Skip for now") } else Spacer(Modifier)
                    Button(onClick = { vm.next(onDone) }, enabled = vm.canContinue()) { Text(if (vm.isLast) "Finish" else "Continue") }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(progress = { (step.ordinal + 1f) / OnboardingStep.entries.size }, modifier = Modifier.fillMaxWidth())
            if (!step.essential) Text("Optional — you can change this later in Settings.", style = MaterialTheme.typography.bodySmall)
            when (step) {
                OnboardingStep.INTERFACE_LANGUAGE -> InterfaceLanguage(draft, vm::update)
                OnboardingStep.TARGET_LANGUAGE -> TargetLanguage(draft, vm::update)
                OnboardingStep.NATIVE_LANGUAGES -> LanguageMulti(draft.nativeLanguages, exclude = emptySet()) { list -> vm.update { it.copy(nativeLanguages = list) } }
                OnboardingStep.OTHER_LANGUAGES -> OtherLanguages(draft, vm::update)
                OnboardingStep.SELF_ASSESSMENT -> LevelChoice(draft.selfAssessedLevel) { l -> vm.update { it.copy(selfAssessedLevel = l) } }
                OnboardingStep.GOALS -> Goals(draft, vm::update)
                OnboardingStep.SITUATIONS -> Situations(draft, vm::update)
                OnboardingStep.DAILY_TIME -> DailyTime(draft, vm::update)
                OnboardingStep.EXPLANATION_LANGUAGE -> ExplanationLanguage(draft, vm::update)
                OnboardingStep.CORRECTION_INTENSITY -> FeedbackModeChoice(draft.feedbackMode ?: FeedbackMode.BALANCED) { m -> vm.update { it.copy(feedbackMode = m) } }
                OnboardingStep.VOICE -> Voice(draft, vm::update)
                OnboardingStep.ACCESSIBILITY -> Accessibility(draft.accessibility ?: AccessibilityPreferences()) { a -> vm.update { it.copy(accessibility = a) } }
                OnboardingStep.PRIVACY -> Privacy(draft, now, vm::update)
            }
            Spacer(Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, onValueChange = onChange, role = Role.Checkbox).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun InterfaceLanguage(d: OnboardingDraft, update: ((OnboardingDraft) -> OnboardingDraft) -> Unit) {
    LanguageCatalog.languages.forEach { l ->
        RadioRow("${l.nativeName} (${l.englishName})", d.interfaceLanguage == l.tag) { update { it.copy(interfaceLanguage = l.tag) } }
    }
    if (d.interfaceLanguage?.base?.let { it != "en" } == true) {
        Text("The app's menus are in English for now. Your choice is saved and explanations can still be in your language.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TargetLanguage(d: OnboardingDraft, update: ((OnboardingDraft) -> OnboardingDraft) -> Unit) {
    Text("Pick the variety you want to sound like. Other varieties are never treated as wrong.", style = MaterialTheme.typography.bodyMedium)
    LanguageCatalog.learningContentLanguages.sorted().forEach { base ->
        val lang = LanguageCatalog.language(LanguageTag(base)) ?: return@forEach
        Text(lang.englishName, style = MaterialTheme.typography.titleSmall)
        LanguageCatalog.variantsOf(base).forEach { v ->
            RadioRow(v.displayName, d.target == v.tag, v.description) { update { it.copy(target = v.tag, voice = (it.voice ?: VoicePreferences()).copy(accent = v.tag)) } }
        }
    }
    Text("More languages are coming. We only offer languages with reviewed learning content.", style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageMulti(selected: List<LanguageTag>, exclude: Set<String>, onChange: (List<LanguageTag>) -> Unit) {
    Text("Select all that apply.", style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LanguageCatalog.languages.filter { it.tag.base !in exclude }.forEach { l ->
            val on = selected.any { it.base == l.tag.base }
            FilterChip(selected = on, onClick = { onChange(if (on) selected.filterNot { it.base == l.tag.base } else selected + l.tag) }, label = { Text(l.nativeName) })
        }
    }
}

@Composable
private fun OtherLanguages(d: OnboardingDraft, update: ((OnboardingDraft) -> OnboardingDraft) -> Unit) {
    LanguageMulti(d.otherLanguages.map { it.tag }, exclude = d.nativeLanguages.map { it.base }.toSet()) { list ->
        update { draft -> draft.copy(otherLanguages = list.map { t -> draft.otherLanguages.firstOrNull { it.tag == t } ?: KnownLanguage(t) }) }
    }
    d.otherLanguages.forEach { k ->
        Text(LanguageCatalog.displayName(k.tag), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CefrLevel.entries.forEach { lvl ->
                FilterChip(selected = k.level == lvl, onClick = {
                    update { dr -> dr.copy(otherLanguages = dr.otherLanguages.map { if (it.tag == k.tag) it.copy(level = lvl) else it }) }
                }, label = { Text(lvl.name) })
            }
        }
    }
}

@Composable
fun LevelChoice(selected: CefrLevel?, onChange: (CefrLevel?) -> Unit) {
    Text("A rough guess is fine — a short check-in comes next, and the app keeps adapting.", style = MaterialTheme.typography.bodyMedium)
    RadioRow("I'm not sure", selected == null) { onChange(null) }
    CefrLevel.entries.forEach { l -> RadioRow(l.name, selected == l, l.summary) { onChange(l) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Goals(d: OnboardingDraft, update: ((OnboardingDraft) -> OnboardingDraft) -> Unit) {
    Text("Your first choice shapes your learning path.", style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LearningGoal.entries.forEach { g ->
            val on = g in d.goals
            FilterChip(selected = on, onClick = { update { it.copy(goals = if (on) it.goals - g else it.goals + g) } }, label = { Text(g.label) })
        }
    }
    if (LearningGoal.OTHER in d.goals) {
        OutlinedTextField(d.otherGoal.orEmpty(), { v -> update { it.copy(otherGoal = v.take(200)) } }, label = { Text("Tell us more") }, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Situations(d: OnboardingDraft, update: ((OnboardingDraft) -> OnboardingDraft) -> Unit) {
    val suggestions = listOf("Meetings at work", "Ordering food", "Talking with family", "Doctor's appointments", "Small talk with neighbours", "Job interviews", "Travelling", "Phone calls")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (suggestions + d.situations.filter { it !in suggestions }).forEach { s ->
            val on = s in d.situations
            FilterChip(selected = on, onClick = { update { it.copy(situations = if (on) it.situations - s else it.situations + s) } }, label = { Text(s) })
        }
    }
    var custom by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(custom, { custom = it.take(80) }, label = { Text("Add your own") }, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (custom.isNotBlank()) { update { it.copy(situations = it.situations + custom.trim()) }; custom = "" } }) { Text("Add") }
    }
}

@Composable
private fun DailyTime(d: OnboardingDraft, update: ((OnboardingDraft) -> OnboardingDraft) -> Unit) {
    listOf(5, 10, 15, 20, 30, 45, 60).forEach { m ->
        RadioRow("$m minutes", d.dailyMinutes == m) { update { it.copy(dailyMinutes = m) } }
    }
}

@Composable
private fun ExplanationLanguage(d: OnboardingDraft, update: ((OnboardingDraft) -> OnboardingDraft) -> Unit) {
    val options = (listOfNotNull(d.interfaceLanguage) + d.nativeLanguages + d.otherLanguages.map { it.tag }).distinctBy { it.base }
    Text("Explanations use this language when the target language alone isn't enough.", style = MaterialTheme.typography.bodyMedium)
    options.forEach { t -> RadioRow(LanguageCatalog.displayName(t), d.explanationLanguage == t) { update { it.copy(explanationLanguage = t) } } }
}

@Composable
fun FeedbackModeChoice(selected: FeedbackMode, onChange: (FeedbackMode) -> Unit) {
    FeedbackMode.entries.forEach { m -> RadioRow(m.label, selected == m, m.description) { onChange(m) } }
}

@Composable
private fun Voice(d: OnboardingDraft, update: ((OnboardingDraft) -> OnboardingDraft) -> Unit) {
    val voice = d.voice ?: VoicePreferences(accent = d.target)
    Text("Speaking speed: ${"%.1f".format(voice.speakingRate)}×", style = MaterialTheme.typography.bodyLarge)
    Slider(value = voice.speakingRate, onValueChange = { v -> update { it.copy(voice = voice.copy(speakingRate = (v * 10).toInt() / 10f)) } }, valueRange = 0.6f..1.3f, steps = 6)
    d.target?.let { t ->
        Text("Accent", style = MaterialTheme.typography.titleSmall)
        LanguageCatalog.variantsOf(t.base).forEach { v ->
            RadioRow(v.displayName, (voice.accent ?: t) == v.tag) { update { it.copy(voice = voice.copy(accent = v.tag)) } }
        }
        Text("Voices come from your device's text-to-speech engine. If a variety isn't installed, Android will offer to download it.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun Accessibility(a: AccessibilityPreferences, onChange: (AccessibilityPreferences) -> Unit) {
    CheckRow("Larger text", a.largeText) { onChange(a.copy(largeText = it)) }
    CheckRow("Always show captions for spoken replies", a.alwaysShowCaptions) { onChange(a.copy(alwaysShowCaptions = it)) }
    CheckRow("Give me more time to answer before the microphone stops", a.extendedResponseTime) { onChange(a.copy(extendedResponseTime = it)) }
    CheckRow("Prefer typing to speaking", a.preferTextInput) { onChange(a.copy(preferTextInput = it)) }
    CheckRow("Reduce motion", a.reducedMotion) { onChange(a.copy(reducedMotion = it)) }
}

@Composable
fun ConsentSwitch(title: String, detail: String, consent: Consent, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = consent.granted, onValueChange = onChange, role = Role.Switch).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = consent.granted, onCheckedChange = null)
    }
}

@Composable
private fun Privacy(d: OnboardingDraft, now: Long, update: ((OnboardingDraft) -> OnboardingDraft) -> Unit) {
    Text("You're in control. Each of these can be changed at any time.", style = MaterialTheme.typography.bodyMedium)
    ConsentSwitch("Speech recognition", "Your microphone audio is turned into text by your device's speech service (which may use Google's servers). This app does not save recordings.", d.consents.audioProcessing) { g ->
        update { it.copy(consents = it.consents.copy(audioProcessing = Consent(g, now))) }
    }
    ConsentSwitch("AI conversation partner", "What you say or type in conversations is sent to our server and an AI provider to generate replies and feedback. Email addresses and phone numbers are removed first. Without this, you can still use review and scripted offline practice.", d.consents.cloudAi) { g ->
        update { it.copy(consents = it.consents.copy(cloudAi = Consent(g, now))) }
    }
    ConsentSwitch("Remember me across conversations", "Lets the partner know your recurring difficulties and recent vocabulary. Off by default.", d.consents.conversationMemory) { g ->
        update { it.copy(consents = it.consents.copy(conversationMemory = Consent(g, now))) }
    }
    Text("You can inspect, export or delete everything the app stores in Settings → Your data.", style = MaterialTheme.typography.bodySmall)
}
