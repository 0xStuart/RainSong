# Contract changelog

## 2026-10-30

- `mechanical.md`: frame is 144.5 mm wide (panel glues on top); base print
  ≤ 220 mm; base cutout is Y 49–80 mm from the far edge. Reason: Stuart.

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
