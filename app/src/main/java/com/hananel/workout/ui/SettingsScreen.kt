package com.hananel.workout.ui

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hananel.workout.domain.*
import com.hananel.workout.timer.Notifications
import java.time.LocalDate
import java.util.UUID

@Composable fun settingsTitle(section: String?): String = when (section) {
    "appearance" -> tr("מראה ושפה", "Appearance & language")
    "training" -> tr("אימון והתראות", "Training & notifications")
    "reminders" -> tr("תזכורות אימון", "Workout reminders")
    "backup" -> tr("גיבוי ונתונים", "Backup & data")
    else -> tr("הגדרות", "Settings")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsScreen(state: AppState, vm: WorkoutViewModel, section: String?, onSection: (String) -> Unit) {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var allowed by remember { mutableStateOf(Notifications.allowed(context)) }
    var exact by remember { mutableStateOf(Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) }
    var reminderId by rememberSaveable { mutableStateOf<String?>(null) }
    var explanation by rememberSaveable { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = Notifications.allowed(context) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::export) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::readImport) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            allowed = Notifications.allowed(context)
            exact = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun preferences(change: (Preferences) -> Preferences) = vm.act { it.copy(preferences = change(it.preferences)) }
    key(section) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (section) {
                null -> {
                    item { Panel {
                        SettingsLink(settingsTitle("appearance"), when (state.preferences.theme) { "dark" -> tr("כהה", "Dark"); "light" -> tr("בהיר", "Light"); else -> tr("לפי המכשיר", "Device theme") } + " · " + when (state.preferences.language) { "he" -> "עברית"; "en" -> "English"; else -> tr("שפת המכשיר", "Device language") }, Icons.Default.Palette) { onSection("appearance") }
                        HorizontalDivider()
                        SettingsLink(settingsTitle("training"), if (state.preferences.autoCloseIdle) tr("סיום אוטומטי אחרי 20 דקות", "Auto-finish after 20 idle minutes") else tr("סיום ידני", "Finish manually"), Icons.Default.Timer) { onSection("training") }
                        HorizontalDivider()
                        SettingsLink(settingsTitle("reminders"), state.preferences.reminders.count { it.enabled }.let { if (it == 0) tr("אין תזכורות פעילות", "No active reminders") else tr("$it תזכורות פעילות", "$it active reminders") }, Icons.Default.Event) { onSection("reminders") }
                        HorizontalDivider()
                        SettingsLink(settingsTitle("backup"), tr("התוכנית, המשקלים וההיסטוריה", "Your plan, weights and history"), Icons.Default.Backup) { onSection("backup") }
                    } }
                    item { TextButton({ explanation = "help" }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.HelpOutline, null); Spacer(Modifier.width(8.dp)); Text(tr("איך זה עובד?", "How it works")) } }
                    item { Text("Workout $version", Modifier.fillMaxWidth(), color = muted(), style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
                "appearance" -> item { Panel {
                    Text(tr("מצב תצוגה", "Appearance"), fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("dark" to tr("כהה", "Dark"), "light" to tr("בהיר", "Light"), "system" to tr("מערכת", "System")).forEach { (value, label) -> FilterChip(state.preferences.theme == value, { preferences { it.copy(theme = value) } }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp)) }
                    }
                    HorizontalDivider()
                    Text(tr("שפה", "Language"), fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("he" to "עברית", "en" to "English", "system" to tr("שפת המכשיר", "Device language")).forEach { (value, label) -> FilterChip(state.preferences.language == value, { preferences { it.copy(language = value) } }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp)) }
                    }
                } }
                "training" -> {
                    item { Panel {
                        val autoCloseLabel = tr("סגירה אחרי 20 דקות ללא פעילות", "Close after 20 idle minutes")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(autoCloseLabel, Modifier.weight(1f))
                            Switch(state.preferences.autoCloseIdle, { enabled -> preferences { it.copy(autoCloseIdle = enabled) } }, Modifier.semantics { contentDescription = autoCloseLabel })
                        }
                        if (!state.preferences.autoCloseIdle) Text(tr("האימון נשאר פתוח עד שתסיים ותשמור.", "Your workout stays open until you finish and save."), color = muted(), style = MaterialTheme.typography.bodySmall)
                        TextButton({ explanation = "idle" }) { Text(tr("איך מחושב הזמן?", "How is idle time counted?")) }
                    } }
                    item { Panel {
                        SettingsLink(tr("התראות מנוחה", "Rest notifications"), if (allowed) tr("פעילות · צליל ופעולות ברקע", "Enabled · sound and background actions") else tr("כבויות · לחץ להפעלה", "Disabled · tap to enable"), Icons.Default.NotificationsNone) {
                            if (Build.VERSION.SDK_INT >= 33 && !allowed && !state.preferences.notificationsAsked) {
                                preferences { it.copy(notificationsAsked = true) }; permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                        HorizontalDivider()
                        SettingsLink(tr("דיוק ההתראות ברקע", "Background alert accuracy"), if (exact) tr("תזמון מדויק מורשה", "Exact alarms allowed") else tr("הרשאה אופציונלית", "Optional permission"), Icons.Default.Alarm) { explanation = "precision" }
                    } }
                }
                "reminders" -> {
                    item { PrimaryButton(tr("הוסף תזכורת", "Add reminder"), { reminderId = "new" }, icon = Icons.Default.Add) }
                    if (state.preferences.reminders.isEmpty()) item { Text(tr("בחר ימים ושעה שמתאימים לך.", "Choose days and a time that suit you."), color = muted()) }
                    items(state.preferences.reminders, key = { it.id }) { reminder -> Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { reminderId = reminder.id }.padding(vertical = 8.dp)) {
                                Text("%02d:%02d".format(reminder.hour, reminder.minute), style = MaterialTheme.typography.titleLarge)
                                Text(reminder.days.sorted().map { dayName(it, true) }.joinToString(" · "), color = muted())
                            }
                            val reminderLabel = tr("תזכורת בשעה ", "Reminder at ") + "%02d:%02d".format(reminder.hour, reminder.minute)
                            Switch(reminder.enabled, { enabled -> preferences { p -> p.copy(reminders = p.reminders.map { if (it.id == reminder.id) it.copy(enabled = enabled) else it }) } }, Modifier.semantics { contentDescription = reminderLabel })
                            IconButton({ reminderId = reminder.id }) { Icon(Icons.Default.Edit, tr("ערוך תזכורת", "Edit reminder")) }
                        }
                        if (reminder.message.isNotBlank()) Text(reminder.message, color = muted())
                    } }
                }
                "backup" -> item { Panel {
                    Text(tr("העותק כולל את התוכנית, המשקלים וההיסטוריה שלך.", "Your backup includes your plan, weights and workout history."), color = muted(), style = MaterialTheme.typography.bodySmall)
                    SettingsLink(tr("ייצא גיבוי", "Export backup"), tr("שמירת עותק בקובץ", "Save a copy to a file"), Icons.Default.FileDownload) { export.launch("Workout-${LocalDate.now()}.json") }
                    HorizontalDivider()
                    SettingsLink(tr("שחזור מגיבוי", "Restore backup"), tr("בחירת קובץ ובדיקה לפני אישור", "Choose a file and review before restoring"), Icons.Default.FileUpload) { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                } }
            }
        }
    }
    if (explanation != null) AlertDialog(onDismissRequest = { explanation = null }, title = { Text(when (explanation) { "idle" -> tr("סיום אוטומטי", "Automatic finish"); "help" -> tr("אימון בקצב שלך", "Train at your own pace"); else -> tr("דיוק ההתראות ברקע", "Background alert accuracy") }) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) { Text(when (explanation) {
            "idle" -> tr("נשמר אחרי 20 דקות ללא רישום, עריכה או פעולה בטיימר. מנוחה רצה מקבלת את מלוא זמנה; מנוחה מושהית אינה משאירה אימון פתוח ללא הגבלה. שעת הסיום בהיסטוריה היא הפעולה האחרונה.", "Saves after 20 minutes without logging, editing or timer actions. Running rest gets its full duration; paused rest does not keep a workout open indefinitely. History uses the last action as the end time.")
            "help" -> tr("1. בחר אימון. התוכנית שלך נמצאת ליד ההיסטוריה.\n\n2. פתח תרגיל, עדכן משקל וחזרות, ורשום כל סט שביצעת. המשקל נשמר לפעם הבאה.\n\n3. הטיימר נפתח למנוחה ויכול להמשיך ברקע. בסוף בחר סיים ושמור, גם אם האימון חלקי.\n\n4. שבוע חדש מציג סיכום חדש. התוכנית, המשקלים וההיסטוריה נשמרים.", "1. Choose a workout. Your program is next to History.\n\n2. Open an exercise, set your weight and reps, and log each completed set. Your weight is remembered.\n\n3. Rest opens automatically and can continue in the background. Finish and save even a partial workout.\n\n4. A new week starts a new summary. Your plan, weights and history stay saved.")
            else -> tr("הרשאה אופציונלית לגיבוי מדויק של הטיימר והתזכורות במצב חיסכון. בלעדיה הטיימר ממשיך בשירות רקע, אך תזכורות עשויות להתעכב.", "An optional permission for precise timer and reminder backup during power saving. Without it, the timer uses a background service, but reminders may be delayed.")
        }) }
    }, confirmButton = { TextButton({
        if (explanation == "precision" && Build.VERSION.SDK_INT >= 31 && !exact) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        explanation = null
    }) { Text(if (explanation == "precision" && !exact) tr("אפשר תזמון מדויק", "Allow exact alarms") else tr("הבנתי", "Got it")) } }, dismissButton = { if (explanation == "precision" && !exact) TextButton({ explanation = null }) { Text(tr("בהמשך", "Later")) } })
    if (reminderId != null) ReminderDialog(state.preferences.reminders.find { it.id == reminderId }, { reminderId = null }, onDelete = {
        val id = reminderId; preferences { it.copy(reminders = it.reminders.filter { r -> r.id != id }) }; reminderId = null
    }) { updated ->
        val requestPermission = Build.VERSION.SDK_INT >= 33 && !allowed && !state.preferences.notificationsAsked
        vm.act(after = { reminderId = null; if (requestPermission) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { current -> current.copy(preferences = current.preferences.let { p -> p.copy(
            reminders = if (p.reminders.any { it.id == updated.id }) p.reminders.map { if (it.id == updated.id) updated else it } else p.reminders + updated,
            notificationsAsked = p.notificationsAsked || requestPermission
        ) }) }
    }
}

@Composable private fun SettingsLink(title: String, summary: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, fontWeight = FontWeight.Medium); Text(summary, style = MaterialTheme.typography.bodySmall, color = muted()) }
        Icon(if (LocalEnglish.current) Icons.Default.ChevronRight else Icons.Default.ChevronLeft, null, tint = muted(), modifier = Modifier.size(18.dp))
    }
}

