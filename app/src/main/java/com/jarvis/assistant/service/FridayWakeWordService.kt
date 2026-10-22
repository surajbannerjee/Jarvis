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
        if (isDestroyed || !JarvisApp.instance.isWakeWordEnabled()) return

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
                restartAfterDelay(1500)
            }
        }
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
                // Normal timeout/no match -> restart immediately
                val delayMs = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 300L
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1000L
                    else -> 1500L
                }
                restartAfterDelay(delayMs)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                checkMatchesForWakeWord(matches)
                restartAfterDelay(400)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                checkMatchesForWakeWord(matches)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun checkMatchesForWakeWord(matches: List<String>?) {
        if (matches.isNullOrEmpty()) return

        for (text in matches) {
            val lower = text.lowercase(Locale.ROOT)
            if (lower.contains("friday") || lower.contains("hi friday") ||
                lower.contains("hey friday") || lower.contains("hello friday") ||
                lower.contains("ফ্রাইডে")
            ) {
                Log.d(TAG, "WAKE WORD TRIGGERED via phrase: '$text'")
                triggerAssistantWakeup(text)
                break
            }
        }
    }

    private fun triggerAssistantWakeup(triggerText: String) {
        // Play signature wake audio
        CustomAudioPlayer.playCustomAudio(this, "friday_greeting")

        // Launch MainActivity and activate voice assistant
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
