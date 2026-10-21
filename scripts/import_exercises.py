#!/usr/bin/env python3
"""Build-time exercise library import (milestone 2). Run once; the app itself never downloads anything.

    python scripts/import_exercises.py            # uses scripts/.cache if present, downloads otherwise
    python scripts/import_exercises.py --refresh  # re-download the source data and images

Source: https://github.com/yuhonas/free-exercise-db (public domain, Unlicense). Field names follow its schema.json:
id, name, force, level, mechanic, equipment, primaryMuscles, secondaryMuscles, instructions, category, images.

Outputs
  data/exercises.json        the bundled library the app seeds from
  data/exercise_map.json     our name -> matched record, for a person to review
  app/src/main/assets/exercises/<source id>/<n>.webp   start and end photos, about 600 px wide
"""

from __future__ import annotations

import difflib
import io
import json
import re
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "scripts" / ".cache"
DATA = ROOT / "data"
ASSETS = ROOT / "app" / "src" / "main" / "assets"
SOURCE_JSON = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json"
IMAGE_BASE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"
IMAGE_WIDTH = 600
WEBP_QUALITY = 72
MAX_TOTAL_BYTES = 15 * 1024 * 1024

GROUPS = ["chest", "back", "delts", "biceps", "triceps", "quads", "hams_glutes", "calves", "core"]
GROUP_LABEL = {
    "chest": "Chest", "back": "Back", "delts": "Side and rear delts", "biceps": "Biceps", "triceps": "Triceps",
    "quads": "Quads", "hams_glutes": "Hamstrings and glutes", "calves": "Calves", "core": "Core",
}
AREA_TAGS = {"shoulder", "elbow", "wrist", "lower_back", "knee", "hip", "ankle", "neck"}
MOVEMENT_TAGS = {
    "overhead_press", "dip", "wide_grip_press", "upright_row", "heavy_curl", "skull_crusher", "pull_up",
    "close_grip_press", "barbell_press", "flat_palm_push_up", "front_squat", "barbell_row", "loaded_hinge",
    "back_squat", "standing_overhead", "sit_up", "deep_knee_bend", "lunge", "heavy_leg_extension", "jump",
    "deep_hip_flexion", "wide_stance", "standing_calf", "seated_calf", "shrug", "heavy_carry", "lateral_raise",
    "hip_thrust", "machine_press",
}
BAR_ITEMS = ("barbell", "ez_bar", "smith_machine")
SMALL_WORDS = ["calf raise", "raise", "curl", "pushdown", "extension", "fly", "flye", "pec deck", "face pull",
               "crunch", "skull crusher", "rotation"]
BIG_WORDS = ["press", "row", "pulldown", "pull-up", "chin-up", "dip", "squat", "leg press", "deadlift",
             "hip thrust", "lunge", "split squat"]

EXERCISES: list[dict] = []


def X(eid, name, source, equip="", counts="", tags="", cues=(), **kw):
    """One library entry. `source` is a free-exercise-db id, or None for a custom record without photos."""
    assert len(cues) == 3, f"{eid}: needs exactly three cues"
    EXERCISES.append({
        "id": eid, "name": name, "source_id": source,
        "equipment_ids": equip.split(), "counts_toward": counts.split(), "stresses": tags.split(),
        "cues": list(cues), **kw,
    })


# ---------------------------------------------------------------- Push
X("barbell_bench_press", "Barbell bench press", "Barbell_Bench_Press_-_Medium_Grip", "barbell plates adjustable_bench", "chest",
  "shoulder elbow wrist barbell_press wide_grip_press",
  ("Shoulder blades squeezed and down", "Bar to mid-chest, elbows at about 45°", "Feet planted, hips on the bench"),
  aliases=["bench", "flat bench"])
X("smith_bench_press", "Smith machine bench press", "Smith_Machine_Bench_Press", "smith_machine plates adjustable_bench", "chest",
  "shoulder elbow wrist barbell_press wide_grip_press",
  ("Set the bench so the bar meets mid-chest", "Shoulder blades squeezed and down", "Press up, don't bounce"),
  aliases=["smith bench"])
X("dumbbell_bench_press", "Dumbbell bench press", "Dumbbell_Bench_Press", "dumbbells adjustable_bench", "chest",
  "shoulder elbow wrist",
  ("Dumbbells over the shoulders at the top", "Lower to chest level, elbows at 45°", "Press up in a slight arc"),
  aliases=["db bench"], one_sided="one_arm_dumbbell_bench_press")
X("chest_press_machine", "Chest press machine", "Leverage_Chest_Press", "chest_press_machine", "chest",
  "shoulder elbow wrist machine_press",
  ("Handles level with mid-chest", "Shoulder blades back against the pad", "Press out, don't lock hard"),
  aliases=["chest press"])
X("incline_dumbbell_press", "Incline dumbbell press", "Incline_Dumbbell_Press", "dumbbells adjustable_bench", "chest",
  "shoulder elbow wrist",
  ("Bench at about 30°", "Lower to the upper chest", "Wrists stacked over the elbows"),
  aliases=["incline db press"])
X("incline_smith_press", "Incline Smith press", "Smith_Machine_Incline_Bench_Press", "smith_machine plates adjustable_bench", "chest",
  "shoulder elbow wrist barbell_press wide_grip_press",
  ("Bench at about 30°", "Bar to the upper chest", "Shoulder blades pinned back"))
X("incline_press_machine", "Incline press machine", "Leverage_Incline_Chest_Press", "incline_press_machine", "chest",
  "shoulder elbow wrist machine_press",
  ("Seat so the handles line up with the upper chest", "Back flat on the pad", "Smooth press, slow return"))
X("seated_dumbbell_shoulder_press", "Seated dumbbell shoulder press", "Seated_Dumbbell_Press", "dumbbells adjustable_bench", "",
  "shoulder elbow wrist overhead_press",
  ("Back flat against an upright bench", "Dumbbells start at ear height", "Press up without arching"),
  aliases=["db shoulder press", "seated dumbbell press"])
