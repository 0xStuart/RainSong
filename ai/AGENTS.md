# AI layer agent
Owns `ai/`. Every 30–60s turn the rain pattern summary into a music prompt using Muse Spark via the Meta Model API, then generate a loop with MusicGen (cloud GPU, e.g. Modal).
Must still work with Meta Model API alone if MusicGen is ruled ineligible. Keys from env vars only. Document the app↔AI interface in `../docs/ai-interface.md`.
