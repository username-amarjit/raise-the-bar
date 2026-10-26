# Raise the Bar

A personal, offline strength-training app for Android. It runs a six-day Push, Pull and Legs program and adjusts each weight to how the last sets went. It also handles skipped days and make-up sessions, days off and trips, injuries, and simple food and body-weight tracking.

This is Phase 1, built milestone by milestone from the checklist. Every checklist box is ticked except installing the app on the owner's phone.

## Documents

| File | What it is |
|---|---|
| [raise-the-bar-phase1-build-checklist.md](raise-the-bar-phase1-build-checklist.md) | The Phase 1 specification and source of truth: scope, every rule and number (section 5), the milestones with their ticked boxes, the seed data and the golden tests. |
| [Raise the Bar_ an adaptive strength, muscle and app plan.html](<Raise the Bar_ an adaptive strength, muscle and app plan.html>) | The original plan. Used for context and for the look of the screens. |
| [DECISIONS.md](DECISIONS.md) | Every decision the checklist left open or that was made while building, numbered. Code comments refer to them as "decision N". |
| This README | How to build, test and install the app, and how the code fits together. |

The checklist assumes Expo and React Native. At the owner's request the app is a native Android app in Kotlin instead. [DECISIONS.md](DECISIONS.md) #17–24 map each checklist tool to its Android equivalent.

## Build, test and install

**You need:** Android Studio, or its bundled runtime (JDK 25, which runs Gradle; see `gradle/gradle-daemon-jvm.properties`), and the Android SDK with platform 37. Android Studio writes `local.properties` with the SDK path. On the command line, point `JAVA_HOME` at Android Studio's `jbr` folder.

Run these from the repository root (`gradlew.bat` on Windows, `./gradlew` elsewhere):

| Command | What it does |
|---|---|
| `gradlew :engine:test` | Engine unit tests, including every golden test from checklist section 13 |
| `gradlew :app:testDebugUnitTest` | App tests on the JVM with Robolectric: database, data-layer flows and screens |
| `gradlew ktlintCheck :app:lintDebug secretScan` | Kotlin style, Android Lint, and the repo checks (secrets, logging, raw SQL, network code). `gradlew ktlintFormat` fixes style. |
| `gradlew :app:assembleDebug` | Debug APK: `app/build/outputs/apk/debug/app-debug.apk` |
| `gradlew :app:assembleRelease` | Release APK: `app/build/outputs/apk/release/app-release.apk` (minified, not debuggable, signed with the local debug key) |

The full pre-commit check is:

```
gradlew ktlintFormat
gradlew secretScan ktlintCheck :app:lintDebug :engine:test :app:testDebugUnitTest :app:assembleRelease
```

Run `ktlintFormat` on its own first; running it alongside `ktlintCheck` can make the check race the formatter.

