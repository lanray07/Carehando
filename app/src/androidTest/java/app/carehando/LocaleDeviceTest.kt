package app.carehando

import android.content.res.Configuration
import android.os.LocaleList
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import app.carehando.domain.Command
import app.carehando.domain.IntentDraft
import app.carehando.domain.parseIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

class LocaleDeviceTest {
    @Test fun supportedLocalesLoadLocalLabelsAndAssistantCommands() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = mapOf(
            "en-GB" to "Settings", "en-US" to "Settings", "en-AU" to "Settings",
            "es-ES" to "Ajustes", "es-419" to "Configuración", "fr-FR" to "Paramètres",
            "fr-CA" to "Paramètres", "de-DE" to "Einstellungen", "it-IT" to "Impostazioni",
            "pt-BR" to "Configurações", "pt-PT" to "Definições", "nl-NL" to "Instellingen",
            "pl-PL" to "Ustawienia", "sv-SE" to "Inställningar", "ja-JP" to "設定",
            "hi-IN" to "सेटिंग", "ar" to "الإعدادات", "tr-TR" to "Ayarlar", "id-ID" to "Pengaturan",
        )
        for ((tag, expected) in settings) {
            val locale = Locale.forLanguageTag(tag)
            val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList(locale)) }
            val local = context.createConfigurationContext(config)
            assertEquals(tag, expected, local.getString(R.string.settings))
            assertEquals(tag, if (tag == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                local.resources.configuration.layoutDirection)
            val phrases = mapOf(
                Command.TODAY to R.string.voice_today, Command.APPOINTMENTS to R.string.voice_appointments,
                Command.MEDICATION to R.string.voice_medication, Command.HANDOVER to R.string.voice_handover,
                Command.ADD_TASK to R.string.voice_task, Command.ADD_NOTE to R.string.voice_note,
            ).mapValues { local.getString(it.value).split("|") }
            val unspaced = tag == "ja-JP"
            for ((command, aliases) in phrases) {
                val takesPayload = command == Command.ADD_TASK || command == Command.ADD_NOTE
                for (alias in aliases) {
                    val text = if (takesPayload) alias + (if (unspaced) "" else " ") + "Carehando" else alias
                    assertEquals("$tag $command", IntentDraft(command, if (takesPayload) "Carehando" else ""),
                        parseIntent(text, phrases, allowUnspacedPayload = unspaced))
                }
            }
            for (count in listOf(0, 1, 2, 3, 11, 21, 101)) {
                val description = local.resources.getQuantityString(R.plurals.calendar_day_description, count, "Carehando", count)
                assertFalse("$tag $count", '%' in description)
            }
        }
    }
}
