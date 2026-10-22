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
import android.speech.tts.Voice
import android.util.Log
import com.jarvis.assistant.nlp.SupportedLanguage
import com.jarvis.assistant.persona.PersonaGender
import com.jarvis.assistant.persona.PersonaManager
import com.jarvis.assistant.persona.StarkPersona
import java.util.Locale
import java.util.UUID

class JarvisSpeechManager private constructor(private val context: Context) :
    TextToSpeech.OnInitListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var speechRecognizer: SpeechRecognizer? = null

    var currentPersona: StarkPersona = StarkPersona.JARVIS
        private set

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

            // Load saved persona
            val savedPersona = PersonaManager.getActivePersona()
            applyPersona(savedPersona)
        } else {
            Log.e(TAG, "Failed to initialize Android TextToSpeech engine.")
        }
    }

    fun applyPersona(persona: StarkPersona) {
        currentPersona = persona
        if (!isTtsInitialized || tts == null) return

        try {
            tts?.setPitch(persona.pitch)
            tts?.setSpeechRate(persona.speechRate)

            // Try selecting gender-matched voice if device has multiple voices
            selectVoiceForPersona(persona, SupportedLanguage.ENGLISH)
            Log.d(TAG, "Applied persona: ${persona.displayName} (Pitch: ${persona.pitch}, Rate: ${persona.speechRate})")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying persona voice settings", e)
        }
    }

    private fun selectVoiceForPersona(persona: StarkPersona, language: SupportedLanguage) {
        if (!isTtsInitialized || tts == null) return

        try {
            val voices = tts?.voices ?: return
            val targetLocale = when (language) {
                SupportedLanguage.BENGALI -> Locale("bn", "IN")
                SupportedLanguage.HINDI -> Locale("hi", "IN")
                SupportedLanguage.ENGLISH -> persona.preferredLocale
            }

            // Find matching voice by locale and gender keyword
            val matchingVoices = voices.filter { it.locale.language == targetLocale.language }
            val preferredVoice = when (persona.gender) {
                PersonaGender.FEMALE -> {
                    matchingVoices.firstOrNull {
                        it.name.contains("female", ignoreCase = true) ||
                        it.name.contains("woman", ignoreCase = true) ||
                        it.name.contains("f0", ignoreCase = true)
                    } ?: matchingVoices.firstOrNull()
                }
                PersonaGender.MALE, PersonaGender.ROBOTIC -> {
                    matchingVoices.firstOrNull {
                        it.name.contains("male", ignoreCase = true) ||
                        it.name.contains("man", ignoreCase = true) ||
                        it.name.contains("m0", ignoreCase = true)
                    } ?: matchingVoices.firstOrNull()
                }
            }

            if (preferredVoice != null) {
                tts?.voice = preferredVoice
                Log.d(TAG, "Selected voice for ${persona.displayName}: ${preferredVoice.name}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Voice selection fallback to default locale", e)
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
            SupportedLanguage.ENGLISH -> currentPersona.preferredLocale
        }

        try {
            val result = tts?.setLanguage(targetLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Locale $targetLocale not fully supported, falling back to US.")
                tts?.setLanguage(Locale.US)
            }

            // Re-apply pitch & speechRate and voice
            tts?.setPitch(currentPersona.pitch)
            tts?.setSpeechRate(currentPersona.speechRate)
            selectVoiceForPersona(currentPersona, lang)
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

    fun previewPersona(persona: StarkPersona, inBengali: Boolean = false) {
        applyPersona(persona)
        // If it is FRIDAY, prioritize her signature speech.wav audio!
        val customAudioPlayed = if (persona == StarkPersona.FRIDAY) {
            com.jarvis.assistant.audio.CustomAudioPlayer.playCustomAudio(context, "friday_greeting") ||
            com.jarvis.assistant.audio.CustomAudioPlayer.playAudioSlot(context, com.jarvis.assistant.audio.AudioSlot.GREETING)
        } else {
            com.jarvis.assistant.audio.CustomAudioPlayer.playAudioSlot(
                context,
                com.jarvis.assistant.audio.AudioSlot.GREETING
            ) || com.jarvis.assistant.audio.CustomAudioPlayer.playCustomAudio(
                context,
                persona.rawAudioResName ?: "${persona.id}_greeting"
            )
        }

        if (!customAudioPlayed) {
            val text = if (inBengali) persona.signatureGreetingBengali else persona.signatureGreeting
            val lang = if (inBengali) SupportedLanguage.BENGALI else SupportedLanguage.ENGLISH
            speak(text, lang)
        }
    }

    fun stopSpeaking() {
        com.jarvis.assistant.audio.CustomAudioPlayer.stopAudio()
        tts?.stop()
        completionCallbacks.clear()
    }

    fun createRecognizerIntent(preferredLanguage: SupportedLanguage = SupportedLanguage.BENGALI): Intent {
        val langTag = when (preferredLanguage) {
            SupportedLanguage.BENGALI -> "bn-IN"
            SupportedLanguage.HINDI -> "hi-IN"
            SupportedLanguage.ENGLISH -> currentPersona.preferredLocale.toLanguageTag()
        }
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Jarvis is listening... Speak in Bengali, Hindi or English")
        }
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

        try {
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
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
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

            val intent = createRecognizerIntent(preferredLanguage)
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SpeechRecognizer", e)
            onError(e.localizedMessage ?: "Failed to start speech recognition")
        }
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
