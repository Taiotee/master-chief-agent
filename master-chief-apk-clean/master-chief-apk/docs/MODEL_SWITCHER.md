# Model switcher — how the brain is chosen

## The three brains

| Brain | What it is | Cost | When it is used |
|---|---|---|---|
| **Local** | On-device rule brain (`LocalBrain`) + optional small LLM (`LocalLlm`) | Free, offline | Simple tasks, or when offline / no key |
| **Gemini Free** | Gemini 2.0 Flash via Google AI Studio free tier | Free, rate-limited | Heavy thinking, default API |
| **Gemini Cheap** | Same model, pay-as-you-go billing on | A few pence per 1M tokens | Heavy thinking when the user picks it |

Default model: `gemini-2.0-flash`. Change it in Settings (the key and
model are stored in DataStore, never in code).

## Modes (Settings > Brain mode, or "Chief, switch to local / API")

- **AUTO** (default) — Chief decides per task. See rules below.
- **LOCAL_ONLY** — never calls the network. Works offline.
- **GEMINI_FREE** / **GEMINI_CHEAP** — always use that API tier.

## Auto-switch rules (BrainRouter, in order)

1. User override wins.
2. No network (or no API key) → Local. Never fail a simple task for want
   of a connection.
3. SIMPLE task (time, weather cache, alarm, open app, settings, clean,
   battery) → Local. Fast, free, private.
4. MEDIUM task → local LLM if one is installed, else Gemini Free.
5. HEAVY task (plan, write, explain, compare, decide, email draft,
   mood analysis) → Gemini.
6. If Gemini fails (quota / network / key) → Local + an honest message
   saying the API was unavailable.

Task complexity is classified by keywords + length
(`TaskClassifier`), not by guessing about the user.

## Local AI on the OPPO A74 5G — the honest picture

- Phone: Snapdragon 480 5G, **6 GB RAM**, 128 GB storage, released 2021.
- A 7B-parameter model needs roughly 4–8 GB of RAM. It will not run
  well here — slow, hot, battery-hungry, and likely killed by Android.
- Realistic local models: **~0.5B–2B parameters, quantised** (e.g.
  Qwen2.5-0.5B, Gemma-2B), roughly 0.5–1.5 GB on disk, via MediaPipe
  LLM Inference (`.task` format).
- To enable it: download a small `.task` model into the app's files dir
  as `local_model.task`, uncomment the `tasks-genai` dependency in
  `app/build.gradle.kts`, and wire `LlmInference` in `brain/LocalLlm.kt`
  (the skeleton is commented there).
- Until then, the local brain is the rule brain: instant and offline for
  simple orders, and it says so. It is **not** presented as a full LLM.

## Getting a free Gemini key

1. Go to [Google AI Studio](https://aistudio.google.com/) and sign in
   with Google.
2. **Get API key** → Create API key.
3. Paste it in the app: Settings > Gemini API key > Save key.
4. Free tier limits apply (requests per minute/day). If you hit them,
   Chief falls back to Local and tells you. The cheap pay-as-you-go tier
   is the same key with billing enabled in Google Cloud — the app does
   not need a different key, only the model choice in Settings.
