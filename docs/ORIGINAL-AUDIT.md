# Audit of the original Workout Tracker 2.2

Reference revision: `33aa8d8` — v2.2. Inspection covered the manifest/build configuration, data models and repository, ViewModel, main navigation, workout cards and day tabs, glass theme, timer service and actions, onboarding, catalogue, history/settings/reminders, strings and icon assets. Existing source hashes were captured in `original-source-hashes.json` before building this separate project.

These are code findings and failure risks, not claims that every reported user crash was reproduced. No user device crash logs were supplied.

| Finding in original source | Consequence | New design |
|---|---|---|
| `WorkoutDay` identifies a training plan by `dayIndex`; progress is primarily keyed by exercise IDs | Body-group routines and actual calendar activity are conflated | Stable routine identity separate from date-stamped sessions and optional weekday assignments |
| `startFreshDayIfNeeded` removes counters when a routine's training date changes | Reviewing on another date can clear weekly context | View queries never clear history or counters |
| `Exercise.nameResId` and `WorkoutDay.nameResId` are persisted | Generated resource IDs can refer to different strings after a build/restore | Persist text and stable catalogue IDs |
| `WorkoutViewModel.isWorkoutDataValid` checks a sample resource, then clears progress, timer and structure when invalid | A bad resource reference may discard the user's plan | Validated data format; errors retain the source file |
| Repository decode catches often return empty structures or default preferences | A decode problem appears as a new/empty application | One explicit loading/error path, no silent replacement |
| Timer service uses `runBlocking` for state restoration and writes | Main-thread stalls are possible under slow storage | Suspend reads/writes, immediate foreground notification, asynchronous observer |
| Countdown tick writes persisted timer state every second | Storage churn and whole-screen observers on every tick | Store deadlines on transitions; derive time locally |
| Preferences read then copy then save outside a transaction | Concurrent preference changes can overwrite each other | Atomic transform of latest state |
| Exact alarm scheduling without consistent capability/fallback; manifest declares both exact alarm permissions | Permission-dependent failures and unnecessary authorization surface | Optional special access with fallback; in-context notification request |
| Session volume is `completedSets * weight` | The displayed volume omits repetitions and loses per-set weight differences | One immutable value and weight per set, real volume calculation |
| Serialized Kotlin plugin 1.9.22 alongside Kotlin 2.0.21 | Build compiler/plugin versions are inconsistent | Matching Kotlin, Compose and serialization compiler plugin versions |

## Features carried forward

- Blue/violet glass-style panels, with green completion and amber rest states.
- Card-level set progress, one main log-set action and undo.
- Separate workout elapsed time and rest countdown, clearly labeled.
- Automatic rest sheet with a Continue in background action and an always-accessible compact banner.
- Background notification, completion alert and direct next-set action.
- Bilingual exercise catalogue and custom exercises with sets, target, rest, weight and notes.
- Editable plans, history with individual set details, working-weight carryover and previous performance.
- Theme/language settings, recurring workout reminders and the user's existing artwork.

## Deliberate refinements

- Weekly summary is always automatic and non-destructive; no destructive reset toggle.
- Fixed-day and flexible scheduling can be switched without losing plans.
- Empty rest days do not require empty workout definitions.
- Program builder supports naming, editing, adding/removing exercises and explicit ordering.
- Finished partial sessions remain distinguishable from completed workouts.
- Backup export/validated import provide a user-accessible recovery path for ordinary device transfers.
