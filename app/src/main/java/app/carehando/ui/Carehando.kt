package app.carehando.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.carehando.CareState
import app.carehando.CareViewModel
import app.carehando.R
import app.carehando.data.*
import app.carehando.domain.*
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay

private enum class Destination(val label: Int) {
    TODAY(R.string.today), PLANS(R.string.care_plans), HANDOVERS(R.string.handovers),
    RECORDS(R.string.records), SETTINGS(R.string.settings)
}

private enum class Period(val label: Int) {
    TODAY(R.string.today), TOMORROW(R.string.tomorrow), WEEK(R.string.this_week), OVERDUE(R.string.overdue)
}

private sealed interface Overlay {
    data class Edit(val kind: Kind, val entry: Entry? = null, val title: String = "") : Overlay
    data class Profile(val recipient: Recipient?) : Overlay
    data class Carer(val carer: Caregiver?) : Overlay
    data class Status(val occurrence: Occurrence) : Overlay
    data class Review(val id: Long) : Overlay
    data object Draft : Overlay
    data object Assistant : Overlay
    data object Emergency : Overlay
    data object Report : Overlay
    data object Calendar : Overlay
    data object Backup : Overlay
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Carehando(vm: CareViewModel = viewModel()) {
    val state by vm.care.collectAsStateWithLifecycle()
    val settings by vm.preferences.collectAsStateWithLifecycle()
    val documents by vm.documents.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(vm, context) { vm.errors.collect { snackbar.showSnackbar(context.getString(it)) } }
    CareTheme(settings?.get(Preferences.darkMode)) {
        val data = state
        val prefs = settings
        if (data == null || prefs == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@CareTheme
        }
        DeviceLock(prefs[Preferences.appLock] == true, vm) {
            val active = data.recipients.filterNot { it.archived }
            val recipient = active.firstOrNull { it.id == prefs[Preferences.recipientId] } ?: active.firstOrNull()
            var destination by rememberSaveable { mutableStateOf(Destination.TODAY) }
            var period by rememberSaveable { mutableStateOf(Period.TODAY) }
            var recordKind by rememberSaveable { mutableStateOf(Kind.APPOINTMENT) }
            var search by rememberSaveable { mutableStateOf("") }
            var overlay by remember { mutableStateOf<Overlay?>(null) }
            var now by remember { mutableStateOf(LocalDateTime.now()) }
            LaunchedEffect(Unit) {
                while (true) {
                    now = LocalDateTime.now()
                    delay(30_000)
                }
            }
            val day = now.toLocalDate()
            val entries = data.entries.filter { it.recipientId == recipient?.id }
            val activity = data.activity.filter { it.recipientId == recipient?.id }
            val handovers = data.handovers.filter { it.handover.recipientId == recipient?.id }
            val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                vm.reminders(it)
            }
            fun toggleReminders(enabled: Boolean) {
                if (enabled && Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else vm.reminders(enabled)
            }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 720.dp
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    topBar = {
                        TopAppBar(title = {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Image(painterResource(R.drawable.carehando_mark), null, Modifier.size(36.dp))
                                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                            }
                        }, actions = {
                            if (recipient != null) TextButton(onClick = { overlay = Overlay.Emergency }) {
                                Text(stringResource(R.string.emergency_info))
                            }
                        })
                    },
                    bottomBar = {
                        if (!wide && data.recipients.isNotEmpty()) NavigationBar {
                            Destination.entries.forEach { page ->
                                NavigationBarItem(destination == page, { destination = page }, icon = { NavIcon(page) },
                                    label = {
                                        Text(stringResource(page.label), maxLines = 2, textAlign = TextAlign.Center,
                                            overflow = TextOverflow.Ellipsis)
                                    })
                            }
                        }
                    },
                ) { padding ->
                    Row(Modifier.padding(padding).fillMaxSize()) {
                        if (wide && data.recipients.isNotEmpty()) {
                            NavigationRail(Modifier.fillMaxHeight().width(116.dp)) {
                                Destination.entries.forEach { page ->
                                    NavigationRailItem(destination == page, { destination = page },
                                        icon = { NavIcon(page) }, label = { Text(stringResource(page.label), textAlign = TextAlign.Center) })
                                }
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                            if (data.recipients.isEmpty()) {
                                Onboarding({ overlay = Overlay.Backup }) { profile, mode -> vm.recipient(profile, mode) }
                            } else {
                                LazyColumn(
                                    Modifier.widthIn(max = 1120.dp).fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = if (wide) 30.dp else 16.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    if (destination == Destination.SETTINGS || recipient == null) {
                                        item {
                                            Settings(data, prefs, vm, { overlay = Overlay.Profile(it) },
                                                { overlay = Overlay.Carer(it) }, ::toggleReminders,
                                                { overlay = Overlay.Report }, { overlay = Overlay.Backup }, recipient != null)
                                        }
                                    } else when (destination) {
                                        Destination.TODAY -> {
                                            val start = when (period) {
                                                Period.TODAY, Period.WEEK -> day
                                                Period.TOMORROW -> day.plusDays(1)
                                                Period.OVERDUE -> day.minusDays(30)
                                            }
                                            val end = if (period == Period.WEEK) day.plusDays(6) else
                                                if (period == Period.OVERDUE) day else start
                                            val upcoming = occurrences(entries, activity, start, end).filter {
                                                period != Period.OVERDUE || it.isLate(now)
                                            }
                                            val nextAppointment = entries.firstOrNull {
                                                it.kind == Kind.APPOINTMENT && it.day >= day.toString() &&
                                                    (it.day > day.toString() || it.time.isEmpty() || !LocalTimeComparison.passed(it.time, now))
                                            }
                                            item {
                                                TodayHeader(recipient, day, upcoming, handovers.firstOrNull(), nextAppointment,
                                                    wide, { overlay = Overlay.Edit(Kind.TASK) }, { overlay = Overlay.Assistant },
                                                    { destination = Destination.HANDOVERS }, { destination = Destination.RECORDS })
                                            }
                                            item {
                                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Period.entries.forEach { range ->
                                                        FilterChip(period == range, { period = range }, { Text(stringResource(range.label)) })
                                                    }
                                                }
                                            }
                                            if (upcoming.isEmpty()) item { Panel { Note(R.string.no_scheduled) } }
                                            items(upcoming, key = { "${it.entry.id}/${it.date}" }) { occurrence ->
                                                OccurrenceCard(occurrence, now, data.caregivers, { overlay = Overlay.Status(occurrence) },
                                                    { overlay = Overlay.Edit(it.kind, it) })
                                            }
                                            val latestNote = entries.filter { it.kind == Kind.JOURNAL }.maxByOrNull { it.recordedAt }
                                            if (latestNote != null) item {
                                                Panel {
                                                    Heading(R.string.latest_note)
                                                    Text(latestNote.title)
                                                    TextButton(onClick = { recordKind = Kind.JOURNAL; destination = Destination.RECORDS }) {
                                                        Text(stringResource(R.string.open_records))
                                                    }
                                                }
                                            }
                                        }
                                        Destination.PLANS -> {
                                            item {
                                                PageHeading(R.string.care_plans, R.string.plans_intro, R.string.add_routine) {
                                                    overlay = Overlay.Edit(Kind.ROUTINE)
                                                }
                                            }
                                            val routines = entries.filter { it.kind == Kind.ROUTINE || it.kind == Kind.TASK }
                                            if (routines.isEmpty()) item { Panel { Note(R.string.no_plans) } }
                                            items(routines, key = { it.id }) { entry -> EntryCard(entry, data.caregivers) {
                                                overlay = Overlay.Edit(entry.kind, entry)
                                            } }
                                            item { ActivityHistory(activity.filter { it.kind == Kind.TASK || it.kind == Kind.ROUTINE }) }
                                        }
                                        Destination.HANDOVERS -> {
                                            item { PageHeading(R.string.handovers, R.string.handover_intro, R.string.new_handover) {
                                                overlay = Overlay.Draft
                                            } }
                                            if (handovers.isEmpty()) item { Panel { Note(R.string.no_handovers) } }
                                            items(handovers, key = { it.handover.id }) { saved ->
                                                Panel {
                                                    Text(Timestamp(saved.handover.savedAt), style = MaterialTheme.typography.labelLarge)
                                                    Text(saved.handover.caregiver, style = MaterialTheme.typography.titleLarge)
                                                    Note(if (saved.revisions.any { it.kind == RevisionKind.ACKNOWLEDGEMENT })
                                                        R.string.acknowledged else R.string.awaiting_acknowledgement)
                                                    TextButton(onClick = { overlay = Overlay.Review(saved.handover.id) }) {
                                                        Text(stringResource(R.string.open_handover))
                                                    }
                                                }
                                            }
                                        }
                                        Destination.RECORDS -> {
                                            item {
                                                PageHeading(R.string.records, R.string.records_intro, R.string.add_record) {
                                                    overlay = Overlay.Edit(recordKind)
                                                }
                                            }
                                            item {
                                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    listOf(Kind.APPOINTMENT, Kind.MEDICATION, Kind.JOURNAL).forEach { kind ->
                                                        FilterChip(recordKind == kind, { recordKind = kind }, { Text(stringResource(kind.label())) })
                                                    }
                                                }
                                            }
                                            if (recordKind == Kind.APPOINTMENT) item {
                                                OutlinedButton({ overlay = Overlay.Calendar }) {
                                                    Text(stringResource(R.string.appointment_calendar))
                                                }
                                            }
                                            if (recordKind == Kind.MEDICATION) item { Note(R.string.medication_notice) }
                                            item { Field(search, { search = it }, R.string.search_records) }
                                            val records = entries.filter {
                                                it.kind == recordKind && (it.title.contains(search, true) || it.details.contains(search, true))
                                            }
                                            if (records.isEmpty()) item { Panel { Note(R.string.no_records) } }
                                            items(records, key = { it.id }) { entry ->
                                                EntryCard(entry, data.caregivers) { overlay = Overlay.Edit(entry.kind, entry) }
                                            }
                                            if (recordKind == Kind.MEDICATION) item {
                                                ActivityHistory(activity.filter { it.kind == Kind.MEDICATION })
                                            }
                                        }
                                        Destination.SETTINGS -> Unit
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val dismiss = { overlay = null }
            when (val screen = overlay) {
                is Overlay.Profile -> ProfileEditor(screen.recipient, dismiss, { vm.recipient(it); dismiss() },
                    { vm.delete(it); dismiss() }) {
                    screen.recipient?.takeUnless { it.archived }?.let { profile ->
                        DocumentPanel(vm, documents, profile.id, imagesOnly = true)
                    }
                }
                is Overlay.Carer -> CaregiverEditor(screen.carer, dismiss, { vm.caregiver(it); dismiss() }, { vm.delete(it); dismiss() })
                is Overlay.Edit -> if (recipient != null) EntryEditor(screen.kind, recipient.id, screen.entry, screen.title,
                    data.caregivers, dismiss, { vm.entry(it); dismiss() }, { vm.delete(it); dismiss() }) {
                    val item = screen.entry
                    if (item != null && (item.kind == Kind.APPOINTMENT || item.kind == Kind.JOURNAL)) {
                        DocumentPanel(vm, documents, item.recipientId, entryId = item.id)
                    }
                }
                is Overlay.Status -> StatusEditor(screen.occurrence, dismiss) { outcome ->
                    vm.record(screen.occurrence.entry, screen.occurrence.date, outcome)
                    dismiss()
                }
                Overlay.Draft -> if (recipient != null) DraftEditor(recipient, data, vm, dismiss)
                is Overlay.Review -> handovers.firstOrNull { it.handover.id == screen.id }?.let {
                    ReviewHandover(it, data.caregivers, dismiss, vm::append) {
                        DocumentPanel(vm, documents, it.handover.recipientId, handoverId = it.handover.id)
                    }
                }
                Overlay.Assistant -> Assistant(settings?.get(Preferences.spoken) == true, dismiss) { intent ->
                    when (intent.command) {
                        Command.ADD_TASK -> overlay = Overlay.Edit(Kind.TASK, title = intent.text)
                        Command.ADD_NOTE -> overlay = Overlay.Edit(Kind.JOURNAL, title = intent.text)
                        Command.HANDOVER -> overlay = Overlay.Draft
                        Command.APPOINTMENTS, Command.MEDICATION -> {
                            recordKind = if (intent.command == Command.APPOINTMENTS) Kind.APPOINTMENT else Kind.MEDICATION
                            destination = Destination.RECORDS
                            dismiss()
                        }
                        Command.TODAY -> { period = Period.TODAY; destination = Destination.TODAY; dismiss() }
                    }
                }
                Overlay.Calendar -> if (recipient != null) AppointmentCalendar(
                    entries.filter { it.kind == Kind.APPOINTMENT }, day, dismiss
                ) { overlay = Overlay.Edit(Kind.APPOINTMENT, it) }
                Overlay.Emergency -> if (recipient != null) Emergency(recipient, dismiss)
                Overlay.Backup -> BackupEditor(vm, prefs[Preferences.premium] == true,
                    data.recipients.isEmpty() && data.caregivers.isEmpty(), dismiss)
                Overlay.Report -> if (recipient != null) CareReport(recipient, entries, activity, dismiss)
                null -> Unit
            }
        }
    }
}

private object LocalTimeComparison {
    fun passed(time: String, now: LocalDateTime) = java.time.LocalTime.parse(time) < now.toLocalTime()
}

@Composable
private fun NavIcon(page: Destination) {
    Icon(when (page) {
        Destination.TODAY -> Icons.Filled.Home
        Destination.PLANS -> Icons.Filled.List
        Destination.HANDOVERS -> Icons.Filled.Share
        Destination.RECORDS -> Icons.Filled.Info
        Destination.SETTINGS -> Icons.Filled.Settings
    }, contentDescription = null)
}


