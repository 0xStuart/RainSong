// RainSong solar-panel frame (not final until Stuart says so)
// Panel 300 x 144.5 mm glues on top of the frame (not dropped into the
// opening). Frame is as wide as the panel; 9 mm long rails sit under it.
// Rails: 9 mm on both long sides, 18 mm far end, 30 mm base end, 15 mm high.
//
// Split along the length so it fits a printer bed. The two halves join with
// a full-height tongue-and-groove on each long rail: groove in the base
// piece, tongue on the far piece (so the long print does not grow).
// Joint is visible top and bottom.
//
// Long sides grow into a wedge from the base to the break so the frame sits
// on the floor at tilt_deg. Drop at the base is floor_clearance; at the
// break it is floor_clearance + base_face * tan(tilt_deg).
//
// Origin: outer corner of the base end, bottom of the 15 mm frame.
// +X along panel length (base → far end), +Y across the width, +Z up.
//
// part:
//   "assembled"  both halves + electronics box, sitting on the floor
//   "base"       piece from the base end to the split (grooves + side wedges)
//   "far"        piece from the split to the far end (tongues)
//   "box"        electronics box, closed back against the split, outside
//                (assembled preview: slid +X by explode, like the far half)
//   "lid"        slot-in lid for the box (top down, plug up)
//
// Override from the CLI, e.g.:
//   openscad -D 'part="base"' -D split_frac=0.63 -o frame_base.stl solar_panel_frame.scad
//   openscad -D 'part="box"' -o electronics_box.stl solar_panel_frame.scad
//   openscad -D 'part="lid"' -o electronics_box_lid.stl solar_panel_frame.scad

part = "assembled";

panel_length = 300.0;
panel_width  = 144.5;
frame_height = 15.0;

rail_long = 9.0;
rail_end  = 18.0;
rail_base = 30.0;

panel_clearance = 0.0;

kerf = 0.2;             // gap between the two printed faces, mm

// Break location, measured from the base end as a fraction of outer length.
// Default keeps the base print ≤ 220 mm on the 348 mm frame (~0.632).
// Keep the split in the long rails (not in the 30 mm base or 18 mm end).
max_base_print = 220.0; // mm, longest the base piece may be
split_frac  = (max_base_print + kerf / 2) / (rail_base + panel_length + rail_end);
split_x_abs = -1;       // set >= 0 to place the break at an absolute X instead

// Tongue (far piece) / groove (base piece). Full frame height, through
// the rail, visible from the top and bottom. Not a hidden joint.
tongue_len       = 12.0;  // mm, along X
tongue_w         = 3.0;   // mm, across the rail (Y)
tongue_h         = frame_height;
tongue_clearance = 0.30;  // mm, each side of the groove (Y only)
tongue_extra     = 0.4;   // mm, groove deeper than the tongue

// Notch in the base end, from the top down, through the 30 mm rail.
// Y is measured from the far edge (y = outer_width), looking from the
// lowest (base) end.
base_cutout_y0 = 49.0;  // mm, start along Y from the far edge
base_cutout_y1 = 80.0;  // mm, end along Y from the far edge
base_cutout_h  = 9.9;   // mm, from the top of the frame down

// Sit on the floor at this angle. Side drop is perpendicular to the panel.
tilt_deg         = 20;
floor_clearance  = 20.0;  // mm, side drop at the base (x = 0)

explode = 20;           // mm, assembled preview: slide the far half +X to show the joint

// Extra width at the break for support. Inward on each long side
// (into the opening), running flare_len back from the split face.
flare_extra = 15.0;     // mm, each side
flare_len   = 20.0;     // mm, from the break

// Electronics box: fully below the frame, hanging off the split (outside
// the base half). Closed back against the split face; open toward the far
// end. 3 mm wings flush with that back, 24 mm wide (rail + flare), screw
// into the split face of the supports. No holes in the long-side rails.
box_depth      = 20.0;  // mm, along X, closed back at the split
box_z          = 90.0;  // mm, along Z, hanging down from the rim
box_wall       = 3.0;   // mm, walls and wings
box_under      = 1.0;   // mm, gap from the frame underside down to the box top
box_y_clear    = 0.6;   // mm, each side, aligned with the flare inner faces
screw_d        = 3.2;   // mm, M3 clearance through the wings
insert_d       = 3.9;   // mm, heat-set insert in the support legs
insert_depth   = 6.0;   // mm, into the flare from the split face
screw_edge     = 8.0;   // mm, hole centre in from the wing rim (Z)
screw_from_box = 11.0;  // mm, hole centre out from the flare inner face (Y)

// USB-C through the floor, back-left corner. Board is glued to the closed
// back, so the hole sits against the base. Generous vs a ~9 × 3.2 mm plug.
usb_slot_x     = 10.0;  // mm, along box depth
usb_slot_y     = 20.0;  // mm, along box width
usb_from_left  = 20.0;  // mm, extra inset from the left inner wall
base_hole_d    = 6.0;   // mm, through the closed back, top centre

