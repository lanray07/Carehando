package app.carehando.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.carehando.CareViewModel
import app.carehando.R
import app.carehando.data.CareDocument
import app.carehando.data.CareDocuments

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentPanel(
    vm: CareViewModel,
    documents: List<CareDocument>,
    recipientId: Long,
    entryId: Long? = null,
    handoverId: Long? = null,
    imagesOnly: Boolean = false,
) {
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.addDocument(it, recipientId, entryId, handoverId, imagesOnly) }
    }
    val matches = documents.filter { it.recipientId == recipientId && it.entryId == entryId && it.handoverId == handoverId }
    var toOpen by remember { mutableStateOf<CareDocument?>(null) }
    var toDelete by remember { mutableStateOf<CareDocument?>(null) }
    var error by remember { mutableStateOf(false) }
    val chooser = stringResource(R.string.open_attachment)
    Panel {
        Heading(if (imagesOnly) R.string.profile_photos else R.string.attachments)
        if (matches.isEmpty()) Note(R.string.no_attachments)
        matches.forEach { document ->
            Text(document.name.ifBlank { stringResource(R.string.unnamed_attachment) })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ toOpen = document }) { Text(stringResource(R.string.open_attachment)) }
                TextButton({ toDelete = document }) { Text(stringResource(R.string.delete_attachment)) }
            }
        }
        if (error) Note(R.string.attachment_open_failed)
        OutlinedButton({
            error = false
            pick.launch(if (imagesOnly) arrayOf("image/*") else arrayOf("application/pdf", "image/*"))
        }) { Text(stringResource(if (imagesOnly) R.string.add_photo else R.string.add_attachment)) }
        Note(R.string.attachment_types)
    }
    toOpen?.let { document ->
        Confirm(R.string.open_attachment, R.string.export_notice, { toOpen = null }) {
            toOpen = null
            error = runCatching {
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(CareDocuments.uri(context, document), document.mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(intent, chooser))
            }.isFailure
        }
    }
    toDelete?.let { document ->
        Confirm(R.string.delete_attachment, R.string.delete_attachment_body, { toDelete = null }) {
            vm.deleteDocument(document)
            toDelete = null
        }
    }
}
