package app.carehando.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences as SettingsPreferences
import app.carehando.CareState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.carehando.CareViewModel
import app.carehando.R
import app.carehando.data.*
import app.carehando.domain.Occurrence
import java.time.LocalDate
import java.time.LocalDateTime
import java.text.NumberFormat
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import org.xmlpull.v1.XmlPullParser

@Composable
fun PageHeading(title: Int, intro: Int, action: Int, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineLarge)
        Note(intro)
        Button(onClick) { Text(stringResource(action)) }
    }
}

@Composable
fun Onboarding(restore: () -> Unit, save: (Recipient, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var emergency by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("caregiver") }
    Column(Modifier.widthIn(max = 540.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel(tone = MaterialTheme.colorScheme.primaryContainer) {
            Image(painterResource(R.drawable.carehando_mark), null, Modifier.size(64.dp))
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineLarge)
            Note(R.string.onboarding_intro)
            Image(painterResource(R.drawable.onboarding_family), contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(156.dp).clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop)
        }
        LanguagePicker()
        Choices(R.string.using_as, listOf("caregiver" to R.string.caregiver, "recipient" to R.string.recipient),
            mode) { mode = it }
        Field(name, { name = it }, R.string.preferred_name)
        Field(emergency, { emergency = it }, R.string.emergency_contacts, multiline = true)
        Note(R.string.privacy_intro)
        Button(onClick = { save(Recipient(name = name.trim(), emergency = emergency.trim()), mode) },
            enabled = name.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.create_profile))
        }
        TextButton(restore) { Text(stringResource(R.string.backup_restore)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayHeader(
    recipient: Recipient,
    date: LocalDate,
    occurrences: List<Occurrence>,
    latest: SavedHandover?,
    next: Entry?,
    wide: Boolean,
    add: () -> Unit,
    assistant: () -> Unit,
    handovers: () -> Unit,
    records: () -> Unit,
) {
    val format = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val tasks = occurrences.filter { it.entry.kind == Kind.TASK || it.entry.kind == Kind.ROUTINE }
    val done = tasks.count { it.outcome == Outcome.COMPLETED }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel(tone = MaterialTheme.colorScheme.primaryContainer) {
            Text(DateText(date.toString()), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.care_for, recipient.name), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.plan_progress, format.format(done), format.format(tasks.size)),
                style = MaterialTheme.typography.bodyLarge)
            if (tasks.isNotEmpty()) {
                LinearProgressIndicator(progress = { done.toFloat() / tasks.size }, modifier = Modifier.fillMaxWidth())
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(add) { Text(stringResource(R.string.add_task)) }
                OutlinedButton(assistant) { Text(stringResource(R.string.voice_and_text)) }
            }
        }
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HandoverTile(latest, handovers, Modifier.weight(1f))
            AppointmentTile(next, records, Modifier.weight(1f))
        } else {
            HandoverTile(latest, handovers)
            AppointmentTile(next, records)
        }
    }
}

@Composable
private fun HandoverTile(latest: SavedHandover?, open: () -> Unit, modifier: Modifier = Modifier) {
    Panel(modifier, MaterialTheme.colorScheme.secondaryContainer) {
        Heading(R.string.latest_handover)
        if (latest == null) Note(R.string.no_handovers)
        else {
            Text(latest.handover.caregiver)
            Text(Timestamp(latest.handover.savedAt))
            latest.revisions.sortedByDescending { it.id }.firstOrNull { it.followUp.isNotBlank() }?.let { Text(it.followUp) }
        }
        TextButton(open) { Text(stringResource(R.string.handovers)) }
    }
}