// Lid slots into the open end of the box. Top face sits on the bed.
lid_clear      = 0.4;   // mm, each side, so it slides in
lid_cap        = 3.0;   // mm, outer plate (same as the walls)
lid_plug       = 5.0;   // mm, inner plug depth
lid_plug_inset = 1.2;   // mm, extra shrink of the plug for a lead-in
lid_rim        = 3.0;   // mm, frame left after hollowing the middle
lid_gap        = 20.0;  // mm, assembled preview: sit this far beside the frame

eps = 0.02;
$fn = 32;

outer_length = rail_base + panel_length + rail_end;
outer_width  = panel_width;  // panel sits on top; no extra width outside the rails

function split_x() =
    split_x_abs >= 0 ? split_x_abs : split_frac * outer_length;

function base_face() = split_x() - kerf / 2;
function far_face()  = split_x() + kerf / 2;

function groove_w() = tongue_w + 2 * tongue_clearance;
function groove_d() = tongue_len + tongue_extra;

function rail_ys() = [rail_long / 2, outer_width - rail_long / 2];

// Extra side depth under the 15 mm frame, from base to the break.
function side_drop(x) = floor_clearance + x * tan(tilt_deg);
function drop_at_break() = side_drop(base_face());

function opening_width() = outer_width - 2 * rail_long;
function box_outer_y() = opening_width() - 2 * flare_extra - 2 * box_y_clear;
function box_z_top() = -box_under;
function box_z_bot() = box_z_top() - box_z;
function box_x0() = base_face();
function box_y0() = rail_long + flare_extra + box_y_clear;
function wing_width() = rail_long + flare_extra;
function wing_span() = box_y_clear + wing_width();
function wing_x0() = box_x0();

function screw_ys() = [
    rail_long + flare_extra - screw_from_box,
    outer_width - rail_long - flare_extra + screw_from_box
];
function screw_zs() = [
    box_z_bot() + screw_edge,
    box_z_top() - screw_edge
];

function lid_y() = box_outer_y() - 2 * box_wall - 2 * lid_clear;
function lid_z() = box_z - 2 * box_wall - 2 * lid_clear;
function lid_cut() = lid_plug_inset + lid_rim;

assert(
    split_x() >= rail_base + groove_d() + 2,
    "split is too close to the base end — lower split_frac or tongue_len"
);
assert(
    split_x() <= outer_length - rail_end - 2,
    "split is too close to the far end — raise split_frac"
);

echo(outer = [outer_length, outer_width, frame_height]);
echo(split_x = split_x(), split_frac = split_frac);
echo(base_len = base_face(), far_len = outer_length - far_face() + tongue_len);
echo(tongue = [tongue_len, tongue_w, tongue_h]);
echo(groove = [groove_d(), groove_w(), frame_height]);
echo(base_cutout = [base_cutout_y1 - base_cutout_y0, base_cutout_h],
     base_cutout_y = [base_cutout_y0, base_cutout_y1]);
echo(tilt_deg = tilt_deg, floor_clearance = floor_clearance);
echo(side_drop_base = floor_clearance, side_drop_break = drop_at_break());
echo(side_height_break = drop_at_break() + frame_height);
echo(flare_extra = flare_extra, flare_len = flare_len);
echo(box_outer = [box_depth, box_outer_y(), box_z]);
echo(box_z_range = [box_z_bot(), box_z_top()]);
echo(box_inner = [
    box_depth - box_wall,
    box_outer_y() - 2 * box_wall,
    box_z - 2 * box_wall
]);
echo(usb_slot = [usb_slot_x, usb_slot_y]);
echo(base_hole_d = base_hole_d);
echo(lid = [lid_y(), lid_z(), lid_cap + lid_plug]);
echo(lid_opening = [
    lid_y() - 2 * lid_cut(),
    lid_z() - 2 * lid_cut()
]);

module base_cutout() {
    translate([
        -eps,
        outer_width - base_cutout_y1,
        frame_height - base_cutout_h
    ])
        cube([
            rail_base + 2 * eps,
            base_cutout_y1 - base_cutout_y0,
            base_cutout_h + eps
        ]);
}

module frame_solid() {
    difference() {
        cube([outer_length, outer_width, frame_height]);
        translate([
            rail_base - panel_clearance / 2,
            rail_long - panel_clearance / 2,
            -eps
        ])
            cube([
                panel_length + panel_clearance,
                opening_width() + panel_clearance,
                frame_height + 2 * eps
            ]);
        base_cutout();
    }
}

module grooves() {
    for (y = rail_ys())
        translate([
            base_face() - groove_d(),
            y - groove_w() / 2,
            -eps
        ])
            cube([groove_d() + eps, groove_w(), frame_height + 2 * eps]);
}

module tongues() {
    for (y = rail_ys())
        translate([
            far_face() - tongue_len,
            y - tongue_w / 2,
            0
        ])
            cube([tongue_len + 1, tongue_w, tongue_h]);
}

module side_skirts() {
    h0 = floor_clearance;
    h1 = drop_at_break();
    for (y0 = [0, outer_width - rail_long])
        hull() {
            translate([0, y0, -h0])
                cube([eps, rail_long, h0 + frame_height]);
            translate([base_face() - eps, y0, -h1])
                cube([eps, rail_long, h1 + frame_height]);
        }
}

