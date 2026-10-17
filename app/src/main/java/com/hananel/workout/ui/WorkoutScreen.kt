package com.hananel.workout.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hananel.workout.domain.*
import com.hananel.workout.timer.formatTime
import kotlinx.coroutines.delay
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WorkoutScreen(state: AppState, routine: Routine, today: LocalDate, busy: Boolean, vm: WorkoutViewModel,
    onEdit: () -> Unit, onFinish: () -> Unit, onTimer: () -> Unit, onResume: () -> Unit, onChoose: () -> Unit, onExerciseEdit: (String?) -> Unit) {
    val active = state.active?.takeIf { it.routineId == routine.id }
    val otherActive = state.active != null && active == null
    val exercises = active?.exercises?.map { it.exercise } ?: routine.exercises
    var expandedId by rememberSaveable(routine.id) { mutableStateOf(exercises.firstOrNull { e -> active?.exercises?.find { it.exercise.id == e.id }?.complete != true }?.id) }
    var addMode by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    var previousComplete by remember(routine.id) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(active?.id, active?.totalSets) {
        val completed = active?.exercises?.filter { it.complete }?.map { it.exercise.id }?.toSet() ?: emptySet()
        val newlyCompleted = completed - previousComplete
        previousComplete = completed
        if (newlyCompleted.isNotEmpty()) {
            delay(1400)
            val next = exercises.indexOfFirst { e -> active?.exercises?.find { it.exercise.id == e.id }?.complete != true }
            if (next >= 0 && expandedId in newlyCompleted) {
                expandedId = exercises[next].id
                listState.animateScrollToItem(next + 1)
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("exercise-list"), state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val chooseLabel = tr("בחירת אימון", "Choose workout")
                Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable(onClick = onChoose).semantics { contentDescription = chooseLabel }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(routine.label(LocalEnglish.current), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(23.dp), tint = muted())
                    }
                    Text(tr("${exercises.size} תרגילים · ${active?.totalSets ?: 0}/${exercises.sumOf { it.sets }} סטים", "${exercises.size} exercises · ${active?.totalSets ?: 0}/${exercises.sumOf { it.sets }} sets"), color = muted(), style = MaterialTheme.typography.bodySmall)
                }
                IconButton({ addMode = "choose" }, enabled = !busy && !otherActive) { Icon(Icons.Default.AddCircleOutline, tr("הוסף תרגיל", "Add exercise"), tint = MaterialTheme.colorScheme.primary) }
            }
        }
        if (otherActive) item { Panel {
            Text(tr("האימון ${state.active!!.label(LocalEnglish.current)} עדיין פעיל.", "${state.active!!.label(LocalEnglish.current)} is still active."))
            PrimaryButton(tr("חזרה לאימון הפעיל", "Resume active workout"), onResume, icon = Icons.Default.PlayArrow)
            TextButton(onFinish) { Text(tr("סיום האימון הפעיל", "Finish active workout")) }
        } }
        if (exercises.isEmpty()) item { Panel {
            Heading(tr("בוא נוסיף תרגילים", "Let's add exercises"), tr("בחר מהמאגר או צור תרגיל משלך.", "Choose from the library or create your own."))
            PrimaryButton(tr("הוסף תרגילים", "Add exercises"), { addMode = "choose" }, icon = Icons.Default.Add)
        } }
        items(exercises, key = { it.id }) { exercise ->
            val log = active?.exercises?.find { it.exercise.id == exercise.id }
            val previous = state.history.firstNotNullOfOrNull { past -> past.exercises.find { it.exercise.id == exercise.id && it.exercise.measure == exercise.measure && it.sets.isNotEmpty() } }
            ExerciseCard(exercise, log, previous, state.rest?.takeIf { it.exerciseId == exercise.id }, !busy && !otherActive,
                expanded = exercise.id == expandedId, onExpand = { expandedId = if (expandedId == exercise.id) null else exercise.id },
                onLog = { value, weight -> vm.log(routine.id, exercise, log?.sets?.size ?: 0, value, weight) },
                onComplete = { value, weight -> vm.completeExercise(routine.id, exercise.id, log?.sets?.size ?: 0, value, weight) },
                onReset = { vm.resetExercise(exercise.id, log?.sets?.size ?: 0) },
                onAmend = { saved, value, weight, after -> active?.let { vm.amend(it.id, exercise.id, saved.id, value, weight, after) } },
                onUndo = { vm.undo(exercise.id, log?.sets?.size ?: 0) }, onTimer = onTimer,
                onEdit = { onExerciseEdit(exercise.id) }, onWeight = { weight, after -> vm.workingWeight(routine.id, exercise.id, weight, after) })
        }
        if (exercises.isNotEmpty()) item {
            OutlinedButton({ addMode = "choose" }, Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = !busy && !otherActive) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(tr("הוסף תרגיל", "Add exercise"))
            }
        }
    }
    if (addMode == "choose") ModalBottomSheet(onDismissRequest = { addMode = null }) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Heading(tr("מוסיפים תרגיל", "Add an exercise"))
            PrimaryButton(tr("מהמאגר · 41 תרגילים", "From the library · 41 exercises"), { addMode = "library" }, icon = Icons.Default.Search)
            OutlinedButton({ addMode = null; onExerciseEdit(null) }, Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text(tr("תרגיל אישי", "Custom exercise")) }
        }
    }
    if (addMode == "library") ModalBottomSheet(onDismissRequest = { addMode = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(.92f)) {
            TextButton({ addMode = null }, Modifier.align(Alignment.End)) { Text(tr("סיימתי להוסיף", "Done adding")) }
            CatalogScreen(state, routine.id, busy, vm)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ExerciseCard(exercise: Exercise, log: ExerciseLog?, previous: ExerciseLog?, rest: RestTimer?, enabled: Boolean,
    expanded: Boolean, onExpand: () -> Unit,
    onLog: (Int, Double) -> Unit, onComplete: (Int, Double) -> Unit, onReset: () -> Unit,
    onAmend: (SetLog, Int, Double, () -> Unit) -> Unit, onUndo: () -> Unit, onTimer: () -> Unit,
    onEdit: () -> Unit, onWeight: (Double, () -> Unit) -> Unit) {
    val done = log?.complete == true
    val count = log?.sets?.size ?: 0
    val english = LocalEnglish.current
    val clock = if (rest != null) rememberClock() else null
    val resting = rest != null && (rest.pausedMs != null || rest.remainingMs(clock!!) > 0)
    var value by rememberSaveable(exercise.id, exercise.target) { mutableIntStateOf(log?.sets?.lastOrNull()?.value ?: previous?.sets?.lastOrNull()?.value ?: exercise.target) }
    LaunchedEffect(log?.sets?.lastOrNull()?.id, log?.sets?.lastOrNull()?.value) { log?.sets?.lastOrNull()?.let { value = it.value } }
    var actions by remember { mutableStateOf(false) }
    var bulkDialog by rememberSaveable { mutableStateOf(false) }
    var editSaved by remember { mutableStateOf<SetLog?>(null) }
    var editNumber by rememberSaveable { mutableStateOf<String?>(null) }
    val tint = when { done -> MaterialTheme.colorScheme.tertiary; rest != null -> if (LocalDark.current) Amber else Color(0xFF9B641B); count > 0 -> MaterialTheme.colorScheme.primary; else -> Color.Transparent }
    val expansionLabel = if (expanded) tr("פתוח", "Expanded") else tr("סגור", "Collapsed")
    Panel(tint = tint, modifier = Modifier.testTag("exercise-card-${exercise.id}")) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable(onClick = onExpand).semantics { stateDescription = expansionLabel }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(exercise.label(english), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp), tint = muted())
                }
                val status = when { done -> tr("תרגיל הושלם · $count/${exercise.sets} סטים", "Exercise complete · $count/${exercise.sets} sets")
                    rest?.pausedMs != null -> tr("המנוחה מושהית", "Rest paused")
                    resting -> tr("במנוחה", "Resting")
                    count > 0 -> tr("התרגיל התחיל · סט ${count + 1} הבא", "In progress · set ${count + 1} next")
                    else -> (if (english) exercise.equipmentEn else exercise.equipment).ifBlank { exercise.muscle.label(english) } }
                Text(status, style = MaterialTheme.typography.bodySmall, color = if (tint == Color.Transparent) muted() else tint)
                if (!expanded || done) Text("$count/${exercise.sets} " + tr("סטים", "sets") + if (exercise.weighted) tr(" · ${number(exercise.weight)} ק״ג", " · ${number(exercise.weight)} kg") else "", style = MaterialTheme.typography.bodySmall, color = muted())
            }
            Checkbox(done, { bulkDialog = true }, enabled = enabled, modifier = Modifier.size(48.dp).semantics {
                contentDescription = if (english) "Complete exercise ${exercise.label(true)}" else "השלמת תרגיל ${exercise.label(false)}"
            }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.tertiary))
            Box {
                IconButton({ actions = true }, enabled = enabled, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, tr("פעולות תרגיל", "Exercise actions"), tint = muted()) }
                DropdownMenu(actions, { actions = false }) {
                    DropdownMenuItem(text = { Text(tr("ערוך תרגיל", "Edit exercise")) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { actions = false; onEdit() })
                    if (exercise.weighted) DropdownMenuItem(text = { Text(tr("משקל עבודה", "Working weight")) }, leadingIcon = { Icon(Icons.Default.FitnessCenter, null) }, onClick = { actions = false; editNumber = "weight" })
                    if (count > 0) {
                        DropdownMenuItem(text = { Text(tr("תיקון הסט האחרון", "Edit last set")) }, onClick = { editSaved = log?.sets?.lastOrNull(); actions = false })
                        DropdownMenuItem(text = { Text(tr("בטל סט אחרון", "Undo last set")) }, onClick = { onUndo(); actions = false })
                    }
                    DropdownMenuItem(text = { Text(if (done) tr("נקה סימונים", "Clear sets") else tr("סימון כל הסטים שנותרו", "Log remaining sets")) }, onClick = { actions = false; bulkDialog = true })
                }
            }
        }
        if (expanded && !done) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (exercise.weighted) OutlinedButton({ editNumber = "weight" }, Modifier.weight(1f).heightIn(min = 62.dp).semantics { contentDescription = if (english) "Working weight" else "משקל עבודה" }, enabled = enabled) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr("משקל", "Weight"), style = MaterialTheme.typography.labelSmall)
                        Text(tr("${number(exercise.weight)} ק״ג", "${number(exercise.weight)} kg"), style = MaterialTheme.typography.titleSmall)
                    }
                }
                OutlinedButton({ editNumber = "value" }, Modifier.weight(1f).heightIn(min = 62.dp), enabled = enabled) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(exercise.measure.unit(), style = MaterialTheme.typography.labelSmall)
                        Text(value.toString(), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(exercise.sets) { index ->
                        val checked = index < count
                        Surface(shape = CircleShape, color = if (checked) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                                if (checked) Icon(Icons.Default.Check, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                else Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = muted())
                            }
                        }
                    }
                }
                Text(tr("${exercise.restSeconds} שנ׳ מנוחה", "${exercise.restSeconds}s rest"), style = MaterialTheme.typography.labelSmall, color = muted())
                if (count > 0) IconButton(onUndo, enabled = enabled) { Icon(Icons.Default.Undo, tr("בטל סט אחרון", "Undo last set"), Modifier.size(20.dp), tint = muted()) }
            }
            if (previous != null) Text(tr("בפעם הקודמת: ", "Last time: ") + "${previous.sets.last().value} ${exercise.measure.unit()}" + if (exercise.weighted) tr(" · ${number(previous.sets.last().weight)} ק״ג", " · ${number(previous.sets.last().weight)} kg") else "", style = MaterialTheme.typography.bodySmall, color = muted())
            if (resting) PrimaryButton(tr("מנוחה · ${formatTime(rest!!.seconds(clock!!))}", "Rest · ${formatTime(rest!!.seconds(clock!!))}"), onTimer, enabled = enabled, icon = Icons.Default.Timer)
            else PrimaryButton(tr("סיימתי סט ${count + 1}", "Set ${count + 1} done"), { onLog(value, exercise.weight) }, enabled = enabled, icon = Icons.Default.Check)
            if (exercise.notes.isNotBlank()) Text(exercise.notes, color = muted(), style = MaterialTheme.typography.bodySmall)
        }
    }

    editSaved?.let { saved -> SavedSetDialog(exercise, saved, !enabled, { editSaved = null }) { newValue, weight -> onAmend(saved, newValue, weight) { editSaved = null } } }
    editNumber?.let { field -> WorkingValueSheet(field == "weight", exercise, value, !enabled, { editNumber = null }) { parsed ->
        if (field == "weight") onWeight(parsed) { editNumber = null } else { value = parsed.toInt(); editNumber = null }
    } }
    if (bulkDialog) AlertDialog(onDismissRequest = { bulkDialog = false }, title = { Text(if (done) tr("לנקות את סימוני התרגיל?", "Clear this exercise's sets?") else tr("השלמת את כל הסטים?", "Completed all sets?")) },
        text = { Text(if (done) tr("הסטים יוסרו מהאימון הפעיל בלבד.", "Sets will be removed from the active workout only.") else
            tr("יירשמו עוד ${exercise.sets - count} סטים עם $value ${exercise.measure.unit()}" + (if (exercise.weighted) " ומשקל ${number(exercise.weight)} ק״ג." else ".") + " אשר רק אם ביצעת אותם.",
                "Log ${exercise.sets - count} remaining sets at $value ${exercise.measure.unit()}" + (if (exercise.weighted) " and ${number(exercise.weight)} kg." else ".") + " Confirm only if you performed them.")) },
        confirmButton = { TextButton({ if (done) onReset() else onComplete(value, exercise.weight); bulkDialog = false }, enabled = enabled) { Text(if (done) tr("נקה סימונים", "Clear sets") else tr("ביצעתי את כולם", "I did them all")) } },
        dismissButton = { TextButton({ bulkDialog = false }) { Text(tr("ביטול", "Cancel")) } })
}

