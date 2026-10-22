package com.jarvis.assistant.notifications

import android.app.Notification
import android.media.AudioManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.nlp.SupportedLanguage
import java.util.concurrent.ConcurrentHashMap

class JarvisNotificationService : NotificationListenerService() {

    // Throttling and deduplication cache: maps message digest -> timestamp (ms)
    private val processedNotifications = ConcurrentHashMap<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "JarvisNotificationService connected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        // Filter for WhatsApp and WhatsApp Business
        if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b") {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Filter out non-message system notifications
        if (title.isBlank() || text.isBlank() ||
            text.contains("Checking for new messages", ignoreCase = true) ||
            text.contains("WhatsApp Web is currently active", ignoreCase = true) ||
            text.contains("Calling…", ignoreCase = true) ||
            text.contains("Incoming call", ignoreCase = true)
        ) {
            return
        }

        // Deduplicate notifications from the same sender with same text within 10 seconds
        val digest = "$title::$text"
        val now = System.currentTimeMillis()
        val lastSeen = processedNotifications[digest] ?: 0L

        if (now - lastSeen < DEDUPLICATION_WINDOW_MS) {
            Log.d(TAG, "Throttling duplicate WhatsApp notification: $digest")
            return
        }
        processedNotifications[digest] = now

        // Clean up old entries from cache
        if (processedNotifications.size > 50) {
            val threshold = now - (DEDUPLICATION_WINDOW_MS * 2)
            processedNotifications.entries.removeIf { it.value < threshold }
        }

        // Format announcement (Bengali with English sender/preview)
        val announcement = "$title ekta WhatsApp message pathiyeche: $text"
        Log.d(TAG, "Reading WhatsApp message aloud: $announcement")

        try {
            val speechManager = JarvisApp.instance.speechManager
            speechManager.speak(
                text = announcement,
                language = SupportedLanguage.BENGALI,
                streamType = AudioManager.STREAM_NOTIFICATION
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking WhatsApp notification", e)
        }
    }

    companion object {
        private const val TAG = "JarvisNotification"
        private const val DEDUPLICATION_WINDOW_MS = 10_000L // 10 seconds
    }
}
