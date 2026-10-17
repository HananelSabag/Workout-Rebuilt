package com.hananel.workout

import androidx.datastore.core.DataStore
import com.hananel.workout.data.*
import com.hananel.workout.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class CoordinatorTest {
    private val clock = ClockReading(1_780_000_000_000, 100_000, 5, LocalDate.of(2026, 9, 10))
    private val initial = AppState(preferences = Preferences(onboarded = true), routines = listOf(Routine("r", "Chest", listOf(Exercise("e", name = "Press", muscle = Muscle.CHEST)))))
    private class Effects : WorkoutCoordinator.PlatformEffects {
        val timerStates = mutableListOf<AppState>()
        var reminders = 0
        var alerts = 0
        var failTimer = false
        override fun timer(state: AppState, mayStartService: Boolean) { timerStates += state; if (failTimer) error("platform unavailable") }
        override fun reminders(previous: Preferences, next: Preferences) { reminders++ }
        override fun restComplete(timer: RestTimer, preferences: Preferences) { alerts++ }
    }
    private fun test(block: suspend (WorkoutRepository, WorkoutCoordinator, Effects) -> Unit) = runBlocking {
        // FileStorage uses Android/POSIX rename semantics. Actual disk persistence is
        // covered on Android; this adapter isolates command ordering on Windows JVMs.
        val store = object : DataStore<AppState> {
            override val data = MutableStateFlow(initial)
            private val mutex = Mutex()
            override suspend fun updateData(transform: suspend (AppState) -> AppState): AppState =
                mutex.withLock { transform(data.value).also { data.value = it } }
        }
        val repo = WorkoutRepository(store)
        val effects = Effects()
        val coordinator = WorkoutCoordinator(repo, effects)
        block(repo, coordinator, effects)
    }
    @Test fun uiAndNotificationCommandsShareAtomicBoundary() = test { repo, commands, effects ->
        coroutineScope { repeat(30) { i -> launch(Dispatchers.Default) {
            commands.execute { TrainingEngine.logSet(it, "r", "e", 0, 10, 20.0, "action$i", "s", clock) }
        } } }
        assertEquals(1, repo.current().active!!.totalSets)
        assertEquals(1, effects.timerStates.size)
        assertEquals(repo.current().rest, effects.timerStates.last().rest)
    }
    @Test fun failedPlatformEffectDoesNotLoseSaveOrSkipOtherEffect() = test { repo, commands, effects ->
        effects.failTimer = true
        val result = commands.execute(refresh = true) { it.copy(preferences = it.preferences.copy(theme = "light")) }
        assertEquals("light", repo.current().preferences.theme)
        assertEquals(setOf(PlatformWarning.TIMER), result.warnings)
        assertEquals(1, effects.reminders)
    }
    @Test fun invalidDataCannotBypassCoordinator() = test { repo, commands, effects ->
        assertTrue(runCatching { commands.execute { it.copy(preferences = it.preferences.copy(language = "invalid")) } }.isFailure)
        assertEquals(initial, repo.current())
        assertTrue(effects.timerStates.isEmpty())
    }
    @Test fun concurrentExpirationDeliversOneAlertAndNoSet() = test { repo, commands, effects ->
        commands.execute { TrainingEngine.logSet(it, "r", "e", 0, 10, 20.0, "first", "s", clock) }
        coroutineScope { repeat(20) { launch { commands.completeRest("first", clock.copy(elapsed = clock.elapsed + 70_000)) } } }
        assertEquals(1, effects.alerts)
        assertEquals(1, repo.current().active!!.totalSets)
    }
    @Test fun notificationAndUiCannotResurrectSkippedTimer() = test { repo, commands, effects ->
        commands.execute { TrainingEngine.logSet(it, "r", "e", 0, 10, 20.0, "first", "s", clock) }
        commands.execute { TrainingEngine.skipRest(it, "first") }
        commands.execute { TrainingEngine.pauseRest(it, "first", clock) }
        assertNull(repo.current().rest)
        assertNull(effects.timerStates.last().rest)
    }
    @Test fun changingAppearanceDoesNotRescheduleReminders() = test { _, commands, effects ->
        commands.execute { it.copy(preferences = it.preferences.copy(theme = "dark")) }
        assertEquals(0, effects.reminders)
        assertTrue(effects.timerStates.isEmpty())
        commands.execute { it.copy(preferences = it.preferences.copy(language = "en")) }
        assertEquals(1, effects.timerStates.size)
        assertEquals(0, effects.reminders)
    }
}
