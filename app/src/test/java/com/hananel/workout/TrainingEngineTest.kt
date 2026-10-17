package com.hananel.workout

import com.hananel.workout.domain.*
import com.hananel.workout.data.StateSerializer
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TrainingEngineTest {
    @Test fun lighterUpgradePreservesTrainingAndLaterThemeChoice() {
        val old = log(initial).copy(preferences = Preferences(onboarded = true, theme = "dark"))
        val updated = TrainingEngine.upgradeAppearance(old)
        assertEquals("light", updated.preferences.theme)
        assertEquals(old.active, updated.active)
        assertEquals(old.rest, updated.rest)
        assertEquals(old.routines, updated.routines)
        val chosen = updated.copy(preferences = updated.preferences.copy(theme = "dark"))
        assertEquals(chosen, TrainingEngine.upgradeAppearance(chosen))
    }
    private val now = ClockReading(1_780_000_000_000, 100_000, 5, LocalDate.of(2026, 9, 10))
    private val exercise = Exercise("e1", name = "לחיצת חזה", muscle = Muscle.CHEST, sets = 3, restSeconds = 60)
    private val initial = AppState(preferences = Preferences(onboarded = true, autoCloseIdle = true), routines = listOf(Routine("r1", "חזה", listOf(exercise)), Routine("r2", "גב", listOf(exercise.copy(id = "e2", muscle = Muscle.BACK)))))
    private fun log(state: AppState, count: Int = 0, id: String = "action$count", value: Int = 12, weight: Double = 20.0, clock: ClockReading = now) =
        TrainingEngine.logSet(state, "r1", "e1", count, value, weight, id, "session", clock)

    @Test fun firstSetStartsClockAndCreatesRestAtomically() {
        val state = log(initial)
        assertEquals(1, state.active!!.totalSets)
        assertEquals(now.wall, state.active.startedAt)
        assertEquals(60, state.rest!!.seconds(now))
        assertEquals("session", state.rest.sessionId)
        assertEquals(20.0, state.routines.first().exercises.first().weight, 0.0)
    }
    @Test fun timerExpirationNeverLogsASet() {
        val state = log(initial)
        assertEquals(0, state.rest!!.seconds(now.copy(elapsed = now.elapsed + 90_000)))
        assertEquals(1, state.active!!.totalSets)
    }
    @Test fun duplicateTapDoesNotLogTwice() { val state = log(initial); assertEquals(state, log(state)) }
    @Test fun staleExpectedCountDoesNotLogNextSet() { val state = log(log(initial), 1); assertEquals(state, log(state, 0, "another")) }
    @Test fun sameActionIdIsIdempotentEvenWithNextCount() { val state = log(initial, id = "x"); assertEquals(state, log(state, 1, "x")) }
    @Test fun lastSetCompletesExerciseAndClearsTimer() { val state = log(log(log(initial), 1), 2); assertTrue(state.active!!.complete); assertNull(state.rest); assertEquals(state, log(state, 3)) }
    @Test fun zeroRestStillLogsAllSets() { val base = initial.copy(routines = listOf(initial.routines.first().copy(exercises = listOf(exercise.copy(restSeconds = 0))))); val state = log(log(log(base), 1), 2); assertTrue(state.active!!.complete); assertNull(state.rest) }
    @Test fun undoLastSetReopensExercise() { val state = TrainingEngine.undo(log(log(log(initial), 1), 2), "e1", 3, now); assertEquals(2, state.active!!.totalSets); assertFalse(state.active.complete); assertNull(state.rest) }
    @Test fun undoOnlySetReleasesEmptyWorkout() { val state = TrainingEngine.undo(log(initial), "e1", 1, now); assertNull(state.active); assertNull(state.rest); assertNotNull(TrainingEngine.begin(state, "r2", "new", now).active) }
    @Test fun volumeUsesActualRepsAndWeightForEachSet() { val state = log(log(initial, value = 12, weight = 20.0), 1, value = 8, weight = 22.5); assertEquals(420.0, state.active!!.volume, 0.001) }
    @Test fun amendRepsUpdatesVolume() { val state = TrainingEngine.amendLast(log(initial), "session", "e1", "action0", 10, 20.0); assertEquals(200.0, state.active!!.volume, 0.001) }
    @Test fun timedExerciseDoesNotCountVolume() { val base = initial.copy(routines = listOf(initial.routines.first().copy(exercises = listOf(exercise.copy(measure = Measure.SECONDS))))); assertEquals(0.0, log(base).active!!.volume, 0.0) }
    @Test fun noWeightExerciseCannotRecordWeight() { val base = initial.copy(routines = listOf(initial.routines.first().copy(exercises = listOf(exercise.copy(weighted = false))))); assertEquals(0.0, log(base).active!!.volume, 0.0) }
    @Test fun openingAnEmptySessionDoesNotStartClock() { val state = TrainingEngine.begin(initial, "r1", "x", now); assertEquals(0L, state.active!!.startedAt); assertEquals(0L, state.active.durationMs(now.wall + 50_000)) }
    @Test fun emptySessionsAreNotArchived() { val state = TrainingEngine.finish(TrainingEngine.begin(initial, "r1", "x", now), now); assertTrue(state.history.isEmpty()); assertNull(state.active) }
    @Test fun finishPreservesHistoryAndStopsRest() { val state = TrainingEngine.finish(log(initial), now.copy(wall = now.wall + 60_000)); assertEquals(1, state.history.size); assertEquals(60_000L, state.history.first().durationMs(0)); assertNull(state.rest); assertNull(state.active) }
    @Test fun weekRolloverIsNonDestructiveAndSundayBased() {
        val state = TrainingEngine.finish(log(initial), now)
        assertEquals(LocalDate.of(2026, 9, 6), weekStart(now.date))
        assertEquals(1, state.weekSessions(LocalDate.of(2026, 9, 12)).size)
        assertTrue(state.weekSessions(LocalDate.of(2026, 9, 13)).isEmpty())
        assertEquals(1, state.history.size)
        assertEquals(20.0, state.routines.first().exercises.first().weight, 0.0)
    }
    @Test fun sundayAcrossYearBoundaryIsCorrect() { assertEquals(LocalDate.of(2025, 12, 28), weekStart(LocalDate.of(2026, 1, 1))) }
    @Test fun flexibleModeSuggestsNextIncompleteRoutine() { val state = TrainingEngine.finish(log(log(log(initial), 1), 2), now); assertEquals("r2", state.nextRoutine(now.date)!!.id) }
    @Test fun scheduledModeRespectsTodayAndNeverChangesHistory() { val state = initial.copy(preferences = initial.preferences.copy(mode = PlanMode.SCHEDULED), routines = initial.routines.map { it.copy(days = setOf(0)) }); assertNull(state.nextRoutine(now.date)); assertNotNull(state.nextRoutine(LocalDate.of(2026, 9, 13))) }
    @Test fun pauseResumeMaintainsRemainingTime() { val state = log(initial); val later = now.copy(elapsed = now.elapsed + 20_000, wall = now.wall + 20_000); val paused = TrainingEngine.pauseRest(state, state.rest!!.id, later); assertEquals(40, paused.rest!!.seconds(later.copy(elapsed = later.elapsed + 100_000))); val resumed = TrainingEngine.pauseRest(paused, paused.rest.id, later.copy(elapsed = later.elapsed + 100_000)); assertEquals(40, resumed.rest!!.seconds(later.copy(elapsed = later.elapsed + 100_000))) }
    @Test fun changingWallClockDoesNotChangeLiveCountdown() { val state = log(initial); assertEquals(50, state.rest!!.seconds(now.copy(wall = now.wall + 86_400_000, elapsed = now.elapsed + 10_000))) }
    @Test fun rebootUsesWallClockFallback() { val state = log(initial); assertEquals(30, state.rest!!.seconds(now.copy(boot = 6, elapsed = 5000, wall = now.wall + 30_000))) }
    @Test fun staleSkipCannotCancelNewTimer() { val first = log(initial); val second = log(first, 1); assertEquals(second, TrainingEngine.skipRest(second, first.rest!!.id)) }
    @Test fun idleCloseUsesLastActivityTime() { val state = log(initial); val recovered = TrainingEngine.recover(state, now.copy(wall = now.wall + 1_300_000, elapsed = now.elapsed + 1_300_000)); assertNull(recovered.active); assertEquals(now.wall, recovered.history.first().endedAt); assertTrue(recovered.history.first().autoClosed) }
    @Test fun runningLongRestPreventsIdleClose() { val base = initial.copy(routines = listOf(initial.routines.first().copy(exercises = listOf(exercise.copy(restSeconds = 900))))); val state = log(base); assertNotNull(TrainingEngine.recover(state, now.copy(wall = now.wall + 700_000, elapsed = now.elapsed + 700_000)).active) }
    @Test fun repeatRoutinePreservesPreviousSession() { val state = TrainingEngine.finish(log(initial), now); val newState = TrainingEngine.begin(state, "r1", "new", now); assertEquals(0, newState.active!!.totalSets); assertEquals(1, newState.history.size) }
    @Test(expected = IllegalArgumentException::class) fun cannotSwitchActiveRoutineSilently() { TrainingEngine.begin(log(initial), "r2", "new", now) }
    @Test(expected = IllegalArgumentException::class) fun cannotMutateActivePlan() { TrainingEngine.saveRoutine(log(initial), initial.routines.first().copy(name = "changed")) }
    @Test(expected = IllegalArgumentException::class) fun rejectInvalidWeight() { log(initial, weight = Double.NaN) }
    @Test fun serializedRoundTripPreservesHebrewAndSetDetails() { val state = log(log(initial), 1); val text = StateSerializer.json.encodeToString(state); assertEquals(state, StateSerializer.json.decodeFromString<AppState>(text).validate()) }
    @Test(expected = IllegalArgumentException::class) fun duplicateExerciseIdsAreRejected() { initial.copy(routines = initial.routines.map { it.copy(exercises = listOf(exercise)) }).validate() }
    @Test fun targetRangesSurviveSerialization() { val range = exercise.copy(target = 10, targetMax = 12); assertEquals("10–12", range.targetLabel()); assertEquals(range, StateSerializer.json.decodeFromString<Exercise>(StateSerializer.json.encodeToString(range))) }
    @Test fun oldExerciseWithoutUpperTargetStillDecodes() { val text = """{"id":"e","name":"test","muscle":"CHEST","target":8}"""; val old = StateSerializer.json.decodeFromString<Exercise>(text); assertEquals(8, old.targetMax); old.checked() }
    @Test fun systemLanguageUsesHebrewAndEnglishFallback() { val prefs = Preferences(language = "system"); assertFalse(prefs.isEnglish("he")); assertFalse(prefs.isEnglish("iw")); assertTrue(prefs.isEnglish("en")); assertTrue(prefs.isEnglish("fr")); assertFalse(Preferences(language = "he").isEnglish("en")) }
    @Test fun builtInNamesTranslateButUserNamesArePreserved() { val builtIn = Routine("r", "חזה", nameEn = "Chest", customName = false); assertEquals("Chest", builtIn.label(true)); assertEquals("חזה", builtIn.label(false)); assertEquals("שלי", Routine("r", "שלי").label(true)) }
    @Test fun completeExerciseLogsOnlyRemainingSetsWithConfirmedValues() { val first = log(initial); val completed = TrainingEngine.completeExercise(first, "r1", "e1", 1, 8, 25.0, "bulk", "ignored", now); assertTrue(completed.active!!.complete); assertEquals(listOf(12, 8, 8), completed.active.exercises.first().sets.map { it.value }); assertEquals(640.0, completed.active.volume, 0.0); assertNull(completed.rest); assertEquals(completed, TrainingEngine.completeExercise(completed, "r1", "e1", 1, 8, 25.0, "bulk", "ignored", now)) }
    @Test fun clearExerciseResetsOnlyActiveSets() { val history = TrainingEngine.finish(log(initial), now); val active = log(TrainingEngine.begin(history, "r1", "second", now)); val reset = TrainingEngine.resetExercise(active, "e1", 1, now).validate(); assertNull(reset.active); assertEquals(1, reset.history.size) }
    @Test fun movingWallClockBackwardsDoesNotRejectNextSet() { val first = log(initial); val second = log(first, 1, clock = now.copy(wall = now.wall - 3_600_000, elapsed = now.elapsed + 5000)).validate(); assertEquals(2, second.active!!.totalSets); assertEquals(now.wall, second.active.lastActionAt) }
    @Test fun detachedAndInvalidTimerBackupsAreRejected() { val valid = log(initial); assertTrue(runCatching { valid.copy(rest = valid.rest!!.copy(sessionId = "wrong")).validate() }.isFailure); assertTrue(runCatching { valid.copy(rest = valid.rest!!.copy(pausedMs = Long.MAX_VALUE)).validate() }.isFailure); assertTrue(runCatching { valid.copy(rest = valid.rest!!.copy(loggedSets = 2)).validate() }.isFailure) }
    @Test fun duplicateReminderIdsAreRejected() { val reminder = Reminder("same", 18, 0, setOf(0)); assertTrue(runCatching { initial.copy(preferences = initial.preferences.copy(reminders = listOf(reminder, reminder))).validate() }.isFailure) }
}