@Composable
private fun AppointmentTile(next: Entry?, open: () -> Unit, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Heading(R.string.next_appointment)
        if (next == null) Note(R.string.no_appointments)
        else {
            Text(next.title, style = MaterialTheme.typography.titleMedium)
            Text(DateText(next.day) + if (next.time.isNotEmpty()) " · " + TimeText(next.time) else "")
            if (next.location.isNotBlank()) Text(next.location)
        }
        TextButton(open) { Text(stringResource(R.string.appointments)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OccurrenceCard(item: Occurrence, now: LocalDateTime, caregivers: List<Caregiver>,
                   status: () -> Unit, edit: (Entry) -> Unit) {
    val entry = item.entry
    Panel {
        Text(stringResource(entry.kind.label()), color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge)
        Text(entry.title, style = MaterialTheme.typography.titleLarge)
        EntryDetails(entry, item.date.toString(), caregivers)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(status) {
                val label = if (item.isLate(now) && entry.kind == Kind.MEDICATION) R.string.missed_unconfirmed
                    else if (item.isLate(now)) R.string.overdue else item.outcome.label()
                Text(stringResource(label))
            }
            TextButton({ edit(entry) }) { Text(stringResource(R.string.edit)) }
        }
    }
}

@Composable
fun EntryCard(entry: Entry, caregivers: List<Caregiver>, edit: () -> Unit) {
    Panel {
        Text(entry.title, style = MaterialTheme.typography.titleLarge)
        EntryDetails(entry, entry.day, caregivers)
        TextButton(edit) { Text(stringResource(R.string.edit)) }
    }
}

@Composable
private fun EntryDetails(entry: Entry, day: String, caregivers: List<Caregiver>) {
    Text(DateText(day) + if (entry.time.isNotEmpty()) " · " + TimeText(entry.time) else "")
    caregivers.firstOrNull { it.id == entry.caregiverId }?.let { Text(it.name) }
    if (entry.priority) Note(R.string.high_priority)
    if (entry.paused) Note(R.string.paused)
    if (entry.repeat != Repeat.ONCE) Note(entry.repeat.label())
    if (entry.dosage.isNotBlank()) Text(entry.dosage)
    if (entry.location.isNotBlank()) Text(entry.location)
    if (entry.provider.isNotBlank()) Text(entry.provider)
    if (entry.checklist.isNotBlank()) Text(entry.checklist)
    if (entry.appointmentFollowUp.isNotEmpty()) {
        val items = remember(entry.appointmentFollowUp) { decodeAppointmentFollowUp(entry.appointmentFollowUp) }
        val locale = LocalConfiguration.current.locales[0]
        val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
        Text(stringResource(R.string.follow_up))
        Text(stringResource(R.string.plan_progress, numbers.format(items.count { it.complete }), numbers.format(items.size)))
    }
    if (entry.details.isNotBlank()) Text(entry.details)
}

@Composable
fun ActivityHistory(activity: List<ActivityRecord>) {
    Panel {
        Heading(R.string.activity_history)
        if (activity.isEmpty()) Note(R.string.no_activity)
        activity.take(30).forEach {
            Text(it.title, style = MaterialTheme.typography.titleSmall)
            Text("${DateText(it.day)} · ${stringResource(it.outcome.label())} · ${Timestamp(it.at)}",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun LanguagePicker() {
    var expanded by remember { mutableStateOf(false) }
    val resources = LocalContext.current.resources
    val tags = remember(resources) {
        buildList {
            resources.getXml(R.xml.locales_config).use { parser ->
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                        parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                            ?.let(::add)
                    }
                }
            }
        }
    }
    val active = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag()
    Box {
        OutlinedButton({ expanded = true }) {
            Text(stringResource(R.string.language) + " · " + (active?.let { languageName(it) } ?: stringResource(R.string.device_language)))
        }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.device_language)) }, {
                expanded = false
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            })
            tags.forEach { tag ->
                DropdownMenuItem({ Text(languageName(tag)) }, {
                    expanded = false
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                })
            }
        }
    }
}

