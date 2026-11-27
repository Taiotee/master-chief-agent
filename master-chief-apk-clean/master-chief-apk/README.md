# Master Chief — Independent Android APK

A standalone, market-competitive Android agent for **Tayo's OPPO A74 5G
(CPH2197, Snapdragon 480 5G, 6 GB RAM / 128 GB, Android 13, released 2021)**.

This is a **separate project** from the Master Chief hub built in Muse
(a web hub that lives inside Muse). This app is independent: its own APK,
its own brain, its own sign-ins. It does **not** depend on Muse to run.

## What it is

A full phone agent, Halo / UNSC / Spartan themed throughout:

- **Standby chat box always available** — in-app Chat tab on every screen,
  plus a floating Halo bubble over other apps (needs "Display over other
  apps" permission).
- **Military-style voice and responses** — brief, direct, "Sir",
  "Affirmative", "Copy that". Text-to-speech at lower pitch / slower rate
  for the serious tone. The exact voice depends on the voices installed
  on the phone; the app cannot invent a new voice.
- **Glowing Halo styling** — dark armour black, glowing Halo green/cyan,
  Spartan gold, monospace HUD type. The Halo ring is drawn in Compose.
- **Switchable AI logic, auto-switching** — see [docs/MODEL_SWITCHER.md](docs/MODEL_SWITCHER.md).
  Local model for simple/offline tasks, Gemini API (free tier first) for
  heavy thinking. The app decides per task and shows you which brain it used.
- **System-wide tasks** — open/control apps, read/filter notifications,
  send texts, make calls, set alarms/reminders, change settings by time
  or mood, manage files/photos, play music, check calendar, plan the day,
  morning weather briefing for Lagos, learn likes/style/music/mood and
  pre-empt the user. **Chief learns who Tayo is by itself** — from app
  usage, calendar patterns, notification patterns and cleanup decisions
  on the phone (`agent/Learner.kt`). The Profile screen is only for
  corrections; Tayo does not have to fill it in manually.
- **Intelligent cleanup** — see [Cleanup](#cleanup--intelligent-phone-assessment)
  below. Periodic assessment, scoring, permission levels, and a report.

## Cleanup — intelligent phone assessment

Tayo's old phone cleaner "never seems to get done" because it only runs
when he remembers to open it. Master Chief fixes that by assessing the
phone **on a schedule**, and by being honest about what is safe.

### When it runs
- **Weekly** — every 7 days, in the background (WorkManager), even if the
  app is not open.
- **When storage gets low** — free space below 2 GB **or** below 10%.
  The daily briefing worker checks this and triggers an assessment.
- **Manually** — "Clean my photos" / Cleanup screen > Run assessment now.

### What it looks at
The focus is **apps and other files, not just photos** — that is where
the clutter actually lives on Tayo's phone:
- Unused apps (not opened 180+ days, user-installed only, never system apps)
- Cache/junk files and old downloads (untouched 90+ days)
- Large videos, screenshots, blurry photos, and duplicate photos
  (exact hash) — photos are covered, but they are not the whole job.

### Scoring — safe vs needs a human
Every item gets a 0–100 safety score (`CleanupEngine.score()`):

| Tier | Score | Meaning |
|---|---|---|
| **SAFE** | 80–100 | Only junk: exact duplicates, own app cache. The only tier that can ever auto-clean. |
| **REVIEW** | 50–79 | Plausibly clutter, plausibly wanted: screenshots, blurry photos, old downloads, large videos, unused apps. Always needs a tap. |
| **KEEP** | 0–49 | Do not suggest at all: recent files, favourites, anything the user kept before. |

Protections: files under 7 days old, favourites, the oldest copy of a
duplicate, and anything Tayo previously kept are forced to KEEP.
Learning never promotes a REVIEW item to SAFE on its own.

### Learning what Tayo keeps vs deletes
Every Confirm / Keep decision is recorded per category
(`JsonStore.recordCleanupDecision`). After 3+ decisions on a kind, its
score shifts by up to ±15 points — e.g. if Tayo always keeps screenshots,
screenshots stop being suggested. Learning never overrides a protection.

### Permission — set by Tayo, in Settings > Cleanup
- **ASK_EVERYTHING** — every item waits for a tap, even safe cache.
- **AUTO_SAFE_ONLY** (default) — safe junk (exact duplicates + own cache)
  may be auto-cleaned on schedule. Everything else always asks first.
- **PAUSED** — no automatic assessment or cleaning. Manual sweeps only.

The rule is enforced in **one place** (`CleanupExecutor`): only SAFE
items can be deleted without a tap, and only when the permission allows.
There is no code path that deletes an important photo or file silently.

### Report — what was cleaned, how much space was freed
After every assessment the Clean screen shows a report, e.g.:

> "Assessment complete, sir. 14 clutter items found. Auto-cleaned 3 safe
> items, freeing 212 MB. 11 items (1.4 GB) await your decision in the
> review list — nothing else was touched."

Reports are kept (last 20) in the app's private storage.

### Honest limits on cleanup
- Android does **not** let one app clear another app's cache silently.
  Chief clears its own cache and opens the system storage screen for the rest.
- Unused apps are **never** auto-uninstalled. They are REVIEW items only.
- Deleting photos on Android 11+ may show the system's own delete
  prompt. That is Android protecting the user; Chief does not bypass it.
- Blurry detection is a cheap on-device heuristic (thumbnail variance),
  not a ML model — it only ever suggests REVIEW, never SAFE.

## Architecture

```
app/src/main/java/com/masterchief/agent/
├── MainActivity.kt            # Navigation: Command / Chat / Briefing / Cleanup / Profile / Settings
├── ChiefViewModel.kt          # Chat, commands, cleanup review, missions, profile
├── brain/                     # The switchable brain
│   ├── BrainMode.kt           # AUTO / LOCAL_ONLY / GEMINI_FREE / GEMINI_CHEAP + classifier
│   ├── BrainRouter.kt         # Auto-switch rules (per task, offline, no key)
│   ├── GeminiClient.kt        # Gemini REST API (free tier, AI Studio key)
│   ├── LocalBrain.kt          # On-device rule brain (simple tasks, offline)
│   └── LocalLlm.kt            # Optional small local LLM — stub with a real integration point
├── agent/                     # Doing things on the phone
│   ├── CommandParser.kt       # "open WhatsApp", "set alarm 07:30", "switch to local"…
│   ├── PhoneController.kt     # Real Android intents: apps, dialler, SMS draft, Wi-Fi, brightness
│   ├── PhotoSweeper.kt        # Duplicate detection (MediaStore, hash of first 64 KB)
│   └── cleanup/               # *** Intelligent cleanup module ***
│       ├── CleanupModels.kt   # ClutterKind, SafetyTier, CleanupPermission, reports, learning
│       ├── CleanupEngine.kt   # Scoring, assessment across all categories, reports
│       └── StorageMonitor.kt  # Low-storage trigger (< 2 GB or < 10% free)
├── data/                      # On-device only: profile, missions, settings, JSON store
├── voice/                     # Military voice (TTS, phrasing templates)
├── workers/                   # Morning briefing, weekly cleanup, single deletion path
├── services/                  # Notification listener, standby bubble
└── receivers/                 # Boot: re-schedule briefing + weekly cleanup
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full flow.

## How to build the APK

Prerequisites: **Android Studio** (recommended) or command line:
JDK 17, Android SDK with `platforms;android-34` and `build-tools;34.0.0`.

### Android Studio (easiest)
1. Open this folder in Android Studio (Hedgehog or newer).
2. Let it download the Gradle wrapper and SDK components.
3. Run **Build > Build App Bundle(s) / APK(s) > Build APK(s)**,
   or press Run to install the debug APK on the OPPO A74 5G over USB.
4. The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

### Command line
```bash
# 1. Point Gradle at your SDK (Android Studio does this automatically):
cp local.properties.example local.properties
#    edit local.properties: sdk.dir=/path/to/Android/Sdk

# 2. Build the debug APK:
./scripts/build-apk.sh
#    — or directly: ./gradlew assembleDebug

# 3. Install on the phone (USB debugging on, phone connected):
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release APK (for Tayo's own use, sideloaded — not the Play Store)
```bash
./gradlew assembleRelease   # needs a signing key; see docs/BUILD.md
```
A release build is minified with R8. Do **not** publish this to the Play
Store without a privacy policy, a signed key you control, and a review of
the permissions (Play restricts notification-listener and
`SYSTEM_ALERT_WINDOW` apps).

## First run — setup checklist

1. **Brain / Gemini key** — Settings > Brain. Get a free key from
   [Google AI Studio](https://aistudio.google.com/) (Get API key), paste
   it in. Without a key, Chief still works on the local brain for simple
   tasks; heavy tasks will say so instead of failing silently.
2. **Brain mode** — Settings > Brain mode. Leave on **AUTO** (recommended):
   local for simple/offline, Gemini for heavy. Or pick Local only / Gemini.
3. **Notifications** — Settings > Enable notification access, then turn
   on Master Chief in system settings. Needed to filter notifications.
4. **Photos** — grant Photos/Media permission when the first cleanup runs.
5. **Standby bubble** — Settings > enable "Display over other apps", then
   turn the bubble on. The Halo floats over other apps; tap it to talk.
6. **Cleanup permission** — Cleanup screen. Default is **AUTO_SAFE_ONLY**
   (auto-clean safe junk, ask before anything else). Change it to
   ASK_EVERYTHING if you want to approve every single item.

## What is and is not possible on Android

See [docs/LIMITS.md](docs/LIMITS.md) — the short version:

- **Can do:** open apps, dial, draft texts, alarms, settings that Android
  exposes, read notifications (with the user's grant), read photos/files
  (with permission), clean duplicates/cache with confirmation.
- **Cannot do directly:** control WhatsApp / Instagram / banking apps from
  inside — there is no API for it. Chief opens them and the user acts
  inside the app. It cannot send a text, pay, or delete silently without
  the user confirming, and it cannot bypass Android's own delete/permission
  prompts.
- **Local AI on this phone:** the OPPO A74 5G (6 GB RAM, Snapdragon 480)
  can only run a **small** local model (~0.5–2B parameters, quantised).
  Heavy thinking goes to the Gemini API. That is why the auto-switcher
  exists — see [docs/MODEL_SWITCHER.md](docs/MODEL_SWITCHER.md).

## Security

- The Gemini API key is stored in the app's private DataStore, never in
  source code or git. A production build should move it to
  EncryptedSharedPreferences.
- Profile, missions, chat and cleanup learning stay on the phone.
  Only the text of a chat message is sent to Google when the Gemini brain
  answers it — never photos, contacts, or files.
- Nothing is deleted, sent, or paid for without either (a) the SAFE +
  AUTO_SAFE_ONLY rule, or (b) an explicit user confirmation.

## Status

- v1.0.0 — complete project, all modules above implemented in Kotlin.
  Debug APK: build it with Android Studio or `./scripts/build-apk.sh`.
  **This APK is not installed on Tayo's phone yet and is not on the
  Play Store.** Installing it is a separate step (USB / sideload the
  `app-debug.apk`). A build was attempted in the Linux VM used to write
  this project and could **not** complete there — see
  [BUILD_ATTEMPT.md](BUILD_ATTEMPT.md) for the exact cause (Gradle daemon
  killed by the OS; ~500 MB free RAM) and the Android Studio path.
- Next milestones: Gmail API (OAuth) for on-device mail reading,
  **Yahoo Mail via IMAP** (Tayo has a Yahoo Mail account and wants it
  prioritised too — needs a Yahoo app password, explicit opt-in, and
  the same read-only-then-ask rules as Gmail),
  Open-Meteo weather in the briefing worker, MediaPipe local LLM wiring,
  usage-stats for unused-app detection.
