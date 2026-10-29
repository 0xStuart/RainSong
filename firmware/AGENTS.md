# Firmware agent (ESP32)
Owns `firmware/`. Board: Seeed XIAO ESP32C3, connected to Stuart's PC over USB (only one process may hold the serial port).
Goals: detect piezo drop hits, timestamp, estimate strength, BLE notify with minimal latency, low power (light/deep sleep, optional LM393 wake-on-drop).
Implement the BLE contract exactly as in `../docs/protocol.md`; if it must change, update that file and `../docs/CHANGELOG.md`.
Record board/battery/plate dimensions in `../docs/mechanical.md` for the CAD agent.