@Composable private fun ExerciseMetric(icon: ImageVector, label: String, color: Color, onClick: (() -> Unit)? = null, description: String? = null) {
    Surface(color = color.copy(alpha = .085f), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.heightIn(min = 44.dp).then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .semantics { if (description != null) contentDescription = description }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(17.dp), tint = color)
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun WorkingValueSheet(isWeight: Boolean, exercise: Exercise, value: Int, busy: Boolean, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var input by rememberSaveable { mutableStateOf(if (isWeight) number(exercise.weight) else value.toString()) }
    val parsed = input.replace(',', '.').toDoubleOrNull()
    val valid = parsed != null && parsed.isFinite() && if (isWeight) parsed in 0.0..1500.0 else parsed in 1.0..999.0 && parsed % 1.0 == 0.0
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Heading(if (isWeight) tr("משקל עבודה", "Working weight") else tr("ערך לסט הבא", "Next set value"), exercise.label(LocalEnglish.current))
            OutlinedTextField(input, { input = it.take(8) }, Modifier.fillMaxWidth(), label = { Text(if (isWeight) tr("ק״ג", "kg") else exercise.measure.unit()) }, singleLine = true, isError = !valid,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            if (isWeight) Text(tr("נשמר גם לאימון הבא. משקלים שכבר נרשמו בהיסטוריה לא משתנים.", "Remembered for your next workout. Previously logged weights stay unchanged."), style = MaterialTheme.typography.bodySmall, color = muted())
            PrimaryButton(tr("שמור", "Save"), { onSave(parsed!!) }, enabled = valid && !busy, icon = Icons.Default.Check)
        }
    }
}
