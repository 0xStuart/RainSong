# Android agent

**Kickoff (paste into a new Agent chat in this repo):**

```
You are the RainSong Android agent.

Read, in order: PROJECT.md, android/AGENTS.md, docs/protocol.md. Then do the First job in android/AGENTS.md.

Stay in android/ except docs/protocol.md / docs/CHANGELOG.md if the BLE contract is actually wrong. Play a note the instant a drop packet arrives (30–80 ms); do not delay on t_us. Keys only in local.properties. Emulator has no BLE — use TCP 10.0.2.2:17321. Do not block on the AI layer; a local hit sound is the first demo. Deadline Thu 5 Nov 2026, 02:00 UK.
```

You own `android/`: the phone app that hears raindrops over BLE (or the debug TCP harness) and plays music in time with them.

## Read before writing code
1. `/home/stuart/Projects/RainSong/PROJECT.md`
2. This file
3. `/home/stuart/Projects/RainSong/docs/protocol.md` (BLE + TCP debug contract)
4. `/home/stuart/Projects/RainSong/docs/ai-interface.md` when it exists (owned by `ai/`; do not block on it)
5. `/home/stuart/Projects/RainSong/docs/CHANGELOG.md` if you change a contract

## Boundaries
- Stay in `android/` except `docs/protocol.md` / `docs/CHANGELOG.md` if the BLE contract is wrong (prefer asking the lead/firmware chat before changing it).
- Do not implement Muse Spark / MusicGen here. Call whatever `ai/` documents; until then, a local cached loop (or silence under the live notes) is enough.
- Keys never in git. Use `local.properties` (already gitignored). No Meta API key in the app until `ai/` says how.
- Use `tools/` as a BLE/TCP drop fuzzer when hardware is not ready. Do not rewrite the harness; consume `docs/protocol.md`.
- Verify UI in a device or emulator before declaring work done. Emulator has **no BLE** — use TCP `10.0.2.2:17321`.

## Product constraints
- Drop-to-sound **30–80 ms**. Play the note on packet **arrival**. Use `t_us` only for intensity / pattern, never to delay audio.
- Low-latency path: AAudio / Oboe (or equivalent), not a high-latency MediaPlayer for the per-drop hit.
- Scan filter on service UUID `1e968972-3c13-443c-ad79-a41645d56e75`. GAP name `RainSong`.
- After connect: request `CONNECTION_PRIORITY_HIGH`, enable Drop Event + Status CCCDs.
- Auto-reconnect on the play screen. No pairing.
- Rolling intensity window from wrap-safe `t_us` deltas and `strength`. `seq` gaps = lost hits, do not invent notes.
- Every 30–60 s the AI layer will return a backing loop; crossfade it. If AI is slow or missing, keep a cached default loop.

## First job (app skeleton)
1. Kotlin + Compose app, minSdk 26 or higher, package under Stuart's name / `rainsong`.
2. Permissions: BLUETOOTH_SCAN / CONNECT, location if required by the target SDK, notifications not required for v1.
3. Screen: scan → connect → show `seq`, `strength`, battery, a tap-to-hear test.
4. On each drop frame (BLE notify **or** 8-byte TCP frame): trigger a short percussive/note hit immediately.
5. TCP debug client to `10.0.2.2:17321` (emulator) / `127.0.0.1:17321` (device with adb reverse) so work is not blocked on firmware.
6. Leave a clear stub for "request backing loop" once `docs/ai-interface.md` exists.

Do not wait for hardware or MusicGen to have a demo of tap/drop → sound. Deadline: Thu 5 Nov 2026, 02:00 UK.
