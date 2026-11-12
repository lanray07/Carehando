package app.carehando.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "recipients")
data class Recipient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val preferences: String = "",
    val emergency: String = "",
    val archived: Boolean = false,
    @ColumnInfo(defaultValue = "''") val communicationLanguage: String = "",
    @ColumnInfo(defaultValue = "''") val mobilityNotes: String = "",
    @ColumnInfo(defaultValue = "''") val dietaryNotes: String = "",
    @ColumnInfo(defaultValue = "''") val routineNotes: String = "",
    @ColumnInfo(defaultValue = "''") val healthNotes: String = "",
    @ColumnInfo(defaultValue = "''") val preferredClinic: String = "",
)

@Entity(tableName = "caregivers")
data class Caregiver(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: String,
    val availability: String = "",
    @ColumnInfo(defaultValue = "''") val contactDetails: String = "",
    @ColumnInfo(defaultValue = "''") val preferredLanguage: String = "",
)

enum class Kind { TASK, ROUTINE, APPOINTMENT, MEDICATION, JOURNAL }
enum class Repeat { ONCE, DAILY, WEEKLY, SELECTED_DAYS }
enum class Outcome { COMPLETED, TAKEN, SKIPPED, UNCONFIRMED }
enum class HandoverSection { COMPLETED, OUTSTANDING, APPOINTMENTS, MEDICATION, OBSERVATIONS }

@Entity(
    tableName = "entries",
    foreignKeys = [ForeignKey(entity = Recipient::class, parentColumns = ["id"], childColumns = ["recipientId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Caregiver::class, parentColumns = ["id"], childColumns = ["caregiverId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("recipientId"), Index("caregiverId")],
)
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipientId: Long,
    val kind: Kind,
    val title: String,
    val details: String = "",
    val day: String,
    val time: String = "",
    val repeat: Repeat = Repeat.ONCE,
    val endDay: String = "",
    val caregiverId: Long? = null,
    val priority: Boolean = false,
    val paused: Boolean = false,
    val location: String = "",
    val dosage: String = "",
    val provider: String = "",
    val checklist: String = "",
    val reminder: Boolean = false,
    val recordedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val weekdays: Int = 0,
    @ColumnInfo(defaultValue = "''") val appointmentFollowUp: String = "",
)

@Entity(
    tableName = "activity",
    foreignKeys = [ForeignKey(entity = Recipient::class, parentColumns = ["id"], childColumns = ["recipientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recipientId"), Index(value = ["entryId", "day", "id"])],
)
data class ActivityRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val recipientId: Long,
    val title: String,
    val kind: Kind,
    val day: String,
    val outcome: Outcome,
    val at: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "handovers",
    foreignKeys = [ForeignKey(entity = Recipient::class, parentColumns = ["id"], childColumns = ["recipientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recipientId")],
)
data class Handover(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipientId: Long,
    val recipientName: String,
    val caregiver: String,
    val from: Long,
    val savedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "handover_items",
    foreignKeys = [ForeignKey(entity = Handover::class, parentColumns = ["id"], childColumns = ["handoverId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("handoverId")],
)
data class HandoverItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val handoverId: Long = 0,
    val section: HandoverSection,
    val title: String,
    val day: String,
    val time: String = "",
    val outcome: Outcome? = null,
)

enum class RevisionKind { ORIGINAL, CORRECTION, ACKNOWLEDGEMENT }

@Entity(
    tableName = "handover_revisions",
    foreignKeys = [ForeignKey(entity = Handover::class, parentColumns = ["id"], childColumns = ["handoverId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("handoverId")],
)
data class HandoverRevision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val handoverId: Long,
    val kind: RevisionKind,
    val author: String,
    val notes: String = "",
    val followUp: String = "",
    val at: Long = System.currentTimeMillis(),
)

data class SavedHandover(
    @Embedded val handover: Handover,
    @Relation(parentColumn = "id", entityColumn = "handoverId") val items: List<HandoverItem>,
    @Relation(parentColumn = "id", entityColumn = "handoverId") val revisions: List<HandoverRevision>,
)

@Dao
interface CareDao {
    @Query("SELECT * FROM recipients ORDER BY archived, name") fun recipients(): Flow<List<Recipient>>
    @Query("SELECT * FROM recipients WHERE id = :id") suspend fun recipient(id: Long): Recipient?
    @Upsert suspend fun save(recipient: Recipient): Long
    @Delete suspend fun delete(recipient: Recipient)
    @Query("SELECT * FROM caregivers ORDER BY name") fun caregivers(): Flow<List<Caregiver>>
    @Upsert suspend fun save(caregiver: Caregiver): Long
    @Delete suspend fun delete(caregiver: Caregiver)
    @Query("SELECT * FROM entries ORDER BY day, time, id") fun entries(): Flow<List<Entry>>
    @Query("SELECT * FROM entries WHERE id = :id") suspend fun entry(id: Long): Entry?
    @Query("SELECT entries.* FROM entries JOIN recipients ON entries.recipientId = recipients.id WHERE recipients.archived = 0 AND entries.reminder = 1 AND entries.paused = 0")
    suspend fun reminderEntries(): List<Entry>
    @Upsert suspend fun save(entry: Entry): Long
    @Delete suspend fun delete(entry: Entry)
    @Query("SELECT * FROM activity ORDER BY id DESC") fun activity(): Flow<List<ActivityRecord>>
    @Query("SELECT * FROM activity WHERE entryId = :entry AND day = :day ORDER BY id DESC LIMIT 1")
    suspend fun latestActivity(entry: Long, day: String): ActivityRecord?
    @Insert suspend fun record(activity: ActivityRecord)
    @Transaction @Query("SELECT * FROM handovers ORDER BY savedAt DESC, id DESC")
    fun handovers(): Flow<List<SavedHandover>>
    @Query("SELECT recipientId FROM handovers WHERE id = :id") suspend fun handoverRecipient(id: Long): Long?
    @Insert suspend fun save(handover: Handover): Long
    @Insert suspend fun save(items: List<HandoverItem>)
    @Insert suspend fun append(revision: HandoverRevision)
    @Query("DELETE FROM recipients") suspend fun clearRecipients()
    @Query("DELETE FROM caregivers") suspend fun clearCaregivers()
}

@Database(
    entities = [Recipient::class, Caregiver::class, Entry::class, ActivityRecord::class, Handover::class,
        HandoverItem::class, HandoverRevision::class, ReminderDelivery::class, CareDocument::class],
    version = 5,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)],
)
abstract class CareDatabase : RoomDatabase() {
    abstract fun care(): CareDao
    abstract fun reminders(): ReminderDao
    abstract fun documents(): DocumentsDao

    companion object {
        @Volatile private var instance: CareDatabase? = null

        fun open(context: android.content.Context): CareDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CareDatabase::class.java, "carehando.db")
                .build().also { instance = it }
        }
    }
}

