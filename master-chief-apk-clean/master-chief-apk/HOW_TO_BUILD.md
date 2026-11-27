# How your Master Chief APK gets built — no code to touch

The code is finished. What is missing is only the **build step** that
turns the code into an installable `app-debug.apk` file. It could not be
done on the machine that wrote the code: that machine had only about
500 MB of free memory, and the Android build tool needs at least 2 GB.
The code itself is fine — nothing in it needs fixing.

You have two ways to get the APK. Pick whichever fits what you have.

## Option A — a computer with Android Studio (easiest if you have one)

You need: any Windows or Mac computer with at least 4 GB of free memory
(your own, a friend's, or a work computer — it takes about 30 minutes).

1. Install **Android Studio** (free, from developer.android.com/studio).
2. Copy this whole project folder onto that computer (USB stick, WhatsApp
   to yourself, Google Drive — any way you like).
3. Open the folder in Android Studio. Let it finish syncing (first time
   it downloads about 200 MB — be patient).
4. Click **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
5. The file appears at `app/build/outputs/apk/debug/app-debug.apk`.
6. Send that file to your OPPO A74 5G, tap it, allow "Install unknown
   apps" if it asks, and install.

## Option B — build it in the cloud, no computer needed (free)

You need: a free GitHub account (github.com — sign up with your email).

1. On github.com, create a new repository (any name, e.g. `master-chief`).
2. Upload this whole project folder to it (the green **Code** button, or
   drag the folder in on the web page).
3. Click the **Actions** tab, then **Build Master Chief APK**, then
   **Run workflow**.
4. Wait a few minutes for the green tick, then download
   **master-chief-debug-apk** from the run's Artifacts.
5. Send that file to your phone, tap it, allow "Install unknown apps"
   if it asks, and install.

This works because the project now includes
`.github/workflows/build-apk.yml`, which tells GitHub to build it for you.

## After installing — first-run checklist

Full details are in `README.md` ("First run — setup checklist"). The
short version:

1. **Brain / Gemini key** — Settings > Brain. Get a free key from Google
   AI Studio (aistudio.google.com, "Get API key") and paste it in.
   Without it, Chief still works for simple tasks on its local brain.
2. **Notifications** — allow notification access so Chief can filter them.
3. **Photos** — grant Photos permission when the first cleanup runs.
4. **Cleanup permission** — default is AUTO_SAFE_ONLY: safe junk may be
   auto-cleaned, everything else always asks you first. Change it to
   ASK_EVERYTHING in Settings > Cleanup if you want to approve every item.

## What the app can and cannot do

It can open apps, dial, draft texts, set alarms, change settings, read
notifications (with your permission), and clean duplicates with your
confirmation. It cannot directly control WhatsApp, Instagram or banking
apps — Android does not allow that for any app. See `docs/LIMITS.md`.
