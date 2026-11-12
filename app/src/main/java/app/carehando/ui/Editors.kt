package app.carehando.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.carehando.R
import app.carehando.data.*
import app.carehando.domain.Occurrence
import app.carehando.domain.mask
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun Editor(title: Int, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 620.dp).fillMaxWidth().fillMaxHeight(0.88f),
            shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    TextButton(close) { Text(stringResource(R.string.close)) }
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
    }
}

@Composable
fun Field(value: String, change: (String) -> Unit, label: Int, multiline: Boolean = false) {
    OutlinedTextField(value, { if (it.length <= if (multiline) 4000 else 160) change(it) },
        label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth(),
        singleLine = !multiline, minLines = if (multiline) 3 else 1, maxLines = if (multiline) 10 else 1)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Choices(title: Int, options: List<Pair<T, Int>>, selected: T, change: (T) -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected == value, { change(value) }, { Text(stringResource(label)) })
        }
    }
}

@Composable
fun Toggle(label: Int, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(enabled, change, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun Confirm(title: Int, message: Int, cancel: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = cancel, title = { Heading(title) }, text = { Note(message) },
        confirmButton = { Button(confirm) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(cancel) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun ProfileEditor(profile: Recipient?, close: () -> Unit, save: (Recipient) -> Unit,
                  delete: (Recipient) -> Unit, attachments: @Composable () -> Unit) {
    var name by rememberSaveable { mutableStateOf(profile?.name.orEmpty()) }
    var preferences by rememberSaveable { mutableStateOf(profile?.preferences.orEmpty()) }
    var emergency by rememberSaveable { mutableStateOf(profile?.emergency.orEmpty()) }
    var communicationLanguage by rememberSaveable { mutableStateOf(profile?.communicationLanguage.orEmpty()) }
    var mobilityNotes by rememberSaveable { mutableStateOf(profile?.mobilityNotes.orEmpty()) }
    var dietaryNotes by rememberSaveable { mutableStateOf(profile?.dietaryNotes.orEmpty()) }
    var routineNotes by rememberSaveable { mutableStateOf(profile?.routineNotes.orEmpty()) }
    var healthNotes by rememberSaveable { mutableStateOf(profile?.healthNotes.orEmpty()) }
    var preferredClinic by rememberSaveable { mutableStateOf(profile?.preferredClinic.orEmpty()) }
    var archived by rememberSaveable { mutableStateOf(profile?.archived == true) }
    var confirm by remember { mutableStateOf(false) }
    Editor(R.string.recipient_profile, close) {
        Field(name, { name = it }, R.string.preferred_name)
        Field(preferences, { preferences = it }, R.string.care_preferences, true)
        Field(emergency, { emergency = it }, R.string.emergency_contacts, true)
        if (profile != null) Toggle(R.string.archived, archived) { archived = it }
        Field(communicationLanguage, { communicationLanguage = it }, R.string.preferred_communication_language)
        Field(mobilityNotes, { mobilityNotes = it }, R.string.mobility_notes, true)
        Field(dietaryNotes, { dietaryNotes = it }, R.string.dietary_notes, true)
        Field(routineNotes, { routineNotes = it }, R.string.routine_notes, true)
        Field(healthNotes, { healthNotes = it }, R.string.optional_health_notes, true)
        Field(preferredClinic, { preferredClinic = it }, R.string.preferred_clinic)
        Button({
            save(Recipient(id = profile?.id ?: 0, name = name.trim(), preferences = preferences.trim(),
                emergency = emergency.trim(), archived = archived, communicationLanguage = communicationLanguage.trim(),
                mobilityNotes = mobilityNotes.trim(), dietaryNotes = dietaryNotes.trim(), routineNotes = routineNotes.trim(),
                healthNotes = healthNotes.trim(), preferredClinic = preferredClinic.trim()))
        },
            enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
        attachments()
        if (profile != null) TextButton({ confirm = true }) { Text(stringResource(R.string.delete_profile)) }
    }
    if (confirm && profile != null) Confirm(R.string.delete_profile, R.string.delete_profile_body, { confirm = false }) {
        delete(profile)
    }
}

@Composable
fun CaregiverEditor(carer: Caregiver?, close: () -> Unit, save: (Caregiver) -> Unit, delete: (Caregiver) -> Unit) {
    var name by rememberSaveable { mutableStateOf(carer?.name.orEmpty()) }
    var role by rememberSaveable { mutableStateOf(carer?.role.orEmpty()) }
    var availability by rememberSaveable { mutableStateOf(carer?.availability.orEmpty()) }
    var contactDetails by rememberSaveable { mutableStateOf(carer?.contactDetails.orEmpty()) }
    var preferredLanguage by rememberSaveable { mutableStateOf(carer?.preferredLanguage.orEmpty()) }
    var confirm by remember { mutableStateOf(false) }
    Editor(R.string.caregiver_profile, close) {
        Field(name, { name = it }, R.string.name)
        Field(role, { role = it }, R.string.role)
        Field(availability, { availability = it }, R.string.availability, true)
        Field(contactDetails, { contactDetails = it }, R.string.caregiver_contact_details)
        Field(preferredLanguage, { preferredLanguage = it }, R.string.preferred_communication_language)
        Button({
            save(Caregiver(id = carer?.id ?: 0, name = name.trim(), role = role.trim(),
                availability = availability.trim(), contactDetails = contactDetails.trim(), preferredLanguage = preferredLanguage.trim()))
        }, enabled = name.isNotBlank()) {
            Text(stringResource(R.string.save))
        }
        if (carer != null) TextButton({ confirm = true }) { Text(stringResource(R.string.delete_caregiver)) }
    }
    if (confirm && carer != null) Confirm(R.string.delete_caregiver, R.string.delete_caregiver_body, { confirm = false }) {
        delete(carer)
    }
}

fun Repeat.label() = when (this) {
    Repeat.ONCE -> R.string.once
    Repeat.DAILY -> R.string.daily
    Repeat.WEEKLY -> R.string.weekly
    Repeat.SELECTED_DAYS -> R.string.selected_days
}

@Composable
fun EntryEditor(kind: Kind, recipientId: Long, previous: Entry?, initialTitle: String, carers: List<Caregiver>,
                close: () -> Unit, save: (Entry) -> Unit, delete: (Entry) -> Unit, attachments: @Composable () -> Unit) {
    var title by rememberSaveable { mutableStateOf(previous?.title ?: initialTitle) }
    var details by rememberSaveable { mutableStateOf(previous?.details.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(previous?.day ?: LocalDate.now().toString()) }
    var time by rememberSaveable { mutableStateOf(previous?.time.orEmpty()) }
    var end by rememberSaveable { mutableStateOf(previous?.endDay.orEmpty()) }
    var repeat by rememberSaveable { mutableStateOf(previous?.repeat ?: if (kind == Kind.ROUTINE) Repeat.DAILY else Repeat.ONCE) }
    var weekdays by rememberSaveable { mutableIntStateOf(previous?.weekdays ?: 0) }
    var carer by rememberSaveable { mutableStateOf(previous?.caregiverId) }
    var priority by rememberSaveable { mutableStateOf(previous?.priority == true) }
    var paused by rememberSaveable { mutableStateOf(previous?.paused == true) }
    var location by rememberSaveable { mutableStateOf(previous?.location.orEmpty()) }
    var provider by rememberSaveable { mutableStateOf(previous?.provider.orEmpty()) }
    var dosage by rememberSaveable { mutableStateOf(previous?.dosage.orEmpty()) }
    var checklist by rememberSaveable { mutableStateOf(previous?.checklist.orEmpty()) }
    var followUp by rememberSaveable { mutableStateOf(previous?.appointmentFollowUp.orEmpty()) }
    var newFollowUp by rememberSaveable { mutableStateOf("") }
    val followUpItems = remember(followUp) { decodeAppointmentFollowUp(followUp) }
    var reminder by rememberSaveable { mutableStateOf(previous?.reminder == true) }
    var confirm by remember { mutableStateOf(false) }
    val repeatable = kind == Kind.TASK || kind == Kind.ROUTINE || kind == Kind.MEDICATION
    val validDate = end.isEmpty() || end >= date
    Editor(kind.label(), close) {
        Field(title, { title = it }, if (kind == Kind.MEDICATION) R.string.medication_name else R.string.title)
        DayPicker(R.string.date, date, date) { date = it }
        if (kind != Kind.JOURNAL) TimePicker(time) { time = it }
        if (repeatable) {
            Choices(R.string.schedule, Repeat.entries.map { it to it.label() }, repeat) { repeat = it }
            if (repeat == Repeat.SELECTED_DAYS) WeekdayPicker(weekdays) { weekdays = it }
            if (repeat != Repeat.ONCE) {
                DayPicker(R.string.end_date, end, date, clear = true) { end = it }
                if (!validDate) Note(R.string.end_before_start)
            }
            Toggle(R.string.paused, paused) { paused = it }
        }
        if (kind == Kind.APPOINTMENT) {
            Field(location, { location = it }, R.string.location)
            Field(provider, { provider = it }, R.string.provider)
            Field(checklist, { checklist = it }, R.string.preparation, true)
            Heading(R.string.follow_up)
            followUpItems.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).toggleable(item.complete, role = Role.Checkbox) { complete ->
                        followUp = encodeAppointmentFollowUp(followUpItems.mapIndexed { position, row ->
                            if (position == index) row.copy(complete = complete) else row
                        })
                    }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.complete, onCheckedChange = null)
                        Spacer(Modifier.width(10.dp))
                        Text(item.label)
                    }
                    TextButton({
                        followUp = encodeAppointmentFollowUp(followUpItems.filterIndexed { position, _ -> position != index })
                    }) { Text(stringResource(R.string.clear)) }
                }
            }
            if (followUpItems.size < MAX_FOLLOW_UP_ITEMS) {
                Field(newFollowUp, { newFollowUp = it.take(MAX_FOLLOW_UP_LENGTH) }, R.string.follow_up_item)
                TextButton({
                    followUp = encodeAppointmentFollowUp(followUpItems + AppointmentFollowUp(newFollowUp.trim()))
                    newFollowUp = ""
                }, enabled = newFollowUp.isNotBlank()) { Text(stringResource(R.string.add_follow_up_item)) }
            }
        }
        if (kind == Kind.MEDICATION) {
            Field(dosage, { dosage = it }, R.string.dosage)
            Note(R.string.medication_notice)
        }
        if (kind == Kind.TASK || kind == Kind.ROUTINE) {
            CarerPicker(carers, carer) { carer = it }
            Toggle(R.string.high_priority, priority) { priority = it }
        }
        Field(details, { details = it }, R.string.notes, true)
        if (time.isNotEmpty()) Toggle(R.string.reminder, reminder) { reminder = it }
        val duplicateLabel = kind == Kind.MEDICATION || kind == Kind.APPOINTMENT
        Button({
            val followUpToSave = if (newFollowUp.isNotBlank() && followUpItems.size < MAX_FOLLOW_UP_ITEMS) {
                encodeAppointmentFollowUp(followUpItems + AppointmentFollowUp(newFollowUp.trim()))
            } else followUp
            save(Entry(id = previous?.id ?: 0, recipientId = recipientId, kind = kind, title = title.trim(), details = details.trim(),
                day = date, time = time, repeat = repeat, endDay = end.takeIf { repeat != Repeat.ONCE }.orEmpty(),
                caregiverId = carer, priority = priority, paused = paused, location = location.trim(), dosage = dosage,
                provider = provider.trim(), checklist = checklist.trim(), reminder = reminder && time.isNotEmpty(),
                recordedAt = previous?.recordedAt ?: System.currentTimeMillis(),
                weekdays = if (repeat == Repeat.SELECTED_DAYS) weekdays else 0,
                appointmentFollowUp = followUpToSave))
        }, enabled = title.isNotBlank() && (repeat == Repeat.ONCE || validDate) &&
            (repeat != Repeat.SELECTED_DAYS || weekdays != 0) && (!duplicateLabel || time.isNotEmpty())) {
            Text(stringResource(R.string.save))
        }
        attachments()
        if (previous != null) TextButton({ confirm = true }) { Text(stringResource(R.string.delete_record)) }
    }
    if (confirm && previous != null) Confirm(R.string.delete_record, R.string.delete_record_body, { confirm = false }) {
        delete(previous)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayPicker(selected: Int, onChange: (Int) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(7) { offset ->
            val day = firstDay.plus(offset.toLong())
            val label = day.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            FilterChip(selected and day.mask() != 0, { onChange(selected xor day.mask()) }, { Text(label) })
        }
    }
    if (selected == 0) Note(R.string.choose_weekday)
}

@Composable
private fun DayPicker(label: Int, value: String, default: String, clear: Boolean = false, change: (String) -> Unit) {
    val context = LocalContext.current
    val current = LocalDate.parse(value.ifEmpty { default })
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton({
            DatePickerDialog(context, { _, y, m, d -> change(LocalDate.of(y, m + 1, d).toString()) },
                current.year, current.monthValue - 1, current.dayOfMonth).show()
        }) { Text(stringResource(label) + if (value.isEmpty()) "" else " · " + DateText(value)) }
        if (clear && value.isNotEmpty()) TextButton({ change("") }) { Text(stringResource(R.string.clear)) }
    }
}

@Composable
private fun TimePicker(value: String, change: (String) -> Unit) {
    val context = LocalContext.current
    val current = LocalTime.parse(value.ifEmpty { "09:00" })
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton({
            TimePickerDialog(context, { _, hour, minute ->
                change(LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.ROOT)))
            }, current.hour, current.minute, DateFormat.is24HourFormat(context)).show()
        }) { Text(stringResource(R.string.time) + if (value.isEmpty()) "" else " · " + TimeText(value)) }
        if (value.isNotEmpty()) TextButton({ change("") }) { Text(stringResource(R.string.clear)) }
    }
}

