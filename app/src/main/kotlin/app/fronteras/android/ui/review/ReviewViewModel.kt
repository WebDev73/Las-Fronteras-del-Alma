package app.fronteras.android.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fronteras.android.AppContainer
import app.fronteras.core.conversation.PostSessionProcessor
import app.fronteras.core.feedback.RetrievalMethod
import app.fronteras.core.speech.PronunciationReport
import app.fronteras.core.speech.ReadAloudEstimator
import app.fronteras.core.speech.RecognitionEvent
import app.fronteras.core.srs.Rating
import app.fronteras.core.srs.RatingPolicy
import app.fronteras.core.srs.ReviewTask
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReviewUi(
    val tasks: List<ReviewTask> = emptyList(),
    val index: Int = 0,
    val answer: String = "",
    val revealed: Boolean = false,
    /** Deterministic check result; null when only the learner can judge (open production). */
    val autoCorrect: Boolean? = null,
    val hints: Int = 0,
    val options: List<String> = emptyList(),
    val listening: Boolean = false,
    val pronunciation: PronunciationReport? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val done: Int = 0,
) {
    val current: ReviewTask? get() = tasks.getOrNull(index)
}

class ReviewViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository
    private val _ui = MutableStateFlow(ReviewUi())
    val ui: StateFlow<ReviewUi> = _ui.asStateFlow()
    private var listenJob: Job? = null

    init { load() }

    private fun load() {
        val tasks = repo.reviewQueue()
        _ui.value = ReviewUi(tasks = tasks)
        prepare()
    }

    private fun prepare() {
        val task = _ui.value.current ?: return
        val options = if (task.method == RetrievalMethod.RECOGNIZE) {
            val others = repo.data.value.reviewItemsFor(task.item.target).filter { it.id != task.item.id }.map { it.content }.distinct().shuffled().take(3)
            if (others.size >= 2) (others + task.item.content).shuffled() else emptyList()
        } else emptyList()
        _ui.value = _ui.value.copy(answer = "", revealed = false, autoCorrect = null, hints = 0, options = options, pronunciation = null, startedAt = c.clock.nowMillis())
    }

    fun prompt(task: ReviewTask): String {
        val i = task.item
        return when (task.method) {
            RetrievalMethod.RECOGNIZE -> "Which means: “${i.cue}”?"
            RetrievalMethod.TRANSLATE_TO_TARGET -> "How do you say: “${i.cue}”?"
            RetrievalMethod.RECALL_MISSING_WORD -> blanked(i.examples.firstOrNull(), i.content)?.let { "Fill the gap: $it  (${i.cue})" } ?: "How do you say: “${i.cue}”?"
            RetrievalMethod.CORRECT_ERROR -> "Fix this: “${i.commonError ?: i.content}”"
            RetrievalMethod.REFORMULATE -> "Say it another way, using “${i.content}”: ${i.examples.firstOrNull() ?: i.cue}"
            RetrievalMethod.USE_IN_NEW_SCENARIO -> "Use “${i.content}” in a sentence about your day or your work."
            RetrievalMethod.ANSWER_UNEXPECTED -> "Someone asks you something where “${i.content}” fits. Write what you'd answer."
            RetrievalMethod.EXPLAIN_CONCEPT -> "Explain in your own words when to use “${i.content}”."
            RetrievalMethod.SPEAK_ALOUD -> "Say it aloud: “${i.content}”"
        }
    }

    private fun blanked(example: String?, content: String): String? {
        val key = content.trimEnd('…', '.', '!', '?').trim()
        if (example == null || key.length < 2) return null
        val idx = example.lowercase().indexOf(key.lowercase())
        return if (idx < 0) null else example.substring(0, idx) + "_____" + example.substring(idx + key.length)
    }

    fun setAnswer(a: String) { _ui.value = _ui.value.copy(answer = a) }

    fun hint() {
        val t = _ui.value.current ?: return
        val shown = t.item.content.take(((_ui.value.hints + 1) * 3).coerceAtMost(t.item.content.length))
        _ui.value = _ui.value.copy(hints = _ui.value.hints + 1, answer = shown)
    }

    fun choose(option: String) {
        val t = _ui.value.current ?: return
        _ui.value = _ui.value.copy(answer = option, revealed = true, autoCorrect = option == t.item.content)
    }

    fun check() {
        val t = _ui.value.current ?: return
        val a = norm(_ui.value.answer)
        val target = norm(t.item.content)
        val auto = when (t.method) {
            RetrievalMethod.TRANSLATE_TO_TARGET, RetrievalMethod.RECALL_MISSING_WORD, RetrievalMethod.CORRECT_ERROR, RetrievalMethod.RECOGNIZE ->
                a == target || ReadAloudEstimator.editDistance(a, target) <= maxOf(1, target.length / 8)
            // Open production can't be judged by string matching; check the item was used, then ask the learner.
            RetrievalMethod.REFORMULATE, RetrievalMethod.USE_IN_NEW_SCENARIO, RetrievalMethod.ANSWER_UNEXPECTED ->
                if (a.contains(target)) null else false
            RetrievalMethod.EXPLAIN_CONCEPT, RetrievalMethod.SPEAK_ALOUD -> null
        }
        _ui.value = _ui.value.copy(revealed = true, autoCorrect = auto)
    }

    private fun norm(s: String) = PostSessionProcessor.normalize(s).replace("…", "").trim()

    fun speakModel() {
        val t = _ui.value.current ?: return
        val p = repo.profile ?: return
        c.tts.setLanguage(p.active.voice.accent ?: p.activeTarget, p.active.voice.speakingRate)
        c.tts.speak(t.item.content, 0, {}, {})
    }

    fun listen() {
        val t = _ui.value.current ?: return
        val p = repo.profile ?: return
        listenJob?.cancel()
        _ui.value = _ui.value.copy(listening = true)
        listenJob = viewModelScope.launch {
            c.speechInput.listen(p.activeTarget, p.accessibility.extendedResponseTime, preferOffline = !c.connectivity.online.value).collect { ev ->
                when (ev) {
                    is RecognitionEvent.Final -> {
                        val report = ReadAloudEstimator.estimate(t.item.content, ev.result, p.active.voice.accent ?: p.activeTarget)
                        _ui.value = _ui.value.copy(listening = false, answer = ev.result.text, pronunciation = report, revealed = true,
                            autoCorrect = report.intelligibility?.let { it >= 0.85 })
                    }
                    is RecognitionEvent.Error -> _ui.value = _ui.value.copy(listening = false)
                    else -> Unit
                }
            }
        }
    }

    /** Records the result. [selfRating] lets the learner judge open production honestly. */
    fun grade(correct: Boolean, selfRating: Rating? = null) {
        val u = _ui.value
        val t = u.current ?: return
        val rating = RatingPolicy.fromOutcome(correct, c.clock.nowMillis() - u.startedAt, u.hints, selfRating, production = t.method.production)
        viewModelScope.launch {
            repo.recordReview(t.item, t.method, rating, c.clock.nowMillis() - u.startedAt, u.hints)
            _ui.value = _ui.value.copy(index = u.index + 1, done = u.done + 1)
            prepare()
        }
    }

    fun suspendCurrent() {
        val t = _ui.value.current ?: return
        viewModelScope.launch {
            repo.setSuspended(t.item.id, true)
            _ui.value = _ui.value.copy(index = _ui.value.index + 1)
            prepare()
        }
    }

    override fun onCleared() { listenJob?.cancel(); c.requestSync() }
}
