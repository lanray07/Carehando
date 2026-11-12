package app.carehando.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import app.carehando.R
import app.carehando.domain.*

@Composable
fun Assistant(spoken: Boolean, close: () -> Unit, open: (IntentDraft) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var input by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<Int?>(null) }
    var draft by remember { mutableStateOf<IntentDraft?>(null) }
    var listening by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var speech by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    val supported = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }

    DisposableEffect(context, spoken) {
        val tts = if (spoken) TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        } else null
        speech = tts
        onDispose {
            ttsReady = false
            tts?.stop()
            tts?.shutdown()
        }
    }
    DisposableEffect(context) {
        onDispose {
            recognizer?.cancel()
            recognizer?.destroy()
        }
    }

    fun start() {
        if (!supported || listening) return
        message = null
        draft = null
        try {
            val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
            engine.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onError(error: Int) {
                    listening = false
                    message = if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                        R.string.microphone_denied else R.string.recognition_failed
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val result = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (result.isNullOrBlank()) message = R.string.recognition_failed else input = result.take(160)
                }
            })
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            listening = true
            engine.startListening(request)
        } catch (_: RuntimeException) {
            listening = false
            message = R.string.recognition_failed
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else message = R.string.microphone_denied
    }

    val phrases = mapOf(
        Command.TODAY to stringResource(R.string.voice_today),
        Command.APPOINTMENTS to stringResource(R.string.voice_appointments),
        Command.MEDICATION to stringResource(R.string.voice_medication),
        Command.HANDOVER to stringResource(R.string.voice_handover),
        Command.ADD_TASK to stringResource(R.string.voice_task),
        Command.ADD_NOTE to stringResource(R.string.voice_note),
    ).mapValues { it.value.split("|") }
    Editor(R.string.voice_and_text, close) {
        Note(R.string.assistant_intro)
        if (supported) {
            Note(R.string.voice_privacy)
            OutlinedButton({
                if (listening) {
                    recognizer?.cancel()
                    listening = false
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    start()
                } else permission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text(stringResource(if (listening) R.string.stop_listening else R.string.push_to_talk)) }
        } else Note(R.string.speech_unavailable)
        Field(input, { input = it; draft = null; message = null }, R.string.command)
        Button({
            draft = parseIntent(input, phrases, allowUnspacedPayload = locale.language == "ja")
            message = if (draft == null) R.string.command_unknown else R.string.command_ready
        }, enabled = input.isNotBlank() && !listening) { Text(stringResource(R.string.preview_command)) }
        message?.let { Note(it) }
        draft?.let {
            if (it.text.isNotBlank()) Text(it.text)
            Button({ open(it) }) { Text(stringResource(R.string.open_result)) }
        }
        if (spoken && message != null) {
            OutlinedButton({
                val engine = speech
                if (!ttsReady || engine == null || engine.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE ||
                    engine.speak(context.getString(message ?: R.string.command_ready), TextToSpeech.QUEUE_FLUSH, null, "assistant") == TextToSpeech.ERROR) {
                    message = R.string.speech_output_unavailable
                }
            }) { Text(stringResource(R.string.read_feedback)) }
        }
        Note(R.string.command_examples)
        phrases.values.forEach { Text(it.first()) }
    }
}
