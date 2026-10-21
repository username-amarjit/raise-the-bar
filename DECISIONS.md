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
