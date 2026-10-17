package com.hananel.workout.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hananel.workout.R
import com.hananel.workout.data.*
import com.hananel.workout.domain.*
import com.hananel.workout.timer.Notifications
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable fun rememberClock(): ClockReading {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reading by produceState(context.clock(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { value = context.clock(); delay(250) } }
    }
    return reading
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WorkoutApp(vm: WorkoutViewModel, timerRequest: Int = 0) {
    val state by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val importing by vm.pendingImport.collectAsStateWithLifecycle()
    val prefs = state?.preferences ?: Preferences()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var page by rememberSaveable { mutableStateOf("home") }
    var settingsSection by rememberSaveable { mutableStateOf<String?>(null) }
    var editingRoutineId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    val screenState = rememberSaveableStateHolder()
    var routineId by rememberSaveable { mutableStateOf("") }
    var editorReturn by rememberSaveable { mutableStateOf("home") }
    var observedActive by remember { mutableStateOf<String?>(null) }
    var showTimer by rememberSaveable { mutableStateOf(false) }
    var finishDialog by rememberSaveable { mutableStateOf(false) }
    var showNotificationExplanation by rememberSaveable { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    val snack = remember { SnackbarHostState() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.events.trySend(if (prefs.isEnglish()) "Timer works in the app. Enable notifications in Settings for background alerts." else "הטיימר עובד באפליקציה. לקבלת התראה ברקע אפשר לאפשר התראות בהגדרות.")
    }
    LaunchedEffect(vm) { for (event in vm.events) snack.showSnackbar(event) }
    LaunchedEffect(prefs.language, state != null) {
        if (state != null && Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(android.app.LocaleManager::class.java)
            val requested = if (prefs.language == "system") android.os.LocaleList.getEmptyLocaleList() else android.os.LocaleList.forLanguageTags(prefs.language)
            if (manager.applicationLocales != requested) manager.applicationLocales = requested
        }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            today = LocalDate.now()
            vm.refreshServices()
            while (true) { delay(30_000); today = LocalDate.now(); vm.recover() }
        }
    }
    LaunchedEffect(state != null, prefs.notificationsAsked) {
        if (state != null && !prefs.notificationsAsked && Build.VERSION.SDK_INT >= 33 && !Notifications.allowed(context)) showNotificationExplanation = true
    }
    LaunchedEffect(state?.history?.firstOrNull()?.id) {
        state?.history?.firstOrNull()?.takeIf { it.autoClosed && observedActive == it.id }?.let {
            snack.showSnackbar(if (prefs.isEnglish()) "Workout saved after 20 idle minutes" else "האימון נשמר אחרי 20 דקות ללא פעילות")
        }
    }
    LaunchedEffect(timerRequest) { if (timerRequest > 0) showTimer = true }
    LaunchedEffect(state?.active?.id) {
        if (observedActive != null && state?.active == null) routineId = ""
        if (state?.active == null) { finishDialog = false; showTimer = false }
        observedActive = state?.active?.id
    }
    LaunchedEffect(prefs.mode, prefs.flexibleOrder) { routineId = "" }
    LaunchedEffect(state?.rest?.id) {
        if (state?.rest != null) {
            showTimer = true
            if (!prefs.notificationsAsked && !Notifications.allowed(context)) showNotificationExplanation = true
        }
    }
    fun goBack() {
        if (page == "settings" && settingsSection != null) settingsSection = null
        else page = when (page) { "catalog" -> "editor"; "editor" -> editorReturn; else -> "home" }
    }
    BackHandler(page != "home") { goBack() }
    BackHandler(editingRoutineId != null) { if (!busy) editingRoutineId = null }
    WorkoutTheme(prefs) {
        Backdrop {
            if (editingRoutineId == null) screenState.SaveableStateProvider("main-screens") {
            Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground,
                topBar = {
                    if (prefs.onboarded) TopAppBar(title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (page == "home") Image(painterResource(R.drawable.workout_logo), null, Modifier.size(34.dp))
                            Text(when (page) {
                                "program" -> tr("ניהול התוכנית", "Manage program")
                                "editor" -> tr("עריכת אימון", "Edit workout")
                                "catalog" -> tr("מאגר התרגילים", "Exercise library")
                                "history" -> tr("היסטוריה", "History")
                                "settings" -> settingsTitle(settingsSection)
                                else -> "WORKOUT"
                            }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        }
                    }, navigationIcon = {
                        if (page != "home") IconButton({ goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("חזרה", "Back")) }
                    }, actions = {
                        if (page == "home") {
                            IconButton({ page = "program" }) { Icon(Icons.Default.FitnessCenter, tr("ניהול התוכנית", "Manage program"), tint = MaterialTheme.colorScheme.primary) }
                            IconButton({ page = "history" }) { Icon(Icons.Default.History, tr("היסטוריה", "History"), tint = muted()) }
                            IconButton({ settingsSection = null; page = "settings" }) { Icon(Icons.Default.Tune, tr("הגדרות", "Settings"), tint = muted()) }
                        }
                    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
                },
                bottomBar = {
                    if (prefs.onboarded) Column(Modifier.navigationBarsPadding()) {
                        if (page != "home" && !showTimer && !showNotificationExplanation) state?.rest?.let { RestBanner(it, onClick = { showTimer = true }) }
                        if (page == "home" && !showTimer) state?.active?.takeIf { it.totalSets > 0 }?.let { active ->
                            val reading = rememberClock()
                            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .96f), tonalElevation = 2.dp) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val rest = state?.rest
                                    Column(Modifier.weight(1f).heightIn(min = 48.dp).then(if (rest != null) Modifier.clickable { showTimer = true } else Modifier), verticalArrangement = Arrangement.Center) {
                                        Text(if (rest != null) (if (rest.pausedMs != null) tr("מושהה · ", "Paused · ") else tr("מנוחה · ", "Rest · ")) + com.hananel.workout.timer.formatTime(rest.seconds(reading))
                                            else if (active.complete) tr("האימון הושלם ✓", "Workout complete ✓") else tr("באימון", "Workout in progress"), color = if (rest != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                        Text("${com.hananel.workout.timer.formatTime((active.durationMs(reading.wall) / 1000).toInt())} · ${active.totalSets}/${active.plannedSets}", style = MaterialTheme.typography.labelMedium, color = muted())
                                    }
                                    Button({ finishDialog = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("סיים ושמור", "Finish & save"))
                                    }
                                }
                            }
                        }
                    }
                }, snackbarHost = { SnackbarHost(snack) }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    val current = state
                    if (error != null) Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(error ?: "")
                        Button(vm::load) { Text(tr("נסה שוב", "Try again")) }
                    } else if (current == null) CircularProgressIndicator(Modifier.padding(40.dp))
                    else Box(Modifier.widthIn(max = 680.dp).fillMaxSize()) {
                        if (!prefs.onboarded) WelcomeScreen(prefs, busy, onLanguage = { language -> vm.act { it.copy(preferences = it.preferences.copy(language = language)) } }) { mode, abc, empty, order, name ->
                            vm.act(after = {
                                if (empty) { editorReturn = "home"; routineId = ""; page = "editor" }
                                else if (mode == PlanMode.SCHEDULED) page = "program"
                            }) { it.copy(preferences = it.preferences.copy(onboarded = true, mode = mode, flexibleOrder = order, programName = name), routines = if (empty) emptyList() else Catalog.starter(abc, prefs.isEnglish())) }
                        } else when (page) {
                            "home", "workout" -> HomeScreen(current, today, routineId, busy, vm, onSelect = { routineId = it },
                                onCreate = { editorReturn = "home"; routineId = ""; page = "editor" }, onEdit = { editorReturn = "home"; routineId = it; page = "editor" },
                                onFinish = { finishDialog = true }, onTimer = { showTimer = true }, onProgram = { page = "program" }, onExerciseEdit = { r, e -> editingRoutineId = r; editingExerciseId = e })
                            "program" -> ProgramScreen(current, busy, vm, onEdit = { editorReturn = "program"; routineId = it; page = "editor" }, onCreate = { editorReturn = "program"; routineId = ""; page = "editor" }, onTrain = { routineId = it; page = "home" })
                            "editor" -> RoutineEditor(current, routineId, busy, vm, onCreated = { routineId = it }, onCatalog = { page = "catalog" }, onDeleted = { routineId = ""; page = editorReturn }, onDone = { page = "home" }, onExerciseEdit = { editingRoutineId = routineId; editingExerciseId = it })
                            "catalog" -> CatalogScreen(current, routineId, busy, vm)
                            "history" -> HistoryScreen(current, today, vm)
                            "settings" -> SettingsScreen(current, vm, settingsSection, onSection = { settingsSection = it })
                        }
                    }
                }
            }
            }
            val editRoutine = state?.routines?.find { it.id == editingRoutineId }
            if (editRoutine != null) {
                val activeLog = state?.active?.exercises?.find { it.exercise.id == editingExerciseId }
                val original = activeLog?.exercise ?: editRoutine.exercises.find { it.id == editingExerciseId }
                ExerciseEditor(original, busy, { editingRoutineId = null }, loggedSets = activeLog?.sets?.size ?: 0) { exercise ->
                    vm.saveExercise(editRoutine.id, exercise) { editingRoutineId = null }
                }
            }
        }
        if (showTimer && state?.rest != null && !showNotificationExplanation && editingRoutineId == null) {
            TimerSheet(state!!, vm, busy, onDismiss = { showTimer = false })
        }
        if (finishDialog && state?.active != null) {
            val session = state!!.active!!
            val savedText = tr("האימון נשמר בהיסטוריה", "Workout saved to history")
            AlertDialog(onDismissRequest = { if (!busy) finishDialog = false }, title = { Text(tr("לסיים את האימון?", "Finish workout?")) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(session.label(LocalEnglish.current), fontWeight = FontWeight.Bold)
                    Text(tr("${session.totalSets} מתוך ${session.plannedSets} סטים בוצעו", "${session.totalSets} of ${session.plannedSets} sets logged"))
                    Text(if (session.totalSets == 0) tr("לא בוצעו סטים. האימון הריק ייסגר.", "No sets logged. This empty workout will close.")
                        else if (session.complete) tr("האימון יישמר כהושלם ביומן ובהתקדמות התוכנית.", "Your completed workout will be saved to the calendar and program progress.")
                        else tr("הביצוע יישמר כאימון חלקי. ההתקדמות השבועית תישאר גלויה.", "This will be saved as a partial workout. Weekly progress stays visible."))
                } },
                confirmButton = { Button(enabled = !busy, onClick = { vm.finish(savedText) { finishDialog = false; routineId = ""; page = "home" } }) { Text(tr("סיים ושמור", "Finish & save")) } },
                dismissButton = { TextButton({ finishDialog = false }) { Text(tr("המשך אימון", "Keep training")) } })
        }
        if (showNotificationExplanation) AlertDialog(onDismissRequest = { showNotificationExplanation = false },
            title = { Text(tr("מכינים את הטיימר לאימון", "Get your timer ready")) },
            text = { Text(tr("אפשר התראות כדי לקבל צליל בסיום המנוחה ולשלוט בטיימר כשהמסך כבוי. אפשר להמשיך גם בלי ההרשאה.", "Allow notifications for rest alerts and timer controls with the screen off. You can also continue without permission.")) },
            confirmButton = { TextButton({
                vm.act { it.copy(preferences = it.preferences.copy(notificationsAsked = true)) }
                showNotificationExplanation = false
                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text(tr("אפשר התראות", "Allow notifications")) } },
            dismissButton = { TextButton({ vm.act { it.copy(preferences = it.preferences.copy(notificationsAsked = true)) }; showNotificationExplanation = false }) { Text(tr("בהמשך", "Later")) } })
        if (importing != null) AlertDialog(onDismissRequest = { vm.pendingImport.value = null }, title = { Text(tr("לשחזר את הגיבוי?", "Restore backup?")) },
            text = { Text(tr("הגיבוי כולל ${importing!!.routines.size} אימונים ו־${importing!!.history.size} רשומות. השחזור יחליף את הנתונים בגרסה החדשה. מומלץ לייצא קודם גיבוי של המצב הנוכחי.", "This backup has ${importing!!.routines.size} routines and ${importing!!.history.size} sessions. Restoring replaces this app's current data. Export a backup first if needed.")) },
            confirmButton = { TextButton(onClick = vm::confirmImport, enabled = !busy) { Text(tr("שחזר", "Restore")) } },
            dismissButton = { TextButton({ vm.pendingImport.value = null }) { Text(tr("ביטול", "Cancel")) } })
    }
}

