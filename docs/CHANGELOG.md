# Contract changelog

## 2026-11-01

- `ai-interface.md`: phone talks to a LAN/cloud AI container (Spark +
  MusicGen). Meta API key lives in server env, not the app. Discovery is
  mDNS `_rainsong-ai._tcp` and Wi-Fi scan first (loopback only as USB
  fallback; rediscover if that dies). URL override for hosted boxes.
  `POST /compose` returns a WAV loop plus arrangement header.
  Reason: Stuart — one Docker service, easy phone setup, hostable later.

## 2026-10-31

- `mechanical.md`: XIAO 21.0 × 17.8 mm (Seeed wiki), LM393 module 20.5 ×
  20.5 mm, typical Ø35 mm piezo, 1200 mAh LiPo outline still unmeasured.
  Reason: firmware bring-up; CAD needs the sensor stack, not just the
  solar frame.

## 2026-10-30

- `mechanical.md`: far piece body is 80.5 mm (frame outer length 300.7 mm);
  base print stays 220 mm. Reason: Stuart.

## 2026-10-29

- `mechanical.md`: electronics box (20 × 113.3 × 90 mm) hangs off the
  split with 3 mm wings, USB-C floor slot, Ø6 back hole, and a slot-in
  lid (3 mm cover + hollow plug). Reason: Stuart.
- `mechanical.md`: joint flare is +15 mm inward on each long side, 20 mm
  from the break. Reason: Stuart.
- `mechanical.md`: solar panel frame sits at 40° with 20 mm floor clearance
  at the base; long-side wedges rise as x·tan(40°) up to the split
  (~224 mm drop / ~239 mm side at the break). Reason: Stuart.
- `mechanical.md`: solar panel frame tongue-and-groove is full 15 mm
  height, through the rail (visible). Reason: Stuart asked for full-height
  T&G and is fine with a visible joint / print supports. Not final.
- `protocol.md`: initial BLE v1 contract. RainSong service + Drop Event
  (8-byte notify: `t_us`, `strength`, `seq`, `flags`) + Status (battery /
  firmware). Advertising, 7.5–15 ms connection interval, 50 Hz cap. Optional
  TCP `:17321` debug transport for the emulator (same 8-byte drop frames).
  Reason: unblock firmware, Android and the test harness in parallel.
