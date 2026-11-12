package app.carehando.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import app.carehando.CareState
import app.carehando.CareViewModel
import app.carehando.HandoverDraft
import app.carehando.R
import app.carehando.data.*

@Composable
fun DraftEditor(recipient: Recipient, state: CareState, vm: CareViewModel, close: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var followUp by rememberSaveable { mutableStateOf("") }
    var draft by remember { mutableStateOf<HandoverDraft?>(null) }
    val author = state.caregivers.firstOrNull { it.id == selected }?.name ?: name.trim()
    Editor(if (draft == null) R.string.new_handover else R.string.review_draft, close) {
        Text(recipient.name, style = MaterialTheme.typography.titleLarge)
        val pending = draft
        if (pending == null) {
            Note(R.string.draft_intro)
            if (state.caregivers.isNotEmpty()) CarerPicker(state.caregivers, selected) { selected = it }
            if (selected == null) Field(name, { name = it }, R.string.caregiver_name)
            Field(notes, { notes = it }, R.string.notes, true)
            Field(followUp, { followUp = it }, R.string.follow_up, true)
            Button({ draft = vm.draft(recipient, author, state) }, enabled = author.isNotBlank()) {
                Text(stringResource(R.string.review_draft))
            }
        } else {
            Text(pending.header.caregiver)
            if (pending.header.from > 0L) Text(stringResource(R.string.since_handover, Timestamp(pending.header.from)))
            Note(R.string.handover_window)
            HandoverSections(pending.items)
            if (notes.isNotBlank()) Panel { Heading(R.string.notes); Text(notes) }
            if (followUp.isNotBlank()) Panel { Heading(R.string.follow_up); Text(followUp) }
            Button({ vm.handover(pending, notes.trim(), followUp.trim()); close() }) { Text(stringResource(R.string.save_handover)) }
            TextButton({ draft = null }) { Text(stringResource(R.string.edit_draft)) }
        }
    }
}

@Composable
fun ReviewHandover(saved: SavedHandover, carers: List<Caregiver>, close: () -> Unit,
                   append: (HandoverRevision) -> Unit, attachments: @Composable () -> Unit) {
    var action by rememberSaveable { mutableStateOf<RevisionKind?>(null) }
    var author by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    var followUp by rememberSaveable { mutableStateOf("") }
    Editor(R.string.handover_details, close) {
        Text(saved.handover.recipientName, style = MaterialTheme.typography.titleLarge)
        Text(saved.handover.caregiver + " · " + Timestamp(saved.handover.savedAt))
        HandoverSections(saved.items)
        Heading(R.string.handover_history)
        saved.revisions.sortedBy { it.id }.forEach {
            Panel {
                Text(stringResource(it.kind.label()), style = MaterialTheme.typography.titleMedium)
                Text(it.author + " · " + Timestamp(it.at))
                if (it.notes.isNotEmpty()) Text(it.notes)
                if (it.followUp.isNotEmpty()) {
                    Heading(R.string.follow_up)
                    Text(it.followUp)
                }
            }
        }
        val currentAction = action
        if (currentAction == null) {
            OutlinedButton({ action = RevisionKind.ACKNOWLEDGEMENT }) { Text(stringResource(R.string.acknowledge)) }
            OutlinedButton({ action = RevisionKind.CORRECTION }) { Text(stringResource(R.string.add_correction)) }
            val report = buildList {
                add(saved.handover.recipientName)
                add(saved.handover.caregiver + " · " + Timestamp(saved.handover.savedAt))
                HandoverSection.entries.forEach { section ->
                    add(stringResource(section.label()))
                    saved.items.filter { it.section == section }.forEach { add(ItemLine(it)) }
                }
                saved.revisions.sortedBy { it.id }.forEach {
                    add(stringResource(it.kind.label()) + " · " + it.author + " · " + Timestamp(it.at))
                    if (it.notes.isNotBlank()) add(it.notes)
                    if (it.followUp.isNotBlank()) add(stringResource(R.string.follow_up) + " · " + it.followUp)
                }
            }
            ExportActions(R.string.handover_details, report, null)
            attachments()
        } else {
            val actor = carers.firstOrNull { it.id == selected }?.name ?: author.trim()
            if (carers.isNotEmpty()) CarerPicker(carers, selected) { selected = it }
            if (selected == null) Field(author, { author = it }, R.string.caregiver_name)
            if (currentAction == RevisionKind.CORRECTION) {
                Field(notes, { notes = it }, R.string.correction, true)
                Field(followUp, { followUp = it }, R.string.follow_up, true)
            } else Note(R.string.acknowledge_body)
            Button({
                append(HandoverRevision(handoverId = saved.handover.id, kind = currentAction,
                    author = actor, notes = notes.trim(), followUp = followUp.trim()))
                action = null
                notes = ""
                followUp = ""
            }, enabled = actor.isNotBlank() && (currentAction == RevisionKind.ACKNOWLEDGEMENT || notes.isNotBlank() || followUp.isNotBlank())) {
                Text(stringResource(R.string.confirm))
            }
            TextButton({ action = null; notes = ""; followUp = "" }) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun HandoverSections(items: List<HandoverItem>) {
    HandoverSection.entries.forEach { section ->
        Panel {
            Heading(section.label())
            val lines = items.filter { it.section == section }
            if (lines.isEmpty()) Note(R.string.no_items)
            lines.forEach { Text(ItemLine(it)) }
        }
    }
}

@Composable
private fun ItemLine(item: HandoverItem): String = buildString {
    append(item.title)
    append(" · ")
    append(DateText(item.day))
    if (item.time.isNotEmpty()) append(" · ").append(TimeText(item.time))
    item.outcome?.let { append(" · ").append(stringResource(it.label())) }
}

