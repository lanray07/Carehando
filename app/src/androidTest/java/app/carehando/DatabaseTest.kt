package app.carehando

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import app.carehando.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DatabaseTest {
    @Test fun installedDatabaseOpensAtCurrentSchema() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = CareDatabase.open(context)
        assertEquals(4, database.openHelper.writableDatabase.version)
        runBlocking { database.documents().ids() }
    }
    @Test fun deletionCascadesWhileIndividualEntryDeletionPreservesHistory() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CareDatabase::class.java).build()
        try {
            val dao = db.care()
            val profile = Recipient(name = "Example", communicationLanguage = "English", mobilityNotes = "Use the handrail",
                dietaryNotes = "No added salt", routineNotes = "Morning walk", healthNotes = "Example note", preferredClinic = "Local clinic")
            val profileId = dao.save(profile)
            assertEquals(profile.copy(id = profileId), dao.recipient(profileId))
            val carer = Caregiver(name = "Example carer", role = "Family", contactDetails = "Example number", preferredLanguage = "English")
            val carerId = dao.save(carer)
            assertEquals(carer.copy(id = carerId), dao.caregivers().first().single())
            val appointment = Entry(recipientId = profileId, kind = Kind.APPOINTMENT, title = "Visit", day = "2026-11-11")
            val id = dao.save(appointment)
            dao.record(ActivityRecord(entryId = id, recipientId = profileId, title = appointment.title,
                kind = appointment.kind, day = appointment.day, outcome = Outcome.COMPLETED))
            db.documents().insert(CareDocument("fixture-document", profileId, id, null, "Example.pdf", "application/pdf"))
            dao.delete(appointment.copy(id = id))
            assertTrue(db.documents().ids().isEmpty())
            assertTrue(dao.entries().first().isEmpty())
            assertEquals(1, dao.activity().first().size)

            db.withTransaction {
                val handoverId = dao.save(Handover(recipientId = profileId, recipientName = profile.name, caregiver = "Example", from = 0))
                dao.append(HandoverRevision(handoverId = handoverId, kind = RevisionKind.ORIGINAL, author = "Example"))
            }
            assertEquals(1, dao.handovers().first().single().revisions.size)
            dao.delete(profile.copy(id = profileId))
            assertTrue(dao.activity().first().isEmpty())
            assertTrue(dao.handovers().first().isEmpty())
        } finally {
            db.close()
        }
    }
}