X("shoulder_press_machine", "Shoulder press machine", "Machine_Shoulder_Military_Press", "shoulder_press_machine", "",
  "shoulder elbow wrist overhead_press machine_press",
  ("Handles start at shoulder height", "Back and head on the pad", "Press up, control down"))
X("cable_fly", "Cable fly", "Cable_Crossover", "cable_station single_handles", "chest", "shoulder",
  ("Slight bend in the elbows, kept fixed", "Hug a wide tree, hands meet in front", "Feel the stretch, don't overreach"),
  aliases=["cable crossover", "cable flye"])
X("pec_deck", "Pec deck", "Butterfly", "pec_deck", "chest", "shoulder",
  ("Elbows slightly bent, level with the shoulders", "Squeeze the chest to bring the pads together", "Slow return to a light stretch"),
  aliases=["butterfly", "pec fly"])
X("dumbbell_fly", "Dumbbell fly", "Dumbbell_Flyes", "dumbbells adjustable_bench", "chest", "shoulder",
  ("Elbows slightly bent throughout", "Lower wide until you feel a stretch", "Bring the dumbbells together over the chest"),
  aliases=["db fly", "dumbbell flyes"])
X("dumbbell_lateral_raise", "Dumbbell lateral raise", "Side_Lateral_Raise", "dumbbells", "delts", "shoulder lateral_raise",
  ("Lead with the elbows, not the hands", "Raise to shoulder height, no higher", "No swinging; slow on the way down"),
  aliases=["side raise", "lateral raise", "laterals"])
X("cable_lateral_raise", "Cable lateral raise", "Standing_Low-Pulley_Deltoid_Raise", "cable_station single_handles", "delts",
  "shoulder lateral_raise",
  ("Cable starts behind your hip", "Raise to shoulder height", "Keep the body still"),
  per_side=True, unilateral=True)
X("lateral_raise_machine", "Lateral raise machine", None, "lateral_raise_machine", "delts", "shoulder lateral_raise",
  ("Pads on the outside of the elbows", "Raise to shoulder height", "Control the way down"),
  primary=["shoulders"])
X("triceps_rope_pushdown", "Triceps rope pushdown", "Triceps_Pushdown_-_Rope_Attachment", "cable_station rope_attachment", "triceps",
  "elbow",
  ("Elbows pinned to your sides", "Spread the rope at the bottom", "Only the forearms move"),
  aliases=["rope pushdown", "tricep pushdown"], one_sided="one_arm_pushdown")
X("straight_bar_pushdown", "Straight-bar pushdown", "Triceps_Pushdown", "cable_station straight_bar_attachment", "triceps",
  "elbow wrist",
  ("Elbows pinned to your sides", "Push down until the arms are straight", "Wrists straight, slow on the way up"),
  aliases=["bar pushdown"])
X("bench_dips", "Bench dips", "Bench_Dips", "adjustable_bench", "triceps", "shoulder elbow wrist dip",
  ("Hands on the bench edge, hips close", "Lower until the elbows reach 90°", "Shoulders down, away from the ears"),
  bw=True)
X("barbell_overhead_press", "Barbell overhead press", "Standing_Military_Press", "barbell plates squat_rack", "",
  "shoulder elbow wrist lower_back overhead_press standing_overhead barbell_press",
  ("Squeeze the glutes and brace the stomach", "Bar travels close to the face", "Head through at the top"),
  aliases=["ohp", "military press", "overhead press"])
X("smith_overhead_press", "Smith overhead press", "Smith_Machine_Overhead_Shoulder_Press", "smith_machine plates adjustable_bench", "",
  "shoulder elbow wrist overhead_press barbell_press",
  ("Seat so the bar passes in front of the face", "Brace, don't arch the back", "Press to just short of lockout"))
X("incline_barbell_bench_press", "Incline barbell bench press", "Barbell_Incline_Bench_Press_-_Medium_Grip",
  "barbell plates adjustable_bench", "chest", "shoulder elbow wrist barbell_press wide_grip_press",
  ("Bench at about 30°", "Bar to the upper chest", "Elbows slightly tucked"),
  aliases=["incline bench"])
X("dips", "Dips", "Dips_-_Chest_Version", "dip_station", "chest triceps", "shoulder elbow wrist dip",
  ("Lean forward a little for the chest", "Lower until the upper arms are level", "Shoulders down, away from the ears"),
  bw=True, aliases=["parallel bar dips"])
X("assisted_dip", "Assisted dip machine", None, "assisted_pullup_dip", "chest triceps", "shoulder elbow wrist dip",
  ("Pick assistance that leaves 2–3 reps in the tank", "Lower until the upper arms are level", "Shoulders down, chest proud"),
  assisted=True, size="big", primary=["chest", "triceps"], aliases=["assisted dips"])
X("close_grip_bench_press", "Close-grip bench press", "Close-Grip_Barbell_Bench_Press", "barbell plates adjustable_bench",
  "chest triceps", "shoulder elbow wrist barbell_press close_grip_press",
  ("Hands about shoulder-width apart", "Elbows tucked close to the body", "Bar to the lower chest"))
X("close_grip_smith_press", "Close-grip Smith press", "Smith_Machine_Close-Grip_Bench_Press", "smith_machine plates adjustable_bench",
  "chest triceps", "shoulder elbow wrist barbell_press close_grip_press",
  ("Hands about shoulder-width apart", "Elbows tucked close to the body", "Bar to the lower chest"))
X("cable_overhead_triceps_extension", "Cable overhead triceps extension", "Cable_Rope_Overhead_Triceps_Extension",
  "cable_station rope_attachment", "triceps", "shoulder elbow",
  ("Face away from the stack, rope behind the head", "Elbows point forward and stay still", "Straighten the arms fully"))
