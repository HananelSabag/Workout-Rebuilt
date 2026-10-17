package com.hananel.workout.data

import android.content.Context
import com.hananel.workout.domain.*
import com.hananel.workout.timer.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class PlatformWarning { TIMER, REMINDER }
data class CommandResult(val state: AppState, val warnings: Set<PlatformWarning> = emptySet())

/** Application-wide command boundary shared by screens, notification actions and lifecycle receivers.
 * Serializes persistence + platform handoff so an older command cannot restart a newer timer.
 * Durable data always commits first; platform failures are reported without claiming the save failed.
 */
class WorkoutCoordinator(
    private val repository: WorkoutRepository,
    private val effects: PlatformEffects
) {
    interface PlatformEffects {
        fun timer(state: AppState, mayStartService: Boolean)
        fun reminders(previous: Preferences, next: Preferences)
        fun restComplete(timer: RestTimer, preferences: Preferences)
        fun inactivity(state: AppState) {}
    }
    private val commands = Mutex()

    suspend fun execute(mayStartService: Boolean = true, refresh: Boolean = false, refreshReminders: Boolean = false, transform: (AppState) -> AppState): CommandResult = commands.withLock {
        withContext(NonCancellable) {
            val previous = repository.current()
            val state = repository.update { transform(it.upgradePlanning()).validate() }
            val warnings = mutableSetOf<PlatformWarning>()
            if (refresh || previous.active != state.active || previous.rest != state.rest || previous.preferences.autoCloseIdle != state.preferences.autoCloseIdle)
                runCatching { effects.inactivity(state) }.onFailure { warnings += PlatformWarning.TIMER }
            if (refresh || previous.rest != state.rest || previous.preferences.language != state.preferences.language) runCatching { effects.timer(state, mayStartService) }.onFailure { warnings += PlatformWarning.TIMER }
            if (refresh || refreshReminders || previous.preferences.reminders != state.preferences.reminders || previous.preferences.reminderEnabled != state.preferences.reminderEnabled)
                runCatching { effects.reminders(previous.preferences, state.preferences) }.onFailure { warnings += PlatformWarning.REMINDER }
            CommandResult(state, warnings)
        }
    }

    suspend fun completeRest(timerId: String, clock: ClockReading) = commands.withLock {
        withContext(NonCancellable) {
            repository.claimAlert(clock, timerId)?.let { effects.restComplete(it, repository.current().preferences) }
        }
    }
}

class AndroidEffects(private val context: Context) : WorkoutCoordinator.PlatformEffects {
    override fun inactivity(state: AppState) = IdleAlarms.schedule(context, state)
    override fun timer(state: AppState, mayStartService: Boolean) {
        // Reopening the app must not dismiss an already delivered completion action.
        if (state.rest?.alerted != true) Notifications.clear(context)
        if (mayStartService) RestService.sync(context, state) else TimerAlarms.schedule(context, state.rest)
    }
    override fun reminders(previous: Preferences, next: Preferences) = Reminders.reconcile(context, previous.reminders, next)
    override fun restComplete(timer: RestTimer, preferences: Preferences) = Notifications.complete(context, timer, preferences.isEnglish())
}
