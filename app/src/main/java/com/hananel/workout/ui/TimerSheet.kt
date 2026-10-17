package com.hananel.workout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hananel.workout.domain.*
import com.hananel.workout.timer.formatTime

@Composable fun RestBanner(timer: RestTimer, onClick: () -> Unit) {
    val clock = rememberClock()
    val finished = timer.remainingMs(clock) == 0L
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (finished) Icons.Default.CheckCircle else Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(if (finished) tr("מוכנים לסט ${timer.loggedSets + 1}", "Ready for set ${timer.loggedSets + 1}") else if (timer.pausedMs != null) tr("המנוחה מושהית", "Rest paused") else tr("מנוחה ברקע", "Rest in background"), style = MaterialTheme.typography.labelLarge)
                Text(if (LocalEnglish.current) timer.titleEn else timer.title, style = MaterialTheme.typography.labelSmall, color = muted())
            }
            Text(formatTime(timer.seconds(clock)), style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Default.KeyboardArrowUp, tr("פתח טיימר", "Open timer"), Modifier.size(18.dp))
        }
    }
}

/** Rest completion returns to the exercise; only an explicit performed-set action logs a set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TimerSheet(state: AppState, vm: WorkoutViewModel, busy: Boolean, onDismiss: () -> Unit) {
    val timer = state.rest ?: return
    val clock = rememberClock()
    val left = timer.remainingMs(clock)
    val log = state.active?.exercises?.find { it.exercise.id == timer.exerciseId }
    val complete = left == 0L
    var editLast by rememberSaveable(timer.id) { mutableStateOf(false) }
    val accent = if (complete) MaterialTheme.colorScheme.tertiary else if (LocalDark.current) Amber else Color(0xFFB26B21)
    val fontScale = LocalDensity.current.fontScale
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface, windowInsets = WindowInsets(0, 0, 0, 0)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Tag(tr("טיימר מנוחה", "REST TIMER"), accent)
            Text(if (LocalEnglish.current) timer.titleEn else timer.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(tr("סט ${timer.loggedSets} נשמר · נשארו ${(log?.exercise?.sets ?: timer.loggedSets) - timer.loggedSets}", "Set ${timer.loggedSets} saved · ${(log?.exercise?.sets ?: timer.loggedSets) - timer.loggedSets} remaining"), style = MaterialTheme.typography.bodySmall, color = muted())
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val large = fontScale > 1.2f
                @Composable fun adjustment(delta: Int) {
                    OutlinedButton({ vm.timerAdjust(timer.id, delta) }, enabled = !busy, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(ltrNumber(if (delta > 0) "+15" else "−15"), style = MaterialTheme.typography.labelMedium) }
                }
                @Composable fun dial() {
                Box(Modifier.size(if (large) minOf(maxWidth, (148 * fontScale).dp) else 148.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { if (complete) 1f else (left.toFloat() / timer.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f) }, Modifier.fillMaxSize(), color = accent, strokeWidth = 5.dp, trackColor = accent.copy(alpha = .12f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatTime(timer.seconds(clock)), fontSize = 34.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(if (complete) tr("המנוחה הסתיימה", "Rest finished") else if (timer.pausedMs != null) tr("מושהה", "Paused") else tr("זמן לנשום", "Take a breath"), style = MaterialTheme.typography.labelSmall, color = muted())
                    }
                }
                }
                if (large) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    dial()
                    if (!complete) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { adjustment(-15); adjustment(15) }
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!complete) adjustment(-15)
                    dial()
                    if (!complete) adjustment(15)
                }
            }
            if (!complete) FilledTonalButton({ vm.timerPause(timer.id) }, enabled = !busy) {
                Icon(if (timer.pausedMs == null) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text(if (timer.pausedMs == null) tr("השהה מנוחה", "Pause rest") else tr("המשך מנוחה", "Resume rest"))
            }
            if (complete) {
                Text(tr("עכשיו מבצעים את סט ${timer.loggedSets + 1}. מסמנים אותו בסיום.", "Perform set ${timer.loggedSets + 1} now. Log it when you finish."), style = MaterialTheme.typography.bodySmall, color = muted())
                PrimaryButton(tr("חזרה לסט ${timer.loggedSets + 1}", "Back to set ${timer.loggedSets + 1}"), { vm.timerSkip(timer.id); onDismiss() }, enabled = !busy, icon = Icons.Default.FitnessCenter)
            } else PrimaryButton(tr("המשך ברקע", "Continue in background"), onDismiss, icon = Icons.Default.KeyboardArrowDown)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (log != null && log.sets.isNotEmpty()) TextButton({ editLast = !editLast }) { Text(tr("תיקון הסט שנרשם", "Edit saved set"), style = MaterialTheme.typography.labelMedium) }
                TextButton({ vm.timerSkip(timer.id); onDismiss() }, enabled = !busy) { Text(if (complete) tr("סגור", "Close") else tr("דלג על המנוחה", "Skip rest"), style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
    if (editLast && log != null && log.sets.isNotEmpty()) {
        val saved = remember(timer.id) { log.sets.last() }
        SavedSetDialog(log.exercise, saved, busy, { editLast = false }) { value, weight ->
            vm.amend(timer.sessionId, log.exercise.id, saved.id, value, weight) { editLast = false }
        }
    }
}
