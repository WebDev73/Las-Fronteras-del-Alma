# Privacy and safety

* **Consents** (`core/profile/LearnerProfile.kt`): speech recognition, cloud AI, and
  cross-session memory. Each is explicit, timestamped and revocable. Nothing reaches the AI
  without cloud-AI consent.
* **Raw audio:** the app doesn't record or store audio. Recognition uses the platform service.
  The retention policy defaults to keeping no audio.
* **Redaction:** emails, phone, card and account numbers are replaced before text leaves the
  device (`core/privacy/Privacy.kt`).
* **Memory:** long-term context (recurring errors, recent vocabulary, notes) is sent only with
  memory consent.
* **Retention:** transcripts expire after 30, 90 or 180 days, or are kept until deleted (the
  learner chooses). Learning outcomes stay.
* **Inspect, correct, export, delete:** conversation history and review items can be viewed,
  edited, paused or deleted. Export produces the full JSON. Delete-all erases the device copy
  and asks the server to delete its copy.
* **Backups:** app data is excluded from cloud backup and device transfer.
* **Server:** never logs request bodies, validates input sizes, and rate-limits per user.
* **Sensitive role-plays** (healthcare and similar) are fictional and carry a disclaimer. The
  partner prompt forbids real professional advice.
* **Prompt injection:** learner text is treated as conversation content, not instructions.
