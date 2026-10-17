package com.hananel.workout

import com.hananel.workout.data.StateSerializer
import com.hananel.workout.domain.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RefinementRegressionTest {
    private val now = ClockReading(1_780_000_000_000, 100_000, 5, LocalDate.of(2026, 9, 12))
    private val exercise = Exercise("press", name = "Press", muscle = Muscle.CHEST, sets = 3, weight = 20.0, restSeconds = 60)
    private val base = AppState(preferences = Preferences(onboarded = true, appearanceVersion = 2), routines = listOf(Routine("A", "Chest", listOf(exercise))))
    private fun later(ms: Long) = now.copy(wall = now.wall + ms, elapsed = now.elapsed + ms)
    private fun logged() = TrainingEngine.logSet(base, "A", "press", 0, 12, 20.0, "set", "session", now)

    @Test fun upgradingLegacyActiveSnapshotUsesRememberedWeightAndPreservesRecordedData() {
        val current = TrainingEngine.logSet(base, "A", "press", 0, 12, 27.5, "set", "session", now)
        val legacy = current.copy(preferences = current.preferences.copy(appearanceVersion = 1, autoCloseIdle = false, glass = false),
            active = current.active!!.copy(exercises = current.active.exercises.map { it.copy(exercise = it.exercise.copy(weight = 0.0)) }))
        val upgraded = TrainingEngine.upgradeAppearance(legacy)
        assertEquals(27.5, upgraded.active!!.exercises.first().exercise.weight, 0.0)
        assertEquals(legacy.active!!.exercises.first().sets, upgraded.active.exercises.first().sets)
        assertEquals(legacy.active.lastActionAt, upgraded.active.lastActionAt)
        assertEquals(legacy.rest, upgraded.rest)
        assertEquals(legacy.history, upgraded.history)
        assertEquals(legacy.routines, upgraded.routines)
        val changed = TrainingEngine.setWorkingWeight(upgraded, "A", "press", 35.0, now)
            .copy(preferences = upgraded.preferences.copy(autoCloseIdle = false))
        assertEquals(changed, TrainingEngine.upgradeAppearance(changed))
    }

    @Test fun weightBeforeFirstSetSurvivesReloadWithoutStartingWorkout() {
        val changed = TrainingEngine.setWorkingWeight(base, "A", "press", 32.5, now)
        val reloaded = StateSerializer.json.decodeFromString<AppState>(StateSerializer.json.encodeToString(changed)).validate()
        assertNull(reloaded.active)
        assertEquals(32.5, reloaded.routines.first().exercises.first().weight, 0.0)
    }
    @Test fun activeEditPreservesAlreadyLoggedAndHistoricalWeights() {
        val previous = TrainingEngine.finish(logged(), now)
        val active = TrainingEngine.logSet(previous, "A", "press", 0, 10, 22.5, "next-set", "next-session", now)
        val edited = TrainingEngine.saveExercise(active, "A", exercise.copy(name = "New press", weight = 35.0, sets = 4, restSeconds = 90, notes = "Controlled"), later(1000))
        assertEquals(previous.history, edited.history)
        assertEquals(active.active!!.exercises.first().sets, edited.active!!.exercises.first().sets)
        assertEquals(35.0, edited.active.exercises.first().exercise.weight, 0.0)
        assertEquals(4, edited.active.plannedSets)
        assertEquals(active.rest!!.deadlineElapsed, edited.rest!!.deadlineElapsed)
        assertEquals("New press", edited.rest.title)
    }
    @Test fun editingCannotDropRecordedSetsOrReinterpretUnits() {
        val active = TrainingEngine.logSet(logged(), "A", "press", 1, 10, 20.0, "second", "session", now)
        assertTrue(runCatching { TrainingEngine.saveExercise(active, "A", exercise.copy(sets = 1), now) }.isFailure)
        assertTrue(runCatching { TrainingEngine.saveExercise(active, "A", exercise.copy(measure = Measure.SECONDS), now) }.isFailure)
        assertTrue(runCatching { TrainingEngine.saveExercise(active, "A", exercise.copy(weighted = false), now) }.isFailure)
        assertEquals(2, active.active!!.totalSets)
    }
    @Test fun addingExerciseDuringWorkoutExtendsPlanWithoutChangingRecordedSets() {
        val first = logged()
        val added = TrainingEngine.saveExercise(first, "A", exercise.copy(id = "fly", name = "Fly"), now)
        assertEquals(first.active!!.id, added.active!!.id)
        assertEquals(first.active.exercises.first(), added.active.exercises.first())
        assertEquals(6, added.active.plannedSets)
        assertEquals(1, added.active.totalSets)
        assertEquals(first.rest, added.rest)
    }
    @Test fun pausedTimerExpiresAtTwentyMinutesAndSavesLastActionTime() {
        val paused = TrainingEngine.pauseRest(logged(), "set", later(10_000))
        assertNotNull(TrainingEngine.recover(paused, later(1_209_999)).active)
        val closed = TrainingEngine.recover(paused, later(1_210_000))
        assertNull(closed.active)
        assertNull(closed.rest)
        assertEquals(now.wall + 10_000, closed.history.first().endedAt)
        assertEquals(1, closed.history.first().totalSets)
        assertTrue(closed.history.first().autoClosed)
    }
    @Test fun normalRestGetsTwentyMinutesAfterFinishing() {
        val active = logged()
        assertEquals(1_260_000L, TrainingEngine.idleRemainingMs(active, now))
        assertNotNull(TrainingEngine.recover(active, later(1_259_999)).active)
        assertNull(TrainingEngine.recover(active, later(1_260_000)).active)
    }
    @Test fun timerAndWeightActionsRenewIdleWindowButOpeningDoesNot() {
        val paused = TrainingEngine.pauseRest(logged(), "set", now)
        val changed = TrainingEngine.setWorkingWeight(paused, "A", "press", 27.5, later(1_000_000))
        assertNotNull(TrainingEngine.recover(changed, later(1_200_000)).active)
        assertNull(TrainingEngine.recover(paused, later(1_200_000)).active)
        val adjusted = TrainingEngine.adjustRest(paused, "set", 15, later(1_000_000))
        assertNotNull(TrainingEngine.recover(adjusted, later(1_200_000)).active)
    }
    @Test fun delayedRecoveryDoesNotTurnIdleTimeIntoWorkoutDuration() {
        val ended = TrainingEngine.recover(logged(), later(446L * 60_000))
        assertNull(ended.active)
        assertEquals(0L, ended.history.first().durationMs(later(446L * 60_000).wall))
        assertEquals(1, ended.history.first().totalSets)
    }
    @Test fun completingEditedLastExerciseClearsItsRest() {
        val finished = TrainingEngine.saveExercise(logged(), "A", exercise.copy(sets = 1), now)
        assertTrue(finished.active!!.complete)
        assertNull(finished.rest)
        assertEquals(20.0 * 12, finished.active.volume, 0.0)
    }
    @Test fun migrationRecoversPreviouslyManualSessionBeforeShowingDuration() {
        val old = logged().copy(preferences = Preferences(onboarded = true, autoCloseIdle = false, glass = false, appearanceVersion = 1))
        val loaded = TrainingEngine.recover(TrainingEngine.upgradeAppearance(old), later(446L * 60_000))
        assertNull(loaded.active)
        assertEquals(old.active!!.totalSets, loaded.history.first().totalSets)
        assertEquals(old.routines, loaded.routines)
        assertTrue(loaded.preferences.autoCloseIdle)
    }

}
