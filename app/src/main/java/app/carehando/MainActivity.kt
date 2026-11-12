package app.carehando

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import app.carehando.data.Preferences
import app.carehando.data.ReminderWorker
import app.carehando.data.carePreferences
import app.carehando.ui.Carehando
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val careViewModel by lazy { ViewModelProvider(this)[CareViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        careViewModel.premium.connect()
        enableEdgeToEdge()
        lifecycleScope.launch {
            ReminderWorker.configure(this@MainActivity, carePreferences.data.first()[Preferences.reminders] == true)
        }
        setContent { Carehando(careViewModel) }
    }

    override fun onResume() {
        super.onResume()
        careViewModel.premium.restore()
    }
}
