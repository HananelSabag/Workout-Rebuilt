package com.hananel.workout

import com.hananel.workout.domain.*
import com.hananel.workout.data.StateSerializer
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PlanningTest {
    private val monday = LocalDate.of(2026, 9, 7)
    private val sunday = LocalDate.of(2026, 9, 13)
    private val base = AppState(preferences = Preferences(onboarded = true), routines = listOf(
        Routine("A", "חזה ואחורית", listOf(Exercise("a", name = "חזה", muscle = Muscle.CHEST, sets = 1, restSeconds = 0))),
        Routine("B", "גב וקדמית", listOf(Exercise("b", name = "גב", muscle = Muscle.BACK, sets = 1, restSeconds = 0))),
        Routine("C", "רגליים וכתפיים", listOf(Exercise("c", name = "רגליים", muscle = Muscle.LEGS, sets = 1, restSeconds = 0)))
    ))
    private fun clock(date: LocalDate) = ClockReading(date.atTime(18, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), 100_000, 1, date)
    private fun train(state: AppState, id: String, date: LocalDate, finish: Boolean = true): AppState {
        val e = state.routines.first { it.id == id }.exercises.first()
        val sessionId = "$id-$date-${state.history.size}"
        val logged = TrainingEngine.logSet(state, id, e.id, 0, 12, 20.0, "set-$sessionId", sessionId, clock(date))
        return if (finish) TrainingEngine.finish(logged, clock(date)).validate() else logged.validate()
    }

    @Test fun newWeekKeepsCNextAndStartsAnEmptyWeeklySummary() {
        val state = train(train(base, "A", monday), "B", monday.plusDays(2))
        assertEquals("C", state.nextRoutine(sunday)!!.id)
        assertEquals(setOf("A", "B"), state.cycleProgress().completedIds)
        assertTrue(state.weekSessions(sunday).isEmpty())
        assertEquals(2, state.history.size)
    }

    @Test fun completedRoundRestartsOnlyWhenANewSetIsActuallyLogged() {
        val done = train(train(train(base, "A", monday), "B", monday.plusDays(1)), "C", monday.plusDays(2))
        assertEquals(setOf("A", "B", "C"), done.cycleProgress().completedIds)
        assertEquals("A", done.nextRoutine(sunday)!!.id)
        val opened = TrainingEngine.begin(done, "B", "empty", clock(sunday))
        assertEquals(done.planProgress, opened.cycleProgress())
        val closed = TrainingEngine.finish(opened, clock(sunday))
        assertEquals(done.history, closed.history)
        assertEquals(done.planProgress, closed.planProgress)
        val next = train(closed, "B", sunday)
        assertEquals(2, next.cycleProgress().round)
        assertEquals(setOf("B"), next.cycleProgress().completedIds)
        assertEquals("A", next.nextRoutine(sunday)!!.id)
        assertEquals(next, TrainingEngine.finish(next, clock(sunday)))
    }

    @Test fun undoCompletionRestoresRemainingAndPartialFinishDoesNotAdvance() {
        val active = train(base, "A", monday, finish = false)
        assertTrue("A" in active.cycleProgress().completedIds)
        val undone = TrainingEngine.undo(active, "a", 1, clock(monday))
        assertFalse("A" in undone.cycleProgress().completedIds)
        val partialPlan = base.copy(routines = base.routines.map { if (it.id == "A") it.copy(exercises = it.exercises.map { e -> e.copy(sets = 2) }) else it })
        val partial = train(partialPlan, "A", monday)
        assertFalse(partial.history.first().complete)
        assertEquals("A", partial.nextRoutine(sunday)!!.id)
    }

    @Test fun sameScheduledRoutineOnTwoWeekdaysIsTwoOccurrences() {
        val scheduled = base.copy(preferences = base.preferences.copy(mode = PlanMode.SCHEDULED), routines = base.routines.map { it.copy(days = if (it.id == "A") setOf(1, 4) else emptySet()) })
        val state = train(scheduled, "A", monday)
        assertNull(state.nextRoutine(monday))
        assertEquals("A", state.nextRoutine(monday.plusDays(3))!!.id)
        assertTrue(state.cycleProgress().completedIds.isEmpty())
        assertEquals(1, state.history.size)
    }

    @Test fun freeChoiceHasNoAutomaticSelectionAndOutOfOrderTrainingKeepsOthersRemaining() {
        val free = base.copy(preferences = base.preferences.copy(flexibleOrder = FlexibleOrder.FREE))
        assertNull(free.nextRoutine(monday))
        val state = train(free, "C", monday)
        assertNull(state.nextRoutine(sunday))
        assertEquals(setOf("C"), state.cycleProgress().completedIds)
        assertEquals("A", state.copy(preferences = state.preferences.copy(flexibleOrder = FlexibleOrder.ORDERED)).nextRoutine(sunday)!!.id)
    }

    @Test fun legacyMigrationIsStableAcrossHistoryDeletionAndBackupRoundTrip() {
        val trained = train(train(base, "A", monday), "B", monday.plusDays(1))
        val legacy = trained.copy(planProgress = null, history = trained.history.map { it.copy(planMode = null) })
        val migrated = legacy.upgradePlanning().validate()
        val withoutHistory = migrated.copy(history = emptyList()).upgradePlanning()
        assertEquals("C", withoutHistory.nextRoutine(sunday)!!.id)
        val encoded = StateSerializer.json.encodeToString(migrated)
        assertEquals(migrated, StateSerializer.json.decodeFromString<AppState>(encoded).validate())
        assertEquals(migrated, migrated.upgradePlanning())
    }

    @Test fun sessionKeepsOriginalModeWhenPreferencesChangeMidWorkout() {
        val active = train(base, "A", monday, finish = false)
        val changed = active.copy(preferences = active.preferences.copy(mode = PlanMode.SCHEDULED))
        val finished = TrainingEngine.finish(changed, clock(monday))
        assertEquals(setOf("A"), finished.planProgress!!.completedIds)
        val scheduled = train(finished, "B", monday.plusDays(1), finish = false)
        val backToFlexible = scheduled.copy(preferences = scheduled.preferences.copy(mode = PlanMode.FLEXIBLE))
        assertEquals(setOf("A"), TrainingEngine.finish(backToFlexible, clock(sunday)).planProgress!!.completedIds)
    }

    @Test fun editingNamesOrderAndMembershipUsesStableRoutineIds() {
        val state = train(base, "A", monday)
        val reordered = state.copy(routines = listOf(state.routines[2], state.routines[0].copy(name = "שם חדש"), state.routines[1]))
        assertEquals(setOf("A"), reordered.cycleProgress().completedIds)
        assertEquals("C", reordered.nextRoutine(sunday)!!.id)
        val removed = reordered.copy(routines = reordered.routines.filter { it.id != "A" }).upgradePlanning()
        assertTrue(removed.cycleProgress().completedIds.isEmpty())
    }
}
