package com.hananel.workout.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.hananel.workout.WorkoutApplication
import com.hananel.workout.data.clock
import com.hananel.workout.domain.AppState
import com.hananel.workout.domain.TrainingEngine
import kotlinx.coroutines.*

/** One replaceable alarm, no polling service. Late delivery still saves the last actual activity time. */
object IdleAlarms {
    fun schedule(context: Context, state: AppState) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, 100,
            Intent(context, IdleReceiver::class.java).putExtra("sessionId", state.active?.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.cancel(pending)
        val remaining = TrainingEngine.idleRemainingMs(state, context.clock()) ?: return
        val at = SystemClock.elapsedRealtime() + remaining
        try {
            if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        } catch (_: SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending) }
    }
}

class IdleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                (context.applicationContext as WorkoutApplication).coordinator.execute(mayStartService = false, refresh = true) { state ->
                    if (state.active?.id != intent.getStringExtra("sessionId")) state else TrainingEngine.recover(state, context.clock())
                }
            } catch (e: Exception) { android.util.Log.e("WorkoutIdle", "Could not save inactive workout", e) }
            finally { pending.finish() }
        }
    }
}