@Composable
fun CarerPicker(carers: List<Caregiver>, selected: Long?, select: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }) {
            Text(carers.firstOrNull { it.id == selected }?.name ?: stringResource(R.string.choose_caregiver))
        }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.unassigned)) }, { select(null); expanded = false })
            carers.forEach { carer ->
                DropdownMenuItem({ Text(carer.name) }, { select(carer.id); expanded = false })
            }
        }
    }
}

@Composable
fun StatusEditor(occurrence: Occurrence, close: () -> Unit, save: (Outcome) -> Unit) {
    val medication = occurrence.entry.kind == Kind.MEDICATION
    var taken by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = close, title = { Text(occurrence.entry.title) },
        text = {
            Column {
                Text(DateText(occurrence.date.toString()))
                Note(if (taken) R.string.confirm_taken_body else occurrence.outcome.label())
            }
        },
        confirmButton = {
            Button({
                if (medication && !taken) taken = true else save(if (medication) Outcome.TAKEN else Outcome.COMPLETED)
            }) { Text(stringResource(if (taken) R.string.confirm_taken else if (medication) R.string.mark_taken else R.string.mark_complete)) }
        },
        dismissButton = {
            if (!taken) {
                TextButton({ save(Outcome.SKIPPED) }) { Text(stringResource(R.string.mark_skipped)) }
                if (occurrence.finished) TextButton({ save(Outcome.UNCONFIRMED) }) { Text(stringResource(R.string.undo)) }
            }
            TextButton(close) { Text(stringResource(R.string.cancel)) }
        })
}

@Composable
fun Emergency(recipient: Recipient, close: () -> Unit) {
    Editor(R.string.emergency_info, close) {
        Text(recipient.name, style = MaterialTheme.typography.titleLarge)
        Note(R.string.emergency_notice)
        if (recipient.emergency.isBlank() && recipient.preferredClinic.isBlank() && recipient.communicationLanguage.isBlank()) {
            Note(R.string.no_emergency_info)
        }
        if (recipient.emergency.isNotBlank()) Text(recipient.emergency)
        if (recipient.preferredClinic.isNotBlank()) {
            Text(stringResource(R.string.preferred_clinic), style = MaterialTheme.typography.titleMedium)
            Text(recipient.preferredClinic)
        }
        if (recipient.communicationLanguage.isNotBlank()) {
            Text(stringResource(R.string.preferred_communication_language), style = MaterialTheme.typography.titleMedium)
            Text(recipient.communicationLanguage)
        }
    }
}

