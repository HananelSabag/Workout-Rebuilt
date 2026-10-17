package com.hananel.workout.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hananel.workout.data.Catalog
import com.hananel.workout.data.clock
import com.hananel.workout.domain.*
import java.util.UUID

@Composable fun RoutineEditor(state: AppState, routineId: String, busy: Boolean, vm: WorkoutViewModel, onCreated: (String) -> Unit, onCatalog: () -> Unit, onDeleted: () -> Unit, onDone: () -> Unit, onExerciseEdit: (String?) -> Unit) {
    val routine = state.routines.find { it.id == routineId }
    val english = LocalEnglish.current
    var nameDialog by rememberSaveable(routineId) { mutableStateOf(routine == null) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    val locked = state.active?.routineId == routineId
    val saved = tr("התוכנית נשמרה", "Plan saved")
    fun save(updated: Routine) { vm.act(saved) { TrainingEngine.saveRoutine(it, updated) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading(tr("בונים את האימון", "Build your workout"), tr("תרגילים, סדר וימי אימון — הכול במקום אחד.", "Exercises, order and training days in one place.")) }
        if (routine != null) {
            item { Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(routine.label(LocalEnglish.current), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton({ nameDialog = true }, enabled = !busy && !locked) { Icon(Icons.Default.Edit, tr("שם וימי אימון", "Name and training days")) }
                }
                Text(if (state.preferences.mode == PlanMode.FLEXIBLE) tr("בתוכנית גמישה אפשר לבחור באימון בכל יום.", "Flexible mode lets you do this workout on any day.") else tr("ימים קבועים: ", "Scheduled days: ") + routine.days.sorted().map { dayName(it, true) }.joinToString(), color = muted())
                if (locked) Text(tr("האימון פעיל. סיים אותו לפני עריכת התוכנית.", "Finish the active workout before editing its plan."), color = MaterialTheme.colorScheme.error)
            } }
            items(routine.exercises, key = { it.id }) { exercise ->
                val index = routine.exercises.indexOf(exercise)
                Panel {
                    Text(exercise.label(LocalEnglish.current), fontWeight = FontWeight.Bold)
                    Text("${exercise.sets} × ${exercise.targetLabel()} ${exercise.measure.unit()} · ${exercise.restSeconds}s", color = muted())
                    Row {
                        IconButton({ val list = routine.exercises.toMutableList(); java.util.Collections.swap(list, index, index - 1); save(routine.copy(exercises = list)) }, enabled = index > 0 && !busy && !locked) { Icon(Icons.Default.ArrowUpward, tr("העבר למעלה", "Move up")) }
                        IconButton({ val list = routine.exercises.toMutableList(); java.util.Collections.swap(list, index, index + 1); save(routine.copy(exercises = list)) }, enabled = index < routine.exercises.lastIndex && !busy && !locked) { Icon(Icons.Default.ArrowDownward, tr("העבר למטה", "Move down")) }
                        Spacer(Modifier.weight(1f))
                        IconButton({ onExerciseEdit(exercise.id) }, enabled = !busy && !locked) { Icon(Icons.Default.Edit, tr("ערוך תרגיל", "Edit exercise")) }
                        IconButton({ editId = "delete:${exercise.id}" }, enabled = !busy && !locked) { Icon(Icons.Default.DeleteOutline, tr("מחק תרגיל", "Delete exercise")) }
                    }
                }
            }
            item { PrimaryButton(tr("הוסף מהמאגר", "Add from library"), onCatalog, enabled = !busy && !locked, icon = Icons.Default.Add) }
            item { OutlinedButton({ onExerciseEdit(null) }, Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = !busy && !locked) { Text(tr("תרגיל אישי", "Custom exercise")) } }
            if (routine.exercises.isNotEmpty()) item { PrimaryButton(tr("מוכן · מעבר לאימון", "Done · go to workout"), onDone, enabled = !busy, icon = Icons.Default.Check) }
            item { TextButton({ deleteDialog = true }, enabled = !busy && !locked) { Text(tr("מחק אימון מהתוכנית", "Delete routine"), color = MaterialTheme.colorScheme.error) } }
        } else item { PrimaryButton(tr("שם לאימון החדש", "Name your new routine"), { nameDialog = true }) }
    }
    if (nameDialog) RoutineDialog(routine, busy, scheduled = state.preferences.mode == PlanMode.SCHEDULED, onDismiss = { nameDialog = false }) { name, days, shortLabel ->
        val id = routine?.id ?: UUID.randomUUID().toString()
        val updated = if (routine != null && name == routine.label(english)) routine.copy(days = days, shortLabel = shortLabel)
            else Routine(id, name, routine?.exercises ?: emptyList(), days, shortLabel = shortLabel)
        vm.act(saved, after = { nameDialog = false; onCreated(id) }) { TrainingEngine.saveRoutine(it, updated) }
    }
    if (editId?.startsWith("delete:") == true && routine != null) ConfirmDelete(tr("למחוק את התרגיל מהתוכנית?", "Delete exercise from plan?"), { editId = null }) {
        save(routine.copy(exercises = routine.exercises.filter { it.id != editId!!.removePrefix("delete:") })); editId = null
    }
    if (deleteDialog && routine != null) ConfirmDelete(tr("למחוק את ${routine.label(LocalEnglish.current)}? ההיסטוריה תישמר.", "Delete ${routine.label(LocalEnglish.current)}? History is kept."), { deleteDialog = false }) {
        vm.act(after = { deleteDialog = false; onDeleted() }) { current -> TrainingEngine.deleteRoutine(current, routine.id) }
    }
}

