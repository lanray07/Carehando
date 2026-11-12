package app.carehando.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(entity = Recipient::class, parentColumns = ["id"], childColumns = ["recipientId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Entry::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Handover::class, parentColumns = ["id"], childColumns = ["handoverId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("recipientId"), Index("entryId"), Index("handoverId")],
)
data class CareDocument(
    @PrimaryKey val id: String,
    val recipientId: Long,
    val entryId: Long?,
    val handoverId: Long?,
    val name: String,
    val mime: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Dao
interface DocumentsDao {
    @Query("SELECT * FROM documents ORDER BY addedAt DESC") fun observe(): Flow<List<CareDocument>>
    @Query("SELECT id FROM documents") suspend fun ids(): List<String>
    @Insert suspend fun insert(document: CareDocument)
    @Delete suspend fun delete(document: CareDocument)
}

class CareDocuments(private val app: Context, private val database: CareDatabase) {
    private val folder = File(app.filesDir, "care_documents")
    private val resolver = app.contentResolver
    private val dao = database.documents()

    suspend fun import(
        uri: Uri,
        recipientId: Long,
        entryId: Long? = null,
        handoverId: Long? = null,
        imagesOnly: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        fileAccess.withLock {
            require(uri.scheme == "content")
            require(entryId == null || handoverId == null)
            check(database.care().recipient(recipientId)?.archived == false)
            if (entryId != null) check(database.care().entry(entryId)?.recipientId == recipientId)
            if (handoverId != null) check(database.care().handoverRecipient(handoverId) == recipientId)
            val id = UUID.randomUUID().toString()
            val name = displayName(uri)
            check(folder.isDirectory || folder.mkdirs())
            val staged = File(folder, "$id.pending")
            val stored = File(folder, id)
            var persisted = false
            try {
                val mime = requireNotNull(resolver.openInputStream(uri)).buffered().use { input ->
                    input.mark(16)
                    val header = ByteArray(12)
                    java.io.DataInputStream(input).readFully(header)
                    input.reset()
                    val type = requireNotNull(attachmentMime(header, header.size))
                    require(!imagesOnly || type.startsWith("image/"))
                    staged.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= MAX_BYTES)
                            output.write(buffer, 0, read)
                        }
                        output.fd.sync()
                    }
                    type
                }
                check(staged.renameTo(stored))
                dao.insert(CareDocument(id, recipientId, entryId, handoverId, name, mime))
                persisted = true
            } finally {
                staged.delete()
                if (!persisted && id !in dao.ids()) stored.delete()
            }
        }
    }

    suspend fun delete(document: CareDocument) = withContext(Dispatchers.IO) {
        fileAccess.withLock {
            dao.delete(document)
            File(folder, document.id).delete()
        }
    }

    suspend fun prune() = withContext(Dispatchers.IO) {
        fileAccess.withLock {
            val used = dao.ids().toHashSet()
            folder.listFiles()?.forEach { file -> if (file.name !in used) file.delete() }
        }
    }

    private fun displayName(uri: Uri): String {
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.take(160)?.replace(Regex("[\\r\\n\\u0000]"), " ") else null
        }.orEmpty()
    }

    companion object {
        const val MAX_BYTES = 10L * 1024 * 1024
        internal val fileAccess = Mutex()

        fun uri(context: Context, document: CareDocument): Uri {
            val file = File(File(context.filesDir, "care_documents"), document.id)
            check(file.isFile)
            return FileProvider.getUriForFile(context, context.packageName + ".documents", file)
        }
    }
}

internal fun attachmentMime(bytes: ByteArray, count: Int): String? {
    fun startsAt(offset: Int, signature: List<Int>): Boolean =
        count >= offset + signature.size && signature.indices.all { (bytes[offset + it].toInt() and 255) == signature[it] }
    return when {
        startsAt(0, listOf(0x25, 0x50, 0x44, 0x46, 0x2D)) -> "application/pdf"
        startsAt(0, listOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> "image/png"
        startsAt(0, listOf(0xFF, 0xD8, 0xFF)) -> "image/jpeg"
        startsAt(0, listOf(0x52, 0x49, 0x46, 0x46)) && startsAt(8, listOf(0x57, 0x45, 0x42, 0x50)) -> "image/webp"
        else -> null
    }
}


