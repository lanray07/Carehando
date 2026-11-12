package app.carehando.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.carehando.R
import app.carehando.data.Entry
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppointmentCalendar(entries: List<Entry>, today: LocalDate, close: () -> Unit, edit: (Entry) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val dated = remember(entries) { entries.groupBy { LocalDate.parse(it.day) } }
    val initialDay = dated.keys.filter { it >= today }.minOrNull() ?: today
    var chosenDay by rememberSaveable { mutableStateOf(initialDay.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(chosenDay)
    val month = YearMonth.from(selected)
    val dayNumber = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val offset = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val weeks = (offset + month.lengthOfMonth() + 6) / 7
    val fullDate = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    Editor(R.string.appointment_calendar, close) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton({ chosenDay = month.minusMonths(1).atDay(1).toEpochDay() }) { Text(stringResource(R.string.previous_month)) }
            TextButton({ chosenDay = today.toEpochDay() }) { Text(stringResource(R.string.today)) }
            TextButton({ chosenDay = month.plusMonths(1).atDay(1).toEpochDay() }) { Text(stringResource(R.string.next_month)) }
        }
        val monthPattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMM")
        Text(month.format(DateTimeFormatter.ofPattern(monthPattern, locale)), style = MaterialTheme.typography.titleLarge)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 336.dp) {
                OutlinedButton({
                    DatePickerDialog(context, { _, year, zeroBasedMonth, day ->
                        chosenDay = LocalDate.of(year, zeroBasedMonth + 1, day).toEpochDay()
                    }, selected.year, selected.monthValue - 1, selected.dayOfMonth).show()
                }) { Text(stringResource(R.string.choose_appointment_date)) }
            } else {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(firstDay.plus(column.toLong()).getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                    repeat(weeks) { week ->
                        Row(Modifier.fillMaxWidth()) {
                            repeat(7) { column ->
                                val dayOfMonth = week * 7 + column - offset + 1
                                if (dayOfMonth !in 1..month.lengthOfMonth()) {
                                    Spacer(Modifier.weight(1f).height(48.dp))
                                } else {
                                    val date = month.atDay(dayOfMonth)
                                    val count = dated[date]?.size ?: 0
                                    val label = pluralStringResource(R.plurals.calendar_day_description, count, date.format(fullDate), count)
                                    val isSelected = date == selected
                                    val colors = MaterialTheme.colorScheme
                                    Column(Modifier.weight(1f).height(48.dp)
                                        .background(if (isSelected) colors.primaryContainer else colors.surface, RoundedCornerShape(12.dp))
                                        .selectable(isSelected, role = Role.Button) { chosenDay = date.toEpochDay() }
                                        .semantics { contentDescription = label },
                                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text(dayNumber.format(dayOfMonth), Modifier.clearAndSetSemantics { },
                                            color = if (isSelected) colors.onPrimaryContainer else colors.onSurface)
                                        if (count > 0) Box(Modifier.size(5.dp).background(colors.primary, RoundedCornerShape(50)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Text(selected.format(fullDate), style = MaterialTheme.typography.titleMedium)
        val dayEntries = dated[selected].orEmpty()
        if (dayEntries.isEmpty()) Note(R.string.no_appointments_on_date)
        dayEntries.forEach { entry ->
            TextButton({ edit(entry) }) {
                val time = entry.time.takeIf { it.isNotEmpty() }?.let { TimeText(it) }
                Text(if (time == null) entry.title else "$time · ${entry.title}")
            }
        }
    }
}
