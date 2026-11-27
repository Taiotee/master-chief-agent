# Architecture

## Request flow

```
User speaks / types in standby chat box
        │
        ▼
ChiefViewModel.sendCommand()
        │
        ├─ CommandParser.parse() ──► on-device order?
        │     open app / call / text / alarm / Wi-Fi / brightness /
        │     clean photos / briefing / switch brain / play music
        │        └─► PhoneController / CleanupEngine / Workers
        │
        └─ Unknown / conversational ──► BrainRouter.route()
              ├─ AUTO: classify task (SIMPLE / MEDIUM / HEAVY)
              │    ├─ SIMPLE  → LocalBrain (offline, free, instant)
              │    ├─ MEDIUM  → LocalLlm if installed, else Gemini Free
              │    └─ HEAVY   → Gemini (free tier, or cheap tier if set)
              ├─ LOCAL_ONLY / GEMINI_* → user override wins
              └─ offline / no key / API error → LocalBrain + honest message
```

The chosen engine and the reason are shown in the chat
(`lastBrainUsed`), so the switch is visible.

## Cleanup flow

```
Weekly (WorkManager) ──┐
Storage low (<2 GB) ───┤
Manual "clean photos" ─┘
        │
        ▼
CleanupAssessmentWorker.doWork()
        │
        ▼
CleanupEngine.assess(trigger, learning)
  duplicates (PhotoSweeper, hash) · screenshots · large videos ·
  old downloads · cache · blurry (thumbnail variance) · unused apps*
        │  scored 0–100, tiered SAFE / REVIEW / KEEP
        ▼
applyPermission(assessment, user permission level)
  ASK_EVERYTHING → all to review list
  AUTO_SAFE_ONLY → SAFE auto-cleaned, rest to review list
  PAUSED         → all to review list, nothing auto
        │
        ▼
CleanupExecutor.deleteIfSafe() / deleteConfirmed()
  — the ONLY deletion path in the app —
        │
        ▼
CleanupReport saved + shown: items assessed, auto-cleaned,
bytes freed, items awaiting review
        │
        ▼
User taps Confirm / Keep in the review list
  → decision recorded → CleanupLearning shifts future scores
```

\* Unused-app detection needs `PACKAGE_USAGE_STATS`, granted in Settings.
Without it the engine lists nothing for that category rather than guessing.

## Data flow

- All state is on-device: `JsonStore` (JSON files in the app's private
  files dir) for profile, missions, protocols, cleanup items, learning,
  reports and chat; `SettingsRepository` (DataStore) for the brain mode,
  Gemini key, voice and permission settings.
- Nothing leaves the phone except:
  - a chat message's text, sent to Google's Gemini API when the API brain
    answers it;
  - a weather request to Open-Meteo (no key, no personal data) — planned,
    see MorningBriefingWorker.
- No analytics, no tracking, no ads.

## Background work

| Worker | Schedule | Job |
|---|---|---|
| `MorningBriefingWorker` | Daily, ~06:30 Lagos | Briefing; storage-low check → triggers cleanup |
| `CleanupAssessmentWorker` | Weekly (7 days) + on storage-low | Full clutter assessment + report |
| `BootReceiver` | On reboot | Re-schedules both workers |

## Why no Room / Retrofit?

Deliberate, for this phone and this build:
- **No Room:** the data is small (profile, missions, cleanup lists).
  A JSON file store has no migrations, no kapt/KSP build cost, and is
  easy to inspect. If the app grows to thousands of records, move to Room.
- **No Retrofit / Google AI SDK:** the Gemini call is one POST of JSON.
  OkHttp + kotlinx-serialization is enough and keeps the APK smaller.
- **No local LLM dependency by default:** MediaPipe `tasks-genai` is
  large and only useful once a ~1 GB model file is on the phone. It is
  commented in `app/build.gradle.kts` with wiring instructions in
  `brain/LocalLlm.kt`.
