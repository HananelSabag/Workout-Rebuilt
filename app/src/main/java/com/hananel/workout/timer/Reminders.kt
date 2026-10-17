package com.hananel.workout.timer

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hananel.workout.R
import com.hananel.workout.WorkoutApplication
import com.hananel.workout.data.clock
import com.hananel.workout.domain.*
import kotlinx.coroutines.*
import java.time.ZonedDateTime

object Reminders {
    private fun pending(context: Context, id: String) = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setData(Uri.parse("workout://reminder/$id")).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun next(reminder: Reminder, now: ZonedDateTime): ZonedDateTime = (0L..7L).map { offset ->
        now.toLocalDate().plusDays(offset).atTime(reminder.hour, reminder.minute).atZone(now.zone)
    }.first { it.isAfter(now) && dayIndex(it.toLocalDate()) in reminder.days }
    fun reconcile(context: Context, previous: List<Reminder>, prefs: Preferences) {
        val manager = context.getSystemService(AlarmManager::class.java)
        previous.forEach { manager.cancel(pending(context, it.id)) }
        prefs.reminders.filter { it.enabled && prefs.reminderEnabled }.forEach { reminder ->
            val at = next(reminder, ZonedDateTime.now()).toInstant().toEpochMilli()
            val intent = pending(context, reminder.id)
            try {
                if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
                else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } catch (_: SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent) }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val state = (context.applicationContext as WorkoutApplication).repository.current()
                val prefs = state.preferences
                val reminder = prefs.reminders.find { it.id == intent.getStringExtra("id") && it.enabled }
                if (reminder != null && prefs.reminderEnabled && Notifications.allowed(context)) {
                    val english = prefs.isEnglish()
                    val next = state.nextRoutine(context.clock().date)?.label(english)
                    val text = reminder.message.ifBlank { next?.let { if (english) "Up next: $it" else "הבא בתור: $it" } ?: if (english) "A little time for yourself" else "זמן קטן בשביל עצמך" }
                    context.getSystemService(NotificationManager::class.java).notify(reminder.id, 50,
                        NotificationCompat.Builder(context, Notifications.REMINDER_CHANNEL).setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle(if (english) "Time to train" else "זמן לאימון").setContentText(text)
                            .setContentIntent(Notifications.open(context)).setAutoCancel(true).build())
                }
                (context.applicationContext as WorkoutApplication).coordinator.execute(mayStartService = false, refreshReminders = true) { it }
            } catch (e: Exception) { android.util.Log.e("WorkoutReminder", "Reminder failed", e) }
            finally { pending.finish() }
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                (context.applicationContext as WorkoutApplication).coordinator.execute(mayStartService = false, refresh = true) { TrainingEngine.recover(TrainingEngine.upgradeAppearance(it), context.clock()) }
            } catch (e: Exception) { android.util.Log.e("WorkoutReminder", "Rescheduling failed", e) }
            finally { pending.finish() }
        }
    }
}
