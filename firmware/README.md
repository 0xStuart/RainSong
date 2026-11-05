# RainSong firmware

Seeed XIAO ESP32C3. Pin map: [`pins.md`](pins.md). BLE contract:
[`../docs/protocol.md`](../docs/protocol.md).

Advertises as **RainSong**. Drop Event notify + Status read/notify.
Tap the disc (or Boot) after the phone enables CCCD.

## Hardware list

| Part | Link |
|---|---|
| Piezo percussion / LM393 shock sensor module | [AliExpress](https://www.aliexpress.com/item/1005005142488820.html?spm=a2g0o.order_list.order_list_main.11.1ab01802xk8klq) |
| Seeed XIAO ESP32C3 (WiFi + BLE 5.0) | [AliExpress](https://www.aliexpress.com/item/1005012304656509.html?spm=a2g0o.order_list.order_list_main.17.1ab01802xk8klq) |
| 103040 3.7 V 1200 mAh LiPo | [AliExpress](https://www.aliexpress.com/item/1005009860250953.html?spm=a2g0o.order_list.order_list_main.23.1ab01802xk8klq) |

```bash
cd firmware
~/.platformio/penv/bin/pio run -t upload
# then, only if nothing else holds the port:
~/.platformio/penv/bin/pio device monitor
```

Port is pinned in `platformio.ini` to this XIAO (`44:B1:76:5F:65:F4` on `/dev/ttyACM1`).
Do not use `/dev/ttyACM0` (Pico). Only one process may hold the serial port.
