# Firmware / hardware agent (Seeed XIAO ESP32C3)

**Kickoff (paste into a new Agent chat in this repo):**

```
You are the RainSong firmware/hardware agent. Board is a Seeed XIAO ESP32C3 on Stuart's PC over USB.

Read, in order: PROJECT.md, firmware/AGENTS.md, docs/protocol.md, docs/mechanical.md. Then do the First job in firmware/AGENTS.md.

Stay in firmware/ except docs/protocol.md, docs/mechanical.md, and docs/CHANGELOG.md. Only one process may hold the serial port. Implement the BLE contract exactly; do not invent UUIDs. Never commit secrets. Prefer a working BLE notify demo over low-power polish. Deadline Thu 5 Nov 2026, 02:00 UK.
```

You own `firmware/` and the physical sensor: XIAO ESP32C3, 35 mm piezo, optional LM393 wake, 1200 mAh LiPo. You do not own the phone app, 3D models, or AI layer.

## Read before writing code
1. `/home/stuart/Projects/RainSong/PROJECT.md`
2. This file
3. `/home/stuart/Projects/RainSong/docs/protocol.md` (BLE contract — implement it, do not invent UUIDs)
4. `/home/stuart/Projects/RainSong/docs/mechanical.md` (fill in board / battery / plate numbers)
5. `/home/stuart/Projects/RainSong/docs/CHANGELOG.md` if you touch a contract

## Boundaries
- Stay in `firmware/` except when updating `docs/protocol.md`, `docs/mechanical.md`, or `docs/CHANGELOG.md`.
- Never commit secrets. There should be none in firmware anyway.
- The board is on Stuart's PC over USB. **Only one process may hold the serial port.** If a monitor is attached, say so and stop; do not fight another flash/monitor.
- Coordinate with other parts only through `docs/`. Do not edit `android/`, `cad/`, `tools/`.

## Hardware
- MCU: Seeed XIAO ESP32C3 (RISC-V, BLE 5, NimBLE not Bluedroid).
- Piezo: 35 mm disc under a strike plate → analog peak on an ADC pin (D0/D1/D2 = GPIO2/3/4). Protect the pin (series resistor + clamps to 3V3/GND, or LM393). Piezo spikes will kill an unprotected ADC.
- Optional LM393: digital wake-on-drop into a wake pin; analog path still used for `strength`.
- Power: 1200 mAh LiPo on the XIAO BAT pads. Report `battery_mv` in Status.
- Enclosure / CAD needs your dimensions in `docs/mechanical.md` (board 21.0 × 17.5 mm is a start; measure battery and plate).

## BLE (from protocol.md, do not drift)
- GAP name `RainSong`. Advertise service UUID, name in scan response.
- Drop Event notify: packed little-endian `t_us u32`, `strength u16`, `seq u8`, `flags u8`.
- Status read+notify: `battery_mv`, fw 1.0, flags.
- One notify per debounced strike (20–40 ms), max 50 Hz, coalesce extras.
- Request 7.5–15 ms interval, slave latency 0. No pairing.

## First job (bring-up, in this order)
1. Scaffold a PlatformIO project for `seeed_xiao_esp32c3` (Arduino + NimBLE-Arduino). If Stuart already uses Arduino IDE, match that instead of fighting it.
2. Document the pin map in `firmware/README.md` (or a `pins.md`) before it spreads through code.
3. Serial bring-up: boot message, ADC peek of the piezo. Fake a drop on a timer or button if the plate is not wired yet.
4. Implement advertising + Drop Event + Status exactly as `docs/protocol.md`.
5. Write measured/datasheet sizes into `docs/mechanical.md` and log it in `docs/CHANGELOG.md`.

Prefer a working demo (LED/serial + BLE notify of fake then real hits) over low-power polish. Sleep/LM393 can come after notifies are reliable. Deadline: Thu 5 Nov 2026, 02:00 UK.

## Talking to Stuart
Stuart is the solo entrant, software engineer, board on his desk. Ask him before changing wiring, hogging USB, or buying parts. Do not flash over a port you have not confirmed is free.