X("dumbbell_overhead_triceps_extension", "Dumbbell overhead triceps extension", "Standing_Dumbbell_Triceps_Extension", "dumbbells",
  "triceps", "shoulder elbow",
  ("Hold one dumbbell with both hands", "Elbows close to the head", "Lower behind the head, then straighten"))
X("ez_bar_skull_crusher", "EZ-bar skull crusher", "EZ-Bar_Skullcrusher", "ez_bar plates adjustable_bench", "triceps",
  "elbow wrist skull_crusher",
  ("Upper arms stay vertical", "Lower the bar to just above the forehead", "Straighten without flaring the elbows"),
  aliases=["skull crusher", "lying triceps extension"])
X("reverse_pec_deck", "Reverse pec deck", "Reverse_Machine_Flyes", "pec_deck", "delts", "shoulder",
  ("Chest against the pad, arms straight", "Sweep back to shoulder level", "Squeeze the upper back"),
  aliases=["rear delt fly", "reverse fly"])
X("face_pull", "Face pull with rope", "Face_Pull", "cable_station rope_attachment", "delts", "shoulder elbow",
  ("Rope at face height", "Pull to the forehead, elbows high", "Spread the rope and squeeze the shoulder blades"),
  aliases=["face pull"])
X("bent_over_rear_delt_raise", "Bent-over rear-delt raise", "Seated_Bent-Over_Rear_Delt_Raise", "dumbbells adjustable_bench", "delts",
  "shoulder",
  ("Chest on the thighs or a bench", "Raise out to the side with soft elbows", "Lead with the elbows, no swinging"),
  aliases=["rear delt raise"])

# ---------------------------------------------------------------- Pull
X("lat_pulldown", "Lat pulldown", "Wide-Grip_Lat_Pulldown", "lat_pulldown", "back", "shoulder elbow",
  ("Thighs locked under the pads", "Pull the bar to the upper chest", "Elbows drive down and back"),
  aliases=["lat pull", "pulldown"], one_sided="one_arm_lat_pulldown")
X("pull_ups", "Pull-ups", "Pullups", "pullup_bar", "back", "shoulder elbow pull_up",
  ("Start from a dead hang", "Chest up towards the bar", "Lower all the way, under control"),
  bw=True, aliases=["pull up", "pullups"])
X("assisted_pull_up", "Assisted pull-up machine", None, "assisted_pullup_dip", "back", "shoulder elbow pull_up",
  ("Pick assistance that leaves 2–3 reps in the tank", "Chest up towards the bar", "Lower all the way, under control"),
  assisted=True, size="big", primary=["lats"], aliases=["assisted pull-ups"])
X("seated_cable_row", "Seated cable row", "Seated_Cable_Rows", "seated_row", "back", "shoulder elbow lower_back",
  ("Sit tall, slight knee bend", "Pull the handle to the stomach", "Squeeze the shoulder blades, don't lean back"),
  aliases=["cable row", "low row"], one_sided="one_arm_cable_row")
X("chest_supported_row_machine", "Chest-supported row machine", "Leverage_Iso_Row", "chest_supported_row_machine", "back",
  "shoulder elbow",
  ("Chest on the pad the whole time", "Pull the elbows back past the body", "Pause and squeeze, then lower slowly"),
  aliases=["machine row", "seated row machine", "row machine"])
X("chest_supported_dumbbell_row", "Chest-supported dumbbell row", "Dumbbell_Incline_Row", "dumbbells adjustable_bench", "back",
  "shoulder elbow",
  ("Lie face down on a bench set to about 30°", "Pull the dumbbells to the hips", "Squeeze the shoulder blades together"),
  aliases=["incline dumbbell row", "chest-supported dumbbell row on an incline bench"])
X("one_arm_dumbbell_row", "One-arm dumbbell row", "One-Arm_Dumbbell_Row", "dumbbells adjustable_bench", "back",
  "shoulder elbow lower_back",
  ("Hand and knee on the bench, back flat", "Pull the dumbbell to the hip", "No twisting the torso"),
  per_side=True, unilateral=True, aliases=["db row", "single-arm row"])
X("one_arm_cable_row", "One-arm cable row", "Seated_One-arm_Cable_Pulley_Rows", "cable_station single_handles", "back",
  "shoulder elbow",
  ("Brace with the free hand", "Pull the handle to the hip", "Full stretch forward, no twisting"),
  per_side=True, unilateral=True)
X("ez_bar_curl", "EZ-bar curl", "EZ-Bar_Curl", "ez_bar plates", "biceps", "elbow wrist heavy_curl",
  ("Elbows pinned to your sides", "Curl without swinging", "Lower all the way, slowly"),
  aliases=["ez curl"], one_sided="concentration_curl")
X("barbell_curl", "Barbell curl", "Barbell_Curl", "barbell plates", "biceps", "elbow wrist heavy_curl",
  ("Hands shoulder-width apart", "Elbows stay at your sides", "No leaning back"),
  one_sided="concentration_curl")
X("dumbbell_curl", "Dumbbell curl", "Dumbbell_Bicep_Curl", "dumbbells", "biceps", "elbow heavy_curl",
  ("Palms forward, elbows at your sides", "Curl up, squeeze at the top", "Lower all the way"),
  aliases=["standing dumbbell curl", "db curl", "bicep curl"], one_sided="concentration_curl")
X("cable_curl", "Cable curl", "Standing_Biceps_Cable_Curl", "cable_station straight_bar_attachment", "biceps", "elbow wrist",
  ("Stand close to the stack", "Elbows stay at your sides", "Squeeze at the top, slow down"))
X("hammer_curl", "Hammer curl", "Hammer_Curls", "dumbbells", "biceps", "elbow",
  ("Palms face each other", "Elbows stay at your sides", "Curl without swinging"),
  aliases=["hammer curls"])
