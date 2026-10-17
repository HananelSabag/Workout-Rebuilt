package com.hananel.workout.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hananel.workout.domain.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProgramScreen(state: AppState, busy: Boolean, vm: WorkoutViewModel, onEdit: (String) -> Unit, onCreate: () -> Unit, onTrain: (String) -> Unit) {
    var styleSheet by rememberSaveable { mutableStateOf(false) }
    var reordering by rememberSaveable { mutableStateOf(false) }
    val progress = state.cycleProgress()
    var rename by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(state.preferences.programName) { mutableStateOf(state.preferences.programName) }
    val english = LocalEnglish.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.preferences.programName.ifBlank { tr("התוכנית שלי", "My program") }, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton({ rename = true }, enabled = !busy) { Icon(Icons.Default.Edit, tr("שם התוכנית", "Program name")) }
        } }
        item { Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (state.preferences.mode == PlanMode.FLEXIBLE) tr("לפי רצף האימונים", "Workout sequence") else tr("לפי ימים קבועים", "Fixed weekdays"), fontWeight = FontWeight.Bold)
                    Text(if (state.preferences.mode == PlanMode.FLEXIBLE) tr("סבב ${progress.round} · ${progress.completedIds.size}/${state.trainingIds().size} הושלמו", "Round ${progress.round} · ${progress.completedIds.size}/${state.trainingIds().size} complete") else tr("כל תאריך הוא אימון נפרד", "Each date is a separate workout"), style = MaterialTheme.typography.bodySmall, color = muted())
                }
                TextButton({ styleSheet = true }) { Text(tr("שנה", "Change")) }
            }
        } }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("האימונים בתוכנית", "Workouts in your program"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton({ reordering = !reordering }) { Text(if (reordering) tr("סיימתי לסדר", "Done reordering") else tr("שנה סדר", "Reorder")) }
        } }
        items(state.routines, key = { it.id }) { routine ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tag(routine.calendarLabel(english))
                    Column(Modifier.weight(1f)) {
                        Text(routine.label(english), fontWeight = FontWeight.Bold)
                        Text(tr("${routine.exercises.size} תרגילים", "${routine.exercises.size} exercises"), color = muted(), style = MaterialTheme.typography.bodySmall)
                        if (state.preferences.mode == PlanMode.SCHEDULED) Text(
                            if (routine.days.isEmpty()) tr("לא נקבעו ימים", "No days assigned") else routine.days.sorted().map { dayName(it, true) }.joinToString(),
                            color = muted(), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton({ onEdit(routine.id) }) { Icon(Icons.Default.Edit, tr("ערוך ", "Edit ") + routine.label(english)) }
                }
                TextButton({ onTrain(routine.id) }) { Text(if (state.active?.routineId == routine.id) tr("חזרה לאימון", "Resume workout") else tr("פתח את האימון", "Open workout")) }
                if (reordering) Row(verticalAlignment = Alignment.CenterVertically) {
                    val index = state.routines.indexOf(routine)
                    Text(tr("מקום ${index + 1} בתוכנית", "Position ${index + 1}"), Modifier.weight(1f), color = muted(), style = MaterialTheme.typography.labelMedium)
                    fun move(delta: Int) = vm.act { TrainingEngine.moveRoutine(it, routine.id, delta) }
                    IconButton({ move(-1) }, enabled = !busy && index > 0) { Icon(Icons.Default.ArrowUpward, tr("הקדם ", "Move earlier ") + routine.label(english)) }
                    IconButton({ move(1) }, enabled = !busy && index < state.routines.lastIndex) { Icon(Icons.Default.ArrowDownward, tr("העבר להמשך ", "Move later ") + routine.label(english)) }
                }
            }
        }
        item { PrimaryButton(tr("הוסף אימון לתוכנית", "Add workout to program"), onCreate, enabled = !busy, icon = Icons.Default.Add) }
    }
    if (styleSheet) ModalBottomSheet(onDismissRequest = { styleSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PlanStyle(state.preferences, enabled = !busy, onChange = { change -> vm.act { it.copy(preferences = change(it.preferences)) } })
            PrimaryButton(tr("סיימתי", "Done"), { styleSheet = false }, icon = Icons.Default.Check)
        }
    }
    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text(tr("שם התוכנית", "Program name")) },
        text = { OutlinedTextField(name, { name = it.take(80) }, singleLine = true, label = { Text(tr("שם התוכנית שלך", "Your program name")) }, placeholder = { Text(tr("למשל תוכנית ABC שלי", "e.g. My ABC program")) }) },
        confirmButton = { TextButton({ vm.act(after = { rename = false }) { it.copy(preferences = it.preferences.copy(programName = name.trim())) } }, enabled = !busy) { Text(tr("שמור", "Save")) } },
        dismissButton = { TextButton({ rename = false }) { Text(tr("ביטול", "Cancel")) } })
}

@Composable fun PlanStyle(prefs: Preferences, enabled: Boolean, onChange: ((Preferences) -> Preferences) -> Unit) {
    Text(tr("איך התוכנית עובדת?", "How does your program work?"), fontWeight = FontWeight.Bold)
    listOf(PlanMode.FLEXIBLE to tr("גמיש · מגיע כשנוח לי", "Flexible · train when it suits me"), PlanMode.SCHEDULED to tr("קבוע · לפי ימים בשבוע", "Scheduled · fixed weekdays")).forEach { (mode, label) ->
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(prefs.mode == mode, enabled = enabled, role = Role.RadioButton, onClick = { onChange { it.copy(mode = mode) } }), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(prefs.mode == mode, null, enabled = enabled); Spacer(Modifier.width(10.dp)); Text(label)
        }
    }
    if (prefs.mode == PlanMode.FLEXIBLE) {
        HorizontalDivider(color = muted().copy(alpha = .12f))
        FlexibleOrderPicker(prefs.flexibleOrder, enabled) { order -> onChange { it.copy(flexibleOrder = order) } }
        Text(tr("ממשיכים מאיפה שעצרת, גם בשבוע חדש. לאחר השלמת כל האימונים מתחיל סבב חדש באימון הבא.", "Continue where you left off, even in a new week. After completing every workout, your next session starts a new round."), color = muted(), style = MaterialTheme.typography.bodySmall)
    } else Text(tr("משייכים ימים בעריכת כל אימון. היומן מציג בנפרד מה תוכנן ומה בוצע בפועל.", "Assign days when editing each workout. The calendar distinguishes plans from actual training."), color = muted(), style = MaterialTheme.typography.bodySmall)
}

@Composable fun FlexibleOrderPicker(order: FlexibleOrder, enabled: Boolean = true, onChange: (FlexibleOrder) -> Unit) {
    Text(tr("בחירת האימון בכל ביקור", "Choosing a workout each visit"), style = MaterialTheme.typography.labelLarge)
    listOf(FlexibleOrder.ORDERED to tr("סדר קבוע · מציעים את הבא", "In order · suggest the next workout"), FlexibleOrder.FREE to tr("בחירה חופשית · בוחר בכל פעם", "Free choice · pick each time")).forEach { (value, label) ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(order == value, enabled = enabled, role = Role.RadioButton, onClick = { onChange(value) }), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(order == value, null, enabled = enabled); Spacer(Modifier.width(10.dp)); Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
