package app.carehando.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException

class BackupCipherTest {
    @Test fun freshSaltAndNonceProduceDifferentArchivesThatAuthenticate() {
        val phrase = "a unique backup phrase".toCharArray()
        val body = "private example".toByteArray()
        fun seal() = ByteArrayOutputStream().apply { BackupCipher.seal(body, phrase, this) }.toByteArray()
        val archive = seal()
        assertFalse(archive.contentEquals(seal()))
        assertArrayEquals(body, BackupCipher.open(ByteArrayInputStream(archive), phrase))
        assertThrows(GeneralSecurityException::class.java) {
            BackupCipher.open(ByteArrayInputStream(archive), "a different passphrase".toCharArray())
        }
        archive[archive.lastIndex] = (archive.last().toInt() xor 1).toByte()
        assertThrows(GeneralSecurityException::class.java) {
            BackupCipher.open(ByteArrayInputStream(archive), phrase)
        }
    }

    @Test fun aBoundedBufferRefusesExcessData() {
        val buffer = BoundedBackupBuffer(8)
        buffer.write(ByteArray(8))
        assertThrows(BackupTooLarge::class.java) { buffer.write(1) }
        assertEquals(8, buffer.size())
        buffer.clearBytes()
        assertEquals(0, buffer.size())
    }
}