X("rope_cable_hammer_curl", "Rope cable hammer curl", "Cable_Hammer_Curls_-_Rope_Attachment", "cable_station rope_attachment",
  "biceps", "elbow",
  ("Rope on the low pulley, palms facing", "Elbows stay at your sides", "Squeeze at the top"))
X("barbell_row", "Barbell row", "Bent_Over_Barbell_Row", "barbell plates", "back", "shoulder elbow lower_back barbell_row",
  ("Hinge until the torso is near 45°", "Pull the bar to the lower ribs", "Back flat the whole set"),
  aliases=["bent-over row", "bb row"])
X("t_bar_row", "T-bar row", "T-Bar_Row_with_Handle", "t_bar_row", "back", "shoulder elbow lower_back barbell_row",
  ("Chest up, back flat", "Pull the handle to the chest", "Lower until the arms are straight"))
X("close_grip_lat_pulldown", "Close-grip lat pulldown", "Close-Grip_Front_Lat_Pulldown", "lat_pulldown", "back",
  "shoulder elbow",
  ("Close or V-grip handle", "Pull to the upper chest", "Lean back only slightly"),
  aliases=["close grip pulldown"])
X("neutral_grip_lat_pulldown", "Neutral-grip lat pulldown", "V-Bar_Pulldown", "lat_pulldown", "back", "shoulder elbow",
  ("Palms facing each other", "Pull to the upper chest", "Elbows drive down"),
  aliases=["v-bar pulldown"])
X("chin_ups", "Chin-ups", "Chin-Up", "pullup_bar", "back", "shoulder elbow pull_up",
  ("Palms facing you, shoulder-width", "Chin over the bar", "Lower all the way"),
  bw=True, aliases=["chin up"])
X("assisted_chin_ups", "Assisted chin-ups", None, "assisted_pullup_dip", "back", "shoulder elbow pull_up",
  ("Palms facing you", "Pick assistance that leaves 2–3 reps in the tank", "Chin over the bar, lower slowly"),
  assisted=True, size="big", primary=["lats", "biceps"])
X("wide_grip_seated_cable_row", "Wide-grip seated cable row", "Seated_Cable_Rows", "seated_row", "back",
  "shoulder elbow lower_back",
  ("Wide bar, overhand grip", "Pull to the lower chest, elbows out", "Squeeze the upper back"))
X("incline_dumbbell_curl", "Incline dumbbell curl", "Incline_Dumbbell_Curl", "dumbbells adjustable_bench", "biceps",
  "shoulder elbow",
  ("Bench at about 45°, arms hang straight", "Curl without moving the elbows", "Full stretch at the bottom"),
  aliases=["incline curl"])
X("preacher_curl_machine", "Preacher curl machine", "Machine_Preacher_Curls", "preacher_machine", "biceps", "elbow",
  ("Armpits snug against the pad", "Curl up, squeeze at the top", "Lower until the arms are almost straight"),
  aliases=["preacher curl"])
X("ez_bar_preacher_curl", "EZ-bar preacher curl", "Preacher_Curl", "ez_bar plates preacher_bench", "biceps",
  "elbow wrist heavy_curl",
  ("Armpits snug against the pad", "Lower until the arms are almost straight", "No bouncing at the bottom"))
X("hanging_knee_raise", "Hanging knee raise", "Hanging_Leg_Raise", "pullup_bar", "core", "shoulder",
  ("Hang with the shoulders active", "Bring the knees up towards the chest", "Lower slowly, no swinging"),
  bw=True)
X("captains_chair_knee_raise", "Captain's chair knee raise", "Knee_Hip_Raise_On_Parallel_Bars", "captains_chair", "core",
  "shoulder",
  ("Back against the pad, forearms on the rests", "Knees up towards the chest", "Lower slowly"),
  aliases=["captain's chair"])
X("lying_leg_raise", "Lying leg raise", "Flat_Bench_Lying_Leg_Raise", "adjustable_bench", "core", "lower_back hip",
  ("Lower back pressed into the bench", "Raise the legs to vertical", "Lower slowly without arching"))

# ---------------------------------------------------------------- Legs
X("barbell_back_squat", "Barbell back squat", "Barbell_Squat", "barbell plates squat_rack", "quads",
  "knee hip lower_back neck back_squat deep_knee_bend deep_hip_flexion",
  ("Bar on the upper back, brace hard", "Sit down between the heels", "Knees track over the toes"),
  aliases=["back squat", "squat"])
X("smith_machine_squat", "Smith machine squat", "Smith_Machine_Squat", "smith_machine plates", "quads",
  "knee hip lower_back neck back_squat deep_knee_bend deep_hip_flexion",
  ("Feet slightly in front of the bar", "Brace and sit down", "Drive up through the whole foot"),
  aliases=["smith squat"])
X("goblet_squat", "Goblet squat", "Goblet_Squat", "dumbbells", "quads", "knee hip deep_knee_bend deep_hip_flexion",
  ("Hold the dumbbell at the chest", "Elbows inside the knees at the bottom", "Chest up, heels down"))
X("leg_press", "Leg press", "Leg_Press", "leg_press", "quads", "knee hip deep_hip_flexion",
  ("Feet hip-width, mid-platform", "Lower until the knees are near 90°", "Don't let the hips lift off the seat"),
  one_sided="single_leg_press")
X("hack_squat", "Hack squat", "Hack_Squat", "hack_squat", "quads", "knee hip deep_knee_bend deep_hip_flexion",
  ("Back and shoulders flat on the pads", "Lower under control", "Drive through the whole foot"))
X("walking_lunges", "Walking lunges", "Bodyweight_Walking_Lunge", "dumbbells", "quads", "knee hip lunge",
  ("Long step, torso upright", "Back knee lightly towards the floor", "Push through the front heel"),
  per_side=True, unilateral=True, aliases=["lunges"])
