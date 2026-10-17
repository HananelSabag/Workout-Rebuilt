package com.hananel.workout.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.hananel.workout.domain.*
import java.util.Locale

val LocalEnglish = staticCompositionLocalOf { false }
val LocalDark = staticCompositionLocalOf { true }
val LocalGlass = staticCompositionLocalOf { true }
val Blue = Color(0xFF76B7FF)
val Violet = Color(0xFF87C6DE)
val Green = Color(0xFF5BDBB0)
val Amber = Color(0xFFFFCA80)
@Composable fun tr(he: String, en: String) = if (LocalEnglish.current) en else he
@Composable fun muted() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable fun WorkoutTheme(prefs: Preferences, content: @Composable () -> Unit) {
    val dark = prefs.theme == "dark" || (prefs.theme == "system" && isSystemInDarkTheme())
    val view = androidx.compose.ui.platform.LocalView.current
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    SideEffect {
        activity?.let {
            androidx.core.view.WindowCompat.getInsetsController(it.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val colors = if (dark) darkColorScheme(primary = Blue, secondary = Violet, tertiary = Green,
        background = Color(0xFF0C131E), onBackground = Color(0xFFF4F8FD), surface = Color(0xFF192738),
        surfaceVariant = Color(0xFF25364A), onSurface = Color(0xFFF4F8FD), onSurfaceVariant = Color(0xFFAFBED0),
        onPrimary = Color(0xFF08203A), primaryContainer = Color(0xFF203C59), onPrimaryContainer = Color(0xFFD5E9FF),
        secondaryContainer = Color(0xFF263F57), onSecondaryContainer = Color(0xFFDAEBFF), onSecondary = Color(0xFF092B38),
        tertiaryContainer = Color(0xFF203E36), onTertiaryContainer = Color(0xFFB7F5DF), onTertiary = Color(0xFF052D21),
        surfaceTint = Blue, inverseSurface = Color(0xFFDFE8F3), inverseOnSurface = Color(0xFF172B40), outline = Color(0xFF4F657D))
    else lightColorScheme(primary = Color(0xFF1763CB), secondary = Color(0xFF286E88), tertiary = Color(0xFF087C59),
        background = Color(0xFFF1F5FA), onBackground = Color(0xFF15263A), surface = Color.White,
        surfaceVariant = Color(0xFFE8EFF7), onSurface = Color(0xFF15263A), onSurfaceVariant = Color(0xFF52667D),
        onPrimary = Color.White, primaryContainer = Color(0xFFDDECFD), onPrimaryContainer = Color(0xFF124574),
        secondaryContainer = Color(0xFFE3EFFC), onSecondaryContainer = Color(0xFF164D80), onSecondary = Color.White,
        tertiaryContainer = Color(0xFFE3F4EB), onTertiaryContainer = Color(0xFF095D42), onTertiary = Color.White,
        surfaceTint = Color(0xFF1763CB), inverseSurface = Color(0xFF20354C), inverseOnSurface = Color(0xFFF2F7FD), outline = Color(0xFFA5B8CD))
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val english = prefs.isEnglish(configuration.locales[0].language)
    CompositionLocalProvider(LocalEnglish provides english, LocalDark provides dark, LocalGlass provides true,
        LocalLayoutDirection provides if (english) LayoutDirection.Ltr else LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

@Composable fun Backdrop(content: @Composable BoxScope.() -> Unit) {
    val dark = LocalDark.current
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(if (dark)
        listOf(Color(0xFF0D1827), Color(0xFF0D1520), Color(0xFF101D26))
        else listOf(Color(0xFFECF3FC), Color(0xFFF7F9FC), Color(0xFFEEF6F6))))) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.radialGradient(listOf(Color(0xFF488FE5).copy(alpha = if (dark) .12f else .11f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width, size.height * .18f), radius = size.width))
        }
        content()
    }
}

@Composable fun Panel(modifier: Modifier = Modifier, tint: Color = Color.Transparent, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val dark = LocalDark.current
    val surface = if (dark) Color(0xFF25364B) else Color.White
    Column(modifier.fillMaxWidth().shadow(3.dp, shape, ambientColor = Color(0xFF102D51).copy(alpha = .08f), spotColor = Color(0xFF102D51).copy(alpha = .08f))
        .clip(shape).background(Brush.verticalGradient(listOf(surface.copy(alpha = if (dark) .82f else .96f), surface.copy(alpha = if (dark) .54f else .77f))))
        .background(tint.copy(alpha = if (tint == Color.Transparent) 0f else if (dark) .10f else .045f))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .20f else .98f),
            (if (tint == Color.Transparent) MaterialTheme.colorScheme.outline else tint).copy(alpha = if (dark) .24f else .28f))), shape)
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable fun Heading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = muted())
    }
}

