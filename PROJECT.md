# RainSong

A battery-powered outdoor rain sensor that streams each raindrop strike over BLE to an Android phone, which plays music in time with the drops and adapts to rain intensity using Meta AI models.

Entry for the **Meta Global AI Developer Hackathon** (Cerebral Valley), Indie track, theme "Push the Frontier". Solo entrant: Stuart Johnson (UK).
- Judging (Indie): creativity, ambition, does it work.
- Submissions close **Thu 5 Nov 2026, 02:00 UK** (Wed 4 Nov, 9pm ET). Judging 4–8 Nov, finals 9 Nov.
- Submission format UNCONFIRMED — assume repo + demo video + description. Check the event page / Discord.

## Data flow
Piezo → ESP32 (detect, timestamp, strength) → BLE notify → Android (instant note per drop + rolling intensity window) → `ai/` container (Muse Spark + MusicGen; `META_API_KEY` in env) → Android (crossfade WAV under live notes). Phone finds the container on LAN or uses a pasted URL.

## Parts and owners
| Folder | Part | Notes |
|---|---|---|
| `firmware/` | ESP32 firmware | Seeed XIAO ESP32C3, 35mm piezo under strike plate, optional LM393 wake, 1200mAh LiPo. Low latency, low power. Board is on Stuart's PC over USB. |
| `android/` | Android app | BLE receiver, 30–80ms drop-to-sound, intensity tracking, AI loop crossfade, cached fallback loop. |
| `ai/` | AI layer | Docker/Python: Muse Spark then MusicGen. Key in env. mDNS `_rainsong-ai._tcp`. See `docs/ai-interface.md`. |
| `cad/` | 3D-printed parts | Strike plate mount / housing. Stuart directs the design. |
| `tools/` | PC test harness | Fakes BLE drop events at varying intensity so the app can be built before hardware. |
| `presentation/` | Video presentation | Demo video for submission. |

## Shared contracts
- `docs/protocol.md` — BLE v1: service/characteristic UUIDs, 8-byte drop event, status, advertising, optional TCP debug port. **Single source of truth** for firmware, app and test harness.
- `docs/mechanical.md` — PCB/board/battery/plate dimensions the enclosure must fit.
- `docs/ai-interface.md` — app ↔ AI layer (not written yet; owned by `ai/`).

## Rules for all agents
- Read this file and `docs/` before working. Stay inside your own folder unless changing a shared contract.
- If you change a contract in `docs/`, note it in `docs/CHANGELOG.md` with date and reason.
- Never commit API keys or secrets. Use env vars / `local.properties` / `.env` (gitignored).
- Part agents: new Agent chat in this repo, paste the kickoff from that folder's `AGENTS.md` (firmware = hardware, cad = 3D print, android = app). This lead chat owns `PROJECT.md`.
