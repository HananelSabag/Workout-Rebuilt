package com.hananel.workout

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.core.DataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.hananel.workout.data.*
import com.hananel.workout.domain.*
import com.hananel.workout.timer.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WorkoutIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as WorkoutApplication
    private val repo get() = app.repository
    private val device get() = UiDevice.getInstance(instrumentation)
    private val exercise = Exercise("exercise", name = "לחיצת חזה", nameEn = "Chest press", muscle = Muscle.CHEST, sets = 2, restSeconds = 2)
    private fun fixture(language: String = "he") = AppState(preferences = Preferences(onboarded = true, notificationsAsked = true, language = language), routines = listOf(Routine("routine", "אימון בדיקה", listOf(exercise))))

    @Before fun setup() {
        app.stopService(Intent(app, RestService::class.java))
        app.getSystemService(NotificationManager::class.java).cancelAll()
        runBlocking { repo.update { fixture() } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("השבוע שלך").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() { app.stopService(Intent(app, RestService::class.java)); runBlocking { repo.update { fixture() } } }

    @Test fun completeWorkoutWithTimerUndoAndHistory() {
        // The workout is already on the first screen.
        compose.onNodeWithContentDescription("משקל עבודה").performScrollTo().performClick()
        compose.onNodeWithText("ק״ג").performTextReplacement("12.5")
        compose.onNodeWithText("שמור").performClick()
        compose.onNodeWithText("סיימתי סט 1").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { repo.current().active?.totalSets == 1 } }
        assertEquals(12.5, runBlocking { repo.current().active!!.exercises.first().sets.first().weight }, 0.0)
        compose.onNodeWithText("טיימר מנוחה").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("חזרה לסט 2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("חזרה לסט 2").performScrollTo().performClick()
        assertEquals(1, runBlocking { repo.current().active!!.totalSets })
        compose.onNodeWithContentDescription("בטל סט אחרון").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().active == null } }
        compose.onNodeWithText("סיימתי סט 1").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("חזרה לסט 2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("חזרה לסט 2").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().rest == null } }
        compose.waitForIdle()
        compose.onNodeWithText("סיימתי סט 2").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().active?.complete == true } }
        assertNull(runBlocking { repo.current().rest })
        compose.onNodeWithText("סיים ושמור").performClick()
        compose.onNode(hasText("סיים ושמור") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().history.size == 1 } }
        compose.onNodeWithContentDescription("היסטוריה").performClick()
        compose.onNodeWithText("הדרך שעשית").assertExists()
        assertEquals(2, runBlocking { repo.current().history.first().totalSets })
    }

    @Test fun concurrentDuplicateCommandsCommitOneSetAndSurviveReopen() = runBlocking {
        val now = app.clock()
        coroutineScope { repeat(25) { index -> launch(Dispatchers.IO) {
            app.coordinator.execute { TrainingEngine.logSet(it, "routine", "exercise", 0, 12, 20.0, "tap$index", "s", now) }
        } } }
        assertEquals(1, repo.current().active!!.totalSets)
        val export = repo.export()
        val parsed = repo.parseBackup(export)
        assertEquals(repo.current(), parsed)
        val file = File(app.cacheDir, "repository-${System.nanoTime()}.json")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = DataStoreFactory.create(serializer = StateSerializer, scope = scope, produceFile = { file })
        WorkoutRepository(store).update { parsed }
        scope.cancel()
        scope.coroutineContext[Job]!!.join()
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val reopened = WorkoutRepository(DataStoreFactory.create(serializer = StateSerializer, scope = secondScope, produceFile = { file }))
        assertEquals(parsed, reopened.current())
        secondScope.cancel()
    }

    @Test fun backgroundTimerAndNotificationActionAreIdempotent() = runBlocking {
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val logged = repo.update { TrainingEngine.logSet(it, "routine", "exercise", 0, 12, 20.0, "first", "s", app.clock()) }
        withContext(Dispatchers.Main) { RestService.sync(app, logged) }
        device.pressHome()
        withTimeout(15_000) { while (repo.current().rest?.alerted != true) delay(100) }
        val notifications = app.getSystemService(NotificationManager::class.java)
        withTimeout(5000) { while (notifications.activeNotifications.none { it.id == Notifications.COMPLETE }) delay(100) }
        val action = Notifications.action(app, "next", "first")
        action.send()
        action.send()
        withTimeout(5000) { while (repo.current().active?.totalSets != 2) delay(100) }
        assertEquals(2, repo.current().active!!.totalSets)
        assertNull(repo.current().rest)
    }

    @Test fun deniedNotificationPermissionDoesNotBreakTimer() = runBlocking {
        // pm revoke kills the target; use app-ops for a live denial path instead.
        device.executeShellCommand("appops set ${app.packageName} POST_NOTIFICATION deny")
        try {
            val state = repo.update { TrainingEngine.logSet(it, "routine", "exercise", 0, 12, 20.0, "denied", "s", app.clock()) }
            withContext(Dispatchers.Main) { RestService.sync(app, state) }
            withTimeout(15_000) { while (repo.current().rest?.alerted != true) delay(100) }
            assertEquals(1, repo.current().active!!.totalSets)
        } finally { device.executeShellCommand("appops set ${app.packageName} POST_NOTIFICATION allow") }
    }

    @Test fun corruptBackupCannotReplaceData() = runBlocking {
        val original = repo.current()
        assertTrue(runCatching { repo.parseBackup("{broken json") }.isFailure)
        assertEquals(original, repo.current())
    }

    @Test fun reminderCalculatesNextWeekdayAcrossDst() {
        val now = java.time.ZonedDateTime.parse("2026-03-27T17:00:00+03:00[Asia/Jerusalem]")
        val next = Reminders.next(Reminder("r", 18, 0, setOf(0)), now)
        assertEquals(0, dayIndex(next.toLocalDate()))
        assertEquals(18, next.hour)
        assertTrue(next.isAfter(now))
    }

    @Test fun customRoutineBuilderAndCatalogPersistEdits() {
        compose.onNodeWithContentDescription("ניהול התוכנית").performClick()
        compose.onNodeWithText("הוסף אימון לתוכנית").performScrollTo().performClick()
        compose.onNodeWithText("שם האימון").performTextInput("אימון אישי")
        compose.onNodeWithText("שמור", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().routines.size == 2 } }
        compose.onNodeWithText("תרגיל אישי").performScrollTo().performClick()
        compose.onNodeWithText("שם התרגיל").performTextInput("סקוואט אישי")
        compose.onNodeWithText("שמור תרגיל").assertIsDisplayed().assertIsEnabled()
        device.takeScreenshot(File(app.getExternalFilesDir(null), "custom-before-save.png"))
        compose.onNodeWithText("שמור תרגיל").performClick()
        device.takeScreenshot(File(app.getExternalFilesDir(null), "custom-after-save.png"))
        device.dumpWindowHierarchy(File(app.getExternalFilesDir(null), "custom-after-save.xml"))
        compose.waitUntil(5000) { runBlocking { repo.current().routines.last().exercises.size == 1 } }
        compose.onNodeWithText("הוסף מהמאגר").performScrollTo().performClick()
        compose.onNodeWithText("חיפוש תרגיל או ציוד").performTextInput("plank")
        compose.onNodeWithText("הוסף", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().routines.last().exercises.size == 2 } }
        assertEquals(Measure.SECONDS, runBlocking { repo.current().routines.last().exercises.last().measure })
    }

    @Test fun preferencesSwitchModeLanguageAndThemeWithoutDataLoss() {
        compose.onNodeWithContentDescription("ניהול התוכנית").performClick()
        compose.onNodeWithText("שנה").performClick()
        compose.onNodeWithText("קבוע · לפי ימים בשבוע").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().preferences.mode == PlanMode.SCHEDULED } }
        compose.onNodeWithText("סיימתי").performScrollTo().performClick()
        compose.onNodeWithContentDescription("חזרה").performClick()
        compose.onNodeWithContentDescription("הגדרות").performClick()
        compose.onNodeWithText("מראה ושפה").performClick()
        compose.onNodeWithText("English").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().preferences.language == "en" } }
        compose.onNodeWithText("Light").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Appearance & language").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Device language").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().preferences.language == "system" } }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Backup & data").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Your week").assertExists()
        assertEquals(1, runBlocking { repo.current().routines.size })
        assertEquals(PlanMode.SCHEDULED, runBlocking { repo.current().preferences.mode })
        assertEquals("light", runBlocking { repo.current().preferences.theme })
    }

    @Test fun onboardingCreatesEditableBodySplit() {
        runBlocking { repo.update { AppState(preferences = Preferences(notificationsAsked = true)) } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("המשך").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("המשך").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("בוא נתחיל").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("בוא נתחיל").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().preferences.onboarded } }
        assertEquals(5, runBlocking { repo.current().routines.size })
        assertTrue(runBlocking { repo.current().routines.all { it.exercises.isNotEmpty() } })
    }

    @Test fun bulkExerciseLoggingAndClearRequireConfirmation() {
        // The workout is already on the first screen.
        compose.onNodeWithContentDescription("פעולות תרגיל").performScrollTo().performClick()
        assertNull(runBlocking { repo.current().active })
        compose.onNodeWithText("סימון כל הסטים שנותרו").performClick()
        compose.onNodeWithText("ביצעתי את כולם").performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().active?.complete == true } }
        assertEquals(2, runBlocking { repo.current().active!!.totalSets })
        assertNull(runBlocking { repo.current().rest })
        compose.onNodeWithContentDescription("פעולות תרגיל").performScrollTo().performClick()
        assertEquals(2, runBlocking { repo.current().active!!.totalSets })
        compose.onNodeWithText("נקה סימונים").performClick()
        compose.onNodeWithText("נקה סימונים").performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().active == null } }
    }

    @Test fun editorPersistsTargetRangeAndExactRestValue() {
        // The workout is already on the first screen.
        compose.onNodeWithContentDescription("פעולות תרגיל").performScrollTo().performClick()
        compose.onNodeWithText("ערוך תרגיל").performClick()
        compose.onNode(hasText("חזרות") and hasSetTextAction()).performScrollTo().performTextReplacement("10")
        compose.onNodeWithText("מנוחה בשניות").performScrollTo().performTextReplacement("75")
        compose.onNodeWithText("פרטים נוספים · חזה").performScrollTo().performClick()
        compose.onNodeWithText("טווח יעד").performScrollTo().performClick()
        compose.onNodeWithText("שמור תרגיל").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().routines.first().exercises.first().restSeconds == 75 } }
        val saved = runBlocking { repo.current().routines.first().exercises.first() }
        assertEquals(10, saved.target)
        assertEquals(12, saved.targetMax)
    }

    @Test fun activityRecreationPreservesSessionAndTimerDeadline() {
        runBlocking { repo.update { it.copy(routines = listOf(it.routines.first().copy(exercises = listOf(exercise.copy(restSeconds = 120))))) } }
        // The workout is already on the first screen.
        compose.onNodeWithText("סיימתי סט 1").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().rest != null } }
        val before = runBlocking { repo.current() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("טיימר מנוחה").fetchSemanticsNodes().isNotEmpty() }
        val after = runBlocking { repo.current() }
        assertEquals(before.active?.id, after.active?.id)
        assertEquals(1, after.active?.totalSets)
        assertEquals(before.rest?.id, after.rest?.id)
        assertEquals(before.rest?.deadlineElapsed, after.rest?.deadlineElapsed)
        compose.onNodeWithText("המשך ברקע").performScrollTo().performClick()
    }

    @Test fun daysStayVisibleAndNextExerciseOpensAfterFinalSet() {
        runBlocking { repo.update { it.copy(routines = listOf(it.routines.first().copy(exercises = listOf(
            exercise.copy(sets = 1, restSeconds = 0), exercise.copy(id = "second", name = "חתירה", target = 9, targetMax = 9, restSeconds = 0)
        )))) } }
        compose.onNodeWithContentDescription("ראשון,", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("סיימתי סט 1").onFirst().assertIsDisplayed().performClick()
        // The next card is precomposed below the viewport while the completion message is shown.
        // Wait for the real automatic scroll, rather than merely the node's existence.
        val nextValue = hasText("9") and hasAnyAncestor(hasTestTag("exercise-card-second"))
        compose.waitUntil(5000) { runCatching { compose.onNode(nextValue).assertIsDisplayed() }.isSuccess }
        compose.onNode(nextValue).assertIsDisplayed()
        compose.onNodeWithContentDescription("ראשון,", substring = true).assertIsDisplayed()
        val active = runBlocking { repo.current().active!! }
        assertTrue(active.exercises.first().complete)
        assertEquals(0, active.exercises.last().sets.size)
        assertNull(runBlocking { repo.current().rest })
    }

    @Test fun programNameAndFreeChoicePersistWithoutStartingWorkout() {
        compose.onNodeWithContentDescription("ניהול התוכנית").performClick()
        compose.onNodeWithContentDescription("שם התוכנית").performClick()
        compose.onNodeWithText("שם התוכנית שלך").performTextInput("התוכנית שלי ABC")
        compose.onNodeWithText("שמור", useUnmergedTree = true).performClick()
        compose.onNodeWithText("שנה").performClick()
        compose.onNodeWithText("בחירה חופשית · בוחר בכל פעם").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().preferences.flexibleOrder == FlexibleOrder.FREE } }
        compose.onNodeWithText("סיימתי").performScrollTo().performClick()
        compose.onNodeWithContentDescription("חזרה").performClick()
        compose.onNodeWithText("מה מתאמנים היום?").assertIsDisplayed()
        assertNull(runBlocking { repo.current().active })
        compose.onNodeWithText("בחר אימון").performClick()
        compose.onNodeWithText("אימון בדיקה").performClick()
        compose.onNodeWithText("סיימתי סט 1").performScrollTo().assertIsDisplayed()
        assertNull(runBlocking { repo.current().active })
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5000) { compose.onAllNodesWithText("השבוע שלך").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("התוכנית שלי ABC").assertDoesNotExist()
        compose.onNodeWithContentDescription("ניהול התוכנית").performClick()
        compose.onNodeWithText("התוכנית שלי ABC").assertIsDisplayed()
        assertEquals("התוכנית שלי ABC", runBlocking { repo.current().preferences.programName })
        assertEquals(FlexibleOrder.FREE, runBlocking { repo.current().preferences.flexibleOrder })
    }

    @Test fun calendarShowsPlannedAndActualWorkoutAndBrowsingIsReadOnly() {
        val date = weekStart(java.time.LocalDate.now())
        val planned = fixture().copy(preferences = fixture().preferences.copy(mode = PlanMode.SCHEDULED), routines = fixture().routines.map { it.copy(days = setOf(0, 6), shortLabel = "A") })
        val clock = app.clock().copy(date = date)
        val saved = TrainingEngine.finish(TrainingEngine.completeExercise(planned, "routine", "exercise", 0, 12, 20.0, "full", "calendar", clock), clock)
        runBlocking { repo.update { saved } }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("ראשון, אימון בדיקה הושלם").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("ראשון, אימון בדיקה הושלם").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("שבת, מתוכנן אימון בדיקה").assertIsDisplayed().performClick()
        compose.onNodeWithText("שבת · מתוכנן: אימון בדיקה").assertExists()
        assertEquals(saved.history, runBlocking { repo.current().history })
        assertNull(runBlocking { repo.current().active })
        compose.onNodeWithContentDescription("היסטוריה").performClick()
        compose.onNodeWithText("הדרך שעשית").assertExists()
    }

    @Test fun correctionFromExerciseCardUpdatesExactSavedSet() {
        runBlocking { app.coordinator.execute { state ->
            state.copy(routines = listOf(state.routines.first().copy(exercises = listOf(exercise.copy(restSeconds = 0)))))
        } }
        compose.onNodeWithText("סיימתי סט 1").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().active?.totalSets == 1 } }
        compose.onNodeWithContentDescription("פעולות תרגיל").performScrollTo().performClick()
        compose.onNodeWithText("תיקון הסט האחרון").performClick()
        compose.onNode(hasText("חזרות") and hasSetTextAction()).performTextReplacement("8")
        compose.onNodeWithText("משקל בק״ג").performTextReplacement("25.5")
        compose.onNodeWithText("שמור תיקון").performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().active?.volume == 204.0 } }
        assertEquals(1, runBlocking { repo.current().active!!.totalSets })
        assertNull(runBlocking { repo.current().rest })
    }

    @Test fun englishOnboardingCreatesCustomRoutineWithoutStartingSession() {
        runBlocking { app.coordinator.execute { AppState(preferences = Preferences(notificationsAsked = true)) } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("English").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("English").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Build my own").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Build my own").performScrollTo().performClick()
        compose.onNodeWithText("Let's begin").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Routine details").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Routine name").performTextInput("My mixed workout")
        compose.onNodeWithText("Calendar label").performTextInput("M")
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { runBlocking { repo.current().routines.size == 1 } }
        compose.onNodeWithText("Add from library").performScrollTo().assertIsDisplayed()
        assertNull(runBlocking { repo.current().active })
        assertTrue(runBlocking { repo.current().history.isEmpty() })
    }

    @Test fun resumingDoesNotDismissCompletedRestNotification() = runBlocking {
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        app.coordinator.execute { TrainingEngine.logSet(it, "routine", "exercise", 0, 12, 20.0, "return", "s", app.clock()) }
        device.pressHome()
        val notifications = app.getSystemService(NotificationManager::class.java)
        withTimeout(15_000) { while (notifications.activeNotifications.none { it.id == Notifications.COMPLETE }) delay(100) }
        app.coordinator.execute(refresh = true) { it }
        assertTrue(notifications.activeNotifications.any { it.id == Notifications.COMPLETE })
        assertEquals(1, repo.current().active!!.totalSets)
        app.coordinator.execute { TrainingEngine.skipRest(it, "return", app.clock()) }
        assertTrue(notifications.activeNotifications.none { it.id == Notifications.COMPLETE })
    }

    @Test fun staleCompletionActionCannotLogWhileRestWasExtended() = runBlocking {
        val first = app.coordinator.execute { TrainingEngine.logSet(it, "routine", "exercise", 0, 12, 20.0, "extend", "s", app.clock()) }.state
        app.coordinator.execute { TrainingEngine.adjustRest(it, first.rest!!.id, 120, app.clock()) }
        Notifications.action(app, "next", first.rest!!.id).send()
        delay(500)
        assertEquals(1, repo.current().active!!.totalSets)
        assertTrue(repo.current().rest!!.remainingMs(app.clock()) > 100_000)
    }
    @Test fun inactiveReceiverClosesPausedWorkoutAndIgnoresOldSession() = runBlocking {
        val now = app.clock()
        // A freshly booted emulator has less than 20 minutes of uptime. Model a
        // previous boot instead of persisting an impossible negative elapsed time.
        val old = now.copy(wall = now.wall - TrainingEngine.IDLE_MS - 1000, elapsed = 0, boot = now.boot - 1)
        val first = TrainingEngine.pauseRest(TrainingEngine.logSet(fixture(), "routine", "exercise", 0, 12, 20.0, "idle-set", "idle-session", old), "idle-set", old)
        repo.update { first.validate() }
        device.pressHome()
        app.sendBroadcast(Intent(app, IdleReceiver::class.java).putExtra("sessionId", "idle-session"))
        withTimeout(5000) { while (repo.current().active != null) delay(50) }
        assertEquals(1, repo.current().history.first().totalSets)
        assertEquals(old.wall, repo.current().history.first().endedAt)
        assertTrue(repo.current().history.first().autoClosed)
        app.coordinator.execute { TrainingEngine.logSet(it, "routine", "exercise", 0, 10, 25.0, "new-idle-set", "new-idle-session", now) }
        app.sendBroadcast(Intent(app, IdleReceiver::class.java).putExtra("sessionId", "idle-session"))
        delay(400)
        assertEquals("new-idle-session", repo.current().active!!.id)
        assertEquals(1, repo.current().history.size)
    }

    @Test fun nextSetNotificationUsesUpdatedWorkingWeight() = runBlocking {
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        app.coordinator.execute { TrainingEngine.logSet(it, "routine", "exercise", 0, 12, 20.0, "weight-set", "weight-session", app.clock()) }
        app.coordinator.execute { TrainingEngine.setWorkingWeight(it, "routine", "exercise", 32.5, app.clock()) }
        device.pressHome()
        withTimeout(15_000) { while (repo.current().rest?.alerted != true) delay(100) }
        Notifications.action(app, "next", "weight-set").send()
        withTimeout(5000) { while (repo.current().active?.totalSets != 2) delay(50) }
        val sets = repo.current().active!!.exercises.first().sets
        assertEquals(20.0, sets.first().weight, 0.0)
        assertEquals(32.5, sets.last().weight, 0.0)
    }

}
