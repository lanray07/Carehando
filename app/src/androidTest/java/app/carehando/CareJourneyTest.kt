package app.carehando

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.edit
import app.carehando.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import android.view.WindowManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import org.junit.Assume.assumeTrue

@OptIn(ExperimentalTestApi::class)
class CareJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext

    @Test fun caregiverCanCompleteTaskAndSaveAcknowledgeAndCorrectHandover() {
        val db = CareDatabase.open(context)
        runBlocking {
            db.care().clearRecipients()
            db.care().clearCaregivers()
        }
        selectLanguage("en-GB")
        launchAwake().use {
            scrollAndClick("Preferred name") { performTextInput("Robin Example") }
            scrollAndClick("Create profile")
            click("Add task")
            click("Title") { performTextInput("Prepare lunch") }
            scrollAndClick("Selected weekdays")
            val weekday = LocalDate.now().dayOfWeek.getDisplayName(
                java.time.format.TextStyle.FULL_STANDALONE, java.util.Locale.UK)
            scrollAndClick(weekday)
            scrollAndClick("Save")
            compose.waitUntil(15_000) {
                runBlocking {
                    db.care().entries().first().any {
                        it.title == "Prepare lunch" && it.repeat == Repeat.SELECTED_DAYS && it.weekdays != 0
                    }
                }
            }
            scrollAndClick("Unconfirmed")
            click("Mark complete")
            scrollAndClick("Completed") {}
            click("Handovers", last = true)
            click("New handover")
            click("Caregiver name") { performTextInput("Lee Example") }
            scrollAndClick("Review draft")
            await("Prepare lunch", substring = true)
            scrollAndClick("Save handover")
            click("Open handover")
            scrollAndClick("Acknowledge handover")
            click("Caregiver name") { performTextInput("Ari Example") }
            scrollAndClick("Confirm")
            await("Ari Example", substring = true)
            scrollAndClick("Add correction")
            scrollAndClick("Correction") { performTextInput("Lunch delivered at noon") }
            scrollAndClick("Confirm")
            scrollAndClick("Lunch delivered at noon") {}
        }
        launchAwake().use {
            await("Care for Robin Example")
            scrollAndClick("Completed") {}
            click("Handovers", last = true)
            await("Acknowledged")
            click("Open handover")
            scrollAndClick("Lunch delivered at noon") {}
        }
        val saved = runBlocking { db.care().handovers().first().single() }
        val revisions = saved.revisions.sortedBy { it.id }
        assertEquals(listOf(RevisionKind.ORIGINAL, RevisionKind.ACKNOWLEDGEMENT, RevisionKind.CORRECTION),
            revisions.map { it.kind })
        assertEquals(listOf("Lee Example", "Ari Example", "Ari Example"), revisions.map { it.author })
        assertTrue(revisions.zipWithNext().all { (first, second) -> first.at <= second.at })
        assertTrue(saved.items.any { it.title == "Prepare lunch" && it.outcome == Outcome.COMPLETED })
    }

    @Test fun expandedProfilesSaveFromTheirEditorsAndAppearInEmergencyInfo() {
        val dao = CareDatabase.open(context).care()
        runBlocking {
            dao.clearRecipients()
            dao.clearCaregivers()
            dao.save(Recipient(name = "Profile QA"))
        }
        selectLanguage("en-GB")
        launchAwake().use {
            await("Care for Profile QA")
            click("Settings")
            scrollAndClick("Profile QA")
            scrollAndClick("Preferred communication language") { performTextInput("English") }
            scrollAndClick("Mobility support notes") { performTextInput("Use the handrail") }
            scrollAndClick("Preferred hospital or clinic") { performTextInput("Local clinic") }
            scrollAndClick("Save")
            compose.waitUntil(15_000) {
                runBlocking { dao.recipients().first().single().preferredClinic == "Local clinic" }
            }

            click("Emergency info")
            await("Local clinic")
            await("English")
            click("Close")

            scrollAndClick("Add caregiver")
            scrollAndClick("Name") { performTextInput("Example caregiver") }
            scrollAndClick("Contact details (optional)") { performTextInput("Example number") }
            scrollAndClick("Preferred communication language") { performTextInput("French") }
            scrollAndClick("Save")
            compose.waitUntil(15_000) {
                runBlocking { dao.caregivers().first().singleOrNull()?.preferredLanguage == "French" }
            }
        }
        runBlocking {
            val recipient = dao.recipients().first().single()
            assertEquals("English", recipient.communicationLanguage)
            assertEquals("Use the handrail", recipient.mobilityNotes)
            assertEquals("Local clinic", recipient.preferredClinic)
            assertEquals("Example number", dao.caregivers().first().single().contactDetails)
        }
    }

    @Test fun calendarNavigatesMonthsAndOpensTheSelectedAppointment() {
        val dao = CareDatabase.open(context).care()
        val appointmentDay = LocalDate.now().plusDays(1)
        runBlocking {
            dao.clearRecipients()
            dao.clearCaregivers()
            val recipient = dao.save(Recipient(name = "Calendar QA"))
            dao.save(Entry(recipientId = recipient, kind = Kind.APPOINTMENT, title = "Calendar QA visit",
                day = appointmentDay.toString(), time = "15:00", location = "Local clinic"))
        }
        selectLanguage("en-GB")
        launchAwake().use {
            await("Care for Calendar QA")
            click("Records")
            scrollAndClick("Appointment calendar")
            scrollAndClick("Next month")
            scrollAndClick("No appointments on this date.") {}
            scrollAndClick("Previous month")
            val dateLabel = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL)
                .withLocale(java.util.Locale.UK).format(appointmentDay)
            val dateCell = "$dateLabel, 1 appointment"
            compose.waitUntilAtLeastOneExists(hasContentDescription(dateCell), 15_000)
            compose.onNodeWithContentDescription(dateCell).performScrollTo().performClick()
            scrollAndClick(" · Calendar QA visit", substring = true)
            scrollAndClick("Location") {}
            await("Local clinic")
        }
    }

    @Test fun backupRestoreIsReachableOnboardingAndDisabledWithExistingRecords() {
        val dao = CareDatabase.open(context).care()
        runBlocking {
            dao.clearRecipients()
            dao.clearCaregivers()
        }
        selectLanguage("en-GB")
        val dialog = hasAnyAncestor(isDialog())
        val restore = hasText("Restore encrypted backup") and dialog
        launchAwake().use {
            scrollAndClick("Restore encrypted backup")
            compose.onNode(restore).assertIsEnabled().performClick()
            compose.onNode(hasText("Choose backup file") and dialog).assertIsNotEnabled()
            compose.onNode(hasText("Passphrase") and dialog).performTextInput("backup fixture passphrase")
            compose.onNode(hasText("Choose backup file") and dialog).assertIsEnabled()
            click("Close")
        }
        runBlocking { dao.save(Recipient(name = "Backup UI QA")) }
        launchAwake().use {
            await("Care for Backup UI QA")
            click("Settings")
            scrollAndClick("Encrypted backup")
            compose.onNode(restore).assertIsNotEnabled()
            await("Restore on an installation with no care profiles.", substring = true)
        }
    }

    @Test fun appointmentFollowUpPersistsCompletionAndPreparationNotes() {
        val dao = CareDatabase.open(context).care()
        val appointmentId = runBlocking {
            dao.clearRecipients()
            dao.clearCaregivers()
            val recipient = dao.save(Recipient(name = "Follow-up QA"))
            dao.save(Entry(recipientId = recipient, kind = Kind.APPOINTMENT, title = "Clinic visit",
                day = LocalDate.now().toString(), time = "15:00", checklist = "Bring the appointment letter"))
        }
        selectLanguage("en-GB")
        launchAwake().use {
            click("Records")
            scrollAndClick("Edit")
            scrollAndClick("Follow-up item") { performTextInput("Book transport") }
            scrollAndClick("Add follow-up item")
            scrollAndClick("Book transport") { assertIsOff().performClick().assertIsOn() }
            scrollAndClick("Save")
            scrollAndClick("1 of 1 tasks complete") { assertIsDisplayed() }
            scrollAndClick("Edit")
            scrollAndClick("Book transport") { assertIsOn() }
        }
        val saved = requireNotNull(runBlocking { dao.entry(appointmentId) })
        assertEquals("Bring the appointment letter", saved.checklist)
        assertEquals(listOf(AppointmentFollowUp("Book transport", true)), decodeAppointmentFollowUp(saved.appointmentFollowUp))
    }

    @Test fun settingsCanSwitchToArabic() {
        runBlocking { CareDatabase.open(context).care().save(Recipient(name = "Locale QA")) }
        selectLanguage("en-GB")
        try {
            launchAwake().use {
                click("Settings")
                scrollAndClick("Language ·", substring = true)
                scrollAndClick(Locale.forLanguageTag("ar").getDisplayName(Locale.forLanguageTag("ar")))
                await("الإعدادات")
            }
        } finally {
            selectLanguage("en-GB")
        }
    }

    @Test fun appLockHidesRecordsAndSetsSecureWindowFlag() {
        val original = runBlocking { context.carePreferences.data.first()[Preferences.appLock] }
        try {
            runBlocking { context.carePreferences.edit { it[Preferences.appLock] = true } }
            selectLanguage("en-GB")
            launchAwake().use { scenario ->
                await("Care records are locked")
                compose.onNodeWithText("Settings").assertDoesNotExist()
                scenario.onActivity {
                    assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                }
            }
        } finally {
            runBlocking {
                context.carePreferences.edit {
                    if (original == null) it.remove(Preferences.appLock) else it[Preferences.appLock] = original
                }
            }
        }
    }

    @Test fun paywallRestoresVerifiedSandboxPurchase() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("carehandoAppTester") == "true")
        assertTrue(BuildConfig.DEBUG)
        assertEquals("https://127.0.0.1:18765/verify", BuildConfig.RECEIPT_VERIFIER_URL)
        val shell = instrument.uiAutomation.executeShellCommand("getprop debug.amazon.sandboxmode")
        val mode = android.os.ParcelFileDescriptor.AutoCloseInputStream(shell).bufferedReader().use { it.readText().trim() }
        assertEquals("debug", mode)
        val encoded = requireNotNull(arguments.getString("carehandoSandboxCertificate"))
        val der = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der))
        val trusted = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("sandbox", cert)
        }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trusted) }
        val tls = SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }
        val originalSockets = HttpsURLConnection.getDefaultSSLSocketFactory()
        val original = runBlocking { context.carePreferences.data.first() }
        val dao = CareDatabase.open(context).care()
        val fixture = runBlocking {
            if (dao.recipients().first().isEmpty()) {
                val recipient = Recipient(name = "Sandbox QA")
                recipient.copy(id = dao.save(recipient))
            } else null
        }
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(tls.socketFactory)
            runBlocking {
                context.carePreferences.edit {
                    it.remove(Preferences.premium)
                    it.remove(Preferences.receiptId)
                }
            }
            selectLanguage("en-GB")
            launchAwake().use {
                click("Settings")
                scrollAndClick("Restore purchases")
                scrollAndClick("Premium is active.") { assertIsDisplayed() }
                scrollAndClick("Encrypted backup")
                compose.onNodeWithText("Create backup").assertIsEnabled()
            }
            val restored = runBlocking { context.carePreferences.data.first() }
            assertTrue(restored[Preferences.premium] == true)
            assertFalse(restored[Preferences.receiptId].isNullOrBlank())

            // Remove the test CA before reopening the app, so the verifier cannot establish TLS.
            HttpsURLConnection.setDefaultSSLSocketFactory(originalSockets)
            launchAwake().use {
                click("Settings")
                scrollAndClick("Restore purchases")
                scrollAndClick("The purchase service could not verify your access. Try restoring purchases.") {
                    assertIsDisplayed()
                }
                scrollAndClick("Premium is active.") { assertIsDisplayed() }
                scrollAndClick("Encrypted backup")
                compose.onNodeWithText("Create backup").assertIsEnabled()
            }
            val afterOutage = runBlocking { context.carePreferences.data.first() }
            assertTrue(afterOutage[Preferences.premium] == true)
            assertEquals(restored[Preferences.receiptId], afterOutage[Preferences.receiptId])
        } finally {
            HttpsURLConnection.setDefaultSSLSocketFactory(originalSockets)
            runBlocking {
                context.carePreferences.edit { preferences ->
                    preferences.remove(Preferences.premium)
                    preferences.remove(Preferences.receiptId)
                    original[Preferences.premium]?.let { preferences[Preferences.premium] = it }
                    original[Preferences.receiptId]?.let { preferences[Preferences.receiptId] = it }
                }
                fixture?.let { dao.delete(it) }
            }
        }
    }

    @Test fun captureActualScreens() {
        val arguments = InstrumentationRegistry.getArguments()
        val locale = arguments.getString("captureLocale") ?: return
        val calendarOnly = arguments.getString("captureCalendarOnly") == "true"
        val language = Locale.forLanguageTag(locale)
        val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList(language)) }
        val localized = context.createConfigurationContext(config)
        fun label(id: Int) = localized.getString(id)
        val examples = instrument.context.assets.open("capture_examples.json").bufferedReader().use {
            val fixture = when {
                language.language == "en" -> "en"
                locale == "es-MX" -> "es-419"
                else -> locale
            }
            JSONObject(it.readText()).getJSONObject(fixture)
        }
        if (locale == "es-MX") assertEquals("Configuración", label(R.string.settings))
        fun example(key: String) = examples.getString(key)
        val db = CareDatabase.open(context)
        val date = LocalDate.now()
        runBlocking {
            val dao = db.care()
            dao.clearRecipients()
            dao.clearCaregivers()
            val profile = dao.save(Recipient(name = example("recipient")))
            val carerName = example("caregiver")
            val carer = dao.save(Caregiver(name = carerName, role = example("role")))
            val lunch = example("routine")
            val task = dao.save(Entry(recipientId = profile, kind = Kind.ROUTINE, title = lunch, day = date.toString(),
                caregiverId = carer, repeat = Repeat.DAILY, time = "12:30"))
            dao.record(ActivityRecord(entryId = task, recipientId = profile, title = lunch, kind = Kind.ROUTINE,
                day = date.toString(), outcome = Outcome.COMPLETED))
            dao.save(Entry(recipientId = profile, kind = Kind.TASK, title = example("task"),
                day = date.toString(), caregiverId = carer, time = "17:30"))
            val visit = example("appointment")
            dao.save(Entry(recipientId = profile, kind = Kind.APPOINTMENT, title = visit,
                day = date.plusDays(1).toString(), time = "15:00", location = example("location")))
            dao.save(Entry(recipientId = profile, kind = Kind.MEDICATION,
                title = example("medication"),
                day = date.toString(), time = "20:00", repeat = Repeat.DAILY))
            dao.save(Entry(recipientId = profile, kind = Kind.JOURNAL,
                title = example("journal"),
                details = example("journalNote"), day = date.toString()))
            val handover = dao.save(Handover(recipientId = profile, recipientName = example("recipient"),
                caregiver = carerName, from = 0))
            dao.save(listOf(
                HandoverItem(handoverId = handover, section = HandoverSection.COMPLETED, title = lunch,
                    day = date.toString(), outcome = Outcome.COMPLETED),
                HandoverItem(handoverId = handover, section = HandoverSection.APPOINTMENTS, title = visit,
                    day = date.plusDays(1).toString(), time = "15:00"),
            ))
            dao.append(HandoverRevision(handoverId = handover, kind = RevisionKind.ORIGINAL, author = carerName,
                followUp = example("followUp")))
        }
        selectLanguage(locale)
        launchAwake().use {
            await(localized.getString(R.string.care_for, example("recipient")))
            val calendarLabel = label(R.string.appointment_calendar)
            val calendarEntry = " · " + example("appointment")
            if (calendarOnly) {
                click(label(R.string.records))
                scrollAndClick(calendarLabel)
                await(calendarEntry, substring = true)
                capture(locale, "05-appointments")
                return@use
            }
            capture(locale, "01-today")
            click(label(R.string.care_plans))
            capture(locale, "02-plans")
            click(label(R.string.handovers), last = true)
            click(label(R.string.open_handover))
            capture(locale, "03-handovers")
            click(label(R.string.close))
            click(label(R.string.records))
            scrollAndClick(calendarLabel)
            await(calendarEntry, substring = true)
            capture(locale, "05-appointments")
            click(label(R.string.close))
            click(label(R.string.medication))
            capture(locale, "04-medication")
            click(label(R.string.journal))
            capture(locale, "07-records")
            click(label(R.string.today))
            click(label(R.string.voice_and_text))
            capture(locale, "06-assistant")
            click(label(R.string.close))
            click(label(R.string.settings))
            val report = label(R.string.care_report)
            scrollAndClick(report)
            val preview = label(R.string.show_pdf_preview)
            scrollAndClick(preview)
            val page = compose.onNodeWithContentDescription(label(R.string.pdf_first_page))
            compose.waitUntilAtLeastOneExists(hasContentDescription(label(R.string.pdf_first_page)), 15_000)
            page.performScrollTo()
            capture(locale, "08-reports")
        }
    }

    private fun launchAwake() = ActivityScenario.launch(MainActivity::class.java).onActivity {
        it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun selectLanguage(locale: String) {
        instrument.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale)) }
    }

    private fun capture(locale: String, name: String) {
        compose.waitForIdle()
        // Platform dialog transitions run outside the Compose test clock.
        android.os.SystemClock.sleep(300)
        val folder = File(context.filesDir, "screenshots/$locale").apply { mkdirs() }
        val bitmap = requireNotNull(instrument.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    private fun await(text: String, substring: Boolean = false) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = substring), 15_000)

    private fun click(text: String, last: Boolean = false, action: SemanticsNodeInteraction.() -> Unit = { performClick() }) {
        await(text)
        val nodes = compose.onAllNodesWithText(text)
        (if (last) nodes.onLast() else nodes.onFirst()).action()
    }

    private fun scrollAndClick(text: String, substring: Boolean = false, action: SemanticsNodeInteraction.() -> Unit = { performClick() }) {
        val matcher = hasText(text, substring = substring)
        compose.waitUntil(15_000) {
            if (compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) {
                true
            } else {
                val lazyLists = compose.onAllNodes(hasScrollToIndexAction())
                if (lazyLists.fetchSemanticsNodes().isNotEmpty()) {
                    try {
                        lazyLists.onFirst().performScrollToNode(matcher)
                        true
                    } catch (_: AssertionError) {
                        false
                    }
                } else false
            }
        }
        val node = compose.onAllNodes(matcher).onFirst()
        if (!node.isDisplayed()) node.performScrollTo()
        node.action()
    }
}