X("leg_extension", "Leg extension", "Leg_Extensions", "leg_extension", "quads", "knee heavy_leg_extension",
  ("Knee lined up with the machine's pivot", "Straighten fully and squeeze", "Lower slowly"),
  aliases=["leg extensions"], one_sided="single_leg_extension")
X("step_ups", "Step-ups", "Dumbbell_Step_Ups", "dumbbells adjustable_bench", "quads", "knee hip",
  ("Whole foot on the bench", "Drive up through the top leg", "Control the way down"),
  per_side=True, unilateral=True)
X("slow_goblet_squat", "Slow goblet squat", "Goblet_Squat", "dumbbells", "quads", "knee hip deep_knee_bend deep_hip_flexion",
  ("Three seconds down", "Pause at the bottom", "Stand up smoothly"))
X("lying_leg_curl", "Lying leg curl", "Lying_Leg_Curls", "lying_leg_curl", "hams_glutes", "knee",
  ("Knees just off the pad edge", "Curl the heels towards the glutes", "Hips stay down"),
  aliases=["leg curl", "hamstring curl"], one_sided="single_leg_curl")
X("seated_leg_curl", "Seated leg curl", "Seated_Leg_Curl", "seated_leg_curl", "hams_glutes", "knee",
  ("Knees lined up with the pivot", "Curl down and back", "Slow on the way up"))
X("swiss_ball_leg_curl", "Swiss-ball leg curl", "Ball_Leg_Curl", "swiss_ball", "hams_glutes", "knee",
  ("Heels on the ball, hips up", "Roll the ball towards you", "Keep the hips high"),
  aliases=["ball leg curl"])
X("standing_calf_raise", "Standing calf raise", "Standing_Calf_Raises", "standing_calf_raise", "calves",
  "ankle neck standing_calf",
  ("Balls of the feet on the edge", "Full stretch at the bottom", "Pause at the top"),
  aliases=["calf raise"])
X("leg_press_calf_raise", "Calf raise on the leg press", "Calf_Press_On_The_Leg_Press_Machine", "leg_press", "calves", "ankle",
  ("Balls of the feet on the platform edge", "Knees straight but not locked", "Full range, pause at the top"),
  aliases=["leg press calf raise", "calf press"])
X("smith_calf_raise", "Smith calf raise", "Smith_Machine_Calf_Raise", "smith_machine plates", "calves", "ankle standing_calf",
  ("Stand on a plate or step", "Full stretch at the bottom", "Rise high, pause at the top"))
X("cable_crunch", "Cable crunch", "Cable_Crunch", "cable_station rope_attachment", "core", "lower_back",
  ("Kneel, rope beside the head", "Curl the ribs towards the hips", "Hips stay still"))
X("ab_crunch_machine", "Ab crunch machine", "Ab_Crunch_Machine", "ab_crunch_machine", "core", "lower_back",
  ("Curl the chest towards the hips", "Exhale as you crunch", "Slow return"))
X("decline_crunch", "Decline crunch", "Decline_Crunch", "adjustable_bench", "core", "lower_back neck sit_up",
  ("Feet locked in, arms crossed", "Curl up, don't yank the neck", "Lower slowly"))
X("plank", "Plank", "Plank", "", "core", "shoulder lower_back",
  ("Elbows under the shoulders", "Squeeze the glutes, body in one line", "Breathe, don't hold your breath"),
  timed=True)
X("dead_bug", "Dead bug", "Dead_Bug", "", "core", "",
  ("Lower back pressed to the floor", "Reach the opposite arm and leg out", "Move slowly, keep breathing"),
  timed=True)
X("barbell_romanian_deadlift", "Barbell Romanian deadlift", "Romanian_Deadlift", "barbell plates", "hams_glutes",
  "lower_back hip loaded_hinge",
  ("Soft knees, push the hips back", "Bar slides down the thighs", "Stop when the back starts to round"),
  aliases=["rdl", "romanian deadlift"])
X("dumbbell_romanian_deadlift", "Dumbbell Romanian deadlift", "Stiff-Legged_Dumbbell_Deadlift", "dumbbells", "hams_glutes",
  "lower_back hip loaded_hinge",
  ("Soft knees, push the hips back", "Dumbbells close to the legs", "Back flat, feel the hamstrings"),
  aliases=["db rdl"])
X("smith_romanian_deadlift", "Smith Romanian deadlift", "Smith_Machine_Stiff-Legged_Deadlift", "smith_machine plates",
  "hams_glutes", "lower_back hip loaded_hinge",
  ("Soft knees, push the hips back", "Bar close to the legs", "Squeeze the glutes to stand"))
X("dumbbell_bulgarian_split_squat", "Dumbbell Bulgarian split squat", "Split_Squat_with_Dumbbells", "dumbbells adjustable_bench",
  "quads", "knee hip lunge deep_knee_bend",
  ("Back foot on a bench", "Drop the back knee straight down", "Front heel stays down"),
  per_side=True, unilateral=True, aliases=["bulgarian split squat", "bss"])
X("smith_split_squat", "Smith split squat", "Smith_Single-Leg_Split_Squat", "smith_machine plates", "quads", "knee hip lunge",
  ("Long split stance under the bar", "Drop the back knee straight down", "Drive through the front heel"),
  per_side=True, unilateral=True)
X("barbell_hip_thrust", "Barbell hip thrust", "Barbell_Hip_Thrust", "barbell plates adjustable_bench", "hams_glutes",
  "hip lower_back hip_thrust",
  ("Upper back on the bench, bar on the hips", "Drive the hips up, chin tucked", "Squeeze the glutes at the top"),
  aliases=["hip thrust"])
X("smith_hip_thrust", "Smith hip thrust", "Barbell_Hip_Thrust", "smith_machine plates adjustable_bench", "hams_glutes",
  "hip lower_back hip_thrust",
  ("Upper back on the bench, bar on the hips", "Drive the hips up", "Squeeze and pause at the top"))