private fun languageName(tag: String): String {
    val locale = java.util.Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

@Composable
fun Settings(
    state: CareState,
    prefs: SettingsPreferences,
    vm: CareViewModel,
    editRecipient: (Recipient?) -> Unit,
    editCaregiver: (Caregiver?) -> Unit,
    notifications: (Boolean) -> Unit,
    report: () -> Unit,
    backup: () -> Unit,
    active: Boolean,
) {
    var deleteAll by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge)
        Panel {
            Heading(R.string.care_recipients)
            if (prefs[Preferences.premium] == true) {
                OutlinedButton({ editRecipient(null) }) { Text(stringResource(R.string.create_profile)) }
            }
            state.recipients.forEach { profile ->
                if (!profile.archived && state.recipients.count { !it.archived } > 1) {
                    TextButton({ vm.setting { it[Preferences.recipientId] = profile.id } }) {
                        Text(stringResource(R.string.select_profile, profile.name))
                    }
                }
                TextButton({ editRecipient(profile) }) {
                    Text(profile.name + if (profile.archived) " · " + stringResource(R.string.archived) else "")
                }
            }
        }
        Panel {
            Heading(R.string.caregivers)
            state.caregivers.forEach { carer -> TextButton({ editCaregiver(carer) }) { Text(carer.name) } }
            OutlinedButton({ editCaregiver(null) }) { Text(stringResource(R.string.add_caregiver)) }
        }
        Panel {
            Heading(R.string.accessibility)
            LanguagePicker()
            Choices(R.string.appearance, listOf("system" to R.string.device_theme, "light" to R.string.light, "dark" to R.string.dark),
                prefs[Preferences.darkMode] ?: "system") { mode -> vm.setting { it[Preferences.darkMode] = mode } }
            Toggle(R.string.spoken_feedback, prefs[Preferences.spoken] == true) { on -> vm.setting { it[Preferences.spoken] = on } }
        }
        Panel {
            Heading(R.string.reminders)
            Toggle(R.string.enable_reminders, prefs[Preferences.reminders] == true, notifications)
            Note(R.string.reminder_notice)
            HourPicker(R.string.quiet_start, prefs[Preferences.quietStart] ?: 22) { hour ->
                vm.setting { it[Preferences.quietStart] = hour }
            }
            HourPicker(R.string.quiet_end, prefs[Preferences.quietEnd] ?: 7) { hour ->
                vm.setting { it[Preferences.quietEnd] = hour }
            }
        }
        Panel {
            Heading(R.string.privacy)
            Note(R.string.privacy_intro)
            val keyguard = androidx.compose.ui.platform.LocalContext.current.getSystemService(android.app.KeyguardManager::class.java)
            if (keyguard.isDeviceSecure || prefs[Preferences.appLock] == true) {
                Toggle(R.string.app_lock_setting, prefs[Preferences.appLock] == true) { enabled ->
                    vm.deviceUnlocked = false
                    vm.setting { it[Preferences.appLock] = enabled }
                }
            } else Note(R.string.app_lock_unavailable)
            OutlinedButton(backup) { Text(stringResource(R.string.encrypted_backup)) }
            if (active) OutlinedButton(report) { Text(stringResource(R.string.care_report)) }
            TextButton({ deleteAll = true }) { Text(stringResource(R.string.delete_all)) }
        }
        Panel {
            Heading(R.string.premium)
            val billing = vm.premium
            val price by billing.price.collectAsStateWithLifecycle()
            val notice by billing.notice.collectAsStateWithLifecycle()
            Note(R.string.premium_features)
            if (prefs[Preferences.premium] == true) Note(R.string.premium_active)
            if (billing.configured) {
                val availablePrice = price
                if (availablePrice != null && prefs[Preferences.premium] != true) {
                    Button(billing::buy) { Text(stringResource(R.string.buy_premium, availablePrice)) }
                }
                OutlinedButton(billing::restore) { Text(stringResource(R.string.restore_purchases)) }
                notice?.takeIf { it != R.string.premium_active }?.let { Note(it) }
            } else Note(R.string.premium_unavailable)
        }
    }
    if (deleteAll) Confirm(R.string.delete_all, R.string.delete_all_body, { deleteAll = false }) {
        vm.clear()
        deleteAll = false
    }
}

@Composable
private fun HourPicker(label: Int, value: Int, change: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }) { Text(stringResource(label, TimeText("%02d:00".format(java.util.Locale.ROOT, value)))) }
        DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = 280.dp)) {
            (0..23).forEach { hour ->
                DropdownMenuItem({ Text(TimeText("%02d:00".format(java.util.Locale.ROOT, hour))) }, {
                    change(hour)
                    expanded = false
                })
            }
        }
    }
}



