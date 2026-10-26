package app.fronteras.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fronteras.android.data.LearnerRepository
import app.fronteras.core.profile.Consent
import app.fronteras.core.profile.OnboardingDraft
import app.fronteras.core.profile.OnboardingFlow
import app.fronteras.core.profile.OnboardingResult
import app.fronteras.core.profile.OnboardingStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(private val repo: LearnerRepository) : ViewModel() {
    private val _draft = MutableStateFlow(repo.data.value.onboardingDraft ?: OnboardingDraft())
    val draft: StateFlow<OnboardingDraft> = _draft.asStateFlow()

    private val _step = MutableStateFlow(OnboardingFlow.nextStep(_draft.value) ?: OnboardingStep.INTERFACE_LANGUAGE)
    val step: StateFlow<OnboardingStep> = _step.asStateFlow()

    private val _problems = MutableStateFlow<List<String>>(emptyList())
    val problems: StateFlow<List<String>> = _problems.asStateFlow()

    fun update(transform: (OnboardingDraft) -> OnboardingDraft) {
        _draft.value = transform(_draft.value)
        _problems.value = emptyList()
        viewModelScope.launch { repo.saveDraft(_draft.value) }
    }

    fun consent(now: Long, granted: Boolean) = Consent(granted, now)

    val isLast: Boolean get() = _step.value == OnboardingStep.entries.last()

    fun canContinue(): Boolean = !_step.value.essential || OnboardingFlow.isAnswered(_draft.value, _step.value)

    fun next(onComplete: () -> Unit) {
        if (isLast) {
            viewModelScope.launch {
                when (val r = repo.completeOnboarding(_draft.value)) {
                    is OnboardingResult.Complete -> onComplete()
                    is OnboardingResult.Incomplete -> {
                        _problems.value = r.problems + r.missing.map { "Please answer: ${it.name.lowercase().replace('_', ' ')}" }
                        r.missing.firstOrNull()?.let { _step.value = it }
                    }
                }
            }
        } else {
            _step.value = OnboardingStep.entries[_step.value.ordinal + 1]
        }
    }

    fun skip(onComplete: () -> Unit) {
        update { OnboardingFlow.skip(it, _step.value) }
        next(onComplete)
    }

    fun back() {
        if (_step.value.ordinal > 0) _step.value = OnboardingStep.entries[_step.value.ordinal - 1]
    }
}
