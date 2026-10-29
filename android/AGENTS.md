# Android agent
Owns `android/`. Receive BLE drop events per `../docs/protocol.md`; play a note/percussive hit immediately (target 30–80ms drop-to-sound, use a low-latency audio path).
Track rolling intensity/pattern; request backing loops from the AI layer (`../ai/`) and crossfade them in; fall back to a cached default loop if slow.
Keys never hard-coded (use `local.properties`). Use `../tools/` harness for testing before hardware.
