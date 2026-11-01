# Pin map — Seeed XIAO ESP32C3

Sensor: AliExpress piezo percussion / LM393 module
(`1005005142488820`, 20.5 × 20.5 mm board + brass piezo disc).

Power the module from **3V3 only**. Its listing says 5 V; the ESP32-C3 GPIOs are
not 5 V tolerant. LM393 is happy at 3.3 V. Confirm silkscreen on the board
you received (`AO`/`DO`/`GND`/`VCC` or `A0`/`D0`/`GND`/`+`).

| Module | XIAO | GPIO | Role |
|---|---|---|---|
| `VCC` / `+` | `3V3` | — | Module supply. Not `5V`. |
| `GND` | `GND` | — | Common ground |
| `AO` | `D1` | 3 | Analog peak → `strength`. ADC1_CH3 (safest analog). |
| `DO` | `D2` | 4 | Digital hit / sleep wake. Active **high** on this clone (verify with a tap). |

Do **not** put `DO` on `D0` (GPIO2) or `D9` (GPIO9): those are strapping pins.
If `DO` sits low at reset on GPIO2, the C3 can fail to boot.

On-board Boot button is GPIO9 / `D9` (active low). Fine as a fake-drop input
after boot; do not hold it at reset.

## Protection

`AO` on these boards is often the piezo through a 1 MΩ load and a **5.1 V**
zener. That is not a 3.3 V clamp. Add, at the XIAO pin:

- 10 kΩ series into `D1`
- Schottky clamps to `3V3` and `GND` (e.g. BAT54S), or a 3.3 V zener

`DO` is a logic output and does not need the same clamps if VCC is 3.3 V.

## Unused (later)

| XIAO | GPIO | Notes |
|---|---|---|
| `D0` | 2 | Spare ADC1. Strapping — keep floating or high at reset. |
| `D3` | 5 | Spare wake GPIO (ADC2, skip for analog). |
| BAT+ / BAT− | — | 1200 mAh LiPo. No onboard VBAT ADC; `battery_mv` stays 0 until a divider is added. |

No user LED on this XIAO. Charge LED only.

## Intensity

Yes. One notify per debounced strike; `strength` is the peak `AO` in that
window, scaled 0–65535. `CLIP` if the ADC rails. Digital `DO` is on/off only
— it is the wake / gate, not the intensity.

## Sleep vs wind (after BLE notify is reliable)

1. Mechanical first: isolate the strike plate from the housing so wind on the
   box does not rattle the disc. Drops are impulses; wind is rumble.
2. Hardware: turn the pot **left** (less sensitive) until a finger tap trips
   `DO` and a fan / breath does not. The seller notes it can trigger on
   blowing if the pot is maxed.
3. Firmware: GPIO wake on `D2` (C3 can wake from D0–D3). On wake, sample `AO`.
   Keep the event only if it is a short high-crest spike, not a long low
   rumble. Stay awake for a rain session (e.g. 30–60 s after last drop);
   isolated wind wakes go back to sleep without a notify.
4. Deep-sleep `DO` pulses can be only a few milliseconds. If wakes are missed,
   stretch `DO` with an RC, or use light sleep + GPIO interrupt instead.