X("hip_thrust_machine", "Hip thrust machine", None, "hip_thrust_machine", "hams_glutes", "hip lower_back hip_thrust",
  ("Pad across the hips", "Drive the hips up", "Squeeze and pause at the top"),
  size="big", primary=["glutes"])
X("glute_bridge", "Glute bridge", "Barbell_Glute_Bridge", "dumbbells", "hams_glutes", "hip hip_thrust",
  ("Dumbbell across the hips", "Heels close to the glutes", "Squeeze up, pause at the top"))
X("cable_pull_through", "Cable pull-through", "Pull_Through", "cable_station rope_attachment", "hams_glutes", "lower_back hip",
  ("Face away, rope between the legs", "Push the hips back", "Snap the hips forward and squeeze"))
X("seated_calf_raise", "Seated calf raise", "Seated_Calf_Raise", "seated_calf_raise", "calves", "ankle seated_calf",
  ("Pad snug on the thighs", "Full stretch at the bottom", "Pause at the top"))
X("pallof_press", "Pallof press", "Pallof_Press", "cable_station single_handles", "core", "",
  ("Stand side-on to the cable", "Press the handle straight out", "Don't let it twist you"),
  per_side=True)
X("bird_dog", "Bird dog", None, "", "core", "",
  ("Hands under the shoulders, knees under the hips", "Reach the opposite arm and leg", "Keep the hips level"),
  per_side=True, size="small", primary=["lower back", "abdominals"])
X("side_plank", "Side plank", "Side_Bridge", "", "core", "shoulder",
  ("Elbow under the shoulder", "Hips up, body in one line", "Breathe steadily"),
  timed=True, per_side=True)
X("suitcase_carry", "Suitcase carry", "Farmers_Walk", "dumbbells", "core", "shoulder lower_back heavy_carry",
  ("One dumbbell, one side", "Walk tall, don't lean", "Brace the stomach"),
  timed=True, per_side=True)

# ---------------------------------------------------------------- Home and away
X("push_ups", "Push-ups", "Pushups", "", "chest triceps", "shoulder elbow wrist flat_palm_push_up",
  ("Hands under the shoulders", "Body in one line", "Chest to a fist's height from the floor"),
  bw=True, aliases=["pushups", "press-ups"])
X("pike_push_ups", "Pike push-ups", None, "", "", "shoulder elbow wrist overhead_press flat_palm_push_up",
  ("Hips high, body in an upside-down V", "Lower the head towards the floor", "Press back up"),
  size="big", primary=["shoulders", "triceps"])
X("chair_dips", "Chair dips", "Bench_Dips", "", "triceps", "shoulder elbow wrist dip",
  ("Hands on a sturdy chair, hips close", "Lower until the elbows reach 90°", "Shoulders down"))
X("one_arm_backpack_row", "One-arm backpack row", "One-Arm_Dumbbell_Row", "", "back", "shoulder elbow lower_back",
  ("Backpack loaded with books", "Hand on a chair, back flat", "Pull the bag to the hip"),
  per_side=True, unilateral=True)
X("backpack_curl", "Backpack curl", None, "", "biceps", "elbow",
  ("Hold the backpack by the top handle or straps", "Elbows at your sides", "Lower slowly"),
  size="small", primary=["biceps"])
X("y_t_raise", "Face-down Y and T raises", None, "", "delts", "shoulder",
  ("Lie face down, arms overhead", "Raise the arms into a Y, then a T", "Thumbs up, squeeze the shoulder blades"),
  size="small", primary=["shoulders", "middle back"])
X("superman_hold", "Superman hold", "Superman", "", "", "lower_back",
  ("Lie face down", "Lift the arms, chest and legs", "Hold, breathing steadily"),
  timed=True, size="small")
X("backpack_bulgarian_split_squat", "Bulgarian split squat with a backpack", "Split_Squats", "", "quads",
  "knee hip lunge deep_knee_bend",
  ("Backpack on, back foot on a chair", "Drop the back knee straight down", "Front heel stays down"),
  per_side=True, unilateral=True)
X("one_leg_romanian_deadlift", "One-leg Romanian deadlift", None, "", "hams_glutes", "lower_back hip ankle",
  ("Soft standing knee", "Hinge, the free leg reaches back", "Hips stay square"),
  per_side=True, unilateral=True, size="big", primary=["hamstrings", "glutes"])
X("one_leg_glute_bridge", "One-leg glute bridge", "Single_Leg_Glute_Bridge", "", "hams_glutes", "hip",
  ("One foot down, the other leg straight", "Drive the hips up", "Squeeze at the top"),
  per_side=True, unilateral=True)
X("step_calf_raise", "Calf raises on a step", "Calf_Raise_On_A_Dumbbell", "", "calves", "ankle standing_calf",
  ("Balls of the feet on a step", "Full stretch at the bottom", "Rise high, pause"))
X("dumbbell_floor_press", "Dumbbell bench or floor press", "Dumbbell_Floor_Press", "dumbbells", "chest", "shoulder elbow wrist",
  ("Lie on the floor or a bench, knees bent", "Lower until the upper arms touch the floor", "Press up"))

# ---------------------------------------------------------------- Injury alternatives (section 9 "used instead")
X("neutral_grip_dumbbell_press", "Neutral-grip dumbbell press", "Dumbbell_Bench_Press_with_Neutral_Grip",
  "dumbbells adjustable_bench", "chest", "shoulder elbow",
  ("Palms facing each other", "Stop short of the painful range", "Press up smoothly"))
X("cable_external_rotation", "Cable external rotation", "External_Rotation_with_Cable", "cable_station single_handles", "",
  "shoulder",
  ("Elbow at your side, a towel under it", "Rotate the forearm outwards", "Slow and light"),
  per_side=True, size="small")
