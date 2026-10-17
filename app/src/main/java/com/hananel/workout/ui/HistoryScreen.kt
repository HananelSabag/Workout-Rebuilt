package com.hananel.workout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hananel.workout.domain.*
import com.hananel.workout.timer.formatTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable fun HistoryScreen(state: AppState, today: LocalDate, vm: WorkoutViewModel) {
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var allTime by rememberSaveable { mutableStateOf(false) }
    var volumeHelp by rememberSaveable { mutableStateOf(false) }
    val date = today.plusWeeks(weekOffset.toLong())
    val sessions = (if (allTime) state.history else state.weekSessions(date).filter { it.endedAt != null }).sortedByDescending { it.startedAt }
    val start = weekStart(date)
    if (volumeHelp) AlertDialog(onDismissRequest = { volumeHelp = false }, title = { Text(tr("נפח אימון", "Training volume")) }, text = { Text(tr("נפח = משקל × חזרות בכל סט. תרגילי זמן אינם נספרים בנפח.", "Volume = weight × reps per set. Timed exercises are excluded.")) }, confirmButton = { TextButton({ volumeHelp = false }) { Text(tr("הבנתי", "Got it")) } })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading(tr("הדרך שעשית", "Your training journey"), tr("כל סט, כל אימון. הכול נשמר כאן.", "Every set, every session. All saved here.")) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!allTime, { allTime = false }, label = { Text(tr("לפי שבוע", "By week")) })
            FilterChip(allTime, { allTime = true }, label = { Text(tr("כל ההיסטוריה", "All history")) })
        } }
        if (!allTime) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ weekOffset-- }) { Icon(if (LocalEnglish.current) Icons.Default.ChevronLeft else Icons.Default.ChevronRight, tr("שבוע קודם", "Previous week")) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (weekOffset == 0) tr("השבוע", "This week") else "${start.format(DateTimeFormatter.ofPattern("dd.MM"))} — ${start.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}", fontWeight = FontWeight.Bold)
                    if (weekOffset != 0) TextButton({ weekOffset = 0 }) { Text(tr("חזרה לשבוע הנוכחי", "Back to this week")) }
                }
                IconButton({ weekOffset++ }, enabled = weekOffset < 0) { Icon(if (LocalEnglish.current) Icons.Default.ChevronRight else Icons.Default.ChevronLeft, tr("שבוע הבא", "Next week")) }
            }
        }
        if (sessions.isNotEmpty()) item { Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                HistoryStat(sessions.size.toString(), tr("אימונים", "sessions"))
                HistoryStat(sessions.sumOf { it.totalSets }.toString(), tr("סטים", "sets"))
                HistoryStat(number(sessions.sumOf { it.volume }), tr("נפח · ק״ג", "volume · kg"))
            }
            TextButton({ volumeHelp = true }) { Text(tr("איך מחושב הנפח?", "How is volume calculated?")) }
            Text(tr("סך הכול ${sessions.sumOf { it.durationMs(it.endedAt ?: it.lastActionAt) } / 60_000} דקות אימון", "${sessions.sumOf { it.durationMs(it.endedAt ?: it.lastActionAt) } / 60_000} total training minutes"), color = muted(), style = MaterialTheme.typography.bodySmall)
        } }
        if (sessions.isEmpty()) item { Panel { Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary); Heading(if (allTime) tr("עוד אין אימונים שמורים", "No saved workouts yet") else tr("עוד אין אימונים שמורים השבוע", "No saved workouts this week"), if (allTime) tr("סיים אימון והביצוע יופיע כאן.", "Finish a workout to see it here.") else tr("סיים אימון והביצוע יופיע כאן. שבועות קודמים זמינים בחיצים למעלה.", "Finish a workout to see it here. Use the arrows for earlier weeks.")) } }
        items(sessions, key = { it.id }) { session -> Panel(Modifier.fillMaxWidth().clickable { expanded = if (expanded == session.id) "" else session.id }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(session.label(LocalEnglish.current), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(dayName(dayIndex(LocalDate.parse(session.date))) + " · " + LocalDate.parse(session.date).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), style = MaterialTheme.typography.bodySmall, color = muted())
                    val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault())
                    Text(timeFormat.format(java.time.Instant.ofEpochMilli(session.startedAt)) + " – " + timeFormat.format(java.time.Instant.ofEpochMilli(session.endedAt ?: session.lastActionAt)), style = MaterialTheme.typography.bodySmall, color = muted())
                }
                Tag(if (session.complete) tr("הושלם", "Complete") else tr("חלקי", "Partial"), if (session.complete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
            }
            Text(tr("${session.totalSets}/${session.plannedSets} סטים · זמן אימון ", "${session.totalSets}/${session.plannedSets} sets · Workout time ") + formatTime((session.durationMs(session.endedAt!!) / 1000).toInt()), color = muted())
            LinearProgressIndicator(progress = { session.progress }, Modifier.fillMaxWidth())
            if (expanded == session.id) {
                session.exercises.filter { it.sets.isNotEmpty() }.forEach { log ->
                    HorizontalDivider(color = muted().copy(alpha = .12f))
                    Text(log.exercise.label(LocalEnglish.current), fontWeight = FontWeight.SemiBold)
                    log.sets.forEachIndexed { index, set ->
                        Text(tr("סט ${index + 1} · ", "Set ${index + 1} · ") + "${set.value} ${log.exercise.measure.unit()}" + (if (log.exercise.weighted) tr(" · ${number(set.weight)} ק״ג", " · ${number(set.weight)} kg") else ""), style = MaterialTheme.typography.bodySmall, color = muted())
                    }
                }
                if (session.autoClosed) Text(tr("נשמר אוטומטית לאחר חוסר פעילות. שעת הסיום היא הפעולה האחרונה.", "Saved automatically after inactivity. The end time is the last action."), color = muted(), style = MaterialTheme.typography.labelSmall)
                TextButton({ deleteId = session.id }) { Text(tr("מחק רשומה", "Delete record"), color = MaterialTheme.colorScheme.error) }
            } else Text(tr("לחץ לפירוט הסטים", "Tap for set details"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        } }
    }
    if (deleteId != null) AlertDialog(onDismissRequest = { deleteId = null }, title = { Text(tr("למחוק את רשומת האימון?", "Delete this workout record?")) },
        text = { Text(tr("הביצוע יוסר גם מהיומן ומהסיכום השבועי. התוכנית וההתקדמות בסבב יישמרו.", "This session will also be removed from the calendar and weekly summary. Your program and round progress are kept.")) },
        confirmButton = { TextButton({ val id = deleteId; vm.act { it.copy(history = it.history.filter { s -> s.id != id }) }; deleteId = null }) { Text(tr("מחק", "Delete")) } },
        dismissButton = { TextButton({ deleteId = null }) { Text(tr("ביטול", "Cancel")) } })
}

@Composable private fun HistoryStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, color = muted(), style = MaterialTheme.typography.labelSmall) }
}
