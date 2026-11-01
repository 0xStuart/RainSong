# RainSong firmware

Seeed XIAO ESP32C3. Pin map: [`pins.md`](pins.md). BLE contract:
[`../docs/protocol.md`](../docs/protocol.md).

Advertises as **RainSong**. Drop Event notify + Status read/notify.
Tap the disc (or Boot) after the phone enables CCCD.

```bash
cd firmware
~/.platformio/penv/bin/pio run -t upload
# then, only if nothing else holds the port:
~/.platformio/penv/bin/pio device monitor
```

Port is pinned in `platformio.ini` to this XIAO (`44:B1:76:5F:65:F4` on `/dev/ttyACM1`).
Do not use `/dev/ttyACM0` (Pico). Only one process may hold the serial port.
