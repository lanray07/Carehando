package app.carehando

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.platform.app.InstrumentationRegistry
import app.carehando.ui.writePdf
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.xmlpull.v1.XmlPullParser

class DeviceCapabilitiesTest {
    @Test fun reportSpeechServiceAndInstalledSynthesisLocales() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val locales = buildList {
            context.resources.getXml(R.xml.locales_config).use { parser ->
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                        parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                            ?.let(::add)
                    }
                }
            }
        }
        val init = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        lateinit var engine: TextToSpeech
        instrument.runOnMainSync {
            engine = TextToSpeech(context) {
                status = it
                init.countDown()
            }
        }
        try {
            val ready = init.await(10, TimeUnit.SECONDS) && status == TextToSpeech.SUCCESS
            val recognition = SpeechRecognizer.isRecognitionAvailable(context)
            val result = locales.joinToString(",") { tag ->
                val language = Locale.forLanguageTag(tag)
                val availability = if (ready) engine.isLanguageAvailable(language).toString() else "unavailable"
                "$tag=$availability"
            }
            instrument.sendStatus(2, android.os.Bundle().apply {
                putString("carehando_speech_capabilities", "recognitionService=$recognition; synthesis=$result")
            })
        } finally {
            engine.shutdown()
        }
    }

    @Test fun defaultEnglishVoiceSynthesizesAudio() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val initialized = CountDownLatch(1)
        val engineReady = AtomicBoolean(false)
        val complete = CountDownLatch(1)
        val success = AtomicBoolean(false)
        lateinit var engine: TextToSpeech
        instrument.runOnMainSync {
            engine = TextToSpeech(context) { status ->
                engineReady.set(status == TextToSpeech.SUCCESS)
                initialized.countDown()
            }
        }
        val file = File.createTempFile("carehando-speech-", ".wav", context.cacheDir)
        try {
            assumeTrue(initialized.await(10, TimeUnit.SECONDS) && engineReady.get())
            assumeTrue(engine.isLanguageAvailable(Locale.UK) >= TextToSpeech.LANG_AVAILABLE)
            assertTrue(engine.setLanguage(Locale.UK) >= TextToSpeech.LANG_AVAILABLE)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    success.set(true)
                    complete.countDown()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { complete.countDown() }
            })
            assertTrue(engine.synthesizeToFile("Your care handover is ready.", null, file, "carehando-qa")
                == TextToSpeech.SUCCESS)
            assertTrue("The engine did not finish synthesizing audio", complete.await(15, TimeUnit.SECONDS) && success.get())
            assertTrue("Expected audio samples after the WAV header", file.length() > 44)
        } finally {
            engine.shutdown()
            file.delete()
        }
    }

    @Test fun pdfRendersFirstPageAndPaginatesLongReports() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("carehando-test-", ".pdf", context.cacheDir)
        try {
            file.outputStream().use { writePdf(it, "Care report", List(120) { "Example activity $it" }) }
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(descriptor).use { renderer ->
                assertTrue(renderer.pageCount > 1)
                renderer.openPage(0).use { page -> assertTrue(page.width > 0 && page.height > 0) }
            }
        } finally {
            file.delete()
        }
    }
}
