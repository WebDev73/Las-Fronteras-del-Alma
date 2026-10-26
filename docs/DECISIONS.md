# Decisions and trade-offs

| # | Decision | Why | Revisit when |
|---|---|---|---|
| 1 | Shared pure-Kotlin `core` | One implementation of learning logic and merge rules for client and server; fast JVM tests | — |
| 2 | AI calls only through our server | Keeps the provider key off devices; central quotas, validation, redaction | — |
| 3 | Official Anthropic Java SDK | Supported client; typed errors; streaming accumulator | — |
| 4 | Two model calls (turn, session) | Cost and latency; avoids "ten calls per sentence" | Analysis quality is insufficient in evals |
| 5 | Structured output `{reply, analysis}` and streaming the `reply` field | Parseable analysis without a second call; early TTS; avoids text before tool calls turning into thinking blocks | — |
| 6 | Gaussian/Rasch skill estimates with reliability weights | Gives uncertainty directly; low-quality evidence (ASR, noise) moves estimates less | Item calibration data allows a full IRT model |
| 7 | Evidence levels for mastery (exposed → independent) | Separates activity from ability; a lesson completion is only "exposed" | — |
| 8 | FSRS-4.5 with default weights; separate recognition and production tracks | Established algorithm; multiple-choice success never implies speaking mastery | ≈1,000 logs per learner allow weight optimisation |
| 9 | JSON file store with atomic replace and backup | Simple, no annotation processing; fine for one learner's data | Histories get large → SQLite (Room or SQLDelight); a migration hook exists (`Migrations`) |
| 10 | Deterministic merges (union of logs + FSRS replay; LWW profile; tombstones) | Devices converge without server-side conflict prompts | — |
| 11 | Platform speech recognition and TTS | Available on most devices, on-device where supported, no extra cost | A provider with phoneme alignment is integrated |
| 12 | Read-aloud pronunciation as a labelled *app estimate* | Honest, modest signal when no phoneme-level scorer exists | Pronunciation provider added via `PronunciationAssessor` |
| 13 | Scripted offline partner, never presented as AI | Offline practice without overstating capability | An on-device model is validated for the target languages |
| 14 | Quotas now, billing later | AI has real cost; spec says monetise after the core is reliable | Core loop validated with learners |
| 15 | English UI strings in code | Speed of the first version | Before adding a second UI language: move strings to resources |
| 16 | Seed content marked `CURATED_DRAFT` | Content written by the developer, not yet reviewed by a linguist | A qualified reviewer signs it off |
