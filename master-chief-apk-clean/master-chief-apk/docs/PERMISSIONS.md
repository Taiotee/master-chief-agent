# Permissions

Every permission is requested **at runtime, with an explanation, only
when the feature that needs it is turned on**. Declaring a permission in
the manifest does not grant it. The app works with zero permissions
granted — chat, briefing (local data), local brain and Gemini API all
work; phone features degrade gracefully without theirs.

| Permission | Needed for | How it is granted |
|---|---|---|
| Internet | Gemini API, weather | Install-time (normal) |
| Photos / Media (`READ_MEDIA_IMAGES/VIDEO`) | Duplicate / blurry / screenshot assessment | Runtime prompt on first cleanup |
| Music / Audio (`READ_MEDIA_AUDIO`) | Learn music taste from audio files saved on the phone (MediaStore: title, artist, album) | Runtime prompt on first music learn |
| Usage stats (`PACKAGE_USAGE_STATS`) | Unused-app detection **and** learning which music app is used most | **System settings**: Settings > Apps > Special access > Usage access. Without it, unused apps are simply not listed — never guessed. |
| Notifications (`POST_NOTIFICATIONS`) | Briefing + cleanup report notifications | Runtime prompt (Android 13+) |
| Notification listener | Read / filter / reply to notifications | **System settings only**: Settings > Notifications > Device & app notifications > Master Chief. No app can enable this itself. |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Floating standby Halo bubble | **System settings only** |
| Call phone / Send SMS / Read SMS / Contacts | Call / text on command | Runtime prompt, per feature |
| Calendar read/write | Plan the day, reminders | Runtime prompt |
| Location (fine/coarse) | Lagos weather, location routines | Runtime prompt |
| Wi-Fi state / change | Wi-Fi on/off by time of day | Runtime prompt; Android 10+ may show a panel instead of toggling silently |
| Write Settings | Brightness, DND | **System settings**: "Modify system settings" |
| Record audio | Voice input | Runtime prompt |
| Exact alarms / Boot completed | 06:30 briefing, weekly cleanup | System / install-time |

## What the user controls

- **Cleanup permission** (Cleanup screen): ASK_EVERYTHING /
  AUTO_SAFE_ONLY (default) / PAUSED. See README § Cleanup.
- **Brain mode** (Settings): AUTO / LOCAL_ONLY / GEMINI_FREE / GEMINI_CHEAP.
- **Bubble** on/off, **voice** on/off, **notification filter** on/off.
- Every grant can be revoked in Android Settings; the app checks at
  runtime and degrades instead of crashing.