@Composable private fun WelcomeScreen(prefs: Preferences, busy: Boolean, onLanguage: (String) -> Unit, onDone: (PlanMode, Boolean, Boolean, FlexibleOrder, String) -> Unit) {
    val english = LocalEnglish.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableStateOf(PlanMode.FLEXIBLE) }
    var preset by rememberSaveable { mutableStateOf("body") }
    BackHandler(step > 0) { step-- }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FilterChip(prefs.language == "he", { onLanguage("he") }, enabled = !busy, label = { Text("עברית") })
            Spacer(Modifier.width(8.dp))
            FilterChip(prefs.language == "en", { onLanguage("en") }, enabled = !busy, label = { Text("English") })
        }
        Image(painterResource(R.drawable.workout_logo), null, Modifier.size(56.dp))
        Text(tr("${step + 1} מתוך 2", "${step + 1} of 2"), style = MaterialTheme.typography.labelMedium, color = muted())
        if (step == 0) {
            Heading(tr("איך נוח לך להתאמן?", "How do you like to train?"), tr("נבחר דרך אחת. תמיד אפשר לשנות בתוכנית שלך.", "Choose one approach. You can change it in your program anytime."))
            listOf(PlanMode.FLEXIBLE, PlanMode.SCHEDULED).forEach { option ->
                Panel(tint = if (mode == option) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.selectable(mode == option, role = Role.RadioButton, onClick = { mode = option })) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(mode == option, null)
                        Text(if (option == PlanMode.FLEXIBLE) tr("לפי רצף האימונים", "Follow a workout sequence") else tr("לפי ימים קבועים", "Follow fixed weekdays"), fontWeight = FontWeight.Bold)
                    }
                    Text(if (option == PlanMode.FLEXIBLE) tr("מגיע כשנוח לך. סיימת A? בפעם הבאה יוצע B, גם אם התחיל שבוע חדש.", "Train whenever you can. Finish A, then get B next time, even in a new week.")
                        else tr("משבצים אימונים לימים. היומן יראה מה מתוכנן ומה ביצעת.", "Assign workouts to weekdays. See what is planned and what you actually completed."), color = muted())
                }
            }
            PrimaryButton(tr("המשך", "Continue"), { step = 1 })
        } else {
            Heading(tr("עם מה מתחילים?", "Where shall we start?"), tr("תוכנית מוכנה שאפשר לערוך, או אימון משלך.", "An editable starter plan, or build your own."))
            listOf("body" to tr("לפי חלקי גוף", "Body groups"), "abc" to "A / B / C", "empty" to tr("אבנה בעצמי", "Build my own")).forEach { (key, label) ->
                FilterChip(preset == key, { preset = key }, label = { Text(label) }, leadingIcon = { if (preset == key) Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp))
            }
            if (preset != "empty") {
                val preview = remember(preset) { Catalog.starter(preset == "abc") }
                Panel {
                    preview.forEach { routine -> Text((if (preset == "abc") "${routine.shortLabel} · " else "") + routine.label(english), style = MaterialTheme.typography.bodyMedium) }
                    Text(tr("כל התרגילים מוכנים, כולל סטים ומנוחה. המשקל לבחירתך.", "Exercises, sets and rest are ready. Choose your working weights."), style = MaterialTheme.typography.bodySmall, color = muted())
                }
                if (mode == PlanMode.SCHEDULED) Text(tr("במסך הבא נבדוק את שיבוץ הימים.", "Review weekday assignments next."), color = muted())
            } else Text(tr("אפשר להוסיף מהמאגר של 41 תרגילים או ליצור תרגיל אישי.", "Add from the 41-exercise library or create custom exercises."), color = muted())
            PrimaryButton(tr("בוא נתחיל", "Let's begin"), { onDone(mode, preset == "abc", preset == "empty", FlexibleOrder.ORDERED, "") }, enabled = !busy)
            TextButton({ step = 0 }) { Text(tr("חזרה", "Back")) }
        }
    }
}