@Composable fun RoutineDialog(routine: Routine?, busy: Boolean, scheduled: Boolean, onDismiss: () -> Unit, onSave: (String, Set<Int>, String) -> Unit) {
    val english = LocalEnglish.current
    var name by rememberSaveable { mutableStateOf(routine?.label(english) ?: "") }
    var selected by rememberSaveable { mutableStateOf(routine?.days?.sorted() ?: emptyList()) }
    var shortLabel by rememberSaveable { mutableStateOf(routine?.shortLabel ?: "") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("פרטי האימון", "Routine details")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text(tr("שם האימון", "Routine name")) }, placeholder = { Text(tr("למשל חזה או אימון A", "e.g. Chest or Workout A")) }, singleLine = true)
            OutlinedTextField(shortLabel, { shortLabel = it.take(6) }, label = { Text(tr("סימון קצר ביומן", "Calendar label")) }, placeholder = { Text(tr("למשל A או חזה", "e.g. A or Chest")) }, singleLine = true)
            if (scheduled) {
                Text(tr("ימים קבועים (אפשר להשאיר ריק)", "Scheduled days (optional)"), style = MaterialTheme.typography.bodySmall)
                DaySelector(selected.toSet()) { selected = it.sorted() }
            } else Text(tr("היום יירשם אוטומטית כשתתאמן. הסימון הקצר מופיע מתחת ליום.", "Your training date is recorded automatically. The short label appears beneath the day."), color = muted(), style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton({ onSave(name.trim(), selected.toSet(), shortLabel.trim()) }, enabled = name.isNotBlank() && !busy) { Text(tr("שמור", "Save")) } },
        dismissButton = { TextButton(onDismiss) { Text(tr("ביטול", "Cancel")) } })
}

@Composable fun DaySelector(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    // Two lines keep 48dp targets and long accessibility labels at large font scales.
    Column {
        listOf(0..3, 4..6).forEach { range -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            range.forEach { index -> FilterChip(index in selected, { onChange(if (index in selected) selected - index else selected + index) }, label = { Text(dayName(index, true)) }, modifier = Modifier.heightIn(min = 48.dp)) }
        } }
    }
}

@Composable fun CatalogScreen(state: AppState, routineId: String, busy: Boolean, vm: WorkoutViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<Muscle?>(null) }
    val english = LocalEnglish.current
    val routine = state.routines.find { it.id == routineId } ?: return
    val added = tr("התרגיל נוסף לאימון", "Exercise added to routine")
    val results = Catalog.exercises.filter { (group == null || it.muscle == group) && (it.name.contains(query, true) || it.nameEn.contains(query, true) || it.equipment.contains(query, true) || it.equipmentEn.contains(query, true)) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading(tr("מאגר התרגילים", "Exercise library"), tr("${Catalog.exercises.size} תרגילים · הוספה אל ${routine.label(LocalEnglish.current)}", "${Catalog.exercises.size} exercises · add to ${routine.label(LocalEnglish.current)}")) }
        item { OutlinedTextField(query, { query = it }, label = { Text(tr("חיפוש תרגיל או ציוד", "Search exercise or equipment")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(group == null, { group = null }, label = { Text(tr("הכול", "All")) }) }
            items(Muscle.entries) { muscle -> FilterChip(group == muscle, { group = muscle }, label = { Text(muscle.label(english)) }) }
        } }
        if (results.isEmpty()) item { Text(tr("לא נמצאו תרגילים. אפשר להוסיף תרגיל אישי בעריכת האימון.", "No matches. Add your own exercise in the routine editor."), color = muted()) }
        items(results, key = { it.id }) { exercise -> Panel {
            Text(exercise.label(english), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(exercise.muscle.label(english) + " · " + (if (english) exercise.equipmentEn else exercise.equipment), color = muted())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${exercise.sets} × ${exercise.targetLabel()} ${exercise.measure.unit()}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                val exists = routine.exercises.any { it.libraryId == exercise.libraryId }
                FilledTonalButton({ vm.act(added) { current ->
                    val latest = current.routines.first { it.id == routineId }
                    TrainingEngine.saveExercise(current, routineId, Catalog.fresh(exercise), vm.getApplication<android.app.Application>().clock())
                } }, enabled = !busy && !exists) { Icon(if (exists) Icons.Default.Check else Icons.Default.Add, null, Modifier.size(18.dp)); Text(if (exists) tr("נוסף", "Added") else tr("הוסף", "Add")) }
            }
        } }
    }
}

@Composable fun ConfirmDelete(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(text) }, text = { Text(tr("הרשומות שכבר נשמרו בהיסטוריה לא יושפעו.", "Previously saved history will not be affected.")) },
        confirmButton = { TextButton(onConfirm) { Text(tr("מחק", "Delete"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onDismiss) { Text(tr("ביטול", "Cancel")) } })
}
