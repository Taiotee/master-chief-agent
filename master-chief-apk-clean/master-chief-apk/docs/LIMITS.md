# What is and is not possible on Android

This file exists so nobody — including future-me — over-promises.

## Can do (real Android APIs, implemented in this project)

- Open installed apps by name (`PhoneController.openApp`).
- Open the dialler with a number, draft an SMS for the user to send.
- Set alarms via the system Clock app.
- Change brightness (with Write Settings grant), volume, Wi-Fi (with
  the Android 10+ panel fallback).
- Read photos/videos via MediaStore, hash them, and delete duplicates
  **with the user's confirmation** (system delete prompt on Android 11+).
- Read notifications **after** the user enables the listener in settings;
  dismiss / reply to them from the app.
- Schedule background work (WorkManager) for the morning briefing and
  the weekly cleanup assessment.
- Run a small local model (0.5–2B params) on-device; call the Gemini API.

## Cannot do directly — and this app does not pretend otherwise

- **Control WhatsApp, Instagram, Tinder, banking apps from inside.**
  There is no public API for it. Chief opens the app via intent and the
  user acts inside it. Any app claiming to "control WhatsApp" is using
  accessibility tricks or faking it.
- **Send a text, make a payment, or delete a file silently.** Android
  requires the user (or a system prompt) for these. Chief drafts and
  asks; it never sends/deletes silently. The only exception is the
  SAFE + AUTO_SAFE_ONLY cleanup rule, which the user explicitly chose
  and which covers only exact duplicates and the app's own cache.
- **Clear another app's cache silently.** Android forbids it. Chief
  clears its own cache and opens the system storage screen for the rest.
- **Toggle Wi-Fi silently on Android 10+.** The system may show a panel
  instead. The code handles that and says so.
- **Run a large local LLM on the OPPO A74 5G.** 6 GB RAM is not enough
  for a 7B model. See `docs/MODEL_SWITCHER.md`.
- **Read Gmail on-device without OAuth.** The Gmail API needs a Google
  Cloud OAuth client and user sign-in. That is a planned milestone, not
  in v1. The Muse hub already reads Gmail server-side; this APK's v1
  briefing uses calendar + weather + storage.
- **Listen / act while the phone is locked, or replace the lock screen.**
  The standby bubble works over other apps, not over the lock screen.
- **Bypass Android's own prompts** (delete confirmation, permission
  grants, notification-listener enable). Those prompts are the user's
  protection, not a bug to work around.
