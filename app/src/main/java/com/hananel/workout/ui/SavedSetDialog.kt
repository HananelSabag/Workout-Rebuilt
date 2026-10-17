package com.hananel.workout.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hananel.workout.domain.*

/** The supplied set is a snapshot; saving must validate its identity against current state. */
@Composable fun SavedSetDialog(exercise: Exercise, saved: SetLog, busy: Boolean, onDismiss: () -> Unit, onSave: (Int, Double) -> Unit) {
    var value by rememberSaveable(saved.id) { mutableStateOf(saved.value.toString()) }
    var weight by rememberSaveable(saved.id) { mutableStateOf(number(saved.weight)) }
    val parsed = value.toIntOrNull()
    val parsedWeight = if (exercise.weighted) weight.replace(',', '.').toDoubleOrNull() else 0.0
    val validValue = parsed != null && parsed in 1..999
    val validWeight = parsedWeight != null && parsedWeight.isFinite() && parsedWeight in 0.0..1500.0
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(tr("תיקון הסט האחרון", "Edit last set")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(exercise.label(LocalEnglish.current))
            OutlinedTextField(value, { value = it.take(3) }, label = { Text(exercise.measure.unit()) }, singleLine = true,
                isError = !validValue, supportingText = { Text(ltrNumber("1–999")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (exercise.weighted) OutlinedTextField(weight, { weight = it.take(8) }, label = { Text(tr("משקל בק״ג", "Weight in kg")) }, singleLine = true,
                isError = !validWeight, supportingText = { Text(ltrNumber("0–1500")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Text(tr("הערכים יתעדכנו בסט שכבר נרשם.", "Updates the set you already logged."), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton({ onSave(parsed!!, parsedWeight!!) }, enabled = !busy && validValue && validWeight) { Text(tr("שמור תיקון", "Save correction")) } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text(tr("ביטול", "Cancel")) } })
}
