package app.carehando.data

import org.junit.Assert.*
import org.junit.Test

class AttachmentTest {
    @Test fun verifiesSignatureInsteadOfRelyingOnFileName() {
        assertEquals("application/pdf", attachmentMime("%PDF-1.7".toByteArray(), 8))
        assertEquals("image/webp", attachmentMime("RIFF0000WEBP".toByteArray(), 12))
        assertNull(attachmentMime("RIFF0000WAVE".toByteArray(), 12))
        assertNull(attachmentMime("%PDF-1.7".toByteArray(), 4))
        assertNull(attachmentMime("<html>".toByteArray(), 6))
    }
}