**To install on a phone:** copy `app-release.apk` to the phone and open it, allowing installs from that app once. Or connect the phone with USB debugging on and run `adb install -r app/build/outputs/apk/release/app-release.apk`. The release app is `com.raisethebar.app`. Debug builds install alongside it as `com.raisethebar.app.debug`, under the same name, and keep their own data. Before giving the app to anyone else, replace the debug signing key with a real keystore ([DECISIONS.md](DECISIONS.md) #21).

The repository also holds a ready-made debug build, `app-debug.apk` at the root, stored with Git LFS (run `git lfs pull` after cloning to get the real file). It's a snapshot from when it was committed, and it installs as the debug app, whose data is separate from the release app's. To move from one to the other, export from the app you've been using (Settings → Export your data) and import the file into the other.

## Project layout

```
engine/                 pure Kotlin module: every training rule, no Android code
  src/main/kotlin/com/raisethebar/config/Rules.kt     every number from checklist section 5
  src/main/kotlin/com/raisethebar/engine/            the rules (see "The engine" below)
  src/test/                                           golden tests (section 13) and rule tests
app/                    the Android app
  src/main/kotlin/com/raisethebar/app/
    RaiseTheBarApp.kt   Application: owns the Repo and runs startup on every resume
    MainActivity.kt     hosts the Compose UI; opens the check-in sheet from a notification
    db/                 Room: entities (20 tables), DAOs, migrations, type converters
    data/               data layer: Store, Repo and one Repo* file per feature, seeding, clock
    notify/             check-in notifications and the rest-timer alarm
    backup/             export file and share sheet, reading an imported file
    ui/                 Compose screens, one folder per feature, plus nav, theme and components
  src/main/assets/      exercise photos (WebP) and the font licence
  src/main/res/         fonts, colours, themes, backup and network-security rules
  src/test/             Robolectric tests
  schemas/              exported Room schemas, one file per database version
data/                   seed data: program, exercises, exercise map, equipment, injury rules, foods
scripts/                import_exercises.py, the build-time exercise library import
build.gradle.kts        root build, including the secretScan task
gradle/libs.versions.toml  dependency versions
app-debug.apk           a committed debug build (Git LFS), see "Build, test and install"
```

## Architecture

### Two modules

- **`:engine`** is a plain Kotlin/JVM library. It holds every rule: the calendar and effort targets, weight estimates, exercise selection, session planning, skips and make-up mode, away mode, injury mode and nutrition. It has no Android, database or device imports, and its warnings fail the build. It can be tested on its own.
- **`:app`** is the Android app. It stores data in Room, gathers what the engine needs, calls it, saves the results and shows them. The app depends on the engine and never the other way round.

Every number from checklist section 5 is a constant in `Rules.kt`. Rest times and barbell step sizes are also written into the bundled data, and a test checks them against `Rules.kt` ([DECISIONS.md](DECISIONS.md) #89).

### The engine

| File | Rules it holds |
|---|---|
| `Calendar.kt` | Plan weeks, calibration, six-week blocks, deload weeks, reps to stop with, working sets |
| `Estimates.kt` | e1RM, next weight and rep targets, the first-set check, updates after a session, deloads, the find-your-weight ramp, timed holds |
| `Gym.kt` | The weights each piece of equipment allows, weight steps, the "report it" message, "Is it fixed yet?", "Machine busy?" |
| `Selection.kt` | Which exercise fills a slot today: first choice, backups, one-sided versions, injury alternatives, home version |
| `Planner.kt` | A whole session: kinds (full, minimum, home, away, pre-trip, deload), warm-ups, the make-up block |
| `Workout.kt` | Estimate and hold updates from the sets logged in a finished session |
| `Days.kt` | Weekdays, days off and trips, and what each date is (planned, rest, day off, away) |
| `Skips.kt` | The midnight timeout's outcomes, reasons, the day-off cut-off, check-ins, skip statistics |
| `Makeup.kt` | Make-up mode, weekly sets per muscle group, building the make-up block |
| `Away.kt` | Away sessions, the pre-trip full-body rule, undo, the preview |
| `Injuries.kt` | Triage, red flags, the soreness pattern, the pain light, the four phases, swaps |
| `Food.kt` | Protein target, calorie guess, goal suggestion, canteen oil, the weight average |
| `Model.kt`, `SeedData.kt` | The data model, and reading the bundled JSON |
| `Time.kt`, `Rounding.kt`, `Validate.kt` | Date and time formats, round-half-up, input limits |

### How a screen gets and changes data

```
 Compose screen (ui/)
   │  rememberLoad { … }  reads; re-runs whenever Repo.changes ticks
   │  rememberActions().run { … }  writes; a rejected input becomes a snackbar
   ▼
 Repo (data/)  ── gathers inputs ──▶  engine (rules)  ── results ──▶  Repo
   │  write { … }: one Room transaction, then Repo.changes ticks
   ▼
 Room database (db/)
```

- `Store` validates every input against the section 5 limits inside the transaction, before writing. A rejected input throws `InputRejected` and writes nothing.
- `Repo` extends `Store`. Its features are split into extension files: `RepoSetup`, `RepoToday`, `RepoWorkout`, `RepoSkips`, `RepoPlan`, `RepoInjury`, `RepoGym`, `RepoFood` and `RepoBackup`. `AppClock` is the single source of "now", so tests can fix the date and time.
- Screens get the `Repo` from `LocalRepo` and never touch Room directly.

### App start, resume and the midnight timeout

There are no background jobs. `MainActivity` calls `RaiseTheBarApp.startup()` every time the app starts or comes back to the foreground. Startup:

1. loads the bundled data into the database if it is new or has changed ([DECISIONS.md](DECISIONS.md) #83);
2. runs the midnight timeout for every day since the last run, recording each day's outcome and processing new skips for make-up mode;
3. schedules check-ins for the next 7 days.

The first screen is setup until setup is finished. After that it's Today, and an unfinished workout reopens exactly where it was.

### Navigation and screens

Compose Navigation with type-safe routes (`ui/nav/Routes.kt`). There are four tabs, Today, Food, Progress and My Gym; Settings opens from Today. The screens are setup, Today, the workout logger, the check-in sheet, an exercise page, My Gym and an equipment item, Food, Progress, Plan changes, injury triage, and Settings with its edit sections and About. The theme follows the phone's light or dark setting and uses the bundled Barlow fonts.

### Stored data

Room, schema version 3, in `raise-the-bar.db`. There are 20 tables: `profile`, `schedule`, `plan`, `equipment`, `exercise`, `program_day`, `slot`, `estimate`, `session`, `set_log`, `day_log`, `absence`, `makeup`, `injury`, `pain_log`, `food`, `usual_meal`, `food_log`, `body_log` and `meta`.

- Every schema change is a numbered migration in `db/Migrations.kt`. `MigrationTest` upgrades a filled version 1 database to the current version.
- Dates are local `YYYY-MM-DD` text, timestamps are ISO text with offset, and lists are JSON text ([DECISIONS.md](DECISIONS.md) #26–28).
- When a workout starts, its whole plan and progress go into `session.state` as JSON, and each set is written the moment it is logged. That is how a workout survives the app being killed.
- The program is read from the bundled `program.json`; the database copy keeps the user's promoted first choices.

### Notifications and the rest timer

`AndroidNotifier` schedules everything with `AlarmManager`:

- **Check-ins** use inexact alarms, set for the planned time plus 2 hours. `CheckinReceiver` posts the notification, and tapping it opens the check-in sheet.
- **The rest timer** uses an exact alarm where allowed. `RestReceiver` vibrates once at zero, even with the screen locked.

See [DECISIONS.md](DECISIONS.md) #53 and #84.

### Export and import

Settings → Export writes every table, plus `schema_version`, to one JSON file. The file goes in the app's private cache and is handed to the share sheet through a `FileProvider`. Import parses a picked file strictly and checks its version, structure, value ranges and cross-table references before anything is written. It then shows what the file holds and, once the user confirms, replaces every table in one transaction. The first setup screen offers the same restore. See [DECISIONS.md](DECISIONS.md) #68–72.

### Security and privacy

- **No network.** `INTERNET` is stripped from the manifest and cleartext is blocked.
- **No device backups.** Backup and device-to-device transfer are off; export is the backup.
- **No personal data in logs.** The app never logs, and release builds also strip `android.util.Log`.
- **Optional screen protection.** "Hide in the app switcher" sets `FLAG_SECURE`.
- **Checks.** `secretScan` checks the repo, and `ManifestTest` checks the merged manifest.

See [DECISIONS.md](DECISIONS.md) #22–23 and #79–80.

## Seed data and the exercise library

`data/*.json` is packaged into the engine jar under `rtb-data/`, so the app and the tests read the same files. The library has 118 exercises: 105 come with photos from [free-exercise-db](https://github.com/yuhonas/free-exercise-db) (public domain), and 13 are custom records without photos ([DECISIONS.md](DECISIONS.md) #32).

`scripts/import_exercises.py` builds the library. It needs Python 3 and Pillow.

- It runs once at build time; the app never downloads anything.
- It matches each exercise through a reviewed table, then writes `data/exercises.json` and `data/exercise_map.json` (the matches, for review).
- It converts the photos to WebP, at most 600 px wide, into `app/src/main/assets/exercises/<source id>/`.
- It keeps its downloads in `scripts/.cache` (git-ignored), and `--refresh` downloads them again.
- It fails if any exercise the program needs has no record, or if the photos pass 15 MB.

## Tests

- **Engine** (`engine/src/test`):
  - `GoldenTests.kt` and `EstimatesGoldenTest.kt` hold every case in checklist section 13.
  - `EstimatesRulesTest`, `ProgramAndGymTest`, `ExerciseLibraryTest`, `RoundingTest` and `ValidateTest` cover the section 5 rules, the program and the bundled data.
- **App** (`app/src/test`): Robolectric tests on an in-memory database with a fixed India-time clock (`TestRepo`; the tests run in `Asia/Kolkata`).
  - **Milestone flows:** `SetupFlowTest`, `GymFlowTest`, `WorkoutFlowTest`, `SkipsMakeupFlowTest`, `PlanFlowTest`, `InjuryFlowTest`, `FoodFlowTest` and `BackupTest`, each driving its milestone's data layer and screens.
  - **Release QA:** `SimulatedWeekTest` and `ClockTest`.
  - **Database and shell:** `db/DatabaseCrudTest`, `StoreValidationTest` and `MigrationTest` cover the database. `data/RepoFlowTest` runs the data layer end to end. `ShellSmokeTest`, `ExerciseLibraryScreenTest` and `ManifestTest` cover the app shell, the exercise page and the manifest.

## Changing things

- **A rule or number:** change it in `Rules.kt`. If the value also appears in the bundled data (rest times, barbell steps) or in screen text, change it there too ([DECISIONS.md](DECISIONS.md) #89).
- **The program, exercise data or foods:** edit `data/*.json`. Installed apps pick up the change on their next start after the update ([DECISIONS.md](DECISIONS.md) #83). Injury rules are read straight from the bundled file. Changes to the gym checklist (`equipment.json`) only reach new installs, because the app never overwrites the user's own gym.
- **The exercise library:** edit the table in `scripts/import_exercises.py` and run it again.
- **The database:** change the entities, bump `AppDatabase.VERSION`, and add the next numbered migration and a test for it. Room exports the new schema to `app/schemas/`.
- **A decision:** add it to [DECISIONS.md](DECISIONS.md) with the next number.

## Credits

- **Exercise photos and instructions:** [free-exercise-db](https://github.com/yuhonas/free-exercise-db) by yuhonas, under the Unlicense.
- **Fonts:** Barlow and Barlow Condensed by Jeremy Tribby, under the SIL Open Font License 1.1 (`app/src/main/assets/licenses/barlow-OFL.txt`).

Both are credited in the app under Settings → About.
