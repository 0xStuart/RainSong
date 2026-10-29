// RainSong solar-panel frame (not final until Stuart says so)
// Inner opening: 300 x 144.5 mm.
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
//   "assembled"  both halves, sitting on the floor at tilt_deg
//   "base"       piece from the base end to the split (grooves + side wedges)
//   "far"        piece from the split to the far end (tongues)
//
// Override from the CLI, e.g.:
//   openscad -D 'part="base"' -D split_frac=0.70 -o frame_base.stl solar_panel_frame.scad

part = "assembled";

panel_length = 300.0;
panel_width  = 144.5;
frame_height = 15.0;

rail_long = 9.0;
rail_end  = 18.0;
rail_base = 30.0;

panel_clearance = 0.0;

// Break location, measured from the base end as a fraction of outer length.
// 0.70 → 243.6 mm from the base on the default 348 mm frame.
// Keep the split in the long rails (not in the 30 mm base or 18 mm end).
split_frac  = 0.70;
split_x_abs = -1;       // set >= 0 to place the break at an absolute X instead

kerf = 0.2;             // gap between the two printed faces, mm

// Tongue (far piece) / groove (base piece). Full frame height, through
// the rail, visible from the top and bottom. Not a hidden joint.
tongue_len       = 12.0;  // mm, along X
tongue_w         = 3.0;   // mm, across the rail (Y)
tongue_h         = frame_height;
tongue_clearance = 0.30;  // mm, each side of the groove (Y only)
tongue_extra     = 0.4;   // mm, groove deeper than the tongue

// Notch in the centre of the base end, from the bottom up, through the 30 mm rail.
base_cutout_w = 29.3;   // mm, across Y, centred
base_cutout_h = 9.9;    // mm, from Z=0 up

// Sit on the floor at this angle. Side drop is perpendicular to the panel.
tilt_deg         = 20;
floor_clearance  = 20.0;  // mm, side drop at the base (x = 0)

explode = 20;           // mm, assembled preview: slide the far half +X to show the joint

// Extra width at the break for support. Inward on each long side
// (into the opening), running flare_len back from the split face.
flare_extra = 15.0;     // mm, each side
flare_len   = 20.0;     // mm, from the break

eps = 0.02;

outer_length = rail_base + panel_length + rail_end;
outer_width  = rail_long + panel_width + rail_long;

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
echo(base_cutout = [base_cutout_w, base_cutout_h]);
echo(tilt_deg = tilt_deg, floor_clearance = floor_clearance);
echo(side_drop_base = floor_clearance, side_drop_break = drop_at_break());
echo(side_height_break = drop_at_break() + frame_height);
echo(flare_extra = flare_extra, flare_len = flare_len);

module base_cutout() {
    translate([
        -eps,
        (outer_width - base_cutout_w) / 2,
        -eps
    ])
        cube([
            rail_base + 2 * eps,
            base_cutout_w,
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
                panel_width + panel_clearance,
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
    }
}

if (part == "base")
    on_floor()
        base_half();
else if (part == "far")
    translate([-(far_face() - tongue_len), 0, 0])
        far_half();
else
    assembled();
