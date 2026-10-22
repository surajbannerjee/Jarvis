package com.jarvis.assistant.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.nlp.SupportedLanguage
import com.jarvis.assistant.persona.PersonaManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AnsweringMachineManager {

    private const val TAG = "JarvisAnsweringMachine"
    private val scope = CoroutineScope(Dispatchers.Main)

    @Volatile
    private var isCallInProgress = false

    /**
     * Executes autonomous call pickup, routes audio through speakerphone,
     * speaks the voicemail message using the active Stark AI persona, and terminates the call.
     */
    fun executeAutonomousPickup(context: Context, callerRelationOrName: String?) {
        if (isCallInProgress) {
            Log.w(TAG, "Answering machine already handling a call.")
            return
        }
        isCallInProgress = true

        scope.launch {
            Log.d(TAG, "Starting autonomous answering machine sequence...")

            // 1. Answer Call
            val answered = answerCall(context)
            if (!answered) {
                Log.e(TAG, "Failed to answer call autonomously.")
                isCallInProgress = false
                return@launch
            }

            // 2. Allow telephony stream 1.5s to establish connection
            delay(1500)

            // 3. Configure Speakerphone audio
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val originalMode = audioManager.mode
            val originalSpeakerphone = audioManager.isSpeakerphoneOn

            try {
                audioManager.mode = AudioManager.MODE_IN_CALL
                audioManager.isSpeakerphoneOn = true
                Log.d(TAG, "Audio routed to Speakerphone for voicemail playback.")

                // Start recording call conversation if enabled
                if (JarvisApp.instance.isCallRecordingEnabled()) {
                    CallRecordingManager.startRecording(context, callerRelationOrName)
                }

                // 4. Voice message to caller (Tailored to active Stark Persona)
                val persona = PersonaManager.getActivePersona()

                val finishVoicemail: () -> Unit = {
                    scope.launch {
                        // Allow caller 12 seconds to state their urgent message while Friday records
                        Log.d(TAG, "Voicemail spoken. Waiting 12 seconds for caller to state their message...")
                        delay(12000)
                        Log.d(TAG, "Terminating call post voicemail message window...")
                        CallRecordingManager.stopRecording(context)
                        hangUpCall(context)
                        audioManager.isSpeakerphoneOn = originalSpeakerphone
                        audioManager.mode = originalMode
                        isCallInProgress = false
                    }
                }

                // Check if user uploaded a custom voicemail audio file
                val hasUserCustomVoicemail = com.jarvis.assistant.audio.CustomVoiceStorage.hasCustomAudio(
                    context,
                    com.jarvis.assistant.audio.AudioSlot.VOICEMAIL
                )

                val customVoicemailPlayed = if (hasUserCustomVoicemail) {
                    com.jarvis.assistant.audio.CustomAudioPlayer.playAudioSlot(
                        context = context,
                        slot = com.jarvis.assistant.audio.AudioSlot.VOICEMAIL,
                        streamType = AudioManager.STREAM_VOICE_CALL
                    ) { finishVoicemail() }
                } else false

                if (!customVoicemailPlayed) {
                    // Generate smart AI Answering Machine message via JarvisAiBrain
                    val voiceMessage = com.jarvis.assistant.ai.JarvisAiBrain.generateAutoReply(
                        context = context,
                        senderName = callerRelationOrName ?: "Caller",
                        messageText = null,
                        isCall = true
                    )
                    val speechManager = JarvisApp.instance.speechManager

                    // Speak via STREAM_VOICE_CALL so caller hears it
                    speechManager.speak(
                        text = voiceMessage,
                        language = SupportedLanguage.BENGALI,
                        streamType = AudioManager.STREAM_VOICE_CALL
                    ) {
                        finishVoicemail()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during voicemail playback", e)
                CallRecordingManager.stopRecording(context)
                hangUpCall(context)
                audioManager.isSpeakerphoneOn = originalSpeakerphone
                audioManager.mode = originalMode
                isCallInProgress = false
            }
        }
    }

    private fun answerCall(context: Context): Boolean {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && telecomManager != null) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ANSWER_PHONE_CALLS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    telecomManager.acceptRingingCall()
                    Log.d(TAG, "Accepted call via TelecomManager.acceptRingingCall()")
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "TelecomManager.acceptRingingCall() failed, trying accessibility fallback", e)
                }
            }
        }

        // Accessibility fallback
        val accessibilityService = JarvisAccessibilityService.instance
        if (accessibilityService != null) {
            val clicked = accessibilityService.answerCallFallback()
            if (clicked) {
                Log.d(TAG, "Accepted call via AccessibilityService fallback.")
                return true
            }
        }

        Log.e(TAG, "No permission or accessibility service available to answer call.")
        return false
    }

    private fun hangUpCall(context: Context): Boolean {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && telecomManager != null) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ANSWER_PHONE_CALLS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    val ended = telecomManager.endCall()
                    Log.d(TAG, "Ended call via TelecomManager.endCall(): $ended")
                    if (ended) return true
                } catch (e: Exception) {
                    Log.w(TAG, "TelecomManager.endCall() failed, using accessibility fallback", e)
                }
            }
        }

        val accessibilityService = JarvisAccessibilityService.instance
        if (accessibilityService != null) {
            return accessibilityService.endCallFallback()
        }

        return false
    }
}
