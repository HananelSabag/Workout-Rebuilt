package com.hananel.workout

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.hananel.workout.data.clock
import com.hananel.workout.domain.*
import com.hananel.workout.timer.RestService
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Android product flows; screenshots use demo data and are never bundled into the release APK. */
@RunWith(AndroidJUnit4::class)
class ReviewVisualTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as WorkoutApplication
    private val device get() = UiDevice.getInstance(instrumentation)
    private fun fixture(): AppState {
        fun e(id: String, he: String, en: String, muscle: Muscle, weight: Double) = Exercise(id, name = he, nameEn = en, muscle = muscle, sets = 2, target = 10, targetMax = 12, restSeconds = 180, weight = weight)
        val routines = listOf(
            Routine("A", "חזה ויד אחורית", listOf(e("a1", "לחיצת חזה", "Chest press", Muscle.CHEST, 25.0), e("a2", "פשיטת מרפקים", "Triceps extension", Muscle.ARMS, 12.5)), setOf(1), "Chest & triceps", false, "A"),
            Routine("B", "גב ויד קדמית", listOf(e("b1", "משיכת פולי עליון", "Lat pulldown", Muscle.BACK, 35.0)), setOf(3), "Back & biceps", false, "B"),
            Routine("C", "רגליים וכתפיים", listOf(e("c1", "סקוואט", "Squat", Muscle.LEGS, 40.0), e("c2", "לחיצת כתפיים", "Shoulder press", Muscle.SHOULDERS, 15.0)), setOf(5), "Legs & shoulders", false, "C"))
        var state = AppState(preferences = Preferences(onboarded = true, notificationsAsked = true, appearanceVersion = 2, programName = "התוכנית שלי ABC"), routines = routines)
        routines.take(2).forEachIndexed { index, routine ->
            val date = weekStart(app.clock().date).plusDays(if (index == 0) 1 else 3)
            val reading = app.clock().copy(date = date)
            routine.exercises.forEach { ex -> state = TrainingEngine.completeExercise(state, routine.id, ex.id, 0, 10, ex.weight, "past-${ex.id}", "past-${routine.id}", reading) }
            state = TrainingEngine.finish(state, reading)
        }
        return state.validate()
    }
    private fun replace(state: AppState) { runBlocking { app.coordinator.execute { state } }; compose.waitForIdle() }
    private fun capture(name: String) {
        compose.waitForIdle(); instrumentation.waitForIdleSync(); device.waitForIdle()
        device.executeShellCommand("mkdir -p /sdcard/Pictures/Workout-3.5")
        device.executeShellCommand("screencap -p /sdcard/Pictures/Workout-3.5/$name.png")
    }
    @Before fun setup() {
        app.stopService(Intent(app, RestService::class.java))
        replace(fixture())
        compose.waitUntil(10_000) { compose.onAllNodesWithText("סקוואט").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() { device.executeShellCommand("settings put system font_scale 1.0"); app.stopService(Intent(app, RestService::class.java)) }

    @Test fun focusedHomeWeightAndSeparateProgram() {
        compose.onNodeWithText("התוכנית שלי ABC").assertDoesNotExist()
        compose.onNodeWithContentDescription("ניהול התוכנית").assertIsDisplayed()
        compose.onNodeWithTag("exercise-card-c1").assertIsDisplayed()
        capture("home-light-he")
        compose.onAllNodesWithContentDescription("משקל עבודה").onFirst().performClick()
        compose.onNodeWithText("ק״ג").performTextReplacement("42.5")
        capture("working-weight-he")
        compose.onNodeWithText("שמור").performClick()
        compose.waitUntil(5000) { runBlocking { app.repository.current().routines.last().exercises.first().weight == 42.5 } }
        assertNull(runBlocking { app.repository.current().active })
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5000) { compose.onAllNodesWithText("42.5 ק״ג").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("ניהול התוכנית").performClick()
        compose.onNodeWithText("התוכנית שלי ABC").assertIsDisplayed()
        capture("program-he")
        compose.onNodeWithText("שנה").performClick()
        compose.onNodeWithText("קבוע · לפי ימים בשבוע").performScrollTo().assertIsDisplayed()
        capture("plan-mode-he")
    }

    @Test fun cardEditingLibraryAndCustomExerciseDuringWorkout() {
        compose.onAllNodesWithText("סיימתי סט 1").onFirst().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("השהה מנוחה").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("השהה מנוחה").performClick()
        capture("timer-he")
        compose.onNodeWithText("המשך ברקע").performClick()
        compose.onNodeWithText("סיים ושמור").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("פעולות תרגיל").onFirst().performClick()
        capture("exercise-menu-he")
        compose.onNodeWithText("ערוך תרגיל").performClick()
        compose.onNodeWithText("שם התרגיל").performTextReplacement("סקוואט מוט")
        compose.onNodeWithText("משקל בק״ג").performTextReplacement("45")
        capture("edit-exercise-he")
        compose.onNodeWithText("שמור תרגיל").assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitUntil(5000) { runBlocking { app.repository.current().active?.exercises?.first()?.exercise?.weight == 45.0 } }
        assertEquals(40.0, runBlocking { app.repository.current().active!!.exercises.first().sets.first().weight }, 0.0)
        compose.onNodeWithContentDescription("הוסף תרגיל").performClick()
        capture("add-exercise-he")
        compose.onNodeWithText("מהמאגר · 41 תרגילים").performClick()
        compose.onNodeWithText("חיפוש תרגיל או ציוד").performTextInput("plank")
        compose.onNodeWithText("הוסף", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { app.repository.current().active!!.exercises.size == 3 } }
        capture("library-he")
        compose.onNodeWithText("סיימתי להוסיף").performClick()
        compose.onNodeWithContentDescription("הוסף תרגיל").performClick()
        compose.onNodeWithText("תרגיל אישי").performClick()
        compose.onNodeWithText("שם התרגיל").performTextInput("תרגיל אישי לבדיקה")
        compose.onNodeWithText("שמור תרגיל").assertIsDisplayed().assertIsEnabled().performClick()
        capture("custom-after-save-he")
        compose.waitUntil(5000) { runBlocking { app.repository.current().active!!.exercises.size == 4 } }
        assertEquals(1, runBlocking { app.repository.current().active!!.totalSets })
        compose.onNodeWithText("סיים ושמור").performClick()
        capture("finish-workout-he")
        compose.onNode(hasText("סיים ושמור") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5000) { runBlocking { app.repository.current().active == null } }
        assertEquals(1, runBlocking { app.repository.current().history.first().totalSets })
    }

    @Test fun completionDarkEnglishAndLargeFont() {
        val sample = fixture()
        replace(sample.copy(routines = sample.routines.map { r -> if (r.id == "C") r.copy(exercises = r.exercises.map { it.copy(sets = 1, restSeconds = 0) }) else r }))
        compose.onAllNodesWithText("סיימתי סט 1").onFirst().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("תרגיל הושלם · 1/1 סטים").fetchSemanticsNodes().isNotEmpty() }
        capture("exercise-complete-he")
        compose.onNodeWithContentDescription("הגדרות").performClick()
        capture("settings-home-he")
        compose.onNodeWithText("מראה ושפה").performClick()
        compose.onNodeWithText("כהה").performClick()
        compose.waitUntil(5000) { runBlocking { app.repository.current().preferences.theme == "dark" } }
        compose.onNodeWithText("כהה").assertIsSelected()
        compose.onNodeWithContentDescription("חזרה").performClick()
        compose.onNodeWithContentDescription("חזרה").performClick()
        capture("home-dark-he")
        device.executeShellCommand("settings put system font_scale 1.6")
        compose.waitUntil(10_000) { compose.activity.resources.configuration.fontScale > 1.5f }
        compose.onNodeWithText("סיים ושמור").assertIsDisplayed()
        compose.onNodeWithText("סיימתי סט 1").performScrollTo().assertIsDisplayed()
        capture("large-font-he")
        device.executeShellCommand("settings put system font_scale 1.0")
        compose.waitUntil(10_000) { compose.activity.resources.configuration.fontScale < 1.1f }
        compose.onNodeWithContentDescription("הגדרות").performClick()
        compose.onNodeWithText("מראה ושפה").performClick()
        compose.onNodeWithText("English").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Appearance & language").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Finish & save").assertIsDisplayed()
        capture("home-en")
    }

    @Test fun focusedCardsSettingsBackAndLargeEditorPreserveData() {
        val before = runBlocking { app.repository.current() }
        compose.onNodeWithText("לחיצת כתפיים").performScrollTo().performClick()
        compose.onNodeWithTag("exercise-card-c1").onChildren().filterToOne(hasText("סיימתי סט 1")).assertDoesNotExist()
        compose.onNodeWithContentDescription("הגדרות").performClick()
        compose.onNodeWithText("מראה ושפה").assertIsDisplayed()
        compose.onNodeWithText("אימון והתראות").assertIsDisplayed()
        compose.onNodeWithText("תזכורות אימון").assertIsDisplayed()
        compose.onNodeWithText("גיבוי ונתונים").assertIsDisplayed().performClick()
        compose.onNodeWithText("ייצא גיבוי").assertIsDisplayed()
        capture("backup-he")
        compose.onNodeWithContentDescription("חזרה").performClick()
        compose.onNodeWithText("תזכורות אימון").performClick()
        compose.onNodeWithText("הוסף תזכורת").performClick()
        compose.onNodeWithText("18:00").assertIsDisplayed()
        capture("reminder-he")
        compose.onNodeWithText("ביטול").performClick()
        compose.onNodeWithContentDescription("חזרה").performClick()
        compose.onNodeWithContentDescription("חזרה").performClick()
        device.executeShellCommand("wm size 1080x1920")
        device.executeShellCommand("wm density 480")
        device.executeShellCommand("settings put system font_scale 1.6")
        try {
            compose.waitUntil(10_000) { compose.activity.resources.configuration.fontScale > 1.5f }
            compose.onAllNodesWithContentDescription("פעולות תרגיל").onFirst().performClick()
            compose.onNodeWithText("ערוך תרגיל").performClick()
            compose.onNodeWithText("שם התרגיל").performClick().performTextReplacement("סקוואט נוח")
            capture("editor-large-keyboard-he")
            compose.onNodeWithText("שמור תרגיל").assertIsDisplayed().assertIsEnabled()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("סקוואט נוח").assertExists()
            compose.onNodeWithText("משקל בק״ג").performScrollTo().performTextReplacement("47,5")
            compose.onNodeWithText("שמור תרגיל").assertIsDisplayed().performClick()
            compose.waitUntil(5000) { runBlocking { app.repository.current().routines.last().exercises.first().weight == 47.5 } }
            val after = runBlocking { app.repository.current() }
            assertEquals(before.history, after.history)
            assertEquals(before.planProgress, after.planProgress)
            assertNull(after.active)
        } finally {
            device.executeShellCommand("settings put system font_scale 1.0")
            device.executeShellCommand("wm size 1080x2340")
            device.executeShellCommand("wm density 420")
        }
    }

    @Test fun scheduledStarterIsReady() {
        run {
            replace(AppState(preferences = Preferences(appearanceVersion = 2, notificationsAsked = true)))
            compose.onNodeWithText("לפי ימים קבועים").performScrollTo().performClick()
            capture("welcome-mode-he")
            compose.onNodeWithText("המשך").performScrollTo().performClick()
            compose.onNodeWithText("A / B / C").performClick()
            compose.onNodeWithText("בוא נתחיל").performScrollTo().assertIsDisplayed()
            capture("welcome-starter-he")
            compose.onNodeWithText("בוא נתחיל").performClick()
            compose.waitUntil(5000) { runBlocking { app.repository.current().routines.size == 3 } }
            val state = runBlocking { app.repository.current() }
            assertEquals(PlanMode.SCHEDULED, state.preferences.mode)
            assertTrue(state.routines.all { it.days.isNotEmpty() && it.exercises.isNotEmpty() })
            assertNull(state.active)
            compose.onNodeWithText("כל תאריך הוא אימון נפרד").assertIsDisplayed()
        }
    }
}