X("box_squat", "Box squat", "Box_Squat", "barbell plates squat_rack adjustable_bench", "quads",
  "knee hip lower_back neck back_squat",
  ("Sit back to a box at a pain-free height", "Pause lightly on the box", "Stand up through the heels"))
X("wall_sit", "Wall-sit hold", None, "", "quads", "knee",
  ("Back flat on the wall", "Slide down to a pain-free angle", "Hold, breathing steadily"),
  timed=True, size="small", primary=["quadriceps"])
X("curl_up", "Curl-up", None, "", "core", "",
  ("One knee bent, hands under the lower back", "Lift the head and shoulders slightly", "Hold briefly, keep the neck long"),
  size="small", primary=["abdominals"])
X("push_ups_on_handles", "Push-ups on fists or handles", "Pushups", "", "chest triceps", "shoulder elbow",
  ("Hold handles or make fists", "Keep the wrists straight", "Body in one line"),
  bw=True)

# ---------------------------------------------------------------- One-sided versions (healthy side in injury mode)
X("one_arm_dumbbell_bench_press", "One-arm dumbbell bench press", "One_Arm_Dumbbell_Bench_Press", "dumbbells adjustable_bench",
  "chest", "shoulder elbow wrist",
  ("Hold the bench with the free hand", "Lower to chest level", "Don't let the body twist"),
  per_side=True, unilateral=True)
X("one_arm_lat_pulldown", "One-arm lat pulldown", "One_Arm_Lat_Pulldown", "lat_pulldown single_handles", "back", "shoulder elbow",
  ("Single handle, sit tall", "Pull the elbow down to the side", "Full stretch at the top"),
  per_side=True, unilateral=True)
X("single_leg_press", "Single-leg leg press", "Leg_Press", "leg_press", "quads", "knee hip deep_hip_flexion",
  ("One foot in the middle of the platform", "Lower under control", "Keep the hips on the seat"),
  per_side=True, unilateral=True)
X("single_leg_extension", "Single-leg extension", "Single-Leg_Leg_Extension", "leg_extension", "quads",
  "knee heavy_leg_extension",
  ("One leg at a time", "Straighten fully and squeeze", "Lower slowly"),
  per_side=True, unilateral=True)
X("single_leg_curl", "Single-leg lying leg curl", "Lying_Leg_Curls", "lying_leg_curl", "hams_glutes", "knee",
  ("One leg at a time", "Curl the heel to the glute", "Hips stay down"),
  per_side=True, unilateral=True)
X("concentration_curl", "Concentration curl", "Concentration_Curls", "dumbbells adjustable_bench", "biceps", "elbow",
  ("Elbow braced on the inner thigh", "Curl up, squeeze", "Lower all the way"),
  per_side=True, unilateral=True)
X("one_arm_pushdown", "One-arm cable pushdown", None, "cable_station single_handles", "triceps", "elbow",
  ("Single handle, elbow at the side", "Push down until straight", "Only the forearm moves"),
  per_side=True, unilateral=True, size="small", primary=["triceps"])


def fetch(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=60) as r:  # build time only; the app never does this
        return r.read()


