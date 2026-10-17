# Workout 3 — fresh Android implementation

## Product contract

The original project at `C:\CodingProjects\Workout-Tracker` is a read-only reference. This project has its own source tree, package (`com.hananel.workout.rebuilt`), data file and installable APK. The original application is not replaced, and its data is not automatically imported. Its exercise catalogue content and brand artwork are reused; its application implementation is not.

The default language is Hebrew, with RTL layout. English and device-language selection are available; device language selects Hebrew for he/iw and English otherwise. Built-in routine/exercise labels translate, while user-authored names and notes remain as entered. The preference is also applied through Android 13+ app locales; Android-owned permission screens may use the device language. Light is the initial theme in 3.1; dark and system themes remain available, including system-bar icon contrast. The 3.1 appearance upgrade runs once and preserves training data. No account or network service is needed.

- **Flexible plan:** named program containing any number of custom workouts. Ordered mode suggests the first remaining workout in the current round; free mode waits for a choice. Both allow manual selection. Round progress continues across weeks; there are no imposed rest days.
- **Scheduled plan:** routines can be assigned to any set of weekdays. Only completions on the same actual date satisfy today's suggestion, so repeating a routine on two weekdays creates independent occurrences. Every routine remains accessible.
- **Calendar:** each day shows short workout labels with a clock for planned activity, progress icon for partial activity, or a check for completion. Actual sessions are recorded on their real date. Browsing a date is read-only and the exercise panel always records a new session today.
- **Weekly reset is a query:** Sunday–Saturday is computed in the device's local time zone. A new week starts with an empty summary. It never deletes the plan, working weights, or historical sessions.
- **Completion:** an entire routine is marked complete only when all its planned sets have been logged. An explicitly finished partial workout stays visible as partial. A second workout in the same week starts its own counters and keeps earlier sessions.
- **Timed exercise values:** repetitions, seconds and minutes are distinct units. A plank is not logged as 60 repetitions. Cardio duration is entered as a performed value; the dedicated countdown is for inter-set rest.

## Code boundaries

| Package | Responsibility |
|---|---|
| `domain/Models.kt` | Stable IDs, routine definitions, immutable session snapshots, individual set values, clock calculations, validation and weekly queries |
| `domain/TrainingEngine.kt` | Pure state transitions; no Android Context, service or Compose dependency |
| `domain/Planning.kt` | Flexible round projection, persisted completion IDs, and one-time migration of legacy sessions/progress |
| `data/WorkoutRepository.kt` | One typed DataStore, durable atomic read/modify/write operations, validated backup format |
| `data/WorkoutCoordinator.kt` | Shared command boundary for UI, notification actions, timer expiration and lifecycle receivers; serializes validation, persistence and platform effects |
| `data/Catalog.kt` | 41 bilingual exercises extracted from the user's original content, starter plans |
| `timer/` | Foreground service, notification actions, alarm fallback, recurring reminders |
| `ui/WorkoutViewModel.kt` | Converts UI intent to coordinator commands; loading, busy state, localized feedback and backup file I/O |
| `ui/` | Stateless renderers and local presentation state; no independent set counts |

This is one Gradle application module with explicit package boundaries. Avoiding a dependency-injection framework, navigation graph framework, network stack and unused WorkManager keeps startup and maintenance small. DataStore serializes the app snapshot on meaningful changes; countdown ticks do not write data. History currently remains in the same snapshot. If multi-year large datasets or richer analytics demand it, move session storage to Room behind the repository rather than increasing screen responsibilities.

The application's "middleware" is `WorkoutCoordinator`, not a server or network interceptor. UI and Android event handlers share one application-scoped instance. Commands acquire its mutex, transform the latest repository state through the pure engine, validate, commit, and then reconcile timers/reminders. This prevents an older command's platform handoff from restarting a later timer. Once started, the transaction and handoff finish despite a screen leaving composition. A platform failure is reported separately from a successful data save, and one failed effect does not skip the other. Reads and UI countdown rendering remain read-only flows and do not need this command boundary.

Reminder delivery reschedules reminders without clearing an unrelated rest-completion notification. Timer completion is independently claimed once within the same coordinator. Tests inject platform effects and a DataStore contract adapter on Windows; Android instrumented tests exercise actual disk persistence and platform components.

## Invariants

