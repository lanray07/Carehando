package app.carehando

import android.app.NotificationManager
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.carehando.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class ReminderDeviceTest {
    @Test fun snoozeSchedulesReplayAndCompletedOccurrenceSuppressesIt() = runBlocking<Unit>(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        assumeTrue(manager.areNotificationsEnabled())
        assumeTrue(manager.getNotificationChannel("care_reminders")?.importance != NotificationManager.IMPORTANCE_NONE)
        val db = CareDatabase.open(context)
        val dao = db.care()
        val preferences = context.carePreferences
        val previous = preferences.data.first()
        val work = WorkManager.getInstance(context)
        val recipient = Recipient(name = "Reminder QA")
        val recipientId = dao.save(recipient)
        var batch = 0L
        try {
            preferences.edit {
                it[Preferences.reminders] = true
                it[Preferences.quietStart] = 0
                it[Preferences.quietEnd] = 0
            }
            val due = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
            val day = due.toLocalDate().toString()
            val entry = Entry(recipientId = recipientId, kind = Kind.TASK, title = "Reminder QA task",
                day = day, time = due.toLocalTime().toString(), reminder = true)
            val entryId = dao.save(entry)
            runWorker(work)
            val reminder = manager.activeNotifications.single { it.id == ReminderWorker.NOTIFICATION_ID }.notification
            assertEquals(context.getString(R.string.reminder_body), reminder.extras.getCharSequence("android.text")?.toString())
            batch = reminder.extras.getLong(ReminderWorker.BATCH)
            assertTrue(batch > 0L)
            assertTrue(db.reminders().batch(batch).any { it.entryId == entryId && it.day == day })
            reminder.actions.single().actionIntent.send()
            val name = "${ReminderWorker.SNOOZE_TAG}:$batch"
            val scheduled = withTimeout(10_000) {
                var pending: WorkInfo? = null
                while (pending == null) {
                    pending = work.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS)
                        .firstOrNull { it.state == WorkInfo.State.ENQUEUED }
                    if (pending == null) delay(100)
                }
                pending
            }
            assertEquals(WorkInfo.State.ENQUEUED, scheduled.state)
            withTimeout(5_000) {
                while (manager.activeNotifications.any { it.id == ReminderWorker.NOTIFICATION_ID }) delay(50)
            }

            runWorker(work, batch)
            assertTrue(manager.activeNotifications.any { it.id == ReminderWorker.NOTIFICATION_ID })
            manager.cancel(ReminderWorker.NOTIFICATION_ID)
            dao.record(ActivityRecord(entryId = entryId, recipientId = recipientId, title = entry.title,
                kind = Kind.TASK, day = day, outcome = Outcome.COMPLETED))
            runWorker(work, batch)
            assertFalse(manager.activeNotifications.any { it.id == ReminderWorker.NOTIFICATION_ID })
        } finally {
            if (batch > 0L) work.cancelUniqueWork("${ReminderWorker.SNOOZE_TAG}:$batch").result.get(10, TimeUnit.SECONDS)
            manager.cancel(ReminderWorker.NOTIFICATION_ID)
            dao.delete(recipient.copy(id = recipientId))
            preferences.edit { current ->
                val reminders = previous[Preferences.reminders]
                val start = previous[Preferences.quietStart]
                val end = previous[Preferences.quietEnd]
                if (reminders == null) current.remove(Preferences.reminders) else current[Preferences.reminders] = reminders
                if (start == null) current.remove(Preferences.quietStart) else current[Preferences.quietStart] = start
                if (end == null) current.remove(Preferences.quietEnd) else current[Preferences.quietEnd] = end
            }
        }
    }

    private suspend fun runWorker(work: WorkManager, batch: Long = 0L) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(workDataOf(ReminderWorker.BATCH to batch))
            .build()
        work.enqueue(request).result.get(10, TimeUnit.SECONDS)
        val result = withTimeout(15_000) { work.getWorkInfoByIdFlow(request.id).first { it?.state?.isFinished == true } }
        assertEquals(WorkInfo.State.SUCCEEDED, result?.state)
    }
}
