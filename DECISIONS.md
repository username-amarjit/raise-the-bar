# Decisions

## Decisions already made by the checklist (section 12)

The plan left these open, so the checklist decided them:

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

## Decisions made while building

### Platform and tooling (milestone 0)

17. **Native Android in Kotlin instead of Expo/React Native.** The owner asked for a native Android app. The stack is Kotlin, Jetpack Compose, Room (SQLite) and AlarmManager notifications. iPhone support is dropped for Phase 1, so the iOS-only items (App Transport Security) don't apply.
18. **Module layout mirrors section 4.** `engine/` is a pure Kotlin/JVM Gradle module with no Android, database or device imports; the Android `app/` module depends on it and never the other way round. `config/rules.ts` became `engine/src/main/kotlin/com/raisethebar/config/Rules.kt`. `data/` stays at the repo root and is packaged into the engine jar under `rtb-data/`, so the app and the tests read the same files.
19. **Tooling equivalents.** ESLint + Prettier → ktlint (Gradle plugin) and Android Lint; Jest → JUnit 4 (engine) and Robolectric (database and screen smoke tests on the JVM). Kotlin's null safety plus `allWarningsAsErrors` in the engine stand in for TypeScript strict mode.
20. **Toolchain.** Gradle 9.8.2, Android Gradle Plugin 9.4.1 with built-in Kotlin, Kotlin 2.4.21, KSP 2.3.12, Room 2.8.5, Compose BOM 2026.09.00. compileSdk/targetSdk 37, minSdk 26 (Android 8.0), so `java.time` is available without desugaring.
21. **Release signing.** For personal sideloading, the release APK is signed with the local debug key. Replace it with a real keystore before distributing the app to anyone else.
22. **No network at all.** The manifest removes `INTERNET` and `ACCESS_NETWORK_STATE`, even if a library asks for them; cleartext is blocked by `network_security_config.xml`. Release builds strip `android.util.Log` calls with R8, and the app never logs personal data in any build.
23. **Backups.** `android:allowBackup="false"` plus `data_extraction_rules.xml` excluding every domain from cloud backup and device-to-device transfer (Android 12+ ignores `allowBackup` for transfers).
24. **Tap targets.** 48 dp minimum (Android's guideline), which is above the 44 pt the checklist asks for.

### Local database (milestone 1)

25. **Room on SQLite, numbered migrations.** Schema version 1 is exactly the checklist's table list (plus foreign-key indexes); version 2 adds the columns the app needs (resume state, make-up block, injury phase progress, quick-add labels and so on) and the date indexes. Each migration is a plain SQL `Migration(n, n + 1)` in `db/Migrations.kt`; `MigrationTest` builds a real version 1 database from the exported schema in `app/schemas/`, fills every table, upgrades it and checks every row survived.
26. **Column names.** `order` is an SQL keyword, so program days and slots use `position`. `meta` is a key/value table (`schema_version`, `last_evaluated_date`, `install_date` and later markers), so new markers never need a migration.
27. **Weekdays** are stored 0 = Monday … 6 = Sunday (ISO order).
28. **Time formats.** Dates are `YYYY-MM-DD`; timestamps are `yyyy-MM-dd'T'HH:mm:ss.SSS±hh:mm` (always with offset and milliseconds, so they sort as text within one time zone). `Time.endOfDay` is the following local midnight.
29. **Validation before writes.** `Store` checks every input against `Rules.Limits` (and enum fields against their allowed values) inside the transaction before writing; a rejected input throws `InputRejected` and writes nothing.
30. **Schema version 3** adds `exercise.load_kind`, `exercise.source_id`, `equipment.section` and `equipment.note` (`MIGRATION_2_3`).

### Exercise library (milestone 2)

31. **Curated matching.** `scripts/import_exercises.py` holds a reviewed table (our id → free-exercise-db id), because automatic name matching picked wrong records (for example "pec deck" → "Power Jerk"). `data/exercise_map.json` lists every match with a name-similarity score for review. The script fails if any slot option, make-up candidate, injury alternative or one-sided version has no record.
32. **Custom records without photos** (no suitable free-exercise-db record): lateral raise machine, assisted dip machine, assisted pull-up machine, assisted chin-ups, hip thrust machine, bird dog, pike push-ups, backpack curl, face-down Y and T raises, one-leg Romanian deadlift, wall-sit hold, curl-up, one-arm cable pushdown. The script prints the final list on every run.
33. **Photos borrowed from a close variant:** wide-grip seated cable row (seated cable row), slow goblet squat (goblet squat), Smith hip thrust (barbell hip thrust), glute bridge (barbell glute bridge, done with a dumbbell), chair dips (bench dips), one-arm backpack row (one-arm dumbbell row), backpack Bulgarian split squat (split squats), suitcase carry (farmer's walk), push-ups on handles (push-ups), single-leg leg press (leg press), single-leg lying leg curl (lying leg curl), hanging knee raise (hanging leg raise), calf raises on a step (calf raise on a dumbbell).
34. **One record for several names.** "Machine row", "seated row machine" and "chest-supported row machine" are one exercise, because the equipment list has one chest-supported row machine. "Chest-supported dumbbell row" and "… on an incline bench" are one exercise, and so are "standing dumbbell curl" and "dumbbell curl".
35. **Equipment model.** Plates are one shared item used by the barbell, EZ bar and Smith machine (bars default to 20, 10 and 15 kg). The preacher curl line is two items (bench and machine). The T-bar row is a plate-loaded machine treated as a stack (10–100 kg in 5 kg steps). Home and away exercises list no gym equipment.
36. **Load kinds.** Each exercise has a `load_kind`: barbell (bar plus any combination of plate pairs), dumbbell, stack (machines and cables), bodyweight (body weight plus an added dumbbell), assisted (body weight minus stack assistance), or none (timed holds and home exercises).
37. **Stress tags.** Besides the section 9 movement tags, every exercise carries general area tags (`shoulder`, `elbow`, `wrist`, `lower_back`, `knee`, `hip`, `ankle`, `neck`), which mean "uses the area, so rate pain on every set". Extra tags cover the "if painless" rules: `lateral_raise`, `hip_thrust`, `seated_calf`, `machine_press`. Regular barbell and Smith bench presses count as `wide_grip_press`, and squats carry both `deep_knee_bend` and `deep_hip_flexion`.
38. **Images** are WebP at most 600 px wide (quality 72), stored per source record as `assets/exercises/<source id>/<n>.webp`, so variants that share a record share the files.

### Program, gym and auto-enhancer (milestones 3, 5 and 6)

39. The plan starts on the day setup is finished. Dates before the start count as week 1.
40. Both calibration weeks use 2 working sets. Any exercise without an estimate runs the find-your-weight ramp, in any week: the final ramp set is working set 1 and the remaining working sets follow at that weight.
41. Home and away templates are reps-only: the app doesn't know the backpack's or the hotel's weights, so it prescribes no weight there and those sets don't change estimates. They still count toward weekly sets.
42. Warm-ups are only for the first exercise of a session, only for barbell, dumbbell and machine loads, and never during a ramp. Warm-up weights at or above the working weight are dropped.
43. Full, minimum, home and deload sessions advance the rotation. Away and pre-trip sessions don't, so after a trip the next planned day does the workout that was next before it.
44. Barbell and Smith steps go to the next available weight at least 2.5 kg (upper body) or 5 kg (lower body) heavier. Dumbbells and stacks step to the next available value. A current weight that is no longer available, such as a missing pair, is rounded down to an available one.
45. `pickExercise` skips an exercise already used earlier in the same session. When no option qualifies, the slot uses the first exercise in the day's home version that shares a muscle group with the slot's first choice, or failing that the first one that doesn't clash.
46. "Machine busy? Do the next exercise first" is offered only when the next exercise shares no equipment with the current one. The report message's date reads like "Monday 19 October".
47. After the first-set check moves the weight, the remaining sets' rep targets come from today's first-set e1RM, still one rep fewer per set. The check doesn't run in deload sessions or for single-set exercises.
48. The ramp's starting estimate is the best e1RM of its sets, and the extra working set after the ramp can raise it (gains count in full). Bodyweight exercises ramp from body weight; assisted ones start with the most assistance.
49. A deload's "normal weight" is the current (last prescribed) working weight.
50. Timed holds stop progressing once the top of the range reaches 90 s.

### Workout log, skips and make-up mode (milestones 7, 8 and 9)

51. When a session starts, its full plan is stored in `session.state`, so it resumes exactly as it was even if estimates or equipment change later. Each set is written the moment it is logged.
52. Reasons can be recorded only on the day itself, before its midnight. A day off declared at exactly the cut-off time is refused. The 3rd and later days off in a calendar month are saved as unexcused skips after the warning.
53. Check-ins use inexact `AlarmManager` alarms (`setAndAllowWhileIdle`), which need no exact-alarm permission. "Going later" moves that day's check-in to the new time + 2 hours; any other recorded reason, or starting a session, cancels it.
54. The timeout looks back at most 400 days. Adherence counts planned days only (done, excused and skipped); sessions on rest days still count toward "sessions done this month".
55. Each skip is processed with the 7-day window ending on that skip's own date, as if the timeout had run at that midnight. With no completed session yet, the comeback stub measures from the day before the plan started.
56. A make-up block counts as logged when all its sets are logged; `sessions_left` drops at Finish. If only one group qualifies, its two candidates share the room left under 20 sets. Skips while paused are recorded but don't change `sessions_left`. Blocks are added to gym sessions (full, minimum, deload, pre-trip), not home or away ones.

### Plan changes, injuries and food (milestones 10, 11 and 12)

57. Away with a gym keeps the normal plan (those days stay planned). Away with no time, bodyweight or dumbbells pauses it. For the pre-trip rule, "days since last trained" is counted to the trip's first day. The pre-trip session is the last planned day before leaving, provided that's today or later and not yet trained.
58. Undo during a trip ends the trip today; tomorrow is planned again.
59. Phase 1 swaps clashing exercises out. Phase 2 brings the original exercise back in a pain-free range at 40–60% of the pre-injury weight for 12–20 reps. Phase 3 is the original exercise at full range in the slot's rep range from 60%. Phase 4 is at 100%. With no saved pre-injury weight, the lightest available weight is used.
60. A red flag locks every exercise that uses the area, not just the clashing ones, until "I've been checked".
61. For a one-sided injury, a clashing exercise that has a one-arm or one-leg version switches to that version for the healthy side; others use backups, then the area's listed alternatives.
62. The soreness pattern is: dull, eases with the warm-up, and either on both sides or 12–48 hours after training. Red flags are checked first.
63. A session's pain light is its worst rating for the injured area. Red steps back one phase at Finish. Green or amber move the phases only when the session included the affected exercise at injury loads. Amber holds without resetting the green count.
64. The "usual morning" starts at the triage pain rating and becomes the lowest morning rating since. Anything worse steps back one phase. After 14 days still in phase 1 or 2, the app suggests a physio.
65. Canteen oil is added per portion (45 kcal × quantity) of a cooked item. A usual meal stores its portions and a note in `items`; meals entered as totals get no canteen oil.