1. Logging a set, opening a session, updating working weight, and setting the next rest deadline are a single transaction.
2. `expectedCount` and unique action IDs reject double taps and repeated notification commands. A notification is also tied to the exact timer/session identity.
3. Finishing or skipping rest never logs a set. Completing the last set requires no running service or timer.
4. Each set stores its actual value and weight. Historical volume is the sum of weight × repetitions per set; timed exercises are excluded.
5. Active sessions hold a snapshot of the plan. Editing their routine is blocked until the session is finished, preventing inconsistent planned totals.
6. Set counters are never persisted separately from their logs or derived complete flags.
7. An empty session has no workout clock. The first set starts it. In 3.4, idle closing is enabled once on migration and by default for new installs, per the revised user decision. It uses monotonic time during the same boot and waits twenty minutes after the latest set/edit/timer action, or after a running rest ends. Paused rest expires too. The saved end time is the last actual action, excluding forgotten idle time. A replaceable alarm handles background closure, with recovery before displaying restored sessions. Manual-only finish remains available in timer settings. Undoing all sets releases the empty session.
8. Corrupt or unsupported stored content produces a recoverable error screen and leaves the source file intact; it is never silently replaced with an empty plan. Backup import is validated before its replacement confirmation.
9. Completing an entire exercise requires an explicit confirmation containing the number of remaining sets and their values. Clearing its sets affects the current session only.
10. Rep targets can be ranges, but each performed set stores one actual value. The editor and workout controls both support step buttons and exact numeric input, including decimal weights.
11. Backup validation checks duplicate reminder identities and timer/session references as well as values, so an invalid timer cannot be restored into an unrelated workout.

## Timer and Android lifecycle

A timer persists both elapsed-real-time and wall-clock deadlines, the boot counter, paused remaining duration and its identity. Elapsed time is used during the same boot, so changing the phone's wall clock cannot lengthen a live rest. Wall time is a fallback after reboot or restore. UI ticks are collected only while the screen lifecycle is started. The service uses a bounded partial wake lock while actively counting, releases it on pause/completion/destruction, and does not hold an Activity reference.

The foreground service posts immediately, then reads storage asynchronously. No `runBlocking` appears in production code. Completion is claimed atomically by the service or alarm receiver to avoid duplicate alerts. The service's alert handoff is non-cancellable because writing the completion flag also emits the state that stops its collector.

`POST_NOTIFICATIONS` is requested in context after the first rest starts, with a Later choice. Denial does not block set logging or the timer UI. `SCHEDULE_EXACT_ALARM` is optional and has a capability check plus fallback. `USE_EXACT_ALARM`, sensor, health, location and storage permissions are not requested. SAF document pickers handle backup access. Boot, package replacement, time and time-zone events reschedule reminders. Reminder identity uses a URI rather than collision-prone integer hashes.

Android force-stop intentionally stops an application's background work. On reopening, the saved deadline is reconciled. Device manufacturers may impose additional power restrictions; emulator testing does not establish reliability on every physical device. The application does not claim to override these platform constraints.

Primary Android references consulted:

- [DataStore and atomic updates](https://developer.android.com/topic/libraries/architecture/datastore)
- [Foreground service declarations and permissions](https://developer.android.com/develop/background-work/services/fgs/declare)
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Alarm scheduling and exact-alarm capability](https://developer.android.com/develop/background-work/services/alarms)

## Data and upgrade boundaries

New-format backup schema is explicitly versioned. It contains stable text and string IDs, never generated Android resource IDs. Export/import round trips retain Hebrew names, individual set values, sessions, plans and preferences. Import clears a stale rest notification state. This APK is a separate installation; there is no unsafe attempt to read another Android application's private storage. Automatic migration from v2 is outside this build; the original app continues to hold its data.

The supplied release build is optimized and signed with this machine's Android debug keystore for direct testing. It is not a Play Store production signing configuration. The same key signs debug and release builds here, allowing the emulator upgrade check to verify persisted state under minification.

Version 3.3 freezes the round baseline and calendar marker with the first real set. Active completion remains reversible; program membership edits cannot move the established round boundary. Set corrections validate both session and last-set identity and can update values and weights. Soft glass is an optional presentation preference; it does not change domain behavior.

## 3.4 interaction boundaries

Program configuration lives on its own page; the training page contains the week strip and a compact workout chooser. Exercise editing and library/custom additions are accessible from training through bottom sheets. Working weight is committed independently of logging, including before the first set. `saveExercise` updates future-set configuration in the current session while preserving all existing SetLog values and history; reducing the target below recorded sets or changing their measurement semantics is rejected. The next-set notification uses the current working weight.
