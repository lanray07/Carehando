package app.carehando

import android.content.pm.ApplicationInfo
import android.security.NetworkSecurityPolicy
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PrivacyDeviceTest {
    @Test fun androidBackupAndCleartextAreDisabledAndFileSharingIsScoped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
        val authority = "${context.packageName}.documents"
        val provider = requireNotNull(context.packageManager.resolveContentProvider(authority, 0))
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        val allowed = File(context.filesDir, "care_documents/qa-document.pdf")
        val uri = FileProvider.getUriForFile(context, authority, allowed)
        assertEquals(authority, uri.authority)
        val privateDatabase = context.getDatabasePath("carehando.db")
        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(context, authority, privateDatabase)
        }
    }
}
