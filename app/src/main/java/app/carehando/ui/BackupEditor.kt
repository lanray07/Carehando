package app.carehando.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.carehando.CareViewModel
import app.carehando.R
import app.carehando.data.BackupCipher
import app.carehando.data.BackupTargetNotEmpty
import app.carehando.data.BackupTooLarge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun BackupEditor(vm: CareViewModel, canExport: Boolean, canRestore: Boolean, close: () -> Unit) {
    var exporting by remember { mutableStateOf<Boolean?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var waitingKey by remember { mutableStateOf<CharArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        onDispose { waitingKey?.fill('\u0000') }
    }

    fun transfer(uri: Uri?, export: Boolean) {
        val key = waitingKey ?: return
        waitingKey = null
        if (uri == null) {
            key.fill('\u0000')
            return
        }
        busy = true
        scope.launch {
            try {
                if (export) vm.exportBackup(uri, key) else vm.restoreBackup(uri, key)
                notice = if (export) R.string.backup_saved else R.string.backup_restored
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: BackupTargetNotEmpty) {
                notice = R.string.backup_requires_empty
            } catch (_: BackupTooLarge) {
                notice = R.string.backup_size_limit
            } catch (_: Exception) {
                notice = if (export) R.string.backup_save_error else R.string.backup_open_error
            } finally {
                key.fill('\u0000')
                busy = false
            }
        }
    }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        transfer(it, true)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { transfer(it, false) }
    Editor(R.string.encrypted_backup, { if (!busy && waitingKey == null) close() }) {
        Note(R.string.backup_contents)
        if (!canExport) Note(R.string.backup_premium_export)
        if (!canRestore) Note(R.string.backup_requires_empty)
        notice?.let { Note(it) }
        if (busy) CircularProgressIndicator()
        val export = exporting
        if (export == null) {
            OutlinedButton({ exporting = true; notice = null }, enabled = canExport && !busy && waitingKey == null) {
                Text(stringResource(R.string.backup_create))
            }
            OutlinedButton({ exporting = false; notice = null }, enabled = canRestore && !busy && waitingKey == null) {
                Text(stringResource(R.string.backup_restore))
            }
        } else {
            Note(R.string.backup_passphrase_hint)
            BackupPassword(passphrase, { passphrase = it }, R.string.backup_passphrase)
            if (export) BackupPassword(confirmation, { confirmation = it }, R.string.backup_confirm_passphrase)
            Button({
                waitingKey = passphrase.toCharArray()
                passphrase = ""
                confirmation = ""
                exporting = null
                try {
                    if (export) create.launch("Carehando.carebackup") else open.launch(arrayOf("*/*"))
                } catch (_: ActivityNotFoundException) {
                    waitingKey?.fill('\u0000')
                    waitingKey = null
                    notice = if (export) R.string.backup_save_error else R.string.backup_open_error
                }
            }, enabled = passphrase.length in BackupCipher.MIN_PASSPHRASE..BackupCipher.MAX_PASSPHRASE &&
                (!export || passphrase == confirmation)) {
                Text(stringResource(if (export) R.string.backup_choose_destination else R.string.backup_choose_file))
            }
            TextButton({
                passphrase = ""
                confirmation = ""
                exporting = null
            }) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun BackupPassword(value: String, update: (String) -> Unit, label: Int) {
    OutlinedTextField(value, { if (it.length <= BackupCipher.MAX_PASSPHRASE) update(it) },
        label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
}