@Composable fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector = Icons.Default.ArrowForward) {
    val shape = RoundedCornerShape(17.dp)
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 54.dp).background(
        Brush.horizontalGradient(if (enabled) listOf(Color(0xFF1666D5), Color(0xFF087CAC)) else listOf(Color(0xFFBEC7DA), Color(0xFFBEC7DA))), shape), enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White, disabledContainerColor = Color.Transparent),
        shape = shape, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Icon(icon, null, Modifier.size(20.dp))
    }
}

@Composable fun Tag(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(9.dp)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = color,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable fun NumberControl(label: String, value: String, decrease: () -> Unit, increase: () -> Unit, modifier: Modifier = Modifier, canDecrease: Boolean = true, canIncrease: Boolean = true, onValueClick: (() -> Unit)? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = muted())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = decrease, enabled = canDecrease, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Remove, tr("הפחת $label", "Decrease $label"), Modifier.size(18.dp)) }
            Box(Modifier.widthIn(min = 40.dp).heightIn(min = 48.dp).then(if (onValueClick == null) Modifier else Modifier.clickable(onClick = onValueClick))
                .semantics { contentDescription = "$label $value" }, contentAlignment = Alignment.Center) {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = increase, enabled = canIncrease, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Add, tr("הוסף $label", "Increase $label"), Modifier.size(18.dp)) }
        }
    }
}

@Composable fun EditableNumberControl(label: String, value: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    NumberControl(label, value.toString(), { onChange((value - step).coerceIn(range)) }, { onChange((value + step).coerceIn(range)) },
        Modifier.fillMaxWidth(), value > range.first, value < range.last, onValueClick = { input = value.toString(); editing = true })
    if (editing) {
        val parsed = input.toIntOrNull()
        val valid = parsed != null && parsed in range
        AlertDialog(onDismissRequest = { editing = false }, title = { Text(label) }, text = {
            OutlinedTextField(input, { input = it.take(4) }, label = { Text(tr("ערך", "Value")) }, singleLine = true, isError = !valid,
                supportingText = { Text("${range.first}–${range.last}") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        }, confirmButton = { TextButton({ onChange(parsed!!); editing = false }, enabled = valid) { Text(tr("עדכן", "Update")) } },
            dismissButton = { TextButton({ editing = false }) { Text(tr("ביטול", "Cancel")) } })
    }
}

fun number(value: Double) = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
/** Isolate numeric expressions so Hebrew does not reverse ranges or signed values. */
fun ltrNumber(value: String) = "\u2066$value\u2069"
@Composable fun Muscle.icon(): ImageVector = when(this) {
    Muscle.CHEST -> Icons.Default.FitnessCenter
    Muscle.BACK -> Icons.Default.AccessibilityNew
    Muscle.SHOULDERS -> Icons.Default.SportsGymnastics
    Muscle.ARMS -> Icons.Default.FitnessCenter
    Muscle.LEGS -> Icons.Default.DirectionsWalk
    Muscle.CORE -> Icons.Default.SelfImprovement
    Muscle.CARDIO -> Icons.Default.DirectionsRun
}
@Composable fun Measure.unit() = when(this) { Measure.REPS -> tr("חזרות", "reps"); Measure.SECONDS -> tr("שניות", "seconds"); Measure.MINUTES -> tr("דקות", "minutes") }
@Composable fun dayName(index: Int, short: Boolean = false): String = if (LocalEnglish.current) (if (short) listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa") else listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"))[index]
    else (if (short) listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳") else listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת"))[index]
