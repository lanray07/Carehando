package app.carehando

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import app.carehando.data.*
import app.carehando.billing.AmazonPremium
import app.carehando.domain.handoverItems
import app.carehando.domain.occursOn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class CareState(
    val recipients: List<Recipient> = emptyList(),
    val caregivers: List<Caregiver> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val activity: List<ActivityRecord> = emptyList(),
    val handovers: List<SavedHandover> = emptyList(),
)

data class HandoverDraft(val header: Handover, val items: List<HandoverItem>)

class CareViewModel(private val app: Application) : AndroidViewModel(app) {
    var deviceUnlocked by mutableStateOf(false)
    private val database = CareDatabase.open(app)
    private val dao = database.care()
    val premium = AmazonPremium(app, viewModelScope)
    private val files = CareDocuments(app, database)
    private val backup = CareBackup(app, database)
    val documents = database.documents().observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { save { files.prune() } }
    val care = combine(dao.recipients(), dao.caregivers(), dao.entries(), dao.activity(), dao.handovers(), ::CareState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val preferences = app.carePreferences.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val errors = messages.asSharedFlow()

    private fun save(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                messages.tryEmit(R.string.save_failed)
            }
        }
    }

    suspend fun exportBackup(uri: android.net.Uri, passphrase: CharArray) {
        check(app.carePreferences.data.first()[Preferences.premium] == true)
        backup.export(passphrase) { requireNotNull(app.contentResolver.openOutputStream(uri, "wt")) }
    }

    suspend fun restoreBackup(uri: android.net.Uri, passphrase: CharArray) {
        backup.restore(passphrase) { requireNotNull(app.contentResolver.openInputStream(uri)) }
    }

    fun setting(block: (MutablePreferences) -> Unit) = save { app.carePreferences.edit(block) }

    fun recipient(recipient: Recipient, mode: String? = null) = save {
        val id = database.withTransaction {
            val entitled = app.carePreferences.data.first()[Preferences.premium] == true
            // Archived profiles still count towards the free limit.
            check(recipient.id != 0L || entitled || dao.recipients().first().isEmpty())
            dao.save(recipient).takeIf { it > 0 } ?: recipient.id
        }
        app.carePreferences.edit {
            if (!recipient.archived) it[Preferences.recipientId] = id
            if (mode != null) it[Preferences.mode] = mode
        }
    }

    fun delete(recipient: Recipient) = save {
        dao.delete(recipient)
        files.prune()
    }

    fun addDocument(uri: android.net.Uri, recipientId: Long, entryId: Long?, handoverId: Long?, imagesOnly: Boolean) =
        save { files.import(uri, recipientId, entryId, handoverId, imagesOnly) }

    fun deleteDocument(document: CareDocument) = save { files.delete(document) }
    fun caregiver(caregiver: Caregiver) = save { dao.save(caregiver) }
    fun delete(caregiver: Caregiver) = save { dao.delete(caregiver) }
    fun entry(entry: Entry) = save { dao.save(entry) }
    fun delete(entry: Entry) = save {
        dao.delete(entry)
        files.prune()
    }

    fun record(entry: Entry, day: LocalDate, outcome: Outcome) = save {
        database.withTransaction {
            val current = dao.entry(entry.id) ?: return@withTransaction
            if (dao.recipient(current.recipientId)?.archived != false || current.paused || !current.occursOn(day)) return@withTransaction
            require(outcome != Outcome.TAKEN || current.kind == Kind.MEDICATION)
            require(outcome != Outcome.COMPLETED || current.kind != Kind.MEDICATION)
            val previous = dao.latestActivity(entry.id, day.toString())?.outcome ?: Outcome.UNCONFIRMED
            if (previous != outcome) {
                dao.record(ActivityRecord(entryId = current.id, recipientId = current.recipientId,
                    title = current.title, kind = current.kind, day = day.toString(), outcome = outcome))
            }
        }
    }

    fun draft(recipient: Recipient, caregiver: String, state: CareState): HandoverDraft {
        val now = System.currentTimeMillis()
        val from = state.handovers.firstOrNull { it.handover.recipientId == recipient.id }?.handover?.savedAt ?: 0L
        return HandoverDraft(Handover(recipientId = recipient.id, recipientName = recipient.name,
            caregiver = caregiver, from = from, savedAt = now), handoverItems(
                state.entries.filter { it.recipientId == recipient.id }, state.activity.filter { it.recipientId == recipient.id },
                from, now, LocalDate.now(), ZoneId.systemDefault()))
    }

    fun handover(draft: HandoverDraft, notes: String, followUp: String) = save {
        database.withTransaction {
            check(dao.recipient(draft.header.recipientId)?.archived == false)
            val previous = dao.handovers().first().firstOrNull { it.handover.recipientId == draft.header.recipientId }
            check((previous?.handover?.savedAt ?: 0L) == draft.header.from)
            val id = dao.save(draft.header)
            dao.save(draft.items.map { it.copy(handoverId = id) })
            dao.append(HandoverRevision(handoverId = id, kind = RevisionKind.ORIGINAL,
                author = draft.header.caregiver, notes = notes, followUp = followUp))
        }
    }

    fun append(revision: HandoverRevision) = save { dao.append(revision) }

    fun reminders(enabled: Boolean) = save {
        app.carePreferences.edit { it[Preferences.reminders] = enabled }
        ReminderWorker.configure(app, enabled)
    }

    fun clear() = save {
        database.withTransaction {
            dao.clearRecipients()
            dao.clearCaregivers()
        }
        app.carePreferences.edit {
            val entitlement = it[Preferences.premium]
            val receipt = it[Preferences.receiptId]
            it.clear()
            if (entitlement != null) it[Preferences.premium] = entitlement
            if (receipt != null) it[Preferences.receiptId] = receipt
        }
        files.prune()
        ReminderWorker.configure(app, false)
    }
}