def norm(s: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", s.lower()).strip()


def big_or_small(e: dict, record: dict | None) -> str:
    if e.get("size"):
        return e["size"]
    if e["counts_toward"] == ["core"]:
        return "small"
    name = e["name"].lower()
    if any(w in name for w in SMALL_WORDS):
        return "small"
    if any(w in name for w in BIG_WORDS):
        return "big"
    return "big" if record and record.get("mechanic") == "compound" else "small"


def load_kind(e: dict, equipment: dict) -> str:
    if e.get("timed"):
        return "none"
    if e.get("assisted"):
        return "assisted"
    if e.get("bw"):
        return "bodyweight"
    ids = e["equipment_ids"]
    if any(i in BAR_ITEMS for i in ids):
        return "barbell"
    if any(equipment[i].get("stack_min") is not None for i in ids):
        return "stack"
    if "dumbbells" in ids:
        return "dumbbell"
    return "none"


def program_refs(program: dict) -> dict[str, list[str]]:
    refs: dict[str, list[str]] = {}
    for day in program["days"]:
        for slot in day["slots"]:
            for i, opt in enumerate(slot["options"]):
                refs.setdefault(opt, []).append(f"{slot['id']}#{i}")
    for group, cands in program["makeup_candidates"].items():
        for c in cands:
            refs.setdefault(c, []).append(f"makeup:{group}")
    return refs


def main() -> int:
    refresh = "--refresh" in sys.argv
    CACHE.mkdir(parents=True, exist_ok=True)
    src_path = CACHE / "exercises.json"
    if refresh or not src_path.exists():
        print("downloading", SOURCE_JSON)
        src_path.write_bytes(fetch(SOURCE_JSON))
    source = {r["id"]: r for r in json.loads(src_path.read_text(encoding="utf-8"))}

    program = json.loads((DATA / "program.json").read_text(encoding="utf-8"))
    equipment = {e["id"]: e for e in json.loads((DATA / "equipment.json").read_text(encoding="utf-8"))}
    ours = {e["id"]: e for e in EXERCISES}
    errors: list[str] = []
    if len(ours) != len(EXERCISES):
        errors.append("duplicate exercise ids")

    refs = program_refs(program)
    for ref, where in refs.items():
        if ref not in ours:
            errors.append(f"{ref} (used in {', '.join(where)}) has no exercise record")
    injury_rules_path = DATA / "injury_rules.json"
    if injury_rules_path.exists():
        for area in json.loads(injury_rules_path.read_text(encoding="utf-8"))["areas"]:
            for alt in area.get("alternatives", []):
                if alt not in ours:
                    errors.append(f"injury alternative {alt} ({area['id']}) has no exercise record")

    library, review, custom = [], [], []
    for e in EXERCISES:
        record = source.get(e["source_id"]) if e["source_id"] else None
        if e["source_id"] and record is None:
            errors.append(f"{e['id']}: source record {e['source_id']} not found in free-exercise-db")
        for i in e["equipment_ids"]:
            if i not in equipment:
                errors.append(f"{e['id']}: unknown equipment id {i}")
        for g in e["counts_toward"]:
            if g not in GROUPS:
                errors.append(f"{e['id']}: unknown muscle group {g}")
        for t in e["stresses"]:
            if t not in AREA_TAGS and t not in MOVEMENT_TAGS:
                errors.append(f"{e['id']}: unknown stress tag {t}")
        if e.get("one_sided") and e["one_sided"] not in ours:
            errors.append(f"{e['id']}: one-sided version {e['one_sided']} missing")

        region = "lower" if set(e["counts_toward"]) & {"quads", "hams_glutes", "calves"} else "upper"
        kind = load_kind(e, equipment)
        images = []
        if record:
            images = [f"exercises/{record['id']}/{n}.webp" for n in range(len(record["images"]))]
        out = {
            "id": e["id"],
            "name": e["name"],
            "aliases": e.get("aliases", []),
            "counts_toward": e["counts_toward"],
            "equipment_ids": e["equipment_ids"],
            "big_or_small": big_or_small(e, record),
            "stresses": e["stresses"],
            "cues": e["cues"],
            "instructions": record["instructions"] if record else list(e["cues"]),
            "images": images,
            "primary_muscles": record["primaryMuscles"] if record else e.get("primary", [GROUP_LABEL[g] for g in e["counts_toward"]]),
            "secondary_muscles": record["secondaryMuscles"] if record else [],
            "unilateral": bool(e.get("unilateral", False)),
            "one_sided_version_id": e.get("one_sided"),
            "per_side": bool(e.get("per_side", False)),
            "timed": bool(e.get("timed", False)),
            "bodyweight_based": bool(e.get("bw", False) or e.get("assisted", False)),
            "assisted": bool(e.get("assisted", False)),
            "load_kind": kind,
            "body_region": region,
            "step_kg": (2.5 if region == "upper" else 5.0) if kind == "barbell" else None,
            "source": "free-exercise-db" if record else "custom",
            "source_id": record["id"] if record else None,
            "licence": "Unlicense (public domain)" if record else "Raise the Bar",
        }
        library.append(out)
        if record is None:
            custom.append(e["name"])
        similarity = difflib.SequenceMatcher(None, norm(e["name"]), norm(record["name"])).ratio() if record else 0.0
        review.append({
            "id": e["id"],
            "name": e["name"],
            "matched_id": record["id"] if record else None,
            "matched_name": record["name"] if record else None,
            "match": ("exact" if record and norm(record["name"]) == norm(e["name"]) else "curated") if record else "custom",
            "name_similarity": round(similarity, 2),
            "used_in": refs.get(e["id"], []),
        })

    if errors:
        print("ERRORS:\n  " + "\n  ".join(errors))
        return 1

    library.sort(key=lambda x: x["id"])
    review.sort(key=lambda x: x["id"])
    (DATA / "exercises.json").write_text(json.dumps(library, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    (DATA / "exercise_map.json").write_text(json.dumps(review, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(library)} exercises written; {len(custom)} custom records without photos:")
    for name in custom:
        print("  -", name)

    total = bundle_images({x["source_id"] for x in library if x["source_id"]}, source, refresh)
    print(f"images: {total / 1024 / 1024:.2f} MB in {ASSETS / 'exercises'}")
    if total > MAX_TOTAL_BYTES:
        print("ERROR: images are over the 15 MB budget")
        return 1
    return 0


def bundle_images(source_ids: set[str], source: dict, refresh: bool) -> int:
    try:
        from PIL import Image
    except ImportError:
        print("Pillow is needed for the WebP conversion: python -m pip install pillow")
        raise
    out_root = ASSETS / "exercises"
    out_root.mkdir(parents=True, exist_ok=True)
    jobs = [(sid, n, rel) for sid in sorted(source_ids) for n, rel in enumerate(source[sid]["images"])]
    print(f"\nBundling {len(jobs)} images from {len(source_ids)} exercise records "
          f"(downloads are kept in {CACHE / 'images'} and reused on the next run)", flush=True)
    total = 0
    downloaded = 0
    for i, (sid, n, rel) in enumerate(jobs, start=1):
        cached = CACHE / "images" / rel
        line = f"[{i:>3}/{len(jobs)}] {rel}: "
        if refresh or not cached.exists():
            print(line + "downloading...", end=" ", flush=True)
            started = time.monotonic()
            try:
                data = fetch(IMAGE_BASE + rel)
            except Exception as e:
                print(f"FAILED ({e})", flush=True)
                print("Run the script again: images already downloaded are reused.", flush=True)
                raise
            cached.parent.mkdir(parents=True, exist_ok=True)
            cached.write_bytes(data)
            downloaded += 1
            print(f"{len(data) / 1024:.0f} KB in {time.monotonic() - started:.1f} s,", end=" ", flush=True)
        else:
            print(line + "cached,", end=" ", flush=True)
        target = out_root / sid / f"{n}.webp"
        target.parent.mkdir(parents=True, exist_ok=True)
        with Image.open(io.BytesIO(cached.read_bytes())) as img:
            img = img.convert("RGB")
            if img.width > IMAGE_WIDTH:
                img = img.resize((IMAGE_WIDTH, round(img.height * IMAGE_WIDTH / img.width)), Image.LANCZOS)
            img.save(target, "WEBP", quality=WEBP_QUALITY, method=6)
        size = target.stat().st_size
        total += size
        print(f"saved WebP {size / 1024:.0f} KB", flush=True)
    print(f"Done: {downloaded} downloaded, {len(jobs) - downloaded} reused from the cache.", flush=True)
    return total


if __name__ == "__main__":
    sys.exit(main())
