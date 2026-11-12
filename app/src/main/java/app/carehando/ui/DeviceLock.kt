package app.carehando.ui

import android.app.Activity
import android.app.KeyguardManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.carehando.CareViewModel
import app.carehando.R

@Composable
fun DeviceLock(enabled: Boolean, vm: CareViewModel, content: @Composable () -> Unit) {
    val activity = requireNotNull(LocalActivity.current)
    val manager = activity.getSystemService(KeyguardManager::class.java)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var authError by remember { mutableStateOf(false) }
    var backgroundedAt by remember { mutableLongStateOf(0) }
    val authentication = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.deviceUnlocked = it.resultCode == Activity.RESULT_OK
        authError = !vm.deviceUnlocked
    }
    DisposableEffect(lifecycle, enabled) {
        if (enabled) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> backgroundedAt = SystemClock.elapsedRealtime()
                Lifecycle.Event.ON_START -> if (backgroundedAt != 0L &&
                    SystemClock.elapsedRealtime() - backgroundedAt >= 60_000) vm.deviceUnlocked = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (enabled) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    if (!enabled || vm.deviceUnlocked) {
        content()
    } else {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                Panel(Modifier.widthIn(max = 480.dp)) {
                    Heading(R.string.app_locked)
                    Note(R.string.lock_intro)
                    if (authError) Note(R.string.lock_error)
                    Button({
                        @Suppress("DEPRECATION")
                        val intent = manager.createConfirmDeviceCredentialIntent(
                            activity.getString(R.string.app_name), activity.getString(R.string.lock_intro))
                        if (intent == null) authError = true else authentication.launch(intent)
                    }) { Text(stringResource(R.string.unlock)) }
                }
            }
        }
    }
}

