# BLE protocol (v1)

Single source of truth for `firmware/`, `android/` and `tools/`.
Change this file only with a matching `CHANGELOG.md` entry.

Transport is BLE GATT. The 8-byte drop payload is also the debug TCP frame
(Android emulator has no BLE). Play audio on packet arrival; use the device
timestamp only for inter-drop timing and intensity, never to delay a note.

## Device identity

| Field | Value |
|---|---|
| GAP name | `RainSong` |
| Appearance | 0x0000 (unknown) |
| Pairing / bonding | None. Open GATT, unencrypted. Demo only. |
| Address type | Public or random static; either is fine |

## Advertising

Legacy (31-byte) advertising. Do not depend on extended advertising.

**Advertising packet**

| AD type | Contents |
|---|---|
| 0x01 Flags | LE General Discoverable, BR/EDR not supported |
| 0x07 Complete list of 128-bit service UUIDs | RainSong service UUID below |

**Scan response**

| AD type | Contents |
|---|---|
| 0x09 Complete local name | `RainSong` |

The phone filters scans on the service UUID. Do not put the name in the
advertising packet (it does not fit alongside a 128-bit UUID).

Advertising interval: 100–200 ms while connectable. After disconnect, resume
advertising immediately.

## GATT

All multi-byte fields little-endian. Payloads fit in the default ATT MTU of 23
(20-byte value). Do not require MTU negotiation.

| | UUID |
|---|---|
| RainSong service | `1e968972-3c13-443c-ad79-a41645d56e75` |
| Drop Event characteristic | `81a2f805-d83b-4214-b5d1-895642ccaa8f` |
| Status characteristic | `5c471e86-7a6e-45b2-9434-bc440c0a8dfe` |

Standard CCCD UUID `0x2902` on both characteristics.

### Drop Event — notify only

One notification = one debounced strike. Not a raw ADC stream. Firmware
coalesces piezo ringing into a single event (typical debounce 20–40 ms).

Properties: Notify. No Read, no Write.

Enable via CCCD (`0x0001`). Do not notify until the client has enabled it.

**Value: 8 bytes**

| Offset | Size | Type | Field | Meaning |
|---|---|---|---|---|
| 0 | 4 | `u32` | `t_us` | Microseconds since boot. Wraps every ~71.58 min. |
| 4 | 2 | `u16` | `strength` | Peak energy, 0 = lightest accepted hit, 65535 = clip. |
| 6 | 1 | `u8` | `seq` | Increment by 1 per event, wraps 255 → 0. Detects lost notifies. |
| 7 | 1 | `u8` | `flags` | See below. |

```c
typedef struct __attribute__((packed)) {
    uint32_t t_us;
    uint16_t strength;
    uint8_t  seq;
    uint8_t  flags;
} rainsong_drop_t; /* 8 bytes */
```

**`flags`**

| Bit | Name | Meaning |
|---|---|---|
| 0 | `CLIP` | ADC / analog front-end saturated this hit |
| 1 | `WOKE` | First event after waking from sleep |
| 2–7 | | Reserved, write 0, ignore on read |

**Timing rules (phone)**

- Play the note as soon as the notify arrives. Do not wait on `t_us`.
- Inter-drop interval = unsigned difference of consecutive `t_us` values
  (wrap-safe: `(uint32_t)(t1 - t0)`).
- If `seq` skips, a notify was lost; do not invent a note, just count the gap
  in the intensity window.
- Arrival-time jitter is BLE connection-interval noise; prefer `t_us` for
  pattern / AI summaries.

**Rate limit**

Maximum 50 notifies/s. If strikes arrive faster, coalesce in the debounce
window: keep the earliest `t_us`, the max `strength`, OR the flags, and a
single `seq` increment.

### Status — read + notify

Properties: Read, Notify. Enable notify via CCCD.

Notify once immediately after CCCD is enabled, then at least every 10 s, and
on a change of `flags` (e.g. low-battery set/cleared).

**Value: 6 bytes**

| Offset | Size | Type | Field | Meaning |
|---|---|---|---|---|
| 0 | 2 | `u16` | `battery_mv` | Pack voltage in millivolts. 0 = unknown. |
| 2 | 1 | `u8` | `fw_major` | Firmware major version |
| 3 | 1 | `u8` | `fw_minor` | Firmware minor version |
| 4 | 1 | `u8` | `flags` | See below |
| 5 | 1 | `u8` | `reserved` | Write 0, ignore on read |

```c
typedef struct __attribute__((packed)) {
    uint16_t battery_mv;
    uint8_t  fw_major;
    uint8_t  fw_minor;
    uint8_t  flags;
    uint8_t  reserved;
} rainsong_status_t; /* 6 bytes */
```

**`flags`**

| Bit | Name | Meaning |
|---|---|---|
| 0 | `SENSING` | Piezo path live (not sleeping through hits) |
| 1 | `LOW_BATT` | Firmware considers the pack too low (~3.3 V) |
| 2–7 | | Reserved, write 0, ignore on read |

Firmware v1 ships as `fw_major=1`, `fw_minor=0`.

## Connection parameters

Peripheral requests, and the app should ask Android for high priority
(`BluetoothGatt.requestConnectionPriority(CONNECTION_PRIORITY_HIGH)`):

| Param | Value |
|---|---|
| Interval min | 7.5 ms |
| Interval max | 15 ms |
| Slave latency | 0 |
| Supervision timeout | 4000 ms |

Do not skip connection events (latency 0). Drop-to-sound budget is 30–80 ms;
a 30–50 ms interval would eat it. PHY: 1M. Do not require 2M.

On disconnect: app reconnects automatically while the user is on the play
screen; firmware returns to advertising.

## Debug TCP (emulator / no BLE)

Same drop payload, no GATT. Optional; production path is BLE.

| | |
|---|---|
| Bind | `0.0.0.0:17321` (harness) |
| Emulator host | `10.0.2.2:17321` |
| Framing | Concatenated 8-byte `rainsong_drop_t` frames, no header |
| Connect | Treat as sensor connected (no Status frames) |
| Close | Treat as sensor disconnected |

Do not mix Status into this stream. Status is BLE-only.

## Out of scope for v1

- Write/control characteristic (threshold lives in firmware).
- Bonding, passkeys, or encrypted links.
- Batching multiple drops in one notify.
- Firmware updates over BLE.
