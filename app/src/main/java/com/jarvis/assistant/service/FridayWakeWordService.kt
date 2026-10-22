package com.jarvis.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.audio.CustomAudioPlayer
import com.jarvis.assistant.ui.MainActivity
import java.util.Locale

class FridayWakeWordService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val handler = Handler(Looper.getMainLooper())
    private var isDestroyed = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "FridayWakeWordService created.")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        startListeningLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!JarvisApp.instance.isWakeWordEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isListening) {
            startListeningLoop()
        }
        return START_STICKY
    }

    private fun startListeningLoop() {
        if (isDestroyed || isForegroundActive || !JarvisApp.instance.isWakeWordEnabled()) return

        handler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                        setRecognitionListener(createListener())
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                }

                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d(TAG, "Friday continuous wake-word listening active.")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognizer in wake-word service", e)
                restartAfterDelay(2000)
            }
        }
    }

    fun pauseListening() {
        handler.removeCallbacksAndMessages(null)
        isListening = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying recognizer on pause", e)
        }
        speechRecognizer = null
        Log.d(TAG, "FridayWakeWordService paused — foreground app active.")
    }

    fun resumeListening() {
        if (isDestroyed || isForegroundActive || !JarvisApp.instance.isWakeWordEnabled()) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (!isDestroyed && !isForegroundActive && JarvisApp.instance.isWakeWordEnabled()) {
                startListeningLoop()
            }
        }, 1000)
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                if (isForegroundActive || isDestroyed) return
                // Gentle delay to prevent rapid mic on/off flickering
                val delayMs = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 1200L
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 2500L
                    else -> 2000L
                }
                restartAfterDelay(delayMs)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val triggered = checkMatchesForWakeWord(matches)
                if (!triggered && !isForegroundActive) {
                    restartAfterDelay(800)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                checkMatchesForWakeWord(matches)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun checkMatchesForWakeWord(matches: List<String>?): Boolean {
        if (matches.isNullOrEmpty()) return false

        for (text in matches) {
            val lower = text.lowercase(Locale.ROOT)
            if (lower.contains("friday") || lower.contains("hi friday") ||
                lower.contains("hey friday") || lower.contains("hello friday") ||
                lower.contains("ফ্রাইডে")
            ) {
                Log.d(TAG, "WAKE WORD TRIGGERED via phrase: '$text'")
                triggerAssistantWakeup(text)
                return true
            }
        }
        return false
    }

    private fun triggerAssistantWakeup(triggerText: String) {
        handler.removeCallbacksAndMessages(null)
        isListening = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {}
        speechRecognizer = null

        // Launch MainActivity cleanly without audio bleeding into the microphone
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TRIGGER_VOICE, true)
            putExtra(EXTRA_TRIGGER_QUERY, triggerText)
        }
        startActivity(launchIntent)
    }

    private fun restartAfterDelay(delayMs: Long) {
        if (isDestroyed || !JarvisApp.instance.isWakeWordEnabled()) return
        handler.postDelayed({
            if (!isDestroyed && JarvisApp.instance.isWakeWordEnabled()) {
                startListeningLoop()
            }
        }, delayMs)
    }

    private fun buildForegroundNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("F.R.I.D.A.Y. Voice Engine")
            .setContentText("Online — Say 'Hi Friday' to wake up anytime")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "F.R.I.D.A.Y. Wake Word Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Friday ready to listen for 'Hi Friday' in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isDestroyed = true
        isListening = false
        if (instance == this) instance = null
        handler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech recognizer", e)
        }
        speechRecognizer = null
        Log.d(TAG, "FridayWakeWordService destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FridayWakeWordService"
        private const val CHANNEL_ID = "friday_wake_channel"
        private const val NOTIFICATION_ID = 2001

        const val EXTRA_TRIGGER_VOICE = "extra_trigger_voice"
        const val EXTRA_TRIGGER_QUERY = "extra_trigger_query"

        @Volatile
        private var isForegroundActive = false
        private var instance: FridayWakeWordService? = null

        fun setForegroundActive(active: Boolean) {
            isForegroundActive = active
            if (active) {
                instance?.pauseListening()
            } else {
                instance?.resumeListening()
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, FridayWakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FridayWakeWordService::class.java)
            context.stopService(intent)
        }
    }
}
