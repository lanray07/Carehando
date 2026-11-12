package app.carehando.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.room.*
import androidx.work.*
import app.carehando.MainActivity
import app.carehando.R
import app.carehando.domain.occursOn
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

@Entity(tableName = "reminder_deliveries", primaryKeys = ["entryId", "day"],
    foreignKeys = [ForeignKey(entity = Entry::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)])
data class ReminderDelivery(val entryId: Long, val day: String, val at: Long)

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun claim(delivery: ReminderDelivery): Long

    @Query("SELECT * FROM reminder_deliveries WHERE `at` = :batch")
    suspend fun batch(batch: Long): List<ReminderDelivery>
}

internal fun Entry.dueReminderDates(now: LocalDateTime): List<LocalDate> {
    val due = LocalTime.parse(time.ifEmpty { return emptyList() })
    val earliest = now.minusHours(2)
    return listOf(now.toLocalDate(), earliest.toLocalDate()).distinct().filter { date ->
        occursOn(date) && date.atTime(due) in earliest..now
    }
}

internal fun inQuietHours(hour: Int, start: Int, end: Int): Boolean =
    if (start <= end) hour in start until end else hour >= start || hour < end

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val preferences = context.carePreferences.data.first()
        val manager = context.getSystemService(NotificationManager::class.java)
        if (preferences[Preferences.reminders] != true || !manager.areNotificationsEnabled()) return Result.success()
        val now = LocalDateTime.now()
        if (inQuietHours(now.hour, preferences[Preferences.quietStart] ?: 22, preferences[Preferences.quietEnd] ?: 7)) {
            return Result.success()
        }

        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.reminders), NotificationManager.IMPORTANCE_DEFAULT))
        if (manager.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE) return Result.success()
        val db = CareDatabase.open(context)
        val snoozed = inputData.getLong(BATCH, 0L)
        val batch = if (snoozed > 0L) snoozed else System.currentTimeMillis()
        val pending = db.withTransaction {
            val eligible = db.care().reminderEntries()
            val due = if (snoozed == 0L) {
                eligible.flatMap { entry ->
                    entry.dueReminderDates(now).map { date -> ReminderDelivery(entry.id, date.toString(), batch) }
                }
            } else if (System.currentTimeMillis() - snoozed in 0..MAX_SNOOZE_AGE_MS) {
                val entries = eligible.associateBy { it.id }
                db.reminders().batch(snoozed).filter { delivery ->
                    val entry = entries[delivery.entryId] ?: return@filter false
                    val date = LocalDate.parse(delivery.day)
                    entry.time.isNotEmpty() && entry.occursOn(date) && date.atTime(LocalTime.parse(entry.time)) <= now
                }
            } else emptyList()
            val unconfirmed = due.filter {
                (db.care().latestActivity(it.entryId, it.day)?.outcome ?: Outcome.UNCONFIRMED) == Outcome.UNCONFIRMED
            }
            if (snoozed > 0L) unconfirmed.isNotEmpty()
            else unconfirmed.map { db.reminders().claim(it) }.any { it != -1L }
        }
        if (pending) post(context, manager, batch)
        return Result.success()
    }

    private fun post(context: Context, manager: NotificationManager, batch: Long) {
        val tap = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val snooze = PendingIntent.getBroadcast(context, 0, Intent(context, SnoozeReminderReceiver::class.java)
            .setData(Uri.fromParts("carehando-snooze", batch.toString(), null))
            .putExtra(BATCH, batch), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.notification_mark)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.reminder_body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(tap)
            .addAction(0, context.getString(R.string.snooze_reminder), snooze)
            .addExtras(Bundle().apply { putLong(BATCH, batch) })
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        internal const val BATCH = "reminder_batch"
        internal const val NOTIFICATION_ID = 1
        internal const val SNOOZE_TAG = "care_reminder_snooze"
        private const val CHANNEL = "care_reminders"
        private const val MAX_SNOOZE_AGE_MS = 12L * 60 * 60 * 1000

        fun configure(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (enabled) {
                work.enqueueUniquePeriodicWork(CHANNEL, ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build())
            } else {
                work.cancelUniqueWork(CHANNEL)
                work.cancelAllWorkByTag(SNOOZE_TAG)
                context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            }
        }
    }
}

class SnoozeReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val batch = intent.getLongExtra(ReminderWorker.BATCH, 0L)
        if (batch <= 0L) return
        val manager = context.getSystemService(NotificationManager::class.java)
        fun isCurrentBatch() = manager.activeNotifications.any {
            it.id == ReminderWorker.NOTIFICATION_ID && it.notification.extras.getLong(ReminderWorker.BATCH) == batch
        }
        if (!isCurrentBatch()) return
        val work = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(workDataOf(ReminderWorker.BATCH to batch))
            .setInitialDelay(15, TimeUnit.MINUTES)
            .addTag(ReminderWorker.SNOOZE_TAG)
            .build()
        val operation = WorkManager.getInstance(context)
            .enqueueUniqueWork("${ReminderWorker.SNOOZE_TAG}:$batch", ExistingWorkPolicy.REPLACE, work)
        val pending = goAsync()
        operation.result.addListener({
            try {
                if (runCatching { operation.result.get() }.isSuccess && isCurrentBatch()) {
                    manager.cancel(ReminderWorker.NOTIFICATION_ID)
                }
            } finally {
                pending.finish()
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
