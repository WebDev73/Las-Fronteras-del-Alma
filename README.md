# Fronteras — conversational language learning

An Android-first language-learning app built around one loop:

**ASSESS → CONVERSE → ANALYZE → EXPLAIN → PRACTISE → REVIEW → REASSESS**

Learners hold real conversations (voice or text) with an AI partner. Problems are corrected
in proportion to what's needed. Finished sessions become a short, selective review deck and
evidence in a learner model that measures ability, not activity.

> The `index.html` at the repository root is the existing *Las Fronteras del Alma* music
> landing page. It is left untouched and is unrelated to the app.

## Repository layout

| Module | What it is | Builds where |
|---|---|---|
| `core/` | Pure Kotlin learning engine shared by app and server: learner model, diagnostic, mastery, feedback policy, FSRS review, conversation orchestration, speech turn-taking, sync rules, seed content (es/fr/en) | Any JVM (`./gradlew :core:test`) |
| `server/` | Ktor service that holds the AI key and calls Claude via the official Anthropic Java SDK; auth, quotas, validation, sync | Any JVM (`./gradlew :server:test`) |
| `app/` | Jetpack Compose Android client | Needs an Android SDK (included automatically when one is present) |
| `docs/` | Architecture, setup, decisions, privacy, sync, roadmap | — |

## Quick start

```bash
./gradlew :core:test :server:test          # JVM tests (no Android SDK needed)
./gradlew :app:assembleDebug               # with ANDROID_HOME set
```

Run the server and point the app at it: see [docs/SETUP.md](docs/SETUP.md).

## Status — what works and what doesn't

**Implemented and tested here (JVM):** the whole `core` engine (90 tests) and the server
(22 tests, including the real Anthropic SDK against a local stub of the Messages API).

**Built in CI, not yet tried on a device:** the Android app compiles, passes its unit tests
and lint in GitHub Actions, which also publishes a debug APK artifact. It has not been run on a
phone or emulator yet: see [docs/ROADMAP.md](docs/ROADMAP.md#verification-status).

**Not configured by default, by design:** AI conversation needs a server with
`ANTHROPIC_API_KEY`. Without one, the app says so and offers review and a clearly labelled
*scripted* offline practice partner. It never presents scripted output as AI.

**Not implemented yet:** billing, phoneme-level pronunciation scoring, on-device generative
conversation, production identity provider, translated UI. Details and next steps are in the
roadmap.
