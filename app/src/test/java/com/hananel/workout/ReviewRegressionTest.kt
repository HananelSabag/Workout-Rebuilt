package com.hananel.workout

import com.hananel.workout.data.StateSerializer
import com.hananel.workout.domain.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReviewRegressionTest {
    private val now = ClockReading(1_780_000_000_000, 100_000, 5, LocalDate.of(2026, 9, 10))
    private val exercise = Exercise("a", name = "Press", muscle = Muscle.CHEST, sets = 2, restSeconds = 60)
    private val base = AppState(preferences = Preferences(autoCloseIdle = true), routines = listOf(Routine("A", "Chest", listOf(exercise), shortLabel = "A"),
        Routine("B", "Back", listOf(exercise.copy(id = "b")))))
    private fun log(state: AppState, count: Int = 0, action: String = "set$count") =
        TrainingEngine.logSet(state, "A", "a", count, 12, 20.0, action, if (state.history.isEmpty()) "session" else "session-${state.history.size}", now).validate()

    @Test fun addingWorkoutDuringNewRoundDoesNotResurrectPreviousCompletions() {
        val completed = base.copy(planProgress = PlanProgress(setOf("A", "B"), 4))
        val first = log(completed)
        val expanded = TrainingEngine.saveRoutine(first, Routine("C", "Legs", listOf(exercise.copy(id = "c"))))
        assertEquals(5, expanded.cycleProgress().round)
        assertTrue(expanded.cycleProgress().completedIds.isEmpty())
        val saved = TrainingEngine.finish(log(expanded, 1), now)
        assertEquals(5, saved.planProgress!!.round)
        assertEquals(setOf("A"), saved.planProgress.completedIds)
        assertEquals("B", saved.nextRoutine(now.date)!!.id)
        assertEquals(saved, StateSerializer.json.decodeFromString<AppState>(StateSerializer.json.encodeToString(saved)).validate())
    }

    @Test fun removingRemainingWorkoutDoesNotAdvanceAnAlreadyStartedRound() {
        val active = log(base.copy(planProgress = PlanProgress(setOf("A"), 2)))
        val changed = TrainingEngine.deleteRoutine(active, "B")
        assertEquals(2, changed.cycleProgress().round)
        assertEquals(2, TrainingEngine.finish(changed, now).planProgress!!.round)
    }

    @Test fun undoFirstSetRollsBackNewRoundAndDoesNotCreateHistory() {
        val completed = base.copy(planProgress = PlanProgress(setOf("A", "B"), 4))
        val undone = TrainingEngine.undo(log(completed), "a", 1, now)
        assertNull(undone.active)
        assertEquals(completed.planProgress, undone.cycleProgress())
        assertTrue(undone.history.isEmpty())
    }

    @Test fun correctionTargetsExactSessionAndLastSetAndKeepsDeadline() {
        val first = log(base)
        val corrected = TrainingEngine.amendLast(first, "session", "a", "set0", 8, 25.5)
        assertEquals(204.0, corrected.active!!.volume, 0.0)
        assertEquals(25.5, corrected.routines.first().exercises.first().weight, 0.0)
        assertEquals(first.rest, corrected.rest)
        val second = log(corrected, 1)
        assertEquals(second, TrainingEngine.amendLast(second, "session", "a", "set0", 6, 30.0))
        assertEquals(second, TrainingEngine.amendLast(second, "old-session", "a", "set1", 6, 30.0))
        val undone = TrainingEngine.undo(first, "a", 1, now)
        val replacement = log(undone, action = "replacement")
        assertEquals(replacement, TrainingEngine.amendLast(replacement, "session", "a", "set0", 6, 30.0))
    }

    @Test fun pausedTimerAdjustedToZeroCanComplete() {
        val paused = TrainingEngine.pauseRest(log(base), "set0", now)
        val done = TrainingEngine.adjustRest(paused, "set0", -60, now).validate()
        assertNull(done.rest!!.pausedMs)
        assertEquals(0L, done.rest.remainingMs(now))
        assertEquals(1, done.active!!.totalSets)
    }

    @Test fun renamedRoutineDoesNotRelabelSavedCalendarMarker() {
        val saved = TrainingEngine.finish(log(base), now)
        val renamed = TrainingEngine.saveRoutine(saved, saved.routines.first().copy(name = "Other", shortLabel = "X"))
        assertEquals("A", renamed.history.first().calendarMarker)
        assertEquals("Chest", renamed.history.first().name)
    }

    @Test fun movingAndRepeatingWorkoutKeepsOtherRemainingWorkouts() {
        val saved = TrainingEngine.finish(log(log(base), 1), now)
        val repeat = TrainingEngine.finish(log(log(TrainingEngine.moveRoutine(saved, "B", -1)), 1), now)
        assertEquals(1, repeat.cycleProgress().round)
        assertEquals(setOf("A"), repeat.cycleProgress().completedIds)
        assertEquals("B", repeat.nextRoutine(now.date)!!.id)
        assertEquals(2, repeat.history.size)
    }

    @Test fun pausedRestExpiresButManualFinishSettingPreventsIdleClose() {
        val first = log(base)
        val later = now.copy(wall = now.wall + 3_600_000, elapsed = now.elapsed + 3_600_000)
        val paused = TrainingEngine.pauseRest(first, "set0", now)
        assertNull(TrainingEngine.recover(paused, later).active)
        val manual = first.copy(preferences = first.preferences.copy(autoCloseIdle = false))
        assertNotNull(TrainingEngine.recover(manual, later).active)
        val skipped = TrainingEngine.skipRest(paused, "set0", later)
        assertNotNull(TrainingEngine.recover(skipped, later).active)
    }

    @Test fun longRestHasFullIdleWindowAfterItsDeadline() {
        val longRest = log(base.copy(routines = base.routines.map { it.copy(exercises = it.exercises.map { e -> e.copy(restSeconds = 900) }) }))
        val justEnded = now.copy(wall = now.wall + 901_000, elapsed = now.elapsed + 901_000)
        assertNotNull(TrainingEngine.recover(longRest, justEnded).active)
        assertNull(TrainingEngine.recover(longRest, now.copy(wall = now.wall + 2_200_000, elapsed = now.elapsed + 2_200_000)).active)
    }

    @Test fun changingWallClockDoesNotAutoCloseActiveSession() {
        val first = TrainingEngine.skipRest(log(base), "set0", now)
        assertNotNull(TrainingEngine.recover(first, now.copy(wall = now.wall + 86_400_000, elapsed = now.elapsed + 5000)).active)
        assertNull(TrainingEngine.recover(first, now.copy(wall = now.wall - 86_400_000, elapsed = now.elapsed + 1_300_000)).active)
    }

    @Test fun existingPreferencesReceiveRevisedDefaultsOnceWithoutLosingWorkout() {
        val oldPreferences = StateSerializer.json.decodeFromString<Preferences>("""{"onboarded":true,"language":"he","appearanceVersion":1,"autoCloseIdle":false,"glass":false}""")
        val active = log(base.copy(preferences = oldPreferences))
        val migrated = TrainingEngine.upgradeAppearance(active)
        assertTrue(migrated.preferences.autoCloseIdle)
        assertTrue(migrated.preferences.glass)
        assertEquals(active.active, migrated.active)
        val changed = migrated.copy(preferences = migrated.preferences.copy(autoCloseIdle = false))
        assertEquals(changed, TrainingEngine.upgradeAppearance(changed))
    }
}
