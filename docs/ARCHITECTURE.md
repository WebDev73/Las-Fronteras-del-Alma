# Architecture

```
 Android app (Compose)                 Fronteras server (Ktor)              Anthropic API
 ─────────────────────                 ───────────────────────              ─────────────
 UI ─► ViewModels ─► LearnerRepository  /v1/conversation/turn (SSE) ──────►  Messages API
          │             │               /v1/session/analyze       ──────►  (structured outputs)
          │             ▼               /v1/sync/push|changes
          │        :core engine         /v1/capabilities
          │        (same code as ──────► :core (shared types, merge rules)
          │         the server)
          ▼
 Speech: Android SpeechRecognizer / TextToSpeech, driven by core TurnTakingMachine
 Storage: JSON file (atomic replace + backup)   Sync: WorkManager + outbox
```

## Why three modules

* **`core` is pure Kotlin.** All learning logic is testable on any JVM, and the server and app
  compile against the same wire types and merge rules, so they cannot drift apart.
* **The AI key lives only on the server.** A key shipped in an APK can be extracted. The app
  holds a per-user access token for the Fronteras server.
* **The app is a thin shell** over `core`: screens, platform speech, storage and sync.

## The learning loop in code

| Step | Where |
|---|---|
| Assess | `core/assessment/Diagnostic.kt` (adaptive, per-skill), `SkillEstimate.kt` (Gaussian estimate with uncertainty) |
| Converse | `core/conversation/ConversationEngine.kt` + `server/ai/ClaudeTutorModel.kt` |
| Analyze | Per turn: a light analysis returned in the same call as the reply. Per session: one batched analysis (`/v1/session/analyze`) |
| Explain | `core/feedback/Feedback.kt` (layered views), `CorrectionPolicy.kt` (what interrupts and what waits) |
| Practise / Review | `core/feedback/ReviewSelection.kt` (selective), `core/srs/*` (FSRS-4.5, recognition vs production tracks) |
| Reassess | `core/mastery/Mastery.kt` (evidence levels), `core/progress/Progress.kt` (trend only when it beats uncertainty), `core/planning/*` |

## AI orchestration (spec §6.3)

The ten logical responsibilities map onto **two model calls**, plus deterministic code:

| Responsibility | Implementation |
|---|---|
| Conversation partner, light error analysis, phrasing coach | **One** streamed call per learner turn. Structured output `{reply, analysis}`; the `reply` field is extracted incrementally (`ReplyFieldStreamer`) so TTS can start early |
| Vocabulary/grammar extraction, rubric scoring, objectives met | **One** batched call per session |
| Transcription | Platform speech recogniser (on device where available) |
| Pronunciation assessment | Interface `PronunciationAssessor`; today a labelled app estimate (`ReadAloudEstimator`) |
| Lesson planning, review scheduling, progress evaluation | Deterministic `core` code |
| Safety and privacy | `Redactor` (PII), consent gates, validation bounds, fictional-scenario framing, refusal handling |

Model settings: `claude-opus-5-5` (configurable), adaptive thinking (the default), `effort`
`low` for turns and `high` for analysis, prompt caching on static system prompts,
server-side refusal fallbacks (`fallbacks: "default"`). Why text-plus-tool was not used:
on this model, text written before a tool call can come back as a thinking block. A single
structured output avoids that.

## Failure handling

* Partner calls retry transient failures twice with backoff, then fall back to the scripted
  offline partner (with a notice), and finally keep the learner's turn as *failed* with a
  "Send again" action. The session is never lost; it is saved after every turn.
* Errors are classified: retryable (rate limit, 5xx, network, truncation) or permanent
  (auth, bad request, refusal).