@Composable private fun ReminderDialog(reminder: Reminder?, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Reminder) -> Unit) {
    var hour by rememberSaveable { mutableIntStateOf(reminder?.hour ?: 18) }
    var minute by rememberSaveable { mutableIntStateOf(reminder?.minute ?: 0) }
    var days by rememberSaveable { mutableStateOf(reminder?.days?.sorted() ?: listOf(0, 2, 4)) }
    var message by rememberSaveable { mutableStateOf(reminder?.message ?: "") }
    var showMessage by rememberSaveable { mutableStateOf(message.isNotBlank()) }
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("תזכורת לאימון", "Workout reminder")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedButton({ TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, android.text.format.DateFormat.is24HourFormat(context)).show() }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(12.dp)); Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.titleLarge)
            }
            DaySelector(days.toSet()) { days = it.sorted() }
            if (days.isEmpty()) Text(tr("בחר לפחות יום אחד", "Choose at least one day"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (showMessage) OutlinedTextField(message, { message = it.take(200) }, label = { Text(tr("הודעה אישית (אופציונלי)", "Message (optional)")) })
            else TextButton({ showMessage = true }) { Text(tr("הוסף הודעה אישית", "Add a personal message")) }
            if (reminder != null) TextButton(onDelete) { Text(tr("מחק תזכורת", "Delete reminder"), color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton({ onSave(Reminder(reminder?.id ?: UUID.randomUUID().toString(), hour, minute, days.toSet(), message, reminder?.enabled ?: true)) }, enabled = days.isNotEmpty()) { Text(tr("שמור", "Save")) } }, dismissButton = { TextButton(onDismiss) { Text(tr("ביטול", "Cancel")) } })
}
