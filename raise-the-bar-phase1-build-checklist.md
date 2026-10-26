# Raise the Bar: Phase 1 build checklist

Give this file to your coding assistant together with `plan.html` (the saved plan page). This file is the source of truth for Phase 1. Where the two differ, follow this file. Phase 2 and 3 features are listed in section 3 so they don't get built by accident.

---

## 1. How to use this file (for the coding assistant)

- Build the milestones in section 6 in order. Tick a box only when that item is built and tested.
- Each milestone ends with **Done when**. Don't start the next milestone until it passes.
- Keep every training rule in a pure TypeScript module, `engine/`, with no React, database or device imports, and unit-test it. Screens and the database layer call the engine; the engine never calls them.
- Put every number from section 5 in `config/rules.ts`. No magic numbers anywhere else.
- Phase 1 is fully offline: no network requests, no accounts, no analytics, no API keys.
- If something is unclear, choose the simplest behaviour that fits this file, record it in `DECISIONS.md`, and carry on. Only stop and ask if the choice would change a rule in section 5.
- Don't build anything from section 3, not even partly. The only exceptions are the stubs this file names.
- After each milestone, run all tests and write a three-line summary: what was built, what was decided, what's next.
- Use `plan.html` for context and for the look of the screens. Match its wireframes for Today, Logging a set, After a skip, Something hurts, Food and Exercise page. Ignore its Photo log and Tell the app wireframes and the readiness chips on Today (all Phase 2).

---

## 2. What Phase 1 includes

A personal, offline-first strength app for one person training 6 days a week (Push, Pull, Legs, twice) in an office gym in India.

- First-launch setup screens
- Exercise library: about 100 exercises bundled with photos and form cues
- My Gym: equipment, available weights, and status (working, busy, broken, gone)
- The 6-day program as slots with ranked backups, a minimum session and home versions
- Workout log with reps-left buttons, an automatic rest timer and the first-set check
- Auto-enhancer core: calibration, estimates, weight picking, the effort ramp and scheduled deloads
- Skips: check-ins, the midnight timeout, the skip counter and make-up mode
- Plan changes: one day off and away mode, with a preview and undo
- Injury mode: triage, red flags, swaps, the pain light and four phases
- Food and body: usual meals, quick portions, protein target, morning weight with a 7-day average
- A Progress tab without charts, Settings, and manual export and import (see the note in section 3)

---

## 3. Out of scope: don't build these

**Auto-enhancer (Phase 2):** honesty checks and the reps-left correction (`rir_bias` stays 0); learning each exercise's rep drop between sets (use the fixed default); weekly set adjustments; stall detection; early deloads; the readiness check; strength charts.

**Skips and plan changes (Phase 2):** comeback mode (use the stub in milestone 9); maintenance mode; time-fit; 5-, 4- and 3-day plans; the AI assistant ("Tell the app") and any LLM call; the Sunday review.

**Exercise library (Phase 2):** demo video links; recording your own clips (hide both buttons); downloading exercises that aren't bundled.

**Food (Phase 2):** photo logging; the full Indian Nutrient Databank; weekly calorie nudges; tracking waist over time.

**Infrastructure (Phase 2):** the Cloudflare Worker; GitHub backup; encrypted cloud backup; device keys; any API key or token.

**Progress and later (Phase 2 and 3):** charts; personal records; sets-per-muscle views; reminders other than check-ins; automatic set rebalancing when a whole station breaks; calendar and watch sync; progress photos; the AI coach.

> **Note:** export and import aren't on the plan's Phase 1 list. They're added here because Phase 1 has no backup, and Android device backup is switched off for privacy (milestone 14).

---

## 4. Tech stack and structure

- Expo (managed workflow, current SDK), React Native, TypeScript in strict mode
- Expo Router with four tabs: Today, Food, Progress, My Gym. Settings opens from Today.
- `expo-sqlite` for storage, with numbered migrations. Always use bound parameters, never SQL built from strings.
- `expo-notifications` for check-in notifications only
- `expo-file-system` and `expo-sharing` for export and import
- A small state store on top of SQLite, for example Zustand
- Barlow and Barlow Condensed fonts, bundled with the app (section 11)
- Jest for engine tests, plus a few screen smoke tests
- Test on the owner's phone (Android or iPhone); keep everything working on both

```
app/          screens (Expo Router)
engine/       pure rules: estimates, weights, effort, calendar, skips, make-up,
              absences, injuries, food
config/       rules.ts (everything in section 5)
data/         program.json, exercises.json, exercise_map.json, equipment.json,
              injury_rules.json, foods.json
db/           schema, migrations, queries
scripts/      build-time script that imports the exercise library
assets/       fonts, exercise images (WebP)
tests/        engine tests, including every case in section 13
DECISIONS.md  start it from section 12
```

---

## 5. Rules and constants (`config/rules.ts`)

All rounding is round-half-up (JavaScript `Math.round`), never banker's rounding.

### Estimates and weights

| Name | Value | Meaning |
|---|---|---|
| `EPLEY_DIVISOR` | 30 | e1RM = weight × (1 + (reps + repsLeft) / 30) |
| `REPS_LEFT_MAX` | 4 | The "4+" button is stored and used as 4, a safe lower bound |
| `RIR_BIAS` | 0 | Honesty correction; fixed at 0 in Phase 1 |
| `BAD_DAY_RATIO` | 0.92 | A session below 92% of the estimate is a bad day |
| `BAD_DAYS_TO_COUNT` | 2 | Two bad days in a row move the estimate halfway down |
| `FIRST_SET_GAP` | 2 | A first set 2 or more reps-to-failure off the prediction moves the remaining sets one weight step |
| `REP_DROP_PER_SET` | 1 | Sets 2 and 3 target one rep fewer each |
| `FIRST_TARGET_ABOVE_LOW` | 1 | A first prescription aims at the bottom of the range plus 1 |
| `RAMP_REPS` | 8 | Find-your-weight ramp uses sets of 8 |
| `RAMP_MAX_SETS` | 5 | At most 5 ramp sets per exercise |
| `RAMP_JUMP_4PLUS` | 1.2 | After a ramp set with 4+ left, the next weight is 20% heavier |
| `RAMP_STOP_LEFT` | 2 | Stop the ramp at 2 or fewer reps left, or when the user taps "That's my weight" at 3 left |
| `WARMUP` | 50% × 8, 70% × 4, 85% × 2 | First lift of each session only, rounded down to available weights |
| `DELOAD_WEIGHT` | 0.9 | Deload weight = 90% of the normal weight, rounded down |
| `DELOAD_SETS` | half, rounded up | |
| `DELOAD_LEFT` | 4 | Stop deload sets with 4 reps left |
| `HOLD_STEP_S` / `HOLD_MAX_S` | 5 / 90 | Timed holds: when every set reaches the top of the range, add 5 s to both ends, up to 90 s |
| Weight step, barbell or Smith, upper body | 2.5 kg | 1.25 kg a side |
| Weight step, barbell or Smith, lower body | 5 kg | 2.5 kg a side |
| Weight step, dumbbells | next available pair | |
| Weight step, machines and cables | next plate on that stack | |

**Big lifts:** presses, rows, pulldowns, pull-ups, chin-ups, dips, squats, leg presses, deadlifts, hip thrusts, lunges and split squats. **Small lifts:** raises, curls, pushdowns, extensions, flyes, face pulls, leg extensions, leg curls, calf raises and core work.

### Calendar and effort ramp

