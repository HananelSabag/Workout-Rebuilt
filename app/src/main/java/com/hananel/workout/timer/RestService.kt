package com.hananel.workout.timer

import android.app.*
import android.content.*
import android.os.*
import androidx.core.content.ContextCompat
import com.hananel.workout.WorkoutApplication
import com.hananel.workout.data.clock
import com.hananel.workout.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/** No ticks are written to disk. Service and UI derive the countdown from the same deadline. */
class RestService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val repo get() = (application as WorkoutApplication).repository
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Notifications.RUNNING, Notifications.live(this, null))
        if (job?.isActive != true) job = scope.launch {
            try {
                repo.data.collectLatest { state ->
                    val timer = state.rest
                    if (timer == null || timer.alerted) {
                        releaseWakeLock()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@collectLatest
                    }
                    val english = state.preferences.isEnglish()
                    releaseWakeLock()
                    if (timer.running(clock())) {
                        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "workout:rest").apply {
                            acquire(timer.remainingMs(clock()) + 15_000L)
                        }
                    }
                    TimerAlarms.schedule(this@RestService, timer)
                    while (currentCoroutineContext().isActive) {
                        val reading = clock()
                        if (timer.pausedMs == null && timer.remainingMs(reading) == 0L) {
                            // Committing alerted=true emits to collectLatest and cancels this block.
                            // Complete the alert handoff even when that emission stops the service.
                            withContext(NonCancellable) {
                                (application as WorkoutApplication).coordinator.completeRest(timer.id, reading)
                            }
                            break
                        }
                        getSystemService(NotificationManager::class.java).notify(Notifications.RUNNING, Notifications.live(this@RestService, timer, english))
                        delay(1000)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.e("WorkoutTimer", "Timer service failed", e); stopSelf() }
        }
        return START_STICKY
    }
    private fun releaseWakeLock() { wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null }
    override fun onDestroy() { releaseWakeLock(); scope.cancel(); super.onDestroy() }
    companion object {
        fun sync(context: Context, state: AppState) {
            val timer = state.rest
            TimerAlarms.schedule(context, timer)
            if (timer == null) {
                Notifications.clear(context)
                context.stopService(Intent(context, RestService::class.java))
            } else if (!timer.alerted) ContextCompat.startForegroundService(context, Intent(context, RestService::class.java))
        }
    }
}

object TimerAlarms {
    private fun pending(context: Context, timerId: String = "") = PendingIntent.getBroadcast(context, 99,
        Intent(context, TimerReceiver::class.java).setAction("alarm").putExtra("timerId", timerId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(context: Context, timer: RestTimer?) {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.cancel(pending(context))
        if (timer == null || timer.pausedMs != null || timer.alerted) return
        val intent = pending(context, timer.id)
        val deadline = SystemClock.elapsedRealtime() + timer.remainingMs(context.clock())
        try {
            if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, intent)
            else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, intent)
        } catch (_: SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, intent) }
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as WorkoutApplication
                val timerId = intent.getStringExtra("timerId") ?: return@launch
                if (intent.action == "alarm") {
                    app.coordinator.completeRest(timerId, context.clock())
                } else {
                    app.coordinator.execute { state ->
                        val recovered = TrainingEngine.recover(state, context.clock())
                        if (recovered != state) return@execute recovered
                        val timer = state.rest?.takeIf { it.id == timerId } ?: return@execute state
                        when (intent.action) {
                            "pause" -> TrainingEngine.pauseRest(state, timerId, context.clock())
                            "skip" -> TrainingEngine.skipRest(state, timerId, context.clock())
                            "next" -> {
                                if (timer.pausedMs != null || timer.remainingMs(context.clock()) > 0) return@execute state
                                val active = state.active?.takeIf { it.id == timer.sessionId } ?: return@execute state
                                val log = active.exercises.find { it.exercise.id == timer.exerciseId } ?: return@execute state
                                TrainingEngine.logSet(state, active.routineId, timer.exerciseId, timer.loggedSets,
                                    log.sets.lastOrNull()?.value ?: log.exercise.target, log.exercise.weight,
                                    "notification-${timer.id}", active.id, context.clock())
                            }
                            else -> state
                        }
                    }
                }
            } catch (e: Exception) { android.util.Log.e("WorkoutTimer", "Notification action failed", e) }
            finally { pending.finish() }
        }
    }
}
