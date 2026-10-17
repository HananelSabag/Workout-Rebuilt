package com.hananel.workout.domain

/** Pure state transitions. Only the repository commits them; screens and timers never own counts. */
object TrainingEngine {
    /** Apply the user's revised defaults once; preserve later preferences and all training data. */
    fun upgradeAppearance(state: AppState): AppState = if (state.preferences.appearanceVersion < 2)
        state.copy(preferences = state.preferences.copy(theme = if (state.preferences.appearanceVersion < 1) "light" else state.preferences.theme,
            glass = true, autoCloseIdle = true, appearanceVersion = 2),
            active = state.active?.let { session ->
                val routine = state.routines.find { it.id == session.routineId }
                session.copy(exercises = session.exercises.map { log ->
                    // Before 3.4, logging remembered the weight in the routine, but not its active snapshot.
                    val remembered = routine?.exercises?.find { it.id == log.exercise.id && it.measure == log.exercise.measure && it.weighted }
                    if (!log.exercise.weighted) log else log.copy(exercise = log.exercise.copy(
                        weight = remembered?.weight ?: log.sets.lastOrNull()?.weight ?: log.exercise.weight))
                })
            }) else state
    const val IDLE_MS = 20 * 60 * 1000L

    fun touch(state: AppState, clock: ClockReading): AppState = state.copy(active = state.active?.let {
        it.copy(lastActionAt = maxOf(clock.wall, it.lastActionAt), lastActionElapsed = clock.elapsed, actionBoot = clock.boot)
    })

    /** Passive viewing is not activity. A running rest gets its full duration; a paused rest can expire. */
    fun idleRemainingMs(state: AppState, clock: ClockReading): Long? {
        val active = state.active ?: return null
        if (active.startedAt == 0L || !state.preferences.autoCloseIdle) return null
        val elapsedIdle = if (active.actionBoot == clock.boot && active.lastActionElapsed != null)
            clock.elapsed - active.lastActionElapsed else clock.wall - active.lastActionAt
        val sinceRest = state.rest?.takeIf { it.pausedMs == null }?.let {
            if (it.boot == clock.boot) clock.elapsed - it.deadlineElapsed else clock.wall - it.deadlineWall
        }
        return (IDLE_MS - if (sinceRest == null) elapsedIdle else minOf(elapsedIdle, sinceRest)).coerceAtLeast(0L)
    }

    fun begin(state: AppState, routineId: String, sessionId: String, clock: ClockReading): AppState {
        require(state.active == null) { "צריך לסיים את האימון הפעיל לפני פתיחת אימון נוסף" }
        val routine = state.routines.firstOrNull { it.id == routineId } ?: error("האימון אינו קיים")
        require(routine.exercises.isNotEmpty()) { "הוסף תרגיל כדי להתחיל" }
        return state.upgradePlanning().copy(active = Session(sessionId, routine.id, routine.name, clock.date.toString(), exercises = routine.exercises.map { ExerciseLog(it) }, nameEn = routine.nameEn, customName = routine.customName, planMode = state.preferences.mode), rest = null)
    }

    fun logSet(state: AppState, routineId: String, exerciseId: String, expectedCount: Int, value: Int, weight: Double,
               actionId: String, sessionId: String, clock: ClockReading): AppState {
        require(value in 1..999 && weight.isFinite() && weight in 0.0..1500.0)
        val base = if (state.active == null) begin(state, routineId, sessionId, clock) else state
        val session = base.active ?: return state
        require(session.routineId == routineId) { "יש אימון אחר פעיל. סיים אותו קודם" }
        val log = session.exercises.find { it.exercise.id == exerciseId } ?: return state
        // An old notification or a fast double tap must never record the next set twice.
        if (log.sets.size != expectedCount || log.complete || session.exercises.any { e -> e.sets.any { it.id == actionId } }) return state
        val exercise = log.exercise
        val actualWeight = if (exercise.weighted) weight else 0.0
        val updatedLog = log.copy(exercise = log.exercise.copy(weight = actualWeight), sets = log.sets + SetLog(actionId, value, actualWeight, clock.wall))
        val updated = session.copy(
            startedAt = if (session.startedAt == 0L) clock.wall else session.startedAt,
            date = if (session.startedAt == 0L) clock.date.toString() else session.date,
            roundStart = session.roundStart ?: base.roundAtFirstSet(),
            calendarMarker = session.calendarMarker ?: base.routines.find { it.id == routineId }?.shortLabel,
            lastActionElapsed = clock.elapsed, actionBoot = clock.boot,
            lastActionAt = maxOf(clock.wall, session.lastActionAt), exercises = session.exercises.map { if (it.exercise.id == exerciseId) updatedLog else it }
        )
        val rest = if (!updatedLog.complete && exercise.restSeconds > 0) {
            val duration = exercise.restSeconds * 1000L
            RestTimer(actionId, updated.id, exerciseId, exercise.name, exercise.nameEn, updatedLog.sets.size,
                duration, clock.wall + duration, clock.elapsed + duration, clock.boot)
        } else null
        return base.copy(active = updated, rest = rest, routines = base.routines.map { r ->
            r.copy(exercises = r.exercises.map { if (it.id == exerciseId) it.copy(weight = actualWeight) else it })
        })
    }

