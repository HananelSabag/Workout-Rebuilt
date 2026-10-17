package com.hananel.workout.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hananel.workout.WorkoutApplication
import com.hananel.workout.data.*
import com.hananel.workout.domain.*
import com.hananel.workout.timer.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WorkoutApplication
    val repository = app.repository
    private val _state = MutableStateFlow<AppState?>(null)
    val state = _state.asStateFlow()
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val events = Channel<String>(Channel.BUFFERED)
    val pendingImport = MutableStateFlow<AppState?>(null)
    private var loadJob: Job? = null
    init { load() }
    fun load() {
        loadJob?.cancel()
        error.value = null
        loadJob = viewModelScope.launch {
            try {
                app.coordinator.execute { TrainingEngine.recover(TrainingEngine.upgradeAppearance(it), app.clock()) }
                repository.data.collect { _state.value = it }
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = text("לא הצלחנו לקרוא את הנתונים. הקובץ המקורי נשמר. אפשר לנסות שוב.", "Could not read your data. The original file is preserved. Please try again.") }
        }
    }
    fun act(message: String? = null, after: () -> Unit = {}, onCommitted: (AppState) -> Unit = {}, transform: (AppState) -> AppState) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                val result = app.coordinator.execute(transform = transform)
                onCommitted(result.state)
                report(result.warnings)
                if (message != null) events.send(message)
                after()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { events.send(if (_state.value?.preferences?.isEnglish() == true) "Could not save this change. Finish any active workout, check the values and try again." else e.message ?: "הפעולה לא נשמרה. נסה שוב") }
            finally { busy.value = false }
        }
    }
    // Background housekeeping shares the coordinator but must not mark user controls busy.
    fun recover() = viewModelScope.launch {
        try { app.coordinator.execute { TrainingEngine.recover(it, app.clock()) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { android.util.Log.e("WorkoutRecovery", "Could not recover workout", e) }
    }
    fun refreshServices() {
        viewModelScope.launch {
            try {
                val result = app.coordinator.execute(refresh = true) { TrainingEngine.recover(it, app.clock()) }
                report(result.warnings)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { events.send(text("לא ניתן לחדש את ההתראות כרגע", "Could not resume notifications right now")) }
        }
    }
    fun begin(routineId: String) = act { TrainingEngine.begin(it, routineId, UUID.randomUUID().toString(), app.clock()) }
    fun log(routineId: String, exercise: Exercise, expected: Int, value: Int, weight: Double) {
        val actionId = UUID.randomUUID().toString()
        val sessionId = UUID.randomUUID().toString()
        act(onCommitted = { saved ->
            val log = saved.active?.exercises?.find { it.exercise.id == exercise.id }
            if (log?.complete == true && log.sets.lastOrNull()?.id == actionId)
                events.trySend(text("תרגיל הושלם ✓ · ", "Exercise complete ✓ · ") + exercise.label(saved.preferences.isEnglish()))
        }) {
            val reading = app.clock()
            val current = TrainingEngine.recover(it, reading)
            TrainingEngine.logSet(current, routineId, exercise.id, if (it.active != null && current.active == null) 0 else expected, value, weight, actionId, sessionId, reading)
        }
    }
    fun undo(exerciseId: String, expected: Int) = act { TrainingEngine.undo(it, exerciseId, expected, app.clock()) }
    fun saveExercise(routineId: String, exercise: Exercise, after: () -> Unit) = act(text("התרגיל נשמר", "Exercise saved"), after) {
        TrainingEngine.saveExercise(it, routineId, exercise, app.clock())
    }
    fun workingWeight(routineId: String, exerciseId: String, weight: Double, after: () -> Unit) = act(after = after) {
        TrainingEngine.setWorkingWeight(it, routineId, exerciseId, weight, app.clock())
    }
    fun amend(sessionId: String, exerciseId: String, setId: String, value: Int, weight: Double, after: () -> Unit) = act(after = after) {
        val updated = TrainingEngine.amendLast(it, sessionId, exerciseId, setId, value, weight)
        if (updated == it) it else TrainingEngine.touch(updated, app.clock())
    }
    fun completeExercise(routineId: String, exerciseId: String, expected: Int, value: Int, weight: Double) = act(text("התרגיל הושלם בהצלחה ✓", "Exercise completed ✓")) {
        TrainingEngine.completeExercise(it, routineId, exerciseId, expected, value, weight, UUID.randomUUID().toString(), UUID.randomUUID().toString(), app.clock())
    }
    fun resetExercise(exerciseId: String, expected: Int) = act { TrainingEngine.resetExercise(it, exerciseId, expected, app.clock()) }
    fun finish(message: String, after: () -> Unit) = act(message, after) { TrainingEngine.finish(it, app.clock()) }
    fun timerPause(id: String) = act { TrainingEngine.pauseRest(it, id, app.clock()) }
    fun timerAdjust(id: String, delta: Int) = act { TrainingEngine.adjustRest(it, id, delta, app.clock()) }
    fun timerSkip(id: String) = act { TrainingEngine.skipRest(it, id, app.clock()) }
    fun export(uri: Uri) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(repository.export().encodeToByteArray()) } ?: error("לא ניתן לפתוח את הקובץ")
            }
            events.send(if (state.value?.preferences?.isEnglish() == true) "Backup saved" else "הגיבוי נשמר")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { events.send(text("שמירת הגיבוי נכשלה", "Could not save the backup")) }
    }
    fun readImport(uri: Uri) = viewModelScope.launch {
        try {
            val result = withContext(Dispatchers.IO) {
                val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(8192)
                    val output = java.io.ByteArrayOutputStream()
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 20_000_000) { "הקובץ גדול מדי" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: error("לא ניתן לקרוא את הקובץ")
                repository.parseBackup(bytes.decodeToString())
            }
            pendingImport.value = result
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { events.send(text("הגיבוי אינו תקין. הנתונים הקיימים נשמרו.", "Invalid backup. Your data is unchanged.")) }
    }
    fun confirmImport() {
        val imported = pendingImport.value ?: return
        act(if (imported.preferences.isEnglish()) "Backup restored" else "הגיבוי שוחזר", after = { pendingImport.value = null }) { TrainingEngine.upgradeAppearance(imported.copy(rest = null)) }
    }
    private fun text(he: String, en: String) = if (_state.value?.preferences?.isEnglish() == true) en else he
    private suspend fun report(warnings: Set<PlatformWarning>) {
        if (PlatformWarning.TIMER in warnings) events.send(text("נשמר. הטיימר ברקע לא הופעל; אפשר לחזור לאפליקציה כדי להמשיך.", "Saved. The background timer could not start; reopen the app to resume."))
        if (PlatformWarning.REMINDER in warnings) events.send(text("נשמר. כדאי לבדוק את הרשאות התזכורות בהגדרות.", "Saved. Please check reminder permissions in Settings."))
    }
}
