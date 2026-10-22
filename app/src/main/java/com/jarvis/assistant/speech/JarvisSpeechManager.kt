package com.jarvis.assistant.speech

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.jarvis.assistant.nlp.SupportedLanguage
import java.util.Locale
import java.util.UUID

class JarvisSpeechManager private constructor(private val context: Context) :
    TextToSpeech.OnInitListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var speechRecognizer: SpeechRecognizer? = null

    // Callbacks for Utterance completion
    private val completionCallbacks = mutableMapOf<String, () -> Unit>()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS started speaking: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS completed speaking: $utteranceId")
                    utteranceId?.let { id ->
                        completionCallbacks.remove(id)?.invoke()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error on utterance: $utteranceId")
                    utteranceId?.let { id ->
                        completionCallbacks.remove(id)?.invoke()
                    }
                }
            })
            // Default to English initially
            setTtsLanguage(SupportedLanguage.ENGLISH)
        } else {
            Log.e(TAG, "Failed to initialize Android TextToSpeech engine.")
        }
    }

    fun setTtsLanguage(lang: SupportedLanguage) {
        if (!isTtsInitialized || tts == null) return

        val targetLocale = when (lang) {
            SupportedLanguage.BENGALI -> {
                val bnIn = Locale("bn", "IN")
                if (tts?.isLanguageAvailable(bnIn) == TextToSpeech.LANG_AVAILABLE ||
                    tts?.isLanguageAvailable(bnIn) == TextToSpeech.LANG_COUNTRY_AVAILABLE
                ) {
                    bnIn
                } else {
                    Locale("bn", "BD")
                }
            }
            SupportedLanguage.HINDI -> Locale("hi", "IN")
            SupportedLanguage.ENGLISH -> Locale.US
        }

        try {
            val result = tts?.setLanguage(targetLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Locale $targetLocale not fully supported, falling back to default.")
                tts?.setLanguage(Locale.US)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching TTS language", e)
        }
    }

    fun speak(
        text: String,
        language: SupportedLanguage = SupportedLanguage.ENGLISH,
        streamType: Int = AudioManager.STREAM_MUSIC,
        onComplete: (() -> Unit)? = null
    ) {
        if (!isTtsInitialized || tts == null) {
            Log.w(TAG, "TTS not ready yet. Dropping text: $text")
            onComplete?.invoke()
            return
        }

        setTtsLanguage(language)

        val utteranceId = UUID.randomUUID().toString()
        if (onComplete != null) {
            completionCallbacks[utteranceId] = onComplete
        }

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, streamType)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stopSpeaking() {
        tts?.stop()
        completionCallbacks.clear()
    }

    // --- Native Speech Recognition (STT) ---
    fun startListening(
        preferredLanguage: SupportedLanguage = SupportedLanguage.BENGALI,
        onReady: () -> Unit = {},
        onResults: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Native Speech Recognition is not available on this device.")
            return
        }

        stopListening()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onReady()
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error (Ensure offline language pack installed)"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match recognized"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                        else -> "Recognition error: $error"
                    }
                    onError(message)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onResults(matches[0])
                    } else {
                        onError("No match found")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Request offline preferential recognition
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Language hint
            val langTag = when (preferredLanguage) {
                SupportedLanguage.BENGALI -> "bn-IN"
                SupportedLanguage.HINDI -> "hi-IN"
                SupportedLanguage.ENGLISH -> "en-US"
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
        }

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recognizer", e)
        } finally {
            speechRecognizer = null
        }
    }

    companion object {
        private const val TAG = "JarvisSpeech"

        @Volatile
        private var INSTANCE: JarvisSpeechManager? = null

        fun getInstance(context: Context): JarvisSpeechManager {
            return INSTANCE ?: synchronized(this) {
                val instance = JarvisSpeechManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
