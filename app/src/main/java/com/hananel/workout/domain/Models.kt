package com.hananel.workout.domain

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil

@Serializable enum class PlanMode { FLEXIBLE, SCHEDULED }
@Serializable enum class FlexibleOrder { ORDERED, FREE }
@Serializable enum class Measure { REPS, SECONDS, MINUTES }
@Serializable enum class Muscle(val he: String, val en: String) {
    CHEST("חזה", "Chest"), BACK("גב", "Back"), SHOULDERS("כתפיים", "Shoulders"),
    ARMS("ידיים", "Arms"), LEGS("רגליים", "Legs"), CORE("בטן", "Core"), CARDIO("אירובי", "Cardio");
    fun label(english: Boolean) = if (english) en else he
}
@Serializable data class Exercise(
    val id: String,
    val libraryId: String = "",
    val name: String,
    val nameEn: String = name,
    val muscle: Muscle,
    val equipment: String = "",
    val equipmentEn: String = equipment,
    val sets: Int = 3,
    val target: Int = 12,
    val measure: Measure = Measure.REPS,
    val restSeconds: Int = 60,
    val weighted: Boolean = true,
    val weight: Double = 0.0,
    val notes: String = "",
    val targetMax: Int = target
) { fun label(english: Boolean) = if (english) nameEn else name }

@Serializable data class Routine(val id: String, val name: String, val exercises: List<Exercise> = emptyList(), val days: Set<Int> = emptySet(), val nameEn: String = name, val customName: Boolean = true, val shortLabel: String = "") {
    fun label(english: Boolean) = if (english && !customName) nameEn else name
    fun calendarLabel(english: Boolean): String = shortLabel.ifBlank {
        Regex("\\b[A-Z]\\b").find(label(english))?.value
            ?: label(english).substringBefore(' ').trim(',', '·', '-', '־').take(6)
    }
}
@Serializable data class SetLog(val id: String, val value: Int, val weight: Double, val at: Long)
@Serializable data class ExerciseLog(val exercise: Exercise, val sets: List<SetLog> = emptyList()) {
    val complete get() = sets.size >= exercise.sets
    val volume get() = if (exercise.measure == Measure.REPS) sets.sumOf { it.weight * it.value } else 0.0
}
@Serializable data class Session(
    val id: String, val routineId: String, val name: String, val date: String,
    val startedAt: Long = 0, val lastActionAt: Long = 0, val endedAt: Long? = null,
    val exercises: List<ExerciseLog>, val autoClosed: Boolean = false, val nameEn: String = name, val customName: Boolean = true,
    val planMode: PlanMode? = null,
    val roundStart: PlanProgress? = null,
    val calendarMarker: String? = null,
    val lastActionElapsed: Long? = null, val actionBoot: Int? = null
) {
    fun label(english: Boolean) = if (english && !customName) nameEn else name
    val totalSets get() = exercises.sumOf { it.sets.size }
    val plannedSets get() = exercises.sumOf { it.exercise.sets }
    val complete get() = exercises.isNotEmpty() && exercises.all { it.complete }
    val progress get() = if (plannedSets == 0) 0f else (totalSets.toFloat() / plannedSets).coerceIn(0f, 1f)
    val volume get() = exercises.sumOf { it.volume }
    fun durationMs(now: Long) = if (startedAt == 0L) 0L else ((endedAt ?: now) - startedAt).coerceAtLeast(0L)
}

data class ClockReading(val wall: Long, val elapsed: Long, val boot: Int, val date: LocalDate)
@Serializable data class RestTimer(
    val id: String, val sessionId: String, val exerciseId: String,
    val title: String, val titleEn: String, val loggedSets: Int,
    val durationMs: Long, val deadlineWall: Long, val deadlineElapsed: Long, val boot: Int,
    val pausedMs: Long? = null, val alerted: Boolean = false
) {
    fun remainingMs(clock: ClockReading): Long = pausedMs ?: (if (boot == clock.boot) deadlineElapsed - clock.elapsed else deadlineWall - clock.wall).coerceAtLeast(0L)
    fun seconds(clock: ClockReading) = ceil(remainingMs(clock) / 1000.0).toInt()
    fun running(clock: ClockReading) = pausedMs == null && remainingMs(clock) > 0
}
@Serializable data class Reminder(val id: String, val hour: Int, val minute: Int, val days: Set<Int>, val message: String = "", val enabled: Boolean = true)
@Serializable data class Preferences(
    val onboarded: Boolean = false, val mode: PlanMode = PlanMode.FLEXIBLE,
    val language: String = "he", val theme: String = "light",
    val reminderEnabled: Boolean = true, val notificationsAsked: Boolean = false,
    val reminders: List<Reminder> = emptyList(), val appearanceVersion: Int = 0,
    val programName: String = "", val flexibleOrder: FlexibleOrder = FlexibleOrder.ORDERED,
    val autoCloseIdle: Boolean = true, val glass: Boolean = true
)
fun Preferences.isEnglish(deviceLanguage: String = java.util.Locale.getDefault().language) = language == "en" || (language == "system" && deviceLanguage !in setOf("he", "iw"))
fun Exercise.targetLabel() = if (targetMax > target) "$target–$targetMax" else target.toString()
@Serializable data class AppState(
    val schemaVersion: Int = 1, val preferences: Preferences = Preferences(),
    val routines: List<Routine> = emptyList(), val active: Session? = null,
    val history: List<Session> = emptyList(), val rest: RestTimer? = null,
    val planProgress: PlanProgress? = null
)
@Serializable data class PlanProgress(val completedIds: Set<String> = emptySet(), val round: Int = 1)

fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
fun dayIndex(date: LocalDate) = date.dayOfWeek.value % 7
fun AppState.weekSessions(date: LocalDate): List<Session> {
    val start = weekStart(date).toString()
    val end = weekStart(date).plusDays(7).toString()
    return (history + listOfNotNull(active)).filter { it.date >= start && it.date < end && it.totalSets > 0 }
}
fun AppState.latestFor(routineId: String, date: LocalDate) = weekSessions(date).filter { it.routineId == routineId }.maxByOrNull { it.lastActionAt }
fun AppState.nextRoutine(date: LocalDate): Routine? {
    active?.let { activeSession -> routines.find { it.id == activeSession.routineId }?.let { return it } }
    if (preferences.mode == PlanMode.SCHEDULED) {
        val completedToday = weekSessions(date).filter { it.date == date.toString() && it.complete }.map { it.routineId }.toSet()
        return routines.firstOrNull { dayIndex(date) in it.days && it.id !in completedToday && it.exercises.isNotEmpty() }
    }
    if (preferences.flexibleOrder == FlexibleOrder.FREE) return null
    val candidates = routines.filter { it.exercises.isNotEmpty() }
    val completed = cycleProgress().completedIds
    return candidates.firstOrNull { it.id !in completed } ?: candidates.firstOrNull()
}

fun Exercise.checked(): Exercise {
    require(id.isNotBlank() && name.isNotBlank() && name.length <= 120) { "שם התרגיל חסר או ארוך מדי" }
    require(sets in 1..20 && target in 1..999 && targetMax in target..999 && restSeconds in 0..900) { "ערכי התרגיל מחוץ לטווח" }
    require(weight.isFinite() && weight in 0.0..1500.0 && notes.length <= 2000) { "משקל או הערה לא תקינים" }
    return this
}

fun AppState.validate(): AppState {
    require(schemaVersion == 1) { "גרסת הגיבוי אינה נתמכת" }
    require(routines.size <= 50 && routines.map { it.id }.distinct().size == routines.size)
    require(preferences.language in listOf("he", "en", "system") && preferences.theme in listOf("dark", "light", "system"))
    require(preferences.programName.length <= 80)
    planProgress?.let { require(it.round > 0 && it.completedIds.all(String::isNotBlank)) }
    val allIds = routines.flatMap { it.exercises }.map { it.id }
    require(allIds.distinct().size == allIds.size)
    routines.forEach { r ->
        require(r.id.isNotBlank() && r.name.isNotBlank() && r.name.length <= 80 && r.exercises.size <= 100 && r.days.all { it in 0..6 })
        require(r.shortLabel.length <= 6)
        r.exercises.forEach { it.checked() }
    }
    require(history.map { it.id }.distinct().size == history.size && history.none { it.id == active?.id })
    (history + listOfNotNull(active)).forEach { s ->
        LocalDate.parse(s.date)
        require(s.id.isNotBlank() && s.startedAt >= 0 && s.lastActionAt >= s.startedAt)
        s.roundStart?.let { require(it.round > 0 && it.completedIds.all(String::isNotBlank)) }
        require(s.calendarMarker == null || s.calendarMarker.length <= 6)
        require(s.lastActionElapsed == null || s.lastActionElapsed >= 0)
        require(s.endedAt == null || s.endedAt >= s.startedAt)
        require(s.exercises.map { it.exercise.id }.distinct().size == s.exercises.size)
        s.exercises.forEach { log ->
            log.exercise.checked()
            require(log.sets.size <= log.exercise.sets && log.sets.map { it.id }.distinct().size == log.sets.size)
            log.sets.forEach { require(it.value in 1..999 && it.weight.isFinite() && it.weight in 0.0..1500.0) }
        }
    }
    require(history.all { it.endedAt != null } && active?.endedAt == null)
    require(preferences.reminders.map { it.id }.distinct().size == preferences.reminders.size)
    preferences.reminders.forEach { require(it.id.isNotBlank() && it.hour in 0..23 && it.minute in 0..59 && it.days.isNotEmpty() && it.days.all { d -> d in 0..6 }) }
    active?.let { require(routines.any { r -> r.id == it.routineId }) }
    rest?.let { timer ->
        val session = requireNotNull(active)
        val log = requireNotNull(session.exercises.find { it.exercise.id == timer.exerciseId })
        require(timer.id.isNotBlank() && timer.sessionId == session.id && timer.loggedSets == log.sets.size && timer.loggedSets in 1 until log.exercise.sets)
        require(timer.durationMs in 1..900_000 && timer.deadlineWall >= 0 && timer.deadlineElapsed >= 0 && (timer.pausedMs == null || timer.pausedMs in 0..900_000))
    }
    return this
}
