package com.hananel.workout.domain

/** A training round is independent of calendar weeks. Saved progress is committed with a session. */
private fun PlanProgress.record(session: Session, ids: Set<String>, fallbackMode: PlanMode): PlanProgress {
    if ((session.planMode ?: fallbackMode) != PlanMode.FLEXIBLE || session.totalSets == 0 || session.routineId !in ids) return this
    val clean = copy(completedIds = completedIds.intersect(ids))
    val base = session.roundStart?.let { it.copy(completedIds = it.completedIds.intersect(ids)) }
        ?: clean.startRound(ids)
    return if (session.complete) base.copy(completedIds = base.completedIds + session.routineId) else base
}

private fun PlanProgress.startRound(ids: Set<String>) =
    if (ids.isNotEmpty() && completedIds.containsAll(ids)) PlanProgress(round = round + 1) else this

/** Freeze the round boundary at the first real set, before the program can be edited. */
fun AppState.roundAtFirstSet(): PlanProgress = savedPlanProgress().startRound(trainingIds())

fun AppState.trainingIds() = routines.filter { it.exercises.isNotEmpty() }.map { it.id }.toSet()

/** Used once for older backups. New state stores progress, so deleting old history cannot move the cursor. */
fun AppState.savedPlanProgress(): PlanProgress {
    val ids = trainingIds()
    return planProgress?.let { it.copy(completedIds = it.completedIds.intersect(ids)) }
        ?: history.sortedBy { it.startedAt }.fold(PlanProgress()) { progress, session -> progress.record(session, ids, preferences.mode) }
}

/** Active completion is a preview: undo can remove it until finish atomically commits both history and progress. */
fun AppState.cycleProgress(): PlanProgress {
    val saved = savedPlanProgress()
    return active?.let { saved.record(it, trainingIds(), preferences.mode) } ?: saved
}

fun AppState.upgradePlanning(): AppState = copy(
    planProgress = savedPlanProgress(),
    history = history.map { if (it.planMode == null) it.copy(planMode = preferences.mode) else it },
    active = active?.let { session -> session.copy(
        planMode = session.planMode ?: preferences.mode,
        roundStart = session.roundStart ?: if (session.totalSets > 0) roundAtFirstSet() else null,
        calendarMarker = session.calendarMarker ?: routines.find { it.id == session.routineId }?.shortLabel
    ) }
)
