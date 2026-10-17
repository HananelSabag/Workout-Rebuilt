package com.hananel.workout.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hananel.workout.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Calendar browsing never starts a session or changes the date of a logged set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HomeScreen(state: AppState, today: LocalDate, routineId: String, busy: Boolean, vm: WorkoutViewModel,
    onSelect: (String) -> Unit, onCreate: () -> Unit, onEdit: (String) -> Unit, onFinish: () -> Unit, onTimer: () -> Unit, onProgram: () -> Unit, onExerciseEdit: (String, String?) -> Unit) {
    val english = LocalEnglish.current
    var selectedDate by rememberSaveable(today.toString()) { mutableStateOf(today.toString()) }
    val sessions = remember(state.history, state.active, today) { state.weekSessions(today) }
    val flexible = state.preferences.mode == PlanMode.FLEXIBLE
    val suggested = state.nextRoutine(today)
    val selected = state.routines.find { it.id == routineId } ?: suggested
    var choosing by rememberSaveable { mutableStateOf(false) }
    val daySessions = sessions.filter { it.date == selectedDate }
    val planned = if (flexible) emptyList() else state.routines.filter { dayIndex(LocalDate.parse(selectedDate)) in it.days }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("השבוע שלך", "Your week"), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = muted())
                Text("${weekStart(today).format(DateTimeFormatter.ofPattern("dd.MM"))} – ${weekStart(today).plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM"))}", style = MaterialTheme.typography.labelMedium, color = muted())
            }
            WeekStrip(state, today, selectedDate, sessions) { selectedDate = it.toString() }
            val day = dayName(dayIndex(LocalDate.parse(selectedDate)))
            val completeLabel = tr("הושלם", "complete")
            val activeLabel = tr("בתהליך", "in progress")
            val partialLabel = tr("חלקי", "partial")
            val performedText = daySessions.joinToString(" · ") { session -> session.label(english) + " (" + when {
                session.complete -> completeLabel
                session.endedAt == null -> activeLabel
                else -> partialLabel
            } + ")" }
            val plannedText = planned.map { it.label(english) }.joinToString(" · ")
            Text(when {
                daySessions.isNotEmpty() -> "$day · " + performedText +
                    if (planned.any { r -> daySessions.none { it.routineId == r.id } }) tr(" | תוכנן: ", " | Planned: ") + plannedText else ""
                planned.isNotEmpty() -> "$day · " + tr("מתוכנן: ", "Planned: ") + plannedText
                selectedDate == today.toString() -> tr("היום · מתאמנים בקצב שלך", "Today · train at your own pace")
                else -> "$day · " + tr("לא נרשם אימון", "No workout recorded")
            }, style = MaterialTheme.typography.labelMedium, color = muted(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (selectedDate != today.toString()) Text(tr("צפייה ביומן · סט חדש יירשם היום", "Calendar view · new sets are recorded today"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (state.active == null && state.history.firstOrNull()?.let { it.autoClosed && it.date == today.toString() } == true)
                Text(tr("האימון הקודם נשמר אוטומטית אחרי חוסר פעילות · זמין בהיסטוריה", "Previous workout saved after inactivity · available in History"), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
        }
        if (selected == null) Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.routines.isEmpty()) {
                Heading(tr("איזה אימון בונים?", "What are we training?"), tr("הוסף אימון ואת התרגילים שמתאימים לך.", "Add a workout and the exercises you want."))
                PrimaryButton(tr("צור אימון ראשון", "Create first routine"), onCreate, icon = Icons.Default.Add)
            } else {
                Text(tr("מה מתאמנים היום?", "What are we training today?"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (flexible) tr("בחר אימון כדי לראות את התרגילים שלו.", "Choose a workout to see its exercises.")
                    else tr("היום אין אימון מתוכנן. אפשר לבחור אימון אחר.", "No workout is scheduled today. You can choose another."), color = muted())
                PrimaryButton(tr("בחר אימון", "Choose workout"), { choosing = true }, icon = Icons.Default.FitnessCenter)
            }
        } else key(selected.id) {
            WorkoutScreen(state, selected, today, busy, vm, onEdit = { onEdit(selected.id) }, onFinish = onFinish, onTimer = onTimer, onResume = { state.active?.let { onSelect(it.routineId) } }, onChoose = { choosing = true }, onExerciseEdit = { onExerciseEdit(selected.id, it) })
        }
    }
    if (choosing) ModalBottomSheet(onDismissRequest = { choosing = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Heading(tr("מה מתאמנים היום?", "What are we training?"))
            state.routines.forEach { routine ->
                OutlinedButton({ onSelect(routine.id); choosing = false }, Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Tag(routine.calendarLabel(english))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(routine.label(english), fontWeight = FontWeight.Bold)
                        Text(if (state.active?.routineId == routine.id) tr("אימון פעיל", "Active workout") else tr("${routine.exercises.size} תרגילים", "${routine.exercises.size} exercises"), style = MaterialTheme.typography.bodySmall)
                    }
                    if (routine.id == selected?.id) Icon(Icons.Default.Check, null)
                }
            }
            TextButton({ choosing = false; onProgram() }) { Text(tr("ניהול התוכנית", "Manage program")) }
        }
    }
}