    fun undo(state: AppState, exerciseId: String, expectedCount: Int, clock: ClockReading): AppState {
        val session = state.active ?: return state
        val log = session.exercises.find { it.exercise.id == exerciseId } ?: return state
        if (log.sets.isEmpty() || log.sets.size != expectedCount) return state
        val updated = session.copy(lastActionAt = maxOf(clock.wall, session.lastActionAt), lastActionElapsed = clock.elapsed, actionBoot = clock.boot, exercises = session.exercises.map {
            if (it.exercise.id == exerciseId) it.copy(sets = it.sets.dropLast(1)) else it
        })
        return state.copy(active = if (updated.totalSets == 0) null else updated,
            rest = if (state.rest?.exerciseId == exerciseId) null else state.rest)
    }

    fun amendLast(state: AppState, sessionId: String, exerciseId: String, setId: String, value: Int, weight: Double): AppState {
        require(value in 1..999)
        require(weight.isFinite() && weight in 0.0..1500.0)
        val active = state.active?.takeIf { it.id == sessionId } ?: return state
        val edited = active.exercises.find { it.exercise.id == exerciseId && it.sets.lastOrNull()?.id == setId } ?: return state
        val actualWeight = if (edited.exercise.weighted) weight else 0.0
        return state.copy(active = active.copy(exercises = active.exercises.map { log ->
            if (log.exercise.id != exerciseId || log.sets.lastOrNull()?.id != setId) log else
                log.copy(exercise = log.exercise.copy(weight = actualWeight), sets = log.sets.dropLast(1) + log.sets.last().copy(value = value, weight = actualWeight))
        }), routines = state.routines.map { routine -> routine.copy(exercises = routine.exercises.map { if (it.id == exerciseId) it.copy(weight = actualWeight) else it }) })
    }

    fun completeExercise(state: AppState, routineId: String, exerciseId: String, expectedCount: Int,
                         value: Int, weight: Double, actionId: String, sessionId: String, clock: ClockReading): AppState {
        val base = if (state.active == null) begin(state, routineId, sessionId, clock) else state
        val active = base.active ?: return state
        require(active.routineId == routineId) { "יש אימון אחר פעיל. סיים אותו קודם" }
        val log = active.exercises.find { it.exercise.id == exerciseId } ?: return state
        if (log.sets.size != expectedCount) return state
        return (expectedCount until log.exercise.sets).fold(base) { current, count ->
            logSet(current, routineId, exerciseId, count, value, weight, "$actionId-$count", sessionId, clock)
        }
    }

    fun resetExercise(state: AppState, exerciseId: String, expectedCount: Int, clock: ClockReading): AppState {
        val log = state.active?.exercises?.find { it.exercise.id == exerciseId } ?: return state
        if (log.sets.size != expectedCount) return state
        return (expectedCount downTo 1).fold(state) { current, count -> undo(current, exerciseId, count, clock) }
    }

    fun finish(state: AppState, clock: ClockReading, automatic: Boolean = false): AppState {
        val active = state.active ?: return state.copy(rest = null)
        val end = if (automatic) active.lastActionAt else clock.wall
        val history = if (active.totalSets == 0) state.history else listOf(active.copy(endedAt = end.coerceAtLeast(active.startedAt), autoClosed = automatic)) + state.history
        return state.copy(active = null, rest = null, history = history, planProgress = state.cycleProgress())
    }

    fun recover(state: AppState, clock: ClockReading): AppState {
        if (state.active == null) return if (state.rest != null) state.copy(rest = null) else state
        return if (idleRemainingMs(state, clock) == 0L) finish(state, clock, automatic = true) else state
    }

