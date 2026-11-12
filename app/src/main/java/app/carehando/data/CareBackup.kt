package app.carehando.data

import android.content.ContentValues
import android.content.Context
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

internal class CareBackup(context: Context, private val database: CareDatabase) {
    private val folder = File(context.filesDir, "care_documents")

    suspend fun export(passphrase: CharArray, destination: () -> OutputStream) = withContext(Dispatchers.IO) {
        val body = CareDocuments.fileAccess.withLock {
            database.withTransaction { snapshot(database.openHelper.readableDatabase) }
        }
        try {
            destination().use { BackupCipher.seal(body, passphrase, it) }
        } finally {
            body.fill(0)
        }
    }

    suspend fun restore(passphrase: CharArray, source: () -> InputStream) = withContext(Dispatchers.IO) {
        val body = source().use { BackupCipher.open(it, passphrase) }
        try {
            CareDocuments.fileAccess.withLock {
                val created = mutableListOf<File>()
                var committed = false
                try {
                    withContext(NonCancellable) {
                        database.withTransaction {
                            val db = database.openHelper.writableDatabase
                            if (!empty(db, "recipients") || !empty(db, "caregivers")) throw BackupTargetNotEmpty()
                            restoreSnapshot(db, body, created)
                        }
                        committed = true
                    }
                } finally {
                    if (!committed) created.forEach(File::delete)
                }
            }
        } finally {
            body.fill(0)
        }
    }

    private fun snapshot(db: SupportSQLiteDatabase): ByteArray {
        val buffer = BoundedBackupBuffer()
        try {
            val output = DataOutputStream(buffer)
            output.writeInt(db.version)
            TABLES.forEach { table ->
                val columns = columns(db, table)
                output.writeLong(sequence(db, table))
                db.query("SELECT ${columns.joinToString(",") { quote(it.name) }} FROM ${quote(table)}").use { rows ->
                    require(rows.count <= MAX_ROWS)
                    output.writeInt(rows.count)
                    while (rows.moveToNext()) {
                        columns.forEachIndexed { index, column ->
                            val present = !rows.isNull(index)
                            output.writeBoolean(present)
                            if (present && column.text) output.writeString(rows.getString(index))
                            if (present && !column.text) output.writeLong(rows.getLong(index))
                        }
                    }
                }
            }
            db.query("SELECT id FROM documents ORDER BY id").use { rows ->
                while (rows.moveToNext()) {
                    val id = rows.getString(0)
                    require(UUID.fromString(id).toString() == id)
                    val file = File(folder, id)
                    val size = file.length()
                    require(file.isFile && size in 12..CareDocuments.MAX_BYTES)
                    output.writeString(id)
                    output.writeInt(size.toInt())
                    file.inputStream().use { input -> require(input.copyTo(output) == size) }
                }
            }
            output.flush()
            return buffer.toByteArray()
        } finally {
            buffer.clearBytes()
        }
    }

