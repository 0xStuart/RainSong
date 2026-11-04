# RainSong

A solar-powered outdoor rain sensor that turns each raindrop into music on your smartphone.

A piezo under a strike plate hears a drop. A Seeed XIAO ESP32C3 sends the hit over BLE. An Android phone plays a wind-chime note immediately (about 30–80 ms), then scores the rain with **Muse Spark** and a **MusicGen** loop that follows intensity.

Entry for the Meta Global AI Developer Hackathon (Cerebral Valley), Indie track.

**Demo video:** [RainSong on YouTube](https://www.youtube.com/watch?v=43-K7Legre0)

## How it works

```
piezo → ESP32 (debounce, timestamp, strength)
      → BLE notify
      → Android (chime on arrival + rolling intensity)
      → ai/ container (Spark prompt, then MusicGen WAV)
      → Android (crossfade under the live notes)
```

The phone never holds the Meta API key. It finds the AI box on Wi-Fi (`_rainsong-ai._tcp` / port **8765**) or you paste a URL override. If Spark or MusicGen is slow, drops still make sound.

## Repo

| Folder | What |
|---|---|
| `firmware/` | ESP32-C3 BLE firmware (PlatformIO) |
| `android/` | Compose app: scan, sky, chimes, volumes |
| `ai/` | Docker: Spark + MusicGen on `:8765` |
| `cad/` | Printable solar frame and electronics box |
| `docs/` | BLE contract, mechanical sizes, AI HTTP API |

Contracts: [`docs/protocol.md`](docs/protocol.md), [`docs/ai-interface.md`](docs/ai-interface.md), [`docs/mechanical.md`](docs/mechanical.md).

## Run the AI server

On the same Wi-Fi as the phone:

```bash
cd ai
cp .env.example .env   # put META_API_KEY in .env; never commit it
docker compose up --build
```

`GET http://<this-pc>:8765/health` should return `"service":"rainsong-ai"`. First MusicGen run downloads `facebook/musicgen-small` (a few GB) into a Docker volume.

Hosted box: drop `network_mode: host`, publish `8765`, set `RAINSONG_MDNS=0`, and paste the public URL into the app’s **AI server URL (override)** field.

## Android app

Open `android/` in Android Studio (or `./gradlew assembleDebug`). minSdk 26. Install on a phone with Bluetooth and Wi-Fi.

1. Leave the URL blank on LAN, or paste a host URL.
2. **Scan for RainSong** and connect.
3. Drops play as chimes. Tap the cloud to fake a drop. Rain and music have separate volume sliders.

The app keeps listening in a foreground service after you leave the screen.

## Firmware

Seeed XIAO ESP32C3. Pins: [`firmware/pins.md`](firmware/pins.md).

```bash
cd firmware
pio run -t upload
```

Advertises as **RainSong**. One BLE notify per debounced strike. Optional TCP `:17321` exists only as an emulator harness (same 8-byte drop frame).

## Suggested improvements

- The wakeup sensitivity is adjusted by a 10k pot and doesn’t allow for much fine adjustment. Replace with a multi-turn pot, or better still make it software-controllable.
- iOS support.
