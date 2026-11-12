package app.carehando.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore

val Context.carePreferences by preferencesDataStore("care_settings")

object Preferences {
    val appLock = booleanPreferencesKey("app_lock")
    val premium = booleanPreferencesKey("premium")
    val receiptId = stringPreferencesKey("receipt_id")
    val reminders = booleanPreferencesKey("reminders")
    val quietStart = intPreferencesKey("quiet_start")
    val quietEnd = intPreferencesKey("quiet_end")
    val spoken = booleanPreferencesKey("spoken")
    val darkMode = stringPreferencesKey("dark_mode")
    val mode = stringPreferencesKey("mode")
    val recipientId = longPreferencesKey("recipient_id")
}


