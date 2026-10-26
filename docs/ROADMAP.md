# Roadmap

## Verification status

| Area | State |
|---|---|
| `core` | Compiled; unit tests pass locally |
| `server` | Compiled; tests pass locally; binary smoke-tested (health, auth, capabilities, honest 503 without a key) |
| Claude integration | Request shape and streaming verified against a local Messages API stub with the real SDK. **Not yet run against the live API** (no key was available in development) |
| `app` | **Written but not compiled**: the dev container had no Android SDK, and CI has not run because pushing to GitHub was refused. Expect some compile/lint fixes on first CI run |

## Next milestones

1. **Get the app green in CI.** Fix compile and lint findings, then add Compose UI tests for
   onboarding, the conversation turn loop and review.
2. **Live API evaluation.** Build an eval set of learner turns (multiple languages and
   levels, including dialect forms and ASR errors). Measure correction precision and how
   often valid variants are wrongly flagged, then tune prompts and effort.
3. **Content review.** Have linguists review the seed curriculum, scenarios, variant notes and
   diagnostic items; calibrate item difficulty from response data.
4. **Identity.** Add an OIDC or Firebase `TokenVerifier`, account linking, and server-side
   storage in a database.
5. **Pronunciation.** Integrate a phoneme-level provider behind `PronunciationAssessor`, and
   show provider scores labelled as such.
6. **Storage.** Move to SQLite when histories grow; per-learner FSRS weight optimisation.
7. **UI localisation.** Move strings to resources; translate the interface.
8. **On-device model evaluation** (size, memory, latency, battery, language coverage) before
   offering offline AI conversation.
9. **Monetisation** (the spec's §13 was cut off in the brief): Play Billing for the premium
   plan. Quotas already exist (`core/entitlement`). Review, offline practice, export and
   deletion stay free.