`week = floor((today − plan_start) / 7) + 1`. Weeks 1–2 are calibration. From week 3, `k = (week − 3) mod 7`: k = 0–5 is block week k + 1, and k = 6 is the deload week (so weeks 9, 16, 23 and so on are deloads). The calendar keeps running through days off and away periods.

| Week | Big lifts stop with | Small lifts stop with | Working sets |
|---|---|---|---|
| Calibration week 1 | ramp (milestone 6) | ramp | final ramp set + 1 more |
| Calibration week 2 | 3 left | 3 left | 2 |
| Block weeks 1–2 | 3 left | 2 left | as programmed |
| Block weeks 3–4 | 2 left | 1 left | as programmed |
| Block week 5 | 1 left | 1 left | as programmed |
| Block week 6 | 1 left | 0 left | as programmed |
| Deload | 4 left | 4 left | half, rounded up |

### Skips, day off and make-up mode

| Name | Value | Meaning |
|---|---|---|
| `CHECKIN_DELAY_MIN` | 120 | Check-in fires 2 hours after the planned time if no session has started |
| `CHECKIN_DAYS_AHEAD` | 7 | Only the next 7 days are scheduled (iOS keeps 64 pending notifications); rebuild on every app open |
| `DAY_OFF_CUTOFF` | 22:00 the night before | User setting |
| `DAY_OFF_PER_MONTH` | 2 | The 3rd declared day off in a calendar month counts as an unexcused skip |
| `MAKEUP_WINDOW_DAYS` | 7 | Rolling window, including today |
| `MAKEUP_TRIGGER` | 2 | Unexcused skips in the window that start make-up mode |
| `MAKEUP_SESSIONS_PER_SKIP` | 2 | |
| `MAKEUP_MAX_SESSIONS` | 6 | |
| `MAKEUP_BLOCK` | 15 min, 2 exercises, 2 sets each | |
| `WEEKLY_SET_CAP` | 20 | Hard sets per muscle group in 7 days that make-up blocks never exceed |
| `SKIP_BANNER_AT` | 4 | Skips in 7 days that show the "different training time?" banner |
| `COMEBACK_STUB_GAP` | 7 days | If the last completed session is this old, make-up mode doesn't start |

### Away mode

| Name | Value |
|---|---|
| Sessions while away | `round(days × 3 / 7)`, at least 1 and at most 3 |
| Session days | Day 1 is travel (paused); sessions then go on alternate days (days 2, 4, 6) |
| Minutes per session | 20–45, default 30 |
| Pre-trip full body | If chest, back, quads or hamstrings-and-glutes would go more than 7 days untrained (days since last trained + trip length), the last session before leaving becomes the gym full-body template |

### Injury mode

| Name | Value |
|---|---|
| Pain light | 0–2 green (progress), 3–5 amber (hold), 6 or more, or any sharp pain, red (stop that exercise today and step back one phase) |
| Next-morning rule | Morning pain worse than the usual morning: step back one phase |
| Phase 1, calm it down | Affected exercises removed; moves on after at least 1 day once the morning check is green |
| Phase 2, rebuild | 40% of the pre-injury working weight, +10% per green session up to 60%; 12–20 reps; moves on after 2 green sessions |
| Phase 3, return | Original exercise at 60% of the pre-injury weight; +10% after a green session; amber holds; red steps back |
| Phase 4, back | At the pre-injury weight; 2 green sessions there ends injury mode |

### Food and body

| Name | Value |
|---|---|
| Protein target | 1.7 g per kg body weight, rounded to the nearest 5 g; show the 1.6–2.0 g/kg range |
| Calorie first guess | 36 × kg for lean bulk, 31 × kg for recomp, rounded to the nearest 100 |
| Goal suggestion | Waist less than half the height: lean bulk; otherwise recomp. The user can override. |
| Canteen oil | +45 kcal (1 tsp oil) per cooked item when "Canteen" is on |
| Weight average | Mean of morning weights in the last 7 days; shown only with 3 or more entries |

### Input limits (reject anything outside)

Exercise weight 0.5–500 kg; reps 0–50; reps left 0–4; timed hold 5–300 s; pain 0–10; body weight 30–250 kg; height 120–230 cm; waist 50–200 cm; age 16–90; minutes 10–120.

---

## 6. Milestones

