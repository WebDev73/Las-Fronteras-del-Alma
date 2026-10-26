# Setup

## Requirements

* JDK 17+ (21 recommended). Gradle comes from the wrapper.
* For the app: Android SDK (API 35) with `ANDROID_HOME` set, or a `local.properties` with
  `sdk.dir=…`. Without an SDK, the `:app` module is skipped automatically. Force-skip it with
  `-Pfronteras.android=false`.

## Server

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `ANTHROPIC_API_KEY` | for AI | — | Claude API key. Without it the server runs and reports AI as unavailable |
| `FRONTERAS_DEV_TOKENS` | yes | — | Comma-separated `token:userId[:plan]` (token ≥16 chars, plan `free` or `premium`) |
| `FRONTERAS_MODEL` | no | `claude-opus-5-5` | Model id |
| `FRONTERAS_TURN_EFFORT` | no | `low` | Effort for conversation turns |
| `FRONTERAS_ANALYSIS_EFFORT` | no | `high` | Effort for session analysis |
| `FRONTERAS_DATA_DIR` | no | `server/data` | Sync storage directory |
| `FRONTERAS_RPM` | no | `30` | Per-user requests per minute on AI routes |
| `PORT` | no | `8080` | Listen port |

```bash
./gradlew :server:installDist
ANTHROPIC_API_KEY=sk-ant-... \
FRONTERAS_DEV_TOKENS=change-me-0123456789:ana \
server/build/install/server/bin/server
curl -H "Authorization: Bearer change-me-0123456789" localhost:8080/v1/capabilities
```

Or use Docker: `docker build -f server/Dockerfile -t fronteras-server .`

**Production notes.** Terminate TLS in front of the server; the app refuses cleartext except
for `10.0.2.2` and `localhost`. Replace `DevTokenVerifier` with a real identity provider
(OIDC/Firebase) by implementing `TokenVerifier`. Quotas and sync storage are single-instance
(in memory and on file); move them to a shared store before you scale out.

## App

```bash
./gradlew :app:assembleDebug -Pfronteras.serverUrl=http://10.0.2.2:8080   # emulator → host
```

In the app: **Settings → Server**, enter the address and access token, then **Save and test**.
The status line shows whether AI conversation is actually available.

## Tests

```bash
./gradlew :core:test :server:test            # JVM
./gradlew :app:testDebugUnitTest :app:lintDebug
```
