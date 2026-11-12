package app.carehando.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.carehando.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfPreview(title: Int, lines: List<String>) {
    var visible by remember { mutableStateOf(false) }
    OutlinedButton({ visible = !visible }) {
        Text(stringResource(if (visible) R.string.hide_pdf_preview else R.string.show_pdf_preview))
    }
    if (visible) {
        val context = LocalContext.current
        val name = stringResource(title)
        var image by remember { mutableStateOf<Bitmap?>(null) }
        var failed by remember { mutableStateOf(false) }
        LaunchedEffect(name, lines) {
            image = null
            failed = false
            try {
                image = withContext(Dispatchers.IO) {
                    val file = File.createTempFile("report-preview-", ".pdf", context.cacheDir)
                    try {
                        file.outputStream().use { writePdf(it, name, lines) }
                        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        PdfRenderer(descriptor).use { renderer ->
                            renderer.openPage(0).use { page ->
                                Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                                    .apply {
                                        eraseColor(Color.WHITE)
                                        page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                            }
                        }
                    } finally {
                        file.delete()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            }
        }
        val page = image
        when {
            failed -> Note(R.string.pdf_preview_failed)
            page == null -> CircularProgressIndicator(Modifier.size(30.dp))
            else -> Image(page.asImageBitmap(), contentDescription = stringResource(R.string.pdf_first_page),
                modifier = Modifier.fillMaxWidth().aspectRatio(page.width.toFloat() / page.height))
        }
    }
}