    fun pauseRest(state: AppState, timerId: String, clock: ClockReading): AppState {
        val timer = state.rest?.takeIf { it.id == timerId && !it.alerted } ?: return state
        val left = timer.remainingMs(clock)
        if (left == 0L) return state
        return touch(state, clock).copy(rest = if (timer.pausedMs == null) timer.copy(pausedMs = left) else timer.copy(
            pausedMs = null, deadlineWall = clock.wall + left, deadlineElapsed = clock.elapsed + left, boot = clock.boot
        ))
    }

    fun adjustRest(state: AppState, timerId: String, seconds: Int, clock: ClockReading): AppState {
        val timer = state.rest?.takeIf { it.id == timerId } ?: return state
        val left = (timer.remainingMs(clock) + seconds * 1000L).coerceIn(0L, 900_000L)
        return touch(state, clock).copy(rest = timer.copy(durationMs = maxOf(timer.durationMs, left),
            pausedMs = if (timer.pausedMs != null && left > 0) left else null, deadlineWall = clock.wall + left,
            deadlineElapsed = clock.elapsed + left, boot = clock.boot, alerted = false))
    }

    fun skipRest(state: AppState, timerId: String, clock: ClockReading? = null): AppState = if (state.rest?.id == timerId) state.copy(rest = null,
        active = if (clock == null) state.active else state.active?.let { it.copy(lastActionAt = maxOf(clock.wall, it.lastActionAt), lastActionElapsed = clock.elapsed, actionBoot = clock.boot) }) else state

    fun saveRoutine(state: AppState, routine: Routine): AppState {
        require(state.active?.routineId != routine.id) { "אפשר לערוך את התוכנית אחרי סיום האימון הפעיל" }
        val updated = if (state.routines.any { it.id == routine.id }) state.routines.map { if (it.id == routine.id) routine else it } else state.routines + routine
        return state.copy(routines = updated).validate()
    }

    /** Edits affect future sets while keeping every already-recorded value and past session intact. */
    fun saveExercise(state: AppState, routineId: String, exercise: Exercise, clock: ClockReading): AppState {
        exercise.checked()
        val routine = state.routines.find { it.id == routineId } ?: error("האימון אינו קיים")
        val active = state.active?.takeIf { it.routineId == routineId }
        val logged = active?.exercises?.find { it.exercise.id == exercise.id }
        require(logged == null || exercise.sets >= logged.sets.size) { "מספר הסטים לא יכול להיות קטן ממספר הסטים שכבר נרשמו" }
        require(logged == null || logged.sets.isEmpty() || (logged.exercise.measure == exercise.measure && logged.exercise.weighted == exercise.weighted)) { "אפשר לשנות יחידת מדידה או סוג משקל לפני רישום סטים או אחרי סיום האימון" }
        val updated = if (routine.exercises.any { it.id == exercise.id }) routine.exercises.map { if (it.id == exercise.id) exercise else it } else routine.exercises + exercise
        val logs = active?.exercises?.let { list -> if (logged == null) list + ExerciseLog(exercise) else list.map { if (it.exercise.id == exercise.id) it.copy(exercise = exercise) else it } }
        val timer = state.rest?.let { if (it.exerciseId != exercise.id) it else if (logged != null && logged.sets.size >= exercise.sets) null else it.copy(title = exercise.name, titleEn = exercise.nameEn) }
        return touch(state.copy(routines = state.routines.map { if (it.id == routineId) it.copy(exercises = updated) else it },
            active = if (active != null) active.copy(exercises = logs!!) else state.active, rest = timer), clock).validate()
    }

    fun setWorkingWeight(state: AppState, routineId: String, exerciseId: String, weight: Double, clock: ClockReading): AppState {
        val exercise = state.routines.find { it.id == routineId }?.exercises?.find { it.id == exerciseId } ?: return state
        require(exercise.weighted)
        return saveExercise(state, routineId, exercise.copy(weight = weight), clock)
    }

    fun moveRoutine(state: AppState, routineId: String, delta: Int): AppState {
        val list = state.routines.toMutableList()
        val from = list.indexOfFirst { it.id == routineId }
        if (from < 0 || from + delta !in list.indices) return state
        java.util.Collections.swap(list, from, from + delta)
        return state.copy(routines = list)
    }

    fun deleteRoutine(state: AppState, routineId: String): AppState {
        require(state.active?.routineId != routineId) { "סיים את האימון הפעיל לפני מחיקתו" }
        return state.copy(routines = state.routines.filter { it.id != routineId }).upgradePlanning()
    }
}
