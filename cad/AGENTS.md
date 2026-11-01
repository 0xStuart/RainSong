# CAD / 3D-print agent

**Kickoff (paste into a new Agent chat in this repo):**

```
You are the RainSong CAD / 3D-print agent. Stuart directs the design.

Read, in order: PROJECT.md, cad/AGENTS.md, docs/mechanical.md. Then do the First job in cad/AGENTS.md.

Stay in cad/ except docs/mechanical.md and docs/CHANGELOG.md. Fit the CE-TEK IP65 enclosure, 35 mm piezo, XIAO ESP32C3, and 1200 mAh LiPo. Keep source + STL/3MF; never commit G-code. Propose 2–3 mount options and wait for Stuart before treating a model as final. Deadline Thu 5 Nov 2026, 02:00 UK.
```

You own `cad/`: the strike-plate mount and any printed parts that hold the sensor in the CE-TEK IP65 enclosure. Stuart directs the design — propose, then wait for a yes before treating a model as final.

## Read before modelling
1. `/home/stuart/Projects/RainSong/PROJECT.md`
2. This file
3. `/home/stuart/Projects/RainSong/docs/mechanical.md` (fit numbers; if empty, look up datasheets and draft them there)
4. `/home/stuart/Projects/RainSong/docs/CHANGELOG.md` if you change mechanical.md

## Boundaries
- Stay in `cad/` except `docs/mechanical.md` and `docs/CHANGELOG.md`.
- Do not edit firmware, the Android app, or protocol.md.
- Keep source (OpenSCAD, CadQuery, STEP, Fusion export) **and** printable STL/3MF in `cad/`.
- Do **not** commit sliced G-code (`*.gcode` is gitignored).
- Coordinate only through `docs/`.

## What it has to do
- Outdoor rain sensor. Water must hit a strike plate that couples energy into a **35 mm piezo** and then drain; electronics stay dry in a **CE-TEK IP65** box.
- Fit: Seeed XIAO ESP32C3 (~21.0 × 17.5 mm), piezo Ø35 mm, 1200 mAh LiPo, USB access for programming if Stuart wants it without opening the box.
- Plate should register hits, not rattle, not short the piezo leads, not trap water against the LiPo.
- Prefer print-in-place or a handful of screws over a 12-part assembly. Demo by 5 Nov 2026.

## First job
1. Confirm with Stuart: enclosure model/SKU, printer (volume, nozzle, material — PETG/ABS for outdoor, not PLA if it will sit in rain/sun), and whether USB must be reachable from outside.
2. If `docs/mechanical.md` is still TBD, fill datasheet dimensions (XIAO, piezo, a typical 1200 mAh pack) and log the change. Mark anything not measured as "datasheet, not measured".
3. Sketch options (2–3) for plate + piezo clamp + board/battery sled. Do not jump to a single fancy model.
4. After Stuart picks, produce parametric source + STL.

Ask Stuart before ordering hardware or starting a long print. Deadline: Thu 5 Nov 2026, 02:00 UK; working fit beats pretty fillets.
