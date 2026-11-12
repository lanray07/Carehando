package app.carehando

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.carehando.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.GeneralSecurityException
import java.util.UUID

class BackupDeviceTest {
    @Test fun restoresLegacyBackupWithoutFollowUpColumn() = runBlocking<Unit> {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val target = Room.inMemoryDatabaseBuilder(context, CareDatabase::class.java).build()
        val phrase = "schema four fixture".toCharArray()
        try {
            CareBackup(context, target).restore(phrase) { instrument.context.assets.open("schema4-fixture.carebackup") }
            val visit = target.care().entries().first().single()
            assertEquals("Legacy appointment", visit.title)
            assertEquals("Bring the appointment letter", visit.checklist)
            assertEquals("", visit.appointmentFollowUp)
        } finally {
            phrase.fill('\u0000')
            target.close()
        }
    }

    @Test fun authenticatedBackupRestoresRecordsAttachmentsAndSequenceAndRejectsUnsafeRestore() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val temp = File(context.cacheDir, "backup-qa-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val sourceContext = filesAt(context, File(temp, "source"))
        val targetContext = filesAt(context, File(temp, "target"))
        val source = Room.inMemoryDatabaseBuilder(context, CareDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, CareDatabase::class.java).build()
        val phrase = "fixture backup passphrase".toCharArray()
        try {
            val dao = source.care()
            val recipient = Recipient(name = "Backup QA", preferredClinic = "Example clinic")
            val recipientId = dao.save(recipient)
            val carerId = dao.save(Caregiver(name = "Example carer", role = "Family", preferredLanguage = "English"))
            val entry = Entry(recipientId = recipientId, kind = Kind.TASK, title = "Example appointment preparation",
                day = "2026-11-11", repeat = Repeat.SELECTED_DAYS, weekdays = 4, caregiverId = carerId)
            val entryId = dao.save(entry)
            val followUp = encodeAppointmentFollowUp(listOf(AppointmentFollowUp("Confirm transport", true)))
            dao.save(entry.copy(kind = Kind.APPOINTMENT, title = "Example appointment", appointmentFollowUp = followUp))
            val deletedId = dao.save(entry.copy(title = "Deleted task"))
            dao.record(ActivityRecord(entryId = deletedId, recipientId = recipientId, title = "Deleted task",
                kind = Kind.TASK, day = "2026-11-11", outcome = Outcome.COMPLETED))
            dao.delete(entry.copy(id = deletedId))
            val handover = dao.save(Handover(recipientId = recipientId, recipientName = recipient.name, caregiver = "Example carer", from = 0))
            dao.save(listOf(HandoverItem(handoverId = handover, section = HandoverSection.OUTSTANDING,
                title = entry.title, day = entry.day)))
            dao.append(HandoverRevision(handoverId = handover, kind = RevisionKind.ORIGINAL, author = "Example carer"))
            val documentId = UUID.randomUUID().toString()
            val document = CareDocument(documentId, recipientId, null, handover, "Example.pdf", "application/pdf")
            source.documents().insert(document)
            val bytes = "%PDF-1.4\nExample attachment\n".toByteArray()
            File(File(sourceContext.filesDir, "care_documents").apply { check(mkdirs()) }, documentId).writeBytes(bytes)
            val archive = ByteArrayOutputStream()
            CareBackup(sourceContext, source).export(phrase) { archive }
            val encoded = archive.toByteArray()
            assertFalse(encoded.toString(Charsets.ISO_8859_1).contains("Example attachment"))

            val restorer = CareBackup(targetContext, target)
            expectFailure(GeneralSecurityException::class.java) {
                restorer.restore("incorrect passphrase".toCharArray()) { ByteArrayInputStream(encoded) }
            }
            assertTrue(target.care().recipients().first().isEmpty())

            val plain = BackupCipher.open(ByteArrayInputStream(encoded), phrase)
            val truncatedBody = ByteArrayOutputStream()
            BackupCipher.seal(plain.copyOf(plain.size - 1), phrase, truncatedBody)
            plain.fill(0)
            expectFailure(java.io.EOFException::class.java) {
                restorer.restore(phrase) { ByteArrayInputStream(truncatedBody.toByteArray()) }
            }
            assertTrue(target.care().recipients().first().isEmpty())
            assertTrue(File(targetContext.filesDir, "care_documents").listFiles().orEmpty().isEmpty())

            restorer.restore(phrase) { ByteArrayInputStream(encoded) }
            assertEquals(recipient.copy(id = recipientId), target.care().recipients().first().single())
            val entries = target.care().entries().first()
            assertEquals(4, entries.single { it.kind == Kind.TASK }.weekdays)
            assertEquals(followUp, entries.single { it.kind == Kind.APPOINTMENT }.appointmentFollowUp)
            assertEquals("Deleted task", target.care().activity().first().single().title)
            assertEquals(dao.handovers().first(), target.care().handovers().first())
            val copied = target.documents().observe().first().single()
            assertEquals(document.copy(id = copied.id), copied)
            assertArrayEquals(bytes, File(File(targetContext.filesDir, "care_documents"), copied.id).readBytes())
            assertTrue(target.care().save(entry.copy(title = "After restore")) > deletedId)

            expectFailure(BackupTargetNotEmpty::class.java) {
                restorer.restore(phrase) { ByteArrayInputStream(encoded) }
            }
            assertEquals(3, target.care().entries().first().size)
        } finally {
            phrase.fill('\u0000')
            source.close()
            target.close()
            temp.deleteRecursively()
        }
    }

    private suspend fun expectFailure(type: Class<out Exception>, operation: suspend () -> Unit) {
        try {
            operation()
            fail("Expected ${type.simpleName}")
        } catch (error: Exception) {
            if (!type.isInstance(error)) throw error
        }
    }

    private fun filesAt(context: Context, path: File): Context = object : ContextWrapper(context) {
        override fun getFilesDir(): File = path
    }
}