@Composable private fun WeekStrip(state: AppState, today: LocalDate, selectedDate: String, sessions: List<Session>, onSelect: (LocalDate) -> Unit) {
    val english = LocalEnglish.current
    val largeFont = LocalDensity.current.fontScale > 1.2f
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = if (LocalGlass.current) .78f else 1f),
        border = BorderStroke(1.dp, if (LocalGlass.current && !LocalDark.current) Color.White.copy(alpha = .9f) else muted().copy(alpha = .12f))) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(7) { index ->
                val date = weekStart(today).plusDays(index.toLong())
                val logs = sessions.filter { it.date == date.toString() }
                val planned = if (state.preferences.mode == PlanMode.SCHEDULED) state.routines.filter { index in it.days && logs.none { s -> s.routineId == it.id } } else emptyList()
                val names = logs.map { it.label(english) } + planned.map { it.label(english) }
                val completeText = tr(" הושלם", " complete")
                val partialText = tr(" חלקי", " partial")
                val activeText = tr(" בתהליך", " in progress")
                val plannedPrefix = tr("מתוכנן ", "Planned ")
                val description = dayName(index) + ", " + if (names.isEmpty()) tr("ללא אימון", "No workout") else
                    logs.joinToString { it.label(english) + if (it.complete) completeText else if (it.endedAt == null) activeText else partialText } +
                        (if (logs.isNotEmpty() && planned.isNotEmpty()) ", " else "") + planned.joinToString { plannedPrefix + it.label(english) }
                val chosen = selectedDate == date.toString()
                val foreground = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Surface(onClick = { onSelect(date) }, modifier = Modifier.weight(1f).semantics { contentDescription = description; this.selected = chosen },
                    shape = RoundedCornerShape(13.dp), color = if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent,
                    border = if (date == today && !chosen) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .4f)) else null) {
                    Column(Modifier.heightIn(min = 91.dp).padding(vertical = 6.dp, horizontal = 1.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(dayName(index, english || largeFont), style = MaterialTheme.typography.labelSmall, color = if (chosen) foreground.copy(alpha = .85f) else muted(), maxLines = 1)
                        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = foreground)
                        val labels = logs.map { session ->
                            val label = session.calendarMarker?.ifBlank { session.label(english).substringBefore(' ').take(6) }
                                ?: state.routines.find { it.id == session.routineId }?.calendarLabel(english) ?: session.label(english).substringBefore(' ').take(6)
                            Triple(label, if (session.complete) Icons.Default.CheckCircle else Icons.Default.Timelapse, false)
                        } + planned.map { Triple(it.calendarLabel(english), Icons.Default.Schedule, true) }
                        if (labels.isEmpty()) Text("—", style = MaterialTheme.typography.labelSmall, color = foreground.copy(alpha = .45f))
                        labels.take(2).forEach { (label, icon, isPlanned) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(icon, null, Modifier.size(10.dp), tint = if (chosen) foreground else if (isPlanned) muted() else MaterialTheme.colorScheme.tertiary)
                                Text(label, color = foreground, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            }
                        }
                        if (labels.size > 2) Text("+${labels.size - 2}", color = foreground, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