> **Build note (Kotlin):** at the owner's request this was built as a native Android app in Kotlin (Jetpack Compose and Room), not with Expo. The Expo, TypeScript, ESLint, Prettier and Jest items were met with their Android equivalents, and the iOS-only items don't apply (`DECISIONS.md` #17–24). Every training rule is in the `engine/` module, and every number from section 5 is in `engine/src/main/kotlin/com/raisethebar/config/Rules.kt` (`DECISIONS.md` #89). `README.md` describes the architecture and how to build, test and install the app. The one open box is installing on the owner's phone, which is the owner's step (`DECISIONS.md` #82).

### Milestone 0: Project setup

- [x] Create the Expo + TypeScript (strict) project with ESLint, Prettier and Jest.
- [x] Add one passing engine test.
- [x] Add the design tokens and fonts from section 11; follow the phone's light or dark setting.
- [x] Create `config/rules.ts` with everything in section 5.
- [x] Create `DECISIONS.md` and copy section 12 into it.
- [x] Apply the release settings from milestone 14 now: Android backup off, no cleartext traffic, no logging of personal data.

**Done when:** the app opens to a placeholder Today screen in light and dark mode, and `npm test` passes.

### Milestone 1: Local database

- [x] Create these tables. Fields are a starting point; add indexes on dates and foreign keys.
  - `profile`: weight_kg, height_cm, waist_cm, age, diet_type (veg / egg / non_veg), experience, goal (lean_bulk / recomp), protein_target_g, kcal_target, day_off_cutoff, makeup_settings (json), notifications_on
  - `schedule`: weekday (0–6), is_training_day, time (HH:MM)
  - `plan`: template ("ppl6"), plan_start, rotation_index (0–5), previous (json, for undo)
  - `equipment`: id, name, kind (barbell / smith / dumbbell / machine / cable / bench / bodyweight / other), status (working / busy / broken / gone), status_date, broken_since, bar_kg, plate_pairs_kg (json), dumbbell_pairs_kg (json), stack_min, stack_max, stack_step, missing_kg (json)
  - `exercise`: id, name, aliases (json), counts_toward (json, muscle groups from section 7), equipment_ids (json), big_or_small, stresses (json, section 9), cues (json, 3 lines), instructions (json), images (json, bundled paths), unilateral, one_sided_version_id, per_side, timed, bodyweight_based, step_kg, source, licence
  - `program_day`: id (push_a, pull_a, legs_a, push_b, pull_b, legs_b), name, focus, order
  - `slot`: id, program_day_id, order, sets, rep_low, rep_high (or sec_low, sec_high for holds), rest_s, options (json, ranked exercise ids)
  - `estimate`: exercise_id, e1rm_kg, current_weight_kg, bad_days, sessions, calibrated, updated_at
  - `session`: id, local_date, program_day_id, kind (full / minimum / home / away / pre_trip / deload), block_week, started_at, completed_at
  - `set_log`: id, session_id, exercise_id, slot_id, set_no, weight_kg, reps, reps_left, seconds, pain, sharp, is_warmup, is_ramp, is_makeup, in_injury_mode, logged_at
  - `day_log`: local_date, planned, reason (sick / injured / gym_closed / going_later / skipping / null), reason_at, outcome (done / excused / day_off / away / skip)
  - `absence`: id, kind (day_off / away), start, end, equipment_there (none / bodyweight / dumbbells / gym), minutes_per_day, created_at, cancelled_at
  - `makeup`: id, triggered_on, skips_counted, sessions_left, paused, updated_at
  - `injury`: id, area, side, status (watch / active / resolved), phase (1–4), started, red_flag, checked_by_doctor, pre_injury (json: exercise id → e1rm and working weight), green_streak
  - `pain_log`: id, injury_id, local_date, kind (set / morning), value, sharp
  - `food`: id, name, unit, kcal, protein_g, diet_type, cooked, category
  - `usual_meal`: id, name, items (json), kcal, protein_g
  - `food_log`: id, local_date, meal (breakfast / lunch / snack / dinner), food_id or usual_meal_id, qty, kcal, protein_g, canteen, source (tap / usual)
  - `body_log`: local_date, weight_kg
  - `meta`: schema_version, last_evaluated_date, install_date
- [x] Numbered migrations; upgrading the app keeps all data.
- [x] Dates are local `YYYY-MM-DD` strings, times are ISO timestamps with offset, and a day ends at local midnight.
- [x] Every write runs in a transaction, and every input is checked against the limits in section 5 first.

**Done when:** a test creates, reads and updates every table, and a migration test passes.

### Milestone 2: Exercise library

- [x] Write `scripts/import-exercises` to run once at build time. The app itself never downloads anything.
- [x] The script downloads `https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json` (public domain, Unlicense, about 1 MB). Image paths in each record are relative: prefix them with `https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/`. Check the repo's `schema.json` for exact field names.
- [x] Match every exercise named in section 7 (first choices, backups, home and away versions, make-up candidates) to a record by name. Write the matches to `data/exercise_map.json` so a person can review them.
- [x] For each match, copy the name, instructions, muscles, equipment and images. Then add our fields: equipment ids, counts_toward, big_or_small (start from the record's `mechanic`, compound = big and isolation = small, then apply the lists in section 5), stress tags (section 9), three short form cues, aliases such as "lat pull", unilateral flags and step_kg.
- [x] Exercises with no match get a custom record without photos; list them in `DECISIONS.md`.
- [x] Convert the images to WebP at about 600 px wide and bundle them in `assets/exercises/`, keeping the total under about 15 MB.
- [x] Exercise page: start and end photos, muscles, three form cues, and backups in order. No video buttons in Phase 1.
- [x] Credit free-exercise-db in Settings → About.

**Done when:** every slot option in section 7 resolves to an exercise record, and the exercise page works in airplane mode.

### Milestone 3: The program

- [x] Encode section 7 as `data/program.json`: days, slots, ranked options, home versions, away templates and make-up candidates.
- [x] Rotation: Push A → Pull A → Legs A → Push B → Pull B → Legs B, then repeat. It advances only when a session is completed (full, minimum or home). A missed day never skips a workout: the next planned day does the same one.
- [x] Planned training days and times come from `schedule` (default Monday to Saturday, rest on Sunday).
- [x] Block calendar and effort targets as in section 5.
- [x] Warm-up sets for the first lift of every session (section 5).
- [x] Minimum session: the day's first three exercises, 2 sets each. It counts as done.
- [x] Home version for each day type (push, pull, legs). It counts as done.

**Done when:** for any date, the engine returns the right workout, block week, effort target and sets for every slot.

### Milestone 4: Setup screens (first launch)

- [x] **About you:** weight, height, age, diet type (veg, egg or non-veg), lifting experience, and old injuries (area, side, and "Does it hurt now?"). "Yes" opens injury triage after setup; "No" saves the injury as `watch`, which shows a caution note on related exercises.
- [x] **Your goal:** waist and height. Suggest lean bulk or recomp using section 5, let the user choose, then show the protein target and calorie guess (both editable).
- [x] **Your week:** training weekdays and a time for each, plus the rest day. Ask for notification permission here.
- [x] **My Gym:** the equipment checklist in section 8, prefilled with a typical office gym so the user only unticks. Dumbbell pairs, bar weights, plate pairs, machine stacks (min, max and step). Mark anything broken.
- [x] **Your rules:** day-off cut-off (default 22:00 the night before) and make-up settings (defaults from section 5; allowed ranges: trigger 2–3, sessions per skip 1–3, cap 3–8, block 10–20 min).
- [x] Everything stays editable in Settings.

**Done when:** a fresh install finishes setup in about two minutes and Today shows calibration week 1, Push A.

### Milestone 5: My Gym and exercise selection

- [x] Statuses: **working**; **busy** (today only, resets at midnight); **broken** (until fixed; every 3 days the app asks "Is it fixed yet?"); **gone** (permanent).
- [x] Available weights per item: dumbbells are the listed pairs; barbell and Smith are the bar plus any combination of plate pairs; machines run from stack min to max by step. Anything in `missing_kg` is removed.
- [x] `stepUp` and `stepDown` use the steps in section 5 and return null at either end.
- [x] `pickExercise(slot)`: the first ranked option whose equipment is all working and whose stress tags don't clash with an active injury. If none qualifies, use the day's home version.
- [x] The user can promote any backup to first choice for a slot (section 7).
- [x] During a workout, "Machine busy?" offers: wait and do the next exercise first (only if it uses different equipment), or use the next backup for today only.
- [x] A backup with no estimate starts with the find-your-weight ramp (milestone 6).
- [x] A "report it" button copies this message: "Hi, the {equipment} in the office gym has been out of order since {date}. Could someone take a look? Thanks."

**Done when:** marking the leg press broken makes Legs A slot 2 use the hack squat (or walking lunges if there's no hack squat), and marking it fixed restores the leg press.

### Milestone 6: Auto-enhancer core

- [x] Implement these in `engine/estimates.ts` (reference logic; keep the names):

```ts
// repsLeft is capped at 4; RIR_BIAS is 0 in Phase 1
e1rm(w, reps, left) = w * (1 + (reps + Math.min(left, 4) + RIR_BIAS) / 30)
predictedReps(E, w, left) = Math.round(30 * (E / w - 1) - left)

nextWeight(est, slot, left, weights):
  w = est.currentWeight
  if (w == null):
    // first time: heaviest weight predicting at least repLow + 1 reps, else the lightest
    w = max(x in weights where predictedReps(E, x, left) >= slot.repLow + 1) ?? min(weights)
  else if (predictedReps(E, w, left) > slot.repHigh):
    up = stepUp(w)
    if (up != null && predictedReps(E, up, left) >= slot.repLow) w = up   // earned it, one step only
    // otherwise stay and add reps: "the next weight up is too big a jump"
  while (predictedReps(E, w, left) < slot.repLow && stepDown(w) != null) w = stepDown(w)
  set1Target = max(1, predictedReps(E, w, left))
  setNTarget = set1Target - (n - 1) * REP_DROP_PER_SET
  outgrown = (w is the heaviest available) && predictedReps(E, w, left) > slot.repHigh

afterFirstSet(E, set):
  gap = (set.reps + Math.min(set.left, 4)) - predictedReps(E, set.weight, 0)
  if (gap <= -2) remaining sets use stepDown(set.weight)   // much harder than predicted
  if (gap >= 2)  remaining sets use stepUp(set.weight)     // much easier
  otherwise the weight stays

afterSession(est, workingSets):   // skip entirely for deload and injury-mode sets
  S = max(e1rm(s) for s in workingSets)                 // no warm-up sets
  if (S >= E)              { E = S; badDays = 0 }        // gains count in full
  else if (S >= 0.92 * E)  { E = (E + S) / 2; badDays = 0 }
  else { badDays += 1; if (badDays >= 2) E = (E + S) / 2 }   // one bad day is noise
  currentWeight = the prescribed set-1 weight, not the in-session adjusted one
```

- [x] Find-your-weight ramp, used in calibration week 1 and the first time any exercise or backup is used: the user picks a light starting weight (suggest the empty bar, the lightest stack plate, or 5 kg dumbbells). After each set of 8: with 4+ left, suggest 20% heavier; otherwise suggest the weight predicting 8 reps with 2 left, at least one step up. Stop at 2 or fewer reps left, when the user taps "That's my weight" at 3 left, or after 5 sets. The starting estimate is the highest e1RM among the ramp sets. The final ramp set counts as working set 1; one more set follows at the same weight.
- [x] Bodyweight exercises (pull-ups, chin-ups, dips, push-ups, hanging knee raises): effective load = body weight (latest weigh-in, else setup weight) + added weight − assistance. Run the same maths on effective load, then convert back to added or assistance kg.
- [x] Timed holds: target the bottom of the range; when every set reaches the top, add 5 s to both ends next time, up to 90 s.
- [x] Per-side exercises: weight is per dumbbell, reps are per side.
- [x] Deload week: half the sets (rounded up), 90% of the weight rounded down, stop with 4 left. Deload sets don't change estimates.
- [x] When `outgrown` is true, show "Ready for a harder version" on the exercise. Don't switch automatically in Phase 1.

**Done when:** the tests in sections 13.1 and 13.2 pass.

### Milestone 7: Today and the workout log

- [x] **Today:** the date and block label ("Calibration, week 1", "Block 1, week 3" or "Deload week"); today's workout and focus; every exercise with sets × reps, "stop with N left" and the weight, with an up arrow when the weight went up; a note when a backup replaced the first choice ("Row machine broken: backup 1"); make-up status when active. Buttons: Start workout, Minimum session, Gym closed, Something hurts, Plan changes.
- [x] **Session screen:** exercises in order, with the make-up block (if any) at the end.
- [x] **Set logger:** a weight stepper limited to available weights, a reps stepper (or a hold timer for timed holds), reps-left buttons 0, 1, 2, 3 and 4+, and Log set.
- [x] The rest timer starts by itself after each set, using the slot's rest time. Store the end time rather than counting ticks, so it survives the screen locking, and vibrate at zero.
- [x] After the first working set, run the first-set check and say what changed, for example: "Harder than predicted, so sets 2 and 3 drop to 50 kg."
- [x] "Machine busy? Swap" and "Something hurts" links on the set logger.
- [x] **Finish:** mark the session complete, run `afterSession` for each exercise, advance the rotation, and update weekly sets per muscle group (section 7 explains the counting).
- [x] An unfinished session resumes exactly where it was after the app is closed or killed.
- [x] Gym closed: start the home version of today's day type.

**Done when:** a full Push A can be logged in airplane mode, and killing the app mid-workout loses nothing.

### Milestone 8: Skips, check-ins and the midnight timeout

- [x] Planned days are the schedule's training days, minus declared days off and away days.
- [x] **Check-in:** a local notification at the planned time + 2 hours if no session has started that day. Tapping it opens the check-in sheet: Going later (pick a new time and reschedule the check-in), Minimum session, Gym closed (home version), Sick, Injured (opens injury mode), or Skipping today. Starting a session cancels that day's check-in.
- [x] Schedule only the next 7 days of check-ins, and rebuild them every time the app opens.
- [x] **Sick:** show the neck-check rule from section 9. The day is excused, and make-up mode pauses.
- [x] **Day off:** declared any time until the cut-off (default 22:00 the night before). At most 2 per calendar month; warn before saving a 3rd, which counts as an unexcused skip. A day off doesn't advance the rotation.
- [x] **The timeout**, which needs no background job: on every app start and resume, check each planned day from `meta.last_evaluated_date` up to yesterday.
  - Done: a full, minimum, home or away session was completed that day. A session counts for the day it started, even if it ends after midnight.
  - Excused: sick or injured was recorded before that day's midnight.
  - Day off or away: planned absence, not a skip.
  - Otherwise: unexcused skip. Reasons timestamped after midnight are rejected.
- [x] **Skip screen** (in Progress): sessions done this month, adherence (done ÷ planned, where excused days still count as planned), every skip with its reason, skips in the last 7 days, and make-up sessions left.

**Done when:** the tests in section 13.3 pass.

### Milestone 9: Make-up mode

- [x] On each new unexcused skip, count unexcused skips in the last 7 days, including today. With 2 or more, set `sessions_left = min(2 × count, 6)`. This also restarts the clock if make-up mode is already running. One skip changes nothing except the count.
- [x] While `sessions_left > 0`, append a 15-minute block to each session: 2 exercises, 2 sets each, at the usual effort target. Pick the 2 muscle groups furthest below their weekly targets over the last 7 days (section 7), skipping any group trained in today's workout and any group already at 20 hard sets. Use each group's first available make-up candidate. If only one group qualifies, use 2 of its candidates.
- [x] `sessions_left` drops by 1 only when a block is logged. A minimum session still gets its block.
- [x] Pause make-up mode (no blocks, count frozen) on sick days and while any injury is in phase 1.
- [x] Comeback stub: if the last completed session was 7 or more days ago, don't start make-up mode; just resume the rotation. Comeback mode itself is Phase 2.
- [x] With 4 or more skips in 7 days, show a banner: "Four skips this week. Would a different training time work better?"
- [x] Today shows make-up status ("Make-up mode: 3 sessions left"), matching the After a skip wireframe in `plan.html`.

**Done when:** the tests in section 13.4 pass.

### Milestone 10: Plan changes (day off and away mode)

- [x] A Plan changes screen with two options: One day off, and Away mode. Don't show maintenance mode, time-fit or other day counts (Phase 2).
- [x] **Away form:** start date, end date, what you'll have there (no time, bodyweight or backpack, dumbbells, or a gym), and minutes per day (20–45, default 30).
- [x] **Away rules:**
  - No time: every day is paused, with no sessions.
  - Bodyweight or dumbbells: sessions as in section 5, using the matching away template (section 7).
  - A gym: the normal plan continues, and skips count as usual.
  - Away days are never planned days, so they can't be skips or start make-up mode.
- [x] **Before leaving:** apply the pre-trip full-body rule in section 5.
- [x] **Coming back:** the next planned day does the next workout in the rotation.
- [x] **Preview before saving:** each affected date and what happens on it, plus "No skips counted". Confirm or Edit.
- [x] **Undo:** store the previous state. Undo before the change starts restores it exactly; undo during a trip cancels the remaining away days, and planned training resumes the next day.

**Done when:** the tests in section 13.5 pass, including the "going home Thursday to Sunday" example.

### Milestone 11: Injury mode

- [x] Entry points: Today, the set logger, the check-in's Injured option, and setup.
- [x] **Triage:** area (shoulder, elbow, wrist, lower back, knee, hip, ankle, neck, or a muscle), side (left, right or both), what it feels like, and pain right now (0–10).
- [x] **Soreness pattern** (dull, spread across the muscle, often both sides, 12–48 hours after training, eases as you warm up): no injury mode. Suggest a longer warm-up.
- [x] **Red flags** (section 9): any "yes" shows the stop screen and locks the area's exercises until the user confirms "I've been checked". Chest pain, dizziness or unusual breathlessness shows the "stop now and get medical help" screen.
- [x] Apply the area rules in section 9: remove clashing exercises, use the listed alternatives, and keep everything else.
- [x] Record pain (0–10, plus a "sharp" toggle) on every set that uses the injured area, and ask for a morning rating on the first app open of each day.
- [x] Phases, loads and moves between them follow section 5.
- [x] Save pre-injury estimates for affected exercises when injury mode starts. In injury mode, weights come from those saved values × the phase percentage, and sets don't update live estimates.
- [x] **One-sided injury:** the healthy side switches to the one-arm or one-leg version where the exercise has one.
- [x] **Strained muscle:** in phase 1, remove exercises where that muscle is the main target, then follow the same phases.
- [x] Show this note in injury mode: "General training guidance, not a diagnosis. If it isn't clearly improving within two weeks, see a physiotherapist."

**Done when:** the tests in section 13.6 pass.

### Milestone 12: Food and body

- [x] Seed `food` from section 10, and add the three example usual meals there (the user can delete them).
- [x] Protein target and calorie guess as in section 5, recalculated when body weight changes by 2 kg or more.
- [x] **Food screen:** a protein ring (today against target), quick-add chips filtered by diet type, quantity steppers, meal slots (breakfast, lunch, snack, dinner), a Canteen toggle, and today's totals. Calories are shown smaller than protein.
- [x] **Usual meals:** save a meal from logged items, or enter totals after weighing it once. Logging one is a single tap.
- [x] **Morning weight:** a quick entry, the 7-day average and the change from the previous 7 days (section 5).

**Done when:** the tests in section 13.7 pass.

### Milestone 13: Progress, Settings, export and import

- [x] **Progress tab, Phase 1 version:** session history, the skip screen (milestone 8), the weight average and active injuries. No charts.
- [x] **Settings:** profile, goal, schedule, rules, notifications, a My Gym shortcut, and About (credits and the general-guidance note).
- [x] **Export:** all tables as one JSON file with `schema_version`, shared through the share sheet.
- [x] **Import:** validate the file first (version, structure, value ranges), show what it contains, then replace all data after the user confirms. Never execute anything from the file.

**Done when:** export, delete the app, reinstall and import restores everything.

### Milestone 14: Security, QA and install

Security:

- [x] No secrets, tokens or API keys anywhere in the repo or the app; Phase 1 needs none. Add a secret scan to the repo checks.
- [x] No network requests at all. Android blocks cleartext traffic; iOS keeps App Transport Security on with no exceptions.
- [x] Android `android:allowBackup="false"`, so health data stays out of device backups. Export is the backup.
- [x] Release builds aren't debuggable, and personal data (weights, injuries, food) is never logged.
- [x] SQL uses bound parameters only. No `eval` or `new Function`.
- [x] Optional: hide the app's content in the app switcher.

QA:

- [x] Every test in section 13 passes, plus at least one test per rule in section 5.
- [x] An airplane-mode run-through: setup, a workout, a skip, a day off, a trip, an injury, and food logging.
- [x] Clock tests across midnight and month ends, with the device time zone set to India (Asia/Kolkata).
- [x] Light and dark mode, the largest text size, tap targets of at least 44 pt, and colour never the only signal.
- [x] A simulated week, Monday to Saturday with one unexcused skip and one declared day off, gives the right outcomes, rotation and make-up status.
- [ ] Install on the owner's phone, as a development build or an Android APK.

**Done when:** every box in this file is ticked.

---

## 7. The program (seed data)

Muscle groups the app tracks: `chest`, `back`, `delts` (side and rear), `biceps`, `triceps`, `quads`, `hams_glutes`, `calves`, `core`. Shoulder presses train the front delts, which aren't tracked separately.

**Counting weekly sets:** each working set counts 1 toward every group in its exercise's "Counts toward" list. Warm-ups and the earlier ramp sets don't count; the final ramp set, deload sets, make-up sets and home or away sets do.

Rest times: "2–3 min" is stored as 150 s, "2 min" as 120 s, "90 s" as 90 s, "60–90 s" as 75 s and "60 s" as 60 s.

### Push A: chest focus

| # | First choice | Sets × reps | Rest | Counts toward | Backups, in order |
|---|---|---|---|---|---|
| 1 | Barbell bench press | 3 × 6-8 | 150 s | chest | Smith machine bench press; dumbbell bench press; chest press machine |
| 2 | Incline dumbbell press | 3 × 8-12 | 120 s | chest | Incline Smith press; incline press machine |
| 3 | Seated dumbbell shoulder press | 3 × 8-12 | 120 s | none | Shoulder press machine |
| 4 | Cable fly | 3 × 12-15 | 90 s | chest | Pec deck; dumbbell fly |
| 5 | Dumbbell lateral raise | 3 × 12-15 | 75 s | delts | Cable lateral raise; lateral raise machine |
| 6 | Triceps rope pushdown | 3 × 10-15 | 75 s | triceps | Straight-bar pushdown; bench dips |

### Pull A: lats and width

| # | First choice | Sets × reps | Rest | Counts toward | Backups, in order |
|---|---|---|---|---|---|
| 1 | Lat pulldown | 3 × 6-10 | 150 s | back | Pull-ups; assisted pull-up machine |
| 2 | Seated cable row | 3 × 8-12 | 120 s | back | Seated row machine; chest-supported dumbbell row |
| 3 | One-arm dumbbell row (per side) | 3 × 8-12 | 90 s | back | One-arm cable row; machine row |
| 4 | Face pull with rope | 3 × 12-15 | 75 s | delts | Reverse pec deck; bent-over rear-delt raise |
| 5 | EZ-bar curl | 3 × 8-12 | 90 s | biceps | Barbell curl; dumbbell curl; cable curl |
| 6 | Hammer curl | 3 × 10-12 | 75 s | biceps | Rope cable hammer curl |

### Legs A: quads, plus core

| # | First choice | Sets × reps | Rest | Counts toward | Backups, in order |
|---|---|---|---|---|---|
| 1 | Barbell back squat | 3 × 6-8 | 150 s | quads | Smith machine squat; goblet squat |
| 2 | Leg press | 3 × 10-12 | 120 s | quads | Hack squat; walking lunges |
| 3 | Leg extension | 3 × 12-15 | 90 s | quads | Step-ups; slow goblet squat |
| 4 | Lying leg curl | 3 × 10-12 | 90 s | hams_glutes | Seated leg curl; Swiss-ball leg curl |
| 5 | Standing calf raise | 3 × 10-15 | 60 s | calves | Calf raise on the leg press; Smith calf raise |
| 6 | Cable crunch | 3 × 10-15 | 60 s | core | Ab crunch machine; decline crunch |
| 7 | Plank (timed) | 2 × 30-60 s | 60 s | core | Dead bug |

### Push B: shoulder focus

| # | First choice | Sets × reps | Rest | Counts toward | Backups, in order |
|---|---|---|---|---|---|
| 1 | Barbell overhead press | 3 × 6-8 | 150 s | none | Smith overhead press; shoulder press machine; seated dumbbell shoulder press |
| 2 | Incline barbell bench press | 3 × 8-10 | 120 s | chest | Incline Smith press; incline dumbbell press; incline press machine |
| 3 | Cable lateral raise | 4 × 12-15 | 75 s | delts | Dumbbell lateral raise; lateral raise machine |
| 4 | Dips (assisted if needed) | 3 × 8-12 | 120 s | chest, triceps | Assisted dip machine; close-grip bench press; close-grip Smith press; chest press machine |
| 5 | Cable overhead triceps extension | 3 × 10-12 | 90 s | triceps | Dumbbell overhead triceps extension; EZ-bar skull crusher |
| 6 | Reverse pec deck | 3 × 12-15 | 75 s | delts | Face pull with rope; bent-over rear-delt raise |

### Pull B: back thickness and arms

| # | First choice | Sets × reps | Rest | Counts toward | Backups, in order |
|---|---|---|---|---|---|
| 1 | Barbell row | 3 × 6-8 | 150 s | back | T-bar row; machine row |
| 2 | Close-grip lat pulldown | 3 × 8-12 | 120 s | back | Neutral-grip lat pulldown; chin-ups; assisted chin-ups |
| 3 | Chest-supported row machine | 3 × 10-12 | 90 s | back | Chest-supported dumbbell row on an incline bench; wide-grip seated cable row |
| 4 | Incline dumbbell curl | 3 × 10-12 | 90 s | biceps | Standing dumbbell curl |
| 5 | Preacher curl machine | 3 × 12-15 | 75 s | biceps | EZ-bar preacher curl; cable curl |
| 6 | Hanging knee raise | 3 × 10-15 | 60 s | core | Captain's chair knee raise; lying leg raise |

### Legs B: hamstrings and glutes, plus core

| # | First choice | Sets × reps | Rest | Counts toward | Backups, in order |
|---|---|---|---|---|---|
| 1 | Barbell Romanian deadlift | 3 × 8-10 | 150 s | hams_glutes | Dumbbell Romanian deadlift; Smith Romanian deadlift |
| 2 | Dumbbell Bulgarian split squat (per leg) | 3 × 8-12 | 120 s | quads | Walking lunges; Smith split squat |
| 3 | Seated leg curl | 3 × 10-12 | 90 s | hams_glutes | Lying leg curl; Swiss-ball leg curl |
| 4 | Barbell hip thrust | 3 × 8-12 | 120 s | hams_glutes | Smith hip thrust; hip thrust machine; glute bridge; cable pull-through |
| 5 | Seated calf raise | 3 × 12-15 | 60 s | calves | Calf raise on the leg press |
| 6 | Pallof press (per side) | 3 × 10-12 | 60 s | core | Bird dog |
| 7 | Side plank (timed, per side) | 2 × 30-45 s | 60 s | core | Suitcase carry |

**Sunday: rest.** Show "Walk 8–10k steps and stretch for 10 minutes" as text only.

**First-choice preference:** the user can promote any backup to first choice for a slot (for example pull-ups once they can do 6 or more).

### Weekly targets (hard sets per muscle group, rolling 7 days)

| chest | back | delts | biceps | triceps | quads | hams_glutes | calves | core |
|---|---|---|---|---|---|---|---|---|
| 15 | 18 | 13 | 12 | 9 | 12 | 12 | 6 | 13 |

These match the full program. Make-up mode measures how far each group is below its target.

### Home versions (gym closed, about 30 minutes)

| Day type | Exercises (counts toward) |
|---|---|
| Push | Push-ups, feet raised once 20 is easy, 3 × stop with 2 left (chest, triceps); pike push-ups 3 × 8-12 (none); chair dips 3 × 10-15 (triceps); plank 2 × 45 s (core) |
| Pull | One-arm backpack row, loaded with books, 3 × 10-15 per arm (back); backpack curl 3 × 12-15 (biceps); face-down Y and T raises 3 × 12 (delts); superman hold 2 × 30 s (none) |
| Legs | Bulgarian split squat with a backpack 3 × 10-15 per leg (quads); one-leg Romanian deadlift 3 × 10 per leg (hams_glutes); one-leg glute bridge 3 × 12 per leg (hams_glutes); calf raises on a step 3 × 15-20 (calves) |

### Away templates (proposed here; the plan only says "full body")

| Template | Exercises (counts toward) |
|---|---|
| Bodyweight full body, about 25 min | Push-ups 3 × stop with 2 left (chest, triceps); one-arm backpack row 3 × 10-15 (back); Bulgarian split squat with a backpack 3 × 10-15 (quads); one-leg Romanian deadlift 2 × 10 (hams_glutes); plank 2 × 45 s (core) |
| Dumbbell full body, about 30 min | Goblet squat 3 × 8-12 (quads); dumbbell bench or floor press 3 × 8-12 (chest); one-arm dumbbell row 3 × 8-12 (back); dumbbell Romanian deadlift 3 × 8-12 (hams_glutes); dumbbell lateral raise 2 × 12-15 (delts) |
| Gym full body before a trip, about 55 min | Barbell back squat or leg press 3 × 6-10 (quads); barbell bench press 3 × 6-8 (chest); seated cable row 3 × 8-12 (back); barbell Romanian deadlift 3 × 8-10 (hams_glutes); lat pulldown 2 × 8-12 (back); dumbbell lateral raise 2 × 12-15 (delts) |

Away and home sets rest 90 s for big lifts and 60 s for small ones.

### Make-up candidates (the first available one is used)

| Group | Candidates, in order |
|---|---|
| chest | Chest press machine; incline dumbbell press; cable fly |
| back | Seated cable row; lat pulldown; one-arm dumbbell row |
| delts | Dumbbell lateral raise; reverse pec deck; face pull with rope |
| biceps | Hammer curl; EZ-bar curl |
| triceps | Triceps rope pushdown; dumbbell overhead triceps extension |
| quads | Leg press; leg extension |
| hams_glutes | Lying leg curl; seated leg curl; barbell hip thrust |
| calves | Standing calf raise; seated calf raise |
| core | Cable crunch; Pallof press |

Make-up sets use the exercise's rep range from the program (10-15 if it isn't in the program) and 60 s rest, so a block fits in 15 minutes.

---

## 8. Equipment checklist (setup and My Gym)

Prefill all of these as "have it", and let the user untick what's missing.

- Dumbbells: list of pairs (for example 2.5 to 30 kg) and any missing pairs
- Adjustable bench (flat and incline)
- Barbell: bar weight and plate pairs; EZ bar
- Squat rack or power rack
- Smith machine: bar weight
- Cable station (single or dual) with rope, straight bar and single handles
- Lat pulldown; seated row; T-bar row
- Chest press, incline press, shoulder press and lateral raise machines
- Pec deck / reverse pec deck
- Leg press; hack squat; leg extension; lying leg curl; seated leg curl
- Standing calf raise; seated calf raise; hip thrust machine
- Assisted pull-up and dip machine; pull-up bar; dip station; captain's chair
- Preacher curl bench or machine; ab crunch machine; chest-supported row machine
- Swiss ball; resistance bands
- Treadmill, bike or stairs (not used by the Phase 1 plan)

For every machine and cable: stack min, max and step in kg.

---

## 9. Injury rules (data for injury mode)

### Area rules

| Area | Taken out | Used instead | Keeps going |
|---|---|---|---|
| Shoulder | Overhead press, dips, wide-grip bench, upright rows | Neutral-grip dumbbell or machine press over a shorter range, cable rows, external-rotation work, lateral raises only if painless | Legs, core, most rows |
| Elbow | Heavy curls, skull crushers, pull-ups, close-grip pressing | Straps for pulling, neutral grips, lighter high-rep arm work, wrist holds | Legs; chest press if painless |
| Wrist | Barbell pressing, push-ups on flat palms, front squats | Machines with neutral handles, push-ups on fists or handles, straps | Legs, with straps for deadlifts |
| Lower back | Barbell rows, Romanian deadlifts, back squats, standing overhead press, sit-ups | Chest-supported rows, leg press at moderate depth, leg curl and extension, hip thrust if painless, curl-ups, side planks, bird dogs, walking | Upper body on machines or with chest support |
| Knee | Deep squats, lunges, heavy leg extensions, jumping | Box squat to a pain-free depth, partial-range leg press, Romanian deadlift, hip thrust, leg curl, wall-sit holds, cycling | All upper body |
| Hip | Deep squats, deep leg press, lunges, wide stances | Higher box squat, hip thrust or glute bridge if painless, leg extension and curl | All upper body |
| Ankle | Standing calf raises, lunges, jumping | Seated calf raise if painless, leg extension and curl, seated exercises | Upper body, seated |
| Neck | Shrugs, heavy overhead work, heavy carries | Supported and machine exercises with the head kept neutral | Leg machines, most upper body |
| A strained muscle | Heavy and deep-stretch work for that muscle | Light holds, then light full-range sets, building back as pain allows | Everything else |

Implement this with stress tags on each exercise (for example `overhead_press`, `dip`, `wide_grip_press`, `upright_row`, `heavy_curl`, `skull_crusher`, `pull_up`, `close_grip_press`, `barbell_press`, `flat_palm_push_up`, `front_squat`, `barbell_row`, `loaded_hinge`, `back_squat`, `standing_overhead`, `sit_up`, `deep_knee_bend`, `lunge`, `heavy_leg_extension`, `jump`, `deep_hip_flexion`, `wide_stance`, `standing_calf`, `shrug`, `heavy_carry`). Each area lists the tags it removes, in `data/injury_rules.json`. Exercises marked "if painless" stay, but need a pain rating on every set.

### Red flags (any "yes" stops injury mode and locks the area)

- Heard or felt a pop
- Swelling or bruising that appeared quickly
- Numbness, tingling, or pain shooting down a limb
- Can't put weight on it or lift the limb
- Pain at night or at rest
- Hasn't improved in two weeks
- A sudden pop with bruising in the chest or the front of the upper arm can be a torn tendon: see a doctor quickly
- **Stop now and get medical help:** chest pain, dizziness or unusual breathlessness while training

Stop screen text: "See a doctor or physio before training this area. Tap 'I've been checked' when you have."

### Sick-day rule (shown on the Sick option)

Symptoms only above the neck, like a runny nose or a mild sore throat: train lighter or do the minimum session. Fever, chest congestion, a stomach bug, body aches or real fatigue: rest, and the day is excused. If you might be contagious, stay out of the shared office gym.

---

## 10. Food seed data (Phase 1 quick portions)

Rough values from the plan's portion guide. Phase 2 replaces them with the Indian Nutrient Databank. Diet filter: `veg` items show for everyone, `egg` items for egg and non-veg, `non_veg` items for non-veg only. "Cooked" items get the canteen oil when Canteen is on.

| Food | Portion | kcal | Protein (g) | Diet | Cooked |
|---|---|---|---|---|---|
| Roti or chapati, medium, no ghee | 1 | 100 | 3 | veg | no |
| Aloo paratha with ghee | 1 | 280 | 6 | veg | yes |
| Rice, cooked | 1 katori | 200 | 4 | veg | no |
| Dal tadka | 1 katori | 150 | 7 | veg | yes |
| Rajma or chole | 1 katori | 220 | 9 | veg | yes |
| Dry veg sabzi | 1 katori | 120 | 3 | veg | yes |
| Idli | 2 | 120 | 4 | veg | no |
| Plain dosa with sambar | 1 dosa and 1 katori | 280 | 9 | veg | yes |
| Poha or upma | 1 plate | 250 | 5 | veg | yes |
| Banana | 1 medium | 105 | 1 | veg | no |
| Paneer | 100 g (8-10 cubes) | 280 | 18 | veg | no |
| Paneer sabzi (bhurji, palak paneer) | 1 katori | 270 | 13 | veg | yes |
| Curd (dahi) | 1 katori | 100 | 5 | veg | no |
| Hung curd or Greek yogurt | 1 katori | 140 | 12 | veg | no |
| Toned milk | 1 glass (250 ml) | 150 | 8 | veg | no |
| Whole egg | 1 | 75 | 6 | egg | no |
| Egg whites | 3 | 50 | 11 | egg | no |
| Soya chunks | 30 g dry (about 1 katori cooked) | 105 | 15 | veg | yes |
| Chicken breast, cooked | palm-sized, about 100 g | 165 | 30 | non_veg | no |
| Chicken curry | 1 katori | 250 | 20 | non_veg | yes |
| Whey protein | 1 scoop (about 30 g) | 120 | 24 | veg | no |
| Roasted chana | 1 handful (30 g) | 110 | 6 | veg | no |
| Peanuts | 1 handful (30 g) | 170 | 7 | veg | no |
| Ghee or oil | 1 tsp | 45 | 0 | veg | no |
| Chai with milk and sugar | 1 cup | 90 | 2 | veg | no |
| Samosa | 1 | 250 | 4 | veg | no |
| Gulab jamun | 1 | 150 | 2 | veg | no |

Example usual meals (seeded; the user can delete them):

- **Office thali:** 2 roti, 1 katori dal tadka, 1 katori rice, 1 katori dry veg sabzi, 1 katori curd. 770 kcal, 25 g protein, or 860 kcal with Canteen on.
- **Oats breakfast:** 40 g oats cooked in a glass of milk, 1 banana, 1 tbsp peanut butter. 500 kcal, 18 g, entered as totals.
- **After-gym shake:** 1 scoop whey in water and 1 banana. 225 kcal, 25 g.

---

## 11. Design (from `plan.html`)

- **Fonts:** Barlow Condensed (600, 700, 800) for headings and big numbers; Barlow (400 to 700) for text. Both are Google Fonts; bundle them.
- **Day colours:** push days red, pull days blue, leg days green. Gold marks weight increases and active modes; grey marks rest and "free" states.
- **Feel:** big tap targets, steppers, segmented reps-left buttons, chips, and cards with a coloured top border for each day type. The pain scale 0–10 is tinted green (0–2), amber (3–5) and red (6–10).

| Token | Light | Dark |
|---|---|---|
| bg | #E6E8EB | #1A1D21 |
| surface | #FBFBFA | #23272D |
| surface-2 | #EEF0F2 | #2C3138 |
| ink | #1C1F24 | #EEF0F2 |
| ink-2 | #3C424B | #C8CDD4 |
| muted | #5C636E | #9AA2AD |
| line | #CDD2D8 | #363C44 |
| push | #C42F27 | #FF6D5E |
| pull | #1E4FB5 | #7EA0FF |
| legs | #1C7745 | #45C487 |
| gold (text) | #946600 | #F0C04A |
| plate-gold (fills) | #E3A500 | #F0C04A |
| rest | #7D848E | #8A929C |
| push-soft | #F5DDDA | #3B2220 |
| pull-soft | #DCE4F5 | #1E2944 |
| legs-soft | #D5EADD | #173123 |
| gold-soft | #F6EBCB | #3A3117 |
| rest-soft | #E3E6EA | #2C3138 |
| on-accent (text on push, pull and legs fills) | #FFFFFF | #14171A |

---

## 12. Decisions already made (copy into `DECISIONS.md`)

The plan left these open, so this checklist decided them:

1. Rest ranges are stored as one value (section 7).
2. Small lifts stop with 1 rep left in block week 5 and 0 in week 6 (the plan says 0–1).
3. The "4+" reps-left button is stored and used as 4.
4. Deload sets don't change estimates.
5. Timed holds progress by 5 s once every set reaches the top of the range, up to 90 s.
6. Bodyweight exercises use effective load: body weight + added weight − assistance.
7. The ramp stops at 2 or fewer reps left, or at 3 left when the user taps "That's my weight". The plan says 2–3, but its own example kept going after 3.
8. The block calendar keeps running through days off and trips.
9. The away templates and make-up candidates in section 7 are proposals; the plan only says "full body".
10. Weekly sets count only the groups in "Counts toward"; secondary muscles count for nothing.
11. Make-up mode pauses on sick days and while any injury is in phase 1.
12. The pre-trip rule only checks chest, back, quads and hamstrings-and-glutes.
13. Injury phase 1 ends after at least a day plus a green morning check; phase 2 ends after 2 green sessions.
14. The protein target is 1.7 g/kg rounded to 5 g, and the calorie guess rounds to the nearest 100.
15. Away sessions: `round(days × 3 / 7)`, between 1 and 3, on alternate days after a travel day.
16. Export and import are the Phase 1 backup, and Android device backup is off.

---

## 13. Golden tests (write these first)

### 13.1 Bench press, 3 × 6-8, barbell upper body (2.5 kg steps), plenty of plates

| Week | Target | Input | Expected |
|---|---|---|---|
| 1 | ramp | 40 × 8 with 3 left, then 45 × 8 with 2 left | estimate 60.0 |
| 2, prescription | 3 left | estimate 60.0 after the ramp | 45 kg × 7 |
| 2, result | | first set 45 × 8, 3 left | estimate 61.5 |
| 3, prescription | 3 left | current 45 kg | 45 kg × 8 |
| 3, result | | first set 45 × 9, 3 left | estimate 63.0 |
| 4, prescription | 3 left | current 45 kg | 47.5 kg × 7 (weight up) |
| 4, first-set check | | 47.5 × 4, 2 left | gap −4, so sets 2 and 3 drop to 45 kg |
| 4, result | | best sets: 47.5 × 4, 2 left and 45 × 6, 2 left (both 57.0) | bad day, since 57.0 < 57.96: estimate stays 63.0 and bad days = 1 |
| 5, prescription | 2 left | current 47.5 kg | 47.5 kg × 8 |
| 5, result | | 47.5 × 8, 2 left | estimate 63.33 and bad days = 0 |
| 6, prescription | 2 left | current 47.5 kg | 47.5 kg × 8 |
| 6, result | | 47.5 × 9, 2 left | estimate 64.92 |
| 7, prescription | 1 left | current 47.5 kg | 50 kg × 8 (weight up) |

### 13.2 Single-set cases

The set is the only data: the estimate is its e1RM and the current weight is its weight.

| Set | Next session | Expected |
|---|---|---|
| 45 kg × 9, 3 left | 6-8 reps, stop with 3 left, 2.5 kg steps | estimate 63.0; 47.5 kg × 7 |
| 45 kg × 4, 2 left | 6-8 reps, stop with 3 left, 2.5 kg steps | estimate 54.0; 40 kg × 8 |
| 7.5 kg × 15, 2 left | 10-12 reps, stop with 2 left, 2.5 kg steps (10 kg available) | estimate 11.75; stay at 7.5 kg × 15, "too big a jump" |

### 13.3 Skips and the timeout (training Monday to Saturday at 07:30, cut-off 22:00)

- Nothing logged on Tuesday and no reason: at Wednesday's first app open, Tuesday becomes an unexcused skip, and Wednesday's workout is the one Tuesday missed.
- "Sick" recorded Tuesday at 21:00: Tuesday is excused.
- A reason for Tuesday entered Wednesday at 00:10: rejected, and Tuesday stays a skip.
- A session started Tuesday at 23:30 and finished Wednesday at 00:40: Tuesday is done.
- A day off for Thursday declared Wednesday at 21:59 is accepted; at 22:01 it's refused.
- A third declared day off in one calendar month: a warning, then it's saved as an unexcused skip.
- The check-in fires at 09:30 for a 07:30 plan if no session has started.
- No more than 7 days of check-ins are scheduled, reopening the app rebuilds them, and starting a session cancels that day's check-in.

### 13.4 Make-up mode

- 1 unexcused skip in 7 days: no make-up mode; the count shows 1.
- 2 skips within 7 days: `sessions_left` = 4.
- 3 skips within 7 days: 6.
- 5 skips within 7 days: 6, and the banner shows.
- A new skip while `sessions_left` = 2 that makes 3 in the window: resets to 6.
- Today is Pull A: the block avoids back, biceps and delts. With quads and hams_glutes furthest behind, it adds leg press and lying leg curl, 2 sets each.
- A group already at 20 hard sets in the last 7 days is never picked.
- A minimum session during make-up mode still gets the block, and `sessions_left` drops only after the block is logged.
- A sick day: no block, and `sessions_left` doesn't change.
- Last completed session 8 days ago, then a skip: no make-up mode (comeback stub).

### 13.5 Day off and away mode

- Away Thursday 22 to Sunday 25 October 2026 with dumbbells, 30 minutes, set on Wednesday 21 October at 21:40. Preview: Thursday travel (paused); Friday dumbbell full body, 30 min; Saturday rest; Sunday dumbbell full body, 30 min; Monday the next workout in the rotation. Thursday to Sunday aren't planned days, so there are no skips and no make-up mode.
- Away for 7 days with bodyweight only: 3 sessions, on days 2, 4 and 6.
- Away with no time: every day paused and no sessions.
- Chest last trained 5 days before a 4-day trip: the last session before leaving becomes the gym full-body template.
- Undo before the trip restores the original plan exactly. Undo on day 2 of the trip cancels the remaining away days, and training resumes the next day.

### 13.6 Injury mode

- Left shoulder, pain 4, no red flags: overhead press, dips and wide-grip bench (and anything else with those tags) are replaced by the first allowed backup or listed alternative; legs and rows don't change.
- Any red flag: the stop screen shows, and the area's exercises stay locked until "I've been checked".
- Phase 3 at 60% of the pre-injury weight: after a green session the next one is 70%; after amber it stays 60%; after red it goes back to phase 2.
- Morning pain worse than the usual morning: back one phase.
- Phase 4: two green sessions at the pre-injury weight resolve the injury, and normal estimates continue from the saved pre-injury values.
- A soreness pattern (dull, both sides, eases with the warm-up): no injury mode, and the warm-up tip shows.
- Sets logged in injury mode don't change live estimates.

### 13.7 Food and body

- 70 kg, lean bulk: protein target 120 g (range 112–140 g) and 2,500 kcal.
- 70 kg, recomp: 2,200 kcal.
- Height 175 cm with waist 85 cm suggests lean bulk; waist 90 cm suggests recomp.
- Office thali (2 roti, dal tadka, rice, dry veg sabzi, curd): 770 kcal and 25 g protein. With Canteen on: 860 kcal (+45 each for the dal and the sabzi).
- Weigh-ins of 70.2, 70.6 and 70.4 kg in the last 7 days: average 70.4 kg. With only 2 weigh-ins, no average is shown.
- Diet type veg: egg and chicken items are hidden.
