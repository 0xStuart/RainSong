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
| Electronics box | 20 × 113.3 × 90, wall 3 | Closed back against the split, open toward the far end. Fully below the frame (1 mm under the underside). Hangs outside the base half. Inner ~17 × 107.3 × 84. |
| USB-C hole | 12 × 18 slot | Through the **floor**, 20 mm in from the left inner wall, against the closed back (board glued to the base). Generous vs a ~9 × 3.2 mm plug. |
| Base hole | Ø6 | Through the closed back, top centre. |
| Box lid | 106.5 × 83.2 × 8 | Slots into the open end. Solid 3 mm cover + 5 mm hollow plug (3 mm rim), 0.4 mm clearance. |
| Box wings | 3 × 24 mm, flush with the back | 3 mm thick (same as the walls), **24 mm** wide (9 mm rail + 15 mm flare), flush with the closed back. Four Ø3.2 mm (M3) holes through the wings along X into Ø3.8 mm × 6 mm inserts in the **split face** of the supports. No holes in the long-side rails. |

First cut is a through-frame (no rebate). Printed in two length pieces. Not final until Stuart says so.
