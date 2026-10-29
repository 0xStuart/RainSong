# Mechanical dimensions

TBD: board, battery, strike plate, enclosure.

## Solar panel (Stuart, 2026-10-29)

| | mm | Notes |
|---|---|---|
| Panel | 300 × 144.5 | Length × width. Thickness not given. |
| Frame rails | 9 / 9 / 18 / 30 | Long sides 9 mm; far end 18 mm; **base end 30 mm**. |
| Frame height | 15 | All round. |
| Frame outer | 348 × 162.5 × 15 | 300+18+30 by 144.5+9+9. Source: `cad/solar_panel_frame.scad`. |
| Split | 70% from base | Adjustable `split_frac` (or `split_x_abs`). Default 0.70 × 348 = **243.6 mm** from the base, in the long rails. Kerf 0.2 mm. |
| Joint | tongue and groove | Full height, through the rail (visible top and bottom). Tongue **12 × 3 × 15 mm** on the far piece; matching groove in the base piece (0.3 mm Y clearance, 0.4 mm extra depth). One on each 9 mm rail. |
| Base cutout | 29.3 × 9.9 | Centred on the 30 mm base end, from the bottom up, through the rail. |
| Tilt | 40° | Frame sits on the floor at 40°. Long-side drop = `floor_clearance + x * tan(tilt)`. |
| Floor clearance | 20 mm | Side drop at the base (x = 0). |
| Side drop at break | 20 + 243.5 × tan(tilt) | Follows `tilt_deg` in the OpenSCAD file (currently 20°). |
| Joint flare | +15 mm each side, 20 mm from the break | Extra width **inward** (into the opening) on both long sides at the split. |

First cut is a through-frame (no rebate). Printed in two length pieces. Not final until Stuart says so.