    private fun restoreSnapshot(db: SupportSQLiteDatabase, body: ByteArray, created: MutableList<File>) {
        val input = DataInputStream(ByteArrayInputStream(body))
        val schema = input.readInt()
        require(schema in 4..db.version) { "Unsupported backup schema" }
        val documents = mutableMapOf<String, Pair<String, String>>()
        TABLES.forEach { table ->
            val columns = columns(db, table).filterNot { schema == 4 && table == "entries" && it.name == "appointmentFollowUp" }
            val nextSequence = input.readLong()
            require(nextSequence in 0 until Long.MAX_VALUE)
            val count = input.readInt()
            require(count in 0..MAX_ROWS)
            repeat(count) {
                val values = ContentValues(columns.size)
                columns.forEach { column ->
                    when {
                        !input.readBoolean() -> values.putNull(column.name)
                        column.text -> values.put(column.name, input.readString())
                        else -> values.put(column.name, input.readLong())
                    }
                }
                validate(table, values)
                if (table == "documents") {
                    val old = values.getAsString("id")
                    require(UUID.fromString(old).toString() == old)
                    val id = UUID.randomUUID().toString()
                    require(documents.put(old, id to values.getAsString("mime")) == null)
                    values.put("id", id)
                }
                val names = columns.joinToString(",") { quote(it.name) }
                val parameters = columns.joinToString(",") { "?" }
                db.compileStatement("INSERT INTO ${quote(table)}($names) VALUES ($parameters)").use { insert ->
                    columns.forEachIndexed { index, column ->
                        val value = values.get(column.name)
                        when {
                            value == null -> insert.bindNull(index + 1)
                            column.text -> insert.bindString(index + 1, value as String)
                            else -> insert.bindLong(index + 1, value as Long)
                        }
                    }
                    check(insert.executeInsert() != -1L)
                }
            }
            if (nextSequence > 0) {
                if (sequence(db, table) == 0L) db.execSQL("INSERT INTO sqlite_sequence(name,seq) VALUES(?,?)", arrayOf<Any>(table, nextSequence))
                else db.execSQL("UPDATE sqlite_sequence SET seq=MAX(seq,?) WHERE name=?", arrayOf<Any>(nextSequence, table))
            }
        }
        if (documents.isNotEmpty()) check(folder.isDirectory || folder.mkdirs())
        val chunk = ByteArray(32 * 1024)
        repeat(documents.size) {
            val (id, mime) = requireNotNull(documents.remove(input.readString()))
            val length = input.readInt()
            require(length.toLong() in 12..CareDocuments.MAX_BYTES)
            input.readFully(chunk, 0, 12)
            require(attachmentMime(chunk, 12) == mime)
            val file = File(folder, id)
            check(file.createNewFile())
            created.add(file)
            file.outputStream().use { output ->
                output.write(chunk, 0, 12)
                var remaining = length - 12
                while (remaining > 0) {
                    val count = minOf(remaining, chunk.size)
                    input.readFully(chunk, 0, count)
                    output.write(chunk, 0, count)
                    remaining -= count
                }
                output.fd.sync()
            }
        }
        require(input.read() == -1)
    }

    private fun validate(table: String, row: ContentValues) {
        fun date(key: String) = row.getAsString(key)?.takeIf(String::isNotEmpty)?.let(LocalDate::parse)
        fun time(key: String) = row.getAsString(key)?.takeIf(String::isNotEmpty)?.let(LocalTime::parse)
        row.getAsString("day")?.let(LocalDate::parse)
        when (table) {
            "entries" -> {
                val kind = Kind.valueOf(row.getAsString("kind"))
                row.getAsString("appointmentFollowUp")?.takeIf(String::isNotEmpty)?.let {
                    require(kind == Kind.APPOINTMENT)
                    decodeAppointmentFollowUp(it)
                }
                val repeat = Repeat.valueOf(row.getAsString("repeat"))
                val start = requireNotNull(date("day"))
                date("endDay")?.let { require(it >= start) }
                time("time")
                val weekdays = requireNotNull(row.getAsInteger("weekdays"))
                require(weekdays in 0..127 && (repeat != Repeat.SELECTED_DAYS || weekdays != 0))
            }
            "activity" -> {
                Kind.valueOf(row.getAsString("kind"))
                Outcome.valueOf(row.getAsString("outcome"))
            }
            "handover_items" -> {
                HandoverSection.valueOf(row.getAsString("section"))
                row.getAsString("outcome")?.let(Outcome::valueOf)
                time("time")
            }
            "handover_revisions" -> RevisionKind.valueOf(row.getAsString("kind"))
        }
    }

    private data class Column(val name: String, val text: Boolean)

    private fun columns(db: SupportSQLiteDatabase, table: String): List<Column> =
        db.query("PRAGMA table_info(${quote(table)})").use { info ->
            buildList {
                val name = info.getColumnIndexOrThrow("name")
                val type = info.getColumnIndexOrThrow("type")
                while (info.moveToNext()) {
                    val affinity = info.getString(type)
                    check(affinity == "TEXT" || affinity == "INTEGER")
                    add(Column(info.getString(name), affinity == "TEXT"))
                }
            }.sortedBy { it.name }
        }

    private fun sequence(db: SupportSQLiteDatabase, table: String): Long =
        db.query("SELECT seq FROM sqlite_sequence WHERE name=?", arrayOf(table)).use { if (it.moveToFirst()) it.getLong(0) else 0 }

    private fun empty(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT 1 FROM ${quote(table)} LIMIT 1").use { !it.moveToFirst() }

    private fun quote(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_TEXT_BYTES)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt()
        require(size in 0..MAX_TEXT_BYTES && size <= available())
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }

    private companion object {
        const val MAX_ROWS = 100_000
        const val MAX_TEXT_BYTES = 256 * 1024
        val TABLES = listOf("recipients", "caregivers", "entries", "activity", "handovers", "handover_items",
            "handover_revisions", "reminder_deliveries", "documents")
    }
}