module flare_base() {
    x0 = base_face() - flare_len;
    x1 = base_face();
    h0 = side_drop(x0);
    h1 = drop_at_break();
    for (y0 = [rail_long, outer_width - rail_long - flare_extra])
        hull() {
            translate([x0, y0, -h0])
                cube([eps, flare_extra, h0 + frame_height]);
            translate([x1 - eps, y0, -h1])
                cube([eps, flare_extra, h1 + frame_height]);
        }
}

module flare_far() {
    for (y0 = [rail_long, outer_width - rail_long - flare_extra])
        translate([far_face(), y0, 0])
            cube([flare_len, flare_extra, frame_height]);
}

module electronics_box() {
    difference() {
        union() {
            translate([box_x0(), box_y0(), box_z_bot()])
                cube([box_depth, box_outer_y(), box_z]);
            translate([
                wing_x0(),
                box_y0() - wing_span(),
                box_z_bot()
            ])
                cube([box_wall, wing_span(), box_z]);
            translate([
                wing_x0(),
                box_y0() + box_outer_y(),
                box_z_bot()
            ])
                cube([box_wall, wing_span(), box_z]);
        }
        // Open toward the far end (+X). Closed back (and wings) at the split.
        translate([
            box_x0() + box_wall,
            box_y0() + box_wall,
            box_z_bot() + box_wall
        ])
            cube([
                box_depth - box_wall + eps,
                box_outer_y() - 2 * box_wall,
                box_z - 2 * box_wall
            ]);
        for (z = screw_zs())
            for (y = screw_ys())
                translate([wing_x0() + box_wall + eps, y, z])
                    rotate([0, -90, 0])
                        cylinder(d = screw_d, h = box_wall + 2 * eps);
        usb_hole();
        base_hole();
    }
}

module usb_hole() {
    r = usb_slot_x / 2;
    span = usb_slot_y - usb_slot_x;
    translate([
        box_x0() + box_wall + r,
        box_y0() + box_wall + usb_from_left + r,
        box_z_bot() - eps
    ])
        hull() {
            cylinder(r = r, h = box_wall + 2 * eps);
            translate([0, span, 0])
                cylinder(r = r, h = box_wall + 2 * eps);
        }
}

module base_hole() {
    translate([
        box_x0() - eps,
        box_y0() + box_outer_y() / 2,
        box_z_top() - box_wall - base_hole_d / 2
    ])
        rotate([0, 90, 0])
            cylinder(d = base_hole_d, h = box_wall + 2 * eps);
}

module lid() {
    // Top at z = 0 facing down. Solid 3 mm cover; hollow plug extends +Z.
    render()
        difference() {
            union() {
                cube([lid_y(), lid_z(), lid_cap]);
                translate([lid_plug_inset, lid_plug_inset, lid_cap - 1])
                    cube([
                        lid_y() - 2 * lid_plug_inset,
                        lid_z() - 2 * lid_plug_inset,
                        lid_plug + 1
                    ]);
            }
            translate([lid_cut(), lid_cut(), lid_cap])
                cube([
                    lid_y() - 2 * lid_cut(),
                    lid_z() - 2 * lid_cut(),
                    lid_plug + eps
                ]);
        }
}

module flare_insert_holes() {
    for (z = screw_zs())
        for (y = screw_ys())
            translate([base_face() + eps, y, z])
                rotate([0, -90, 0])
                    cylinder(d = insert_d, h = insert_depth + 2 * eps);
}

module base_half() {
    difference() {
        union() {
            frame_solid();
            side_skirts();
            flare_base();
        }
            translate([base_face(), -1, -drop_at_break() - 1])
            cube([
                outer_length,
                outer_width + 2,
                drop_at_break() + frame_height + 2
            ]);
        grooves();
        flare_insert_holes();
    }
}

module far_half() {
    union() {
        difference() {
            frame_solid();
            translate([-eps, -eps, -eps])
                cube([
                    far_face(),
                    outer_width + 2 * eps,
                    frame_height + 2 * eps
                ]);
        }
        tongues();
        flare_far();
    }
}

// Rotate so the wedge bottoms sit on z = 0 and the panel is at tilt_deg.
module on_floor() {
    translate([0, 0, floor_clearance * cos(tilt_deg)])
        rotate([0, -tilt_deg, 0])
            children();
}

module assembled() {
    on_floor() {
        color("SteelBlue")
            base_half();
        color("Goldenrod")
            translate([explode, 0, 0])
                far_half();
        color("Olive")
            translate([explode, 0, 0])
                electronics_box();
    }
    color("Tan")
        translate([0, -lid_gap - lid_z(), 0])
            lid();
}

if (part == "base")
    on_floor()
        base_half();
else if (part == "far")
    translate([-(far_face() - tongue_len), 0, 0])
        far_half();
else if (part == "box")
    // Open (far-facing) side on the bed.
    translate([0, wing_span(), box_depth])
        rotate([0, 90, 0])
            translate([-box_x0(), -box_y0(), -box_z_bot()])
                electronics_box();
else if (part == "lid")
    lid();
else
    assembled();
