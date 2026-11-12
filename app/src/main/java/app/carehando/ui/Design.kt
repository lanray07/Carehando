package app.carehando.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.carehando.R
import app.carehando.data.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.platform.LocalConfiguration

private val light = lightColorScheme(
    primary = Color(0xFF235E61), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8DF), onPrimaryContainer = Color(0xFF173E41),
    secondary = Color(0xFF565477), secondaryContainer = Color(0xFFEAE3F4),
    onSecondaryContainer = Color(0xFF35324D),
    surface = Color(0xFFF9F7F0), onSurface = Color(0xFF293338),
    surfaceContainer = Color(0xFFF0EFE6), surfaceContainerLow = Color(0xFFFFFCF6),
    surfaceVariant = Color(0xFFE4E9E1), onSurfaceVariant = Color(0xFF414C48),
    outline = Color(0xFF6D7973),
)
private val dark = darkColorScheme(
    primary = Color(0xFFA8D6CF), onPrimary = Color(0xFF103738),
    primaryContainer = Color(0xFF214947), onPrimaryContainer = Color(0xFFC5E8E0),
    secondary = Color(0xFFCEC5E9), secondaryContainer = Color(0xFF413B55),
    onSecondaryContainer = Color(0xFFEDE5FF),
    surface = Color(0xFF151C1B), onSurface = Color(0xFFE6EDE6),
    surfaceContainer = Color(0xFF222A27), surfaceContainerLow = Color(0xFF1B2321),
    surfaceVariant = Color(0xFF353F3B), onSurfaceVariant = Color(0xFFCBD5CE),
    outline = Color(0xFF9AA8A0),
)

@Composable
fun CareTheme(mode: String?, content: @Composable () -> Unit) {
    val colours = if (mode == "dark" || mode != "light" && isSystemInDarkTheme()) dark else light
    val type = Typography()
    MaterialTheme(colours, typography = type.copy(
        headlineLarge = type.headlineLarge.copy(fontFamily = FontFamily.Serif),
        headlineMedium = type.headlineMedium.copy(fontFamily = FontFamily.Serif),
        headlineSmall = type.headlineSmall.copy(fontFamily = FontFamily.Serif),
    ), content = content)
}

@Composable
fun Panel(modifier: Modifier = Modifier, tone: Color = MaterialTheme.colorScheme.surfaceContainerLow,
          content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(tone, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
fun Heading(@StringRes title: Int) = Text(stringResource(title), style = MaterialTheme.typography.titleLarge)

@Composable
fun Note(@StringRes body: Int) = Text(stringResource(body), style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun DateText(date: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}

@Composable
fun TimeText(time: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return LocalTime.parse(time).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
}

@Composable
fun Timestamp(value: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
}

fun Kind.label() = when (this) {
    Kind.TASK -> R.string.task
    Kind.ROUTINE -> R.string.routine
    Kind.APPOINTMENT -> R.string.appointment
    Kind.MEDICATION -> R.string.medication
    Kind.JOURNAL -> R.string.journal
}

fun Outcome.label() = when (this) {
    Outcome.COMPLETED -> R.string.completed
    Outcome.TAKEN -> R.string.taken
    Outcome.SKIPPED -> R.string.skipped
    Outcome.UNCONFIRMED -> R.string.unconfirmed
}

fun HandoverSection.label() = when (this) {
    HandoverSection.COMPLETED -> R.string.completed_activities
    HandoverSection.OUTSTANDING -> R.string.outstanding
    HandoverSection.APPOINTMENTS -> R.string.appointments
    HandoverSection.MEDICATION -> R.string.medication
    HandoverSection.OBSERVATIONS -> R.string.observations
}

fun RevisionKind.label() = when (this) {
    RevisionKind.ORIGINAL -> R.string.saved_handover
    RevisionKind.CORRECTION -> R.string.correction
    RevisionKind.ACKNOWLEDGEMENT -> R.string.acknowledgement
}
