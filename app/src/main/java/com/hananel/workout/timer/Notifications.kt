package com.hananel.workout.timer

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hananel.workout.MainActivity
import com.hananel.workout.R
import com.hananel.workout.data.clock
import com.hananel.workout.domain.RestTimer

object Notifications {
    const val RUNNING = 40
    const val COMPLETE = 41
    const val TIMER_CHANNEL = "rest_live_v3"
    const val ALERT_CHANNEL = "rest_alert_v3"
    const val REMINDER_CHANNEL = "training_reminders_v3"
    fun channels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(
            NotificationChannel(TIMER_CHANNEL, "טיימר מנוחה · Rest timer", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) },
            NotificationChannel(ALERT_CHANNEL, "סיום מנוחה · Rest finished", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) },
            NotificationChannel(REMINDER_CHANNEL, "תזכורות אימון · Reminders", NotificationManager.IMPORTANCE_DEFAULT)
        ))
    }
    fun allowed(context: Context) = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    fun open(context: Context, timer: Boolean = false) = PendingIntent.getActivity(context, if (timer) 2 else 1,
        Intent(context, MainActivity::class.java).putExtra("openTimer", timer).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun action(context: Context, action: String, timerId: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, TimerReceiver::class.java).setAction(action).setData(android.net.Uri.parse("workout://timer/$timerId/$action")).putExtra("timerId", timerId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun live(context: Context, timer: RestTimer?, english: Boolean = false): Notification {
        val seconds = timer?.seconds(context.clock()) ?: 0
        val title = if (english) "Rest time" else "זמן מנוחה"
        return NotificationCompat.Builder(context, TIMER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("$title · ${formatTime(seconds)}")
            .setContentText(timer?.let { if (english) it.titleEn else it.title } ?: if (english) "Starting timer" else "מפעיל טיימר")
            .setContentIntent(open(context, true)).setOnlyAlertOnce(true).setOngoing(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply {
                if (timer != null) {
                    setProgress((timer.durationMs / 1000).toInt(), seconds, false)
                    addAction(0, if (timer.pausedMs != null) { if (english) "Resume" else "המשך" } else { if (english) "Pause" else "השהה" }, action(context, "pause", timer.id))
                    addAction(0, if (english) "Skip rest" else "דלג מנוחה", action(context, "skip", timer.id))
                }
            }.build()
    }
    fun complete(context: Context, timer: RestTimer, english: Boolean) {
        if (!allowed(context)) return
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (english) "Rest is over. Ready for the next set?" else "המנוחה הסתיימה. מוכנים לסט הבא?")
            .setContentText(if (english) timer.titleEn else timer.title)
            .setContentIntent(open(context, true)).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0, if (english) "Set done" else "סיימתי סט", action(context, "next", timer.id))
            .addAction(0, if (english) "Dismiss" else "סגור", action(context, "skip", timer.id)).build()
        context.getSystemService(NotificationManager::class.java).notify(COMPLETE, notification)
    }
    fun clear(context: Context) { context.getSystemService(NotificationManager::class.java).cancel(COMPLETE) }
}
fun formatTime(seconds: Int): String = "%02d:%02d".format(java.util.Locale.US, seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)
