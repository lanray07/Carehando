package app.carehando.data

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal class BackupTooLarge : Exception()
internal class BackupTargetNotEmpty : Exception()

internal object BackupCipher {
    const val MAX_BYTES = 16 * 1024 * 1024
    const val MIN_PASSPHRASE = 12
    const val MAX_PASSPHRASE = 128
    private const val ITERATIONS = 600_000
    private const val TAG_BYTES = 16
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private val magic = "CAREHANDOBK1".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()

    fun seal(plain: ByteArray, passphrase: CharArray, output: OutputStream) {
        if (plain.size > MAX_BYTES) throw BackupTooLarge()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = magic + salt + nonce
        val cipher = cipher(Cipher.ENCRYPT_MODE, passphrase, salt, nonce, header)
        output.write(header)
        output.write(cipher.doFinal(plain))
    }

    fun open(input: InputStream, passphrase: CharArray): ByteArray {
        val reader = DataInputStream(input)
        val header = ByteArray(magic.size + SALT_BYTES + NONCE_BYTES)
        reader.readFully(header)
        require(header.copyOf(magic.size).contentEquals(magic))
        val encrypted = BoundedBackupBuffer(MAX_BYTES + TAG_BYTES)
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = reader.read(buffer)
            if (count == -1) break
            encrypted.write(buffer, 0, count)
        }
        val salt = header.copyOfRange(magic.size, magic.size + SALT_BYTES)
        val nonce = header.copyOfRange(magic.size + SALT_BYTES, header.size)
        return cipher(Cipher.DECRYPT_MODE, passphrase, salt, nonce, header)
            .doFinal(encrypted.toByteArray())
    }

    private fun cipher(mode: Int, passphrase: CharArray, salt: ByteArray, nonce: ByteArray, header: ByteArray): Cipher {
        require(passphrase.size in MIN_PASSPHRASE..MAX_PASSPHRASE)
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(header)
            }
        } finally {
            key.fill(0)
        }
    }
}

internal class BoundedBackupBuffer(private val maximum: Int = BackupCipher.MAX_BYTES) : ByteArrayOutputStream() {
    override fun write(value: Int) {
        if (size() >= maximum) throw BackupTooLarge()
        super.write(value)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length > maximum - size()) throw BackupTooLarge()
        super.write(bytes, offset, length)
    }

    fun clearBytes() {
        buf.fill(0)
        reset()
    }
}
