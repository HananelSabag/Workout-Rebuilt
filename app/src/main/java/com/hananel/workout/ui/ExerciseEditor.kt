package com.hananel.workout.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hananel.workout.domain.*
import java.util.UUID

/** Full editor keeps Save outside the scrolling form and above the keyboard. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ExerciseEditor(original: Exercise?, busy: Boolean, onDismiss: () -> Unit, loggedSets: Int = 0, onSave: (Exercise) -> Unit) {
    val english = LocalEnglish.current
    var name by rememberSaveable { mutableStateOf(original?.label(english) ?: "") }
    var muscle by rememberSaveable { mutableStateOf(original?.muscle ?: Muscle.CHEST) }
    var sets by rememberSaveable { mutableStateOf((original?.sets ?: 3).toString()) }
    var target by rememberSaveable { mutableStateOf((original?.target ?: 12).toString()) }
    var maximum by rememberSaveable { mutableStateOf((original?.targetMax ?: 12).toString()) }
    var ranged by rememberSaveable { mutableStateOf(original?.let { it.targetMax > it.target } ?: false) }
    var rest by rememberSaveable { mutableStateOf((original?.restSeconds ?: 60).toString()) }
    var measure by rememberSaveable { mutableStateOf(original?.measure ?: Measure.REPS) }
    var weighted by rememberSaveable { mutableStateOf(original?.weighted ?: true) }
    var weight by rememberSaveable { mutableStateOf(number(original?.weight ?: 0.0)) }
    var notes by rememberSaveable { mutableStateOf(original?.notes ?: "") }
    var more by rememberSaveable { mutableStateOf(false) }
    val parsedWeight = weight.replace(',', '.').toDoubleOrNull()
    val parsedSets = sets.toIntOrNull()
    val parsedTarget = target.toIntOrNull()
    val parsedMax = maximum.toIntOrNull()
    val parsedRest = rest.toIntOrNull()
    val validWeight = !weighted || (parsedWeight != null && parsedWeight.isFinite() && parsedWeight in 0.0..1500.0)
    val valid = name.isNotBlank() && validWeight && parsedSets != null && parsedSets in maxOf(1, loggedSets)..20 && parsedTarget != null && parsedTarget in 1..999 && parsedRest != null && parsedRest in 0..900 && (!ranged || (parsedMax != null && parsedMax in parsedTarget!!..999))
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (original == null) tr("תרגיל אישי", "Custom exercise") else tr("עריכת תרגיל", "Edit exercise"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onDismiss, enabled = !busy) { Icon(Icons.Default.Close, tr("ביטול עריכה", "Cancel editing")) }
            }
            HorizontalDivider()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(name, { name = it.take(120) }, label = { Text(tr("שם התרגיל", "Exercise name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (weighted) OutlinedTextField(weight, { weight = it.take(8) }, label = { Text(tr("משקל בק״ג", "Weight in kg")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = !validWeight, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExerciseNumberField(tr("סטים", "Sets"), sets, { sets = it }, maxOf(1, loggedSets)..20, Modifier.weight(1f))
                    ExerciseNumberField(measure.unit(), target, { target = it }, 1..999, Modifier.weight(1f))
                }
                if (ranged) ExerciseNumberField(tr("עד", "Upper target"), maximum, { maximum = it }, (parsedTarget ?: 1)..999, Modifier.fillMaxWidth())
                ExerciseNumberField(tr("מנוחה בשניות", "Rest seconds"), rest, { rest = it }, 0..900, Modifier.fillMaxWidth())
                OutlinedButton({ more = !more }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(tr("פרטים נוספים · ", "More details · ") + muscle.label(english), Modifier.weight(1f))
                    Icon(if (more) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
                if (more) {
                    Text(tr("קבוצת שריר", "Muscle group"), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Muscle.entries.forEach { item -> FilterChip(muscle == item, { muscle = item }, label = { Text(item.label(english)) }, modifier = Modifier.heightIn(min = 48.dp)) } }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(weighted, enabled = loggedSets == 0, role = Role.Switch, onValueChange = { weighted = it }), verticalAlignment = Alignment.CenterVertically) { Text(tr("תרגיל עם משקל", "Weighted exercise"), Modifier.weight(1f)); Switch(weighted, null, enabled = loggedSets == 0) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Measure.entries.forEach { unit -> FilterChip(measure == unit, { measure = unit }, enabled = loggedSets == 0, label = { Text(unit.unit()) }, modifier = Modifier.heightIn(min = 48.dp)) } }
                    if (loggedSets > 0) Text(tr("סוג המשקל והיחידה נעולים עד סיום האימון כדי לשמור על הסטים שכבר נרשמו.", "Weight type and units are locked until the workout ends to preserve logged sets."), color = muted(), style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(ranged, role = Role.Switch, onValueChange = { ranged = it }), verticalAlignment = Alignment.CenterVertically) { Text(tr("טווח יעד", "Target range"), Modifier.weight(1f)); Switch(ranged, null) }
                    OutlinedTextField(notes, { notes = it.take(2000) }, label = { Text(tr("הערה אישית", "Personal note")) }, modifier = Modifier.fillMaxWidth())
                }
            }
            Surface(shadowElevation = 4.dp) {
                PrimaryButton(tr("שמור תרגיל", "Save exercise"), {
                    val base = original ?: Exercise(UUID.randomUUID().toString(), name = name.trim(), muscle = muscle)
                    val renamed = original == null || name.trim() != original.label(english)
                    onSave(base.copy(name = if (renamed) name.trim() else base.name, nameEn = if (renamed) name.trim() else base.nameEn,
                        muscle = muscle, sets = parsedSets!!, target = parsedTarget!!, restSeconds = parsedRest!!, measure = measure,
                        weighted = weighted, weight = if (weighted) parsedWeight!! else 0.0, notes = notes,
                        targetMax = if (ranged) parsedMax!! else parsedTarget))
                }, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), enabled = !busy && valid, icon = Icons.Default.Check)
            }
        }
    }
}

@Composable private fun ExerciseNumberField(label: String, value: String, onChange: (String) -> Unit, range: IntRange, modifier: Modifier) {
    val parsed = value.toIntOrNull()
    val valid = parsed != null && parsed in range
    OutlinedTextField(value, { onChange(it.take(4)) }, modifier = modifier, label = { Text(label) }, singleLine = true,
        isError = !valid, supportingText = if (!valid) { { Text("${range.first}–${range.last}") } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}
