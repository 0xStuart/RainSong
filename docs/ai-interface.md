# App ↔ AI interface

The Android app does **not** hold a Meta API key. Muse Spark and MusicGen run in
`ai/` (Docker or `python server.py`). The phone sends a rain summary and plays
the WAV that comes back.

## Locate the server

1. If the user typed an **AI server URL** in the app, use that (cloud / VPS).
2. Else browse mDNS **`_rainsong-ai._tcp`** and scan the Wi-Fi `/24` for port **8765**.
3. Loopback `127.0.0.1:8765` / `10.0.2.2:8765` is a last resort (USB reverse /
   emulator). If that dies (cable unplugged), the app searches the LAN again.

Base URL examples: `http://192.168.1.12:8765`, `https://rainsong.example.com`.

## Endpoints

### `GET /health`

JSON: `{ "ok": true, "service": "rainsong-ai", "spark": <bool>, "rate": 32000 }`.

### `POST /compose`

Body:

```json
{
  "dropsPerSecond": 1.2,
  "meanStrength": 18000,
  "lostHits": 0,
  "duration": 8
}
```

`duration` is seconds, clamped 4–12. The server calls Muse Spark (if
`META_API_KEY` is set) then MusicGen. Response is **`audio/wav`** (32 kHz mono
16-bit) plus header `X-RainSong-Arrangement` (JSON, ASCII-escaped) with
`title`, `mood`, `rootHz`, `brightness`, `density`, `prompt`, `source`
(`muse-spark` or `local`).

If Spark is missing or fails, MusicGen still runs from a local intensity
fallback prompt. Live drop hits on the phone never wait for this call.

### `POST /generate`

`{ "prompt": "...", "duration": 8 }` → WAV. Used if the client already has a
prompt. Prefer `/compose`.

## Env (server)

| Variable | Purpose |
|---|---|
| `META_API_KEY` | Meta Model API (Spark). Optional; local prompt if unset. |
| `RAINSONG_PORT` | Listen port, default `8765`. |
| `MUSICGEN_MODEL` | Default `facebook/musicgen-small`. |
| `RAINSONG_MDNS` | `1` (default) advertise on LAN; `0` for cloud. |
| `RAINSONG_ADVERTISE_IP` | Host IP to publish in mDNS when not using host networking. |
