# Verification — Workout 3.0.0

This is the original 3.0 verification record. For the current UI and focused checks, see [Workout 3.1](UI-REFINEMENT-3.1.md).

Completed 11 September 2026, Jerusalem time. The emulator used UTC, so some captures and test records display 10 September.

## Delivered artifact

- APK: `dist/Workout-3.0.0.apk`
- Application ID: `com.hananel.workout.rebuilt`
- Android API 26+, compiled/targeted against API 36.
- Size: **1,481,837 bytes**.
- SHA-256: `11F87C6CD40B67413FB49C19E3D9057717D85B507496A825FBC9F793CDF6062B`
- Optimized Release build with R8 and resource shrinking, signed with this machine's Android debug keystore for sideload testing.
- `apksigner verify`: successful, APK Signature Scheme v2. [Signature evidence](evidence/apk-signature.txt)
- `zipalign -c -P 16 -v 4`: successful. [Alignment evidence](evidence/apk-alignment.txt)

## Automated checks

**58 tests passed: 46 JVM tests and 12 Android integration tests.**

| Suite | Result | What it exercises |
|---|---|---|
| TrainingEngineTest | 41 / 41 | Set logging, duplicates, undo, actual volume, timed/unweighted exercises, first-set clock, explicit/idle finish, weekly rollover and year boundaries, flexible/fixed suggestions, timer pause/resume, clock changes and reboot, stale actions, repeated sessions, editing locks, serialization, ranges, languages, bulk operations and invalid backup references |
| CoordinatorTest | 5 / 5 | Competing UI/notification commands, one completion alert, validation before effects, stale timer actions and an effect failure that retains the successful save and does not skip other effects |
| WorkoutIntegrationTest | 12 / 12 | Compose user flows, real Android DataStore files, background timer and notification actions, denied notifications, activity recreation, language/theme/mode transitions, custom exercise/catalogue, target range/exact rest editing, bulk confirmation and backup validation |

[JVM engine result](evidence/TEST-com.hananel.workout.TrainingEngineTest.xml) · [JVM coordinator result](evidence/TEST-com.hananel.workout.CoordinatorTest.xml) · [Android test result](evidence/instrumentation-final.txt)

The Android suite passed after the final domain and coordinator changes. A subsequent system-bar contrast fix was compiled and checked visually in the delivered Release; it did not change domain behavior. JVM tests and lint were run with that final build.

The coordinator JVM suite uses an in-memory implementation of the DataStore interface: Android FileStorage's replace-by-rename behavior is not portable to this Windows JVM. Actual DataStore durability is tested on Android, including a file close/reopen and 25 competing commands through the coordinator.

The final Gradle invocation passed `assembleRelease assembleDebug testDebugUnitTest lintDebug`. Lint reports **0 errors and 18 warnings**. Warnings concern available dependency upgrades, resource naming/location, an API-qualified locale attribute and optional KTX usage; they were inspected and are not represented as a warning-free build. [Build log](evidence/final-build.log) · [Lint report](evidence/lint-results-debug.txt)

## Emulator and manual Release verification

Environment: Android 16 / API 36, `Pixel_9_Pro_Fold` AVD running with software rendering, resized to 1080 × 2340 at 420 dpi for a phone viewport. Android Studio JBR 21 and Gradle 8.13. No physical device was connected.

- Installed the app and completed onboarding with a five-routine body split.
- Viewed the Hebrew weekly calendar and exercise cards, including target ranges.
- Logged a set; verified the contextual permission explanation and Android notification permission request.
- Observed the countdown while navigating outside the workout; optional exact-alarm access was not granted, exercising the foreground-service path.
- Installed the optimized Release APK over the debug build while an exercise and timer existed. Exported Release state had **the same session ID, timer ID, deadline, five routines and one saved set**. [Upgrade comparison](evidence/release-upgrade-check.json)
- Exported JSON through Android's Downloads document picker, opened that file through Restore, verified the replacement confirmation, and restored successfully. The active session remained; stale rest state was cleared as designed.
- Changed Hebrew to English and Dark to Light in Release; verified translated built-in labels, layout direction and dark status-bar icons on the light background.
- Inspected Hebrew home/workout at **160% font scale**; numeric controls stacked vertically and scrolling kept actions available. English/light settings were inspected at that size as well. Font scale was returned to 100% after checking.
- An earlier debug smoke test killed the app process with `kill -9` and reopened it; persisted session/timer identity, deadline and logged count survived. Activity recreation and disk reopen are also covered by automated tests.
- Final captured crash buffer and filtered `WorkoutTimer`, `WorkoutReminder`, `AndroidRuntime` error logs were empty. [Crash log](evidence/crash-log.txt) · [Runtime errors](evidence/runtime-errors.txt)

Screenshots: [Home](screenshots/home.png), [exercise cards](screenshots/exercise-cards.png), [Release timer](screenshots/release-timer.png), [Release English/light](screenshots/release-english-light.png), [large-font workout](screenshots/large-font-workout.png).

## Original project integrity

All **75 captured original source hashes still match**. `git status --short` in `Workout-Tracker` still reports only the pre-existing untracked `.claude/` directory. No branch, commit, merge or deletion was performed in that repository. [Integrity check](evidence/original-integrity-check.json)

## Limits of the evidence

- These are passing checks and observed behavior, not a guarantee of zero bugs or a claim that the old user's reported crashes were reproduced.
- API 26–35 compatibility is declared and statically checked but was not separately exercised on those emulator versions.
- Physical-device sleep, sound/vibration, OEM battery restrictions, real reboot delivery and large multi-year histories were not exhaustively tested. Android force-stop intentionally disables background work until the user reopens the app.
- The new app starts separately; v2 data is not migrated automatically. New-format backup restore was tested.
- Store publication and production signing are outside this locally installable build.
