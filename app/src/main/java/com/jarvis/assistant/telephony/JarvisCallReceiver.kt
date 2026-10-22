package com.jarvis.assistant.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.telephony.TelephonyManager
import android.util.Log
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.contacts.ContactHelper
import com.jarvis.assistant.nlp.SupportedLanguage
import com.jarvis.assistant.persona.PersonaManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class JarvisCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d(TAG, "Phone State Changed: $stateStr, Number: $incomingNumber")

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                handleIncomingRinging(context, incomingNumber)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                handleCallAnswered()
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                handleCallEnded()
            }
        }
    }

    private fun handleIncomingRinging(context: Context, incomingNumber: String?) {
        isCurrentlyRinging = true
        activeRingingJob?.cancel()

        activeRingingJob = scope.launch {
            // 1. Resolve contact name from device contacts
            val contactName = ContactHelper.resolveContactName(context, incomingNumber)
            val displayName = contactName ?: incomingNumber ?: "Unknown"

            // 2. Cross-reference with Jarvis Memory (Alias Database)
            val aliasDao = JarvisApp.instance.database.aliasDao()
            val alias = if (contactName != null) {
                aliasDao.findByContactName(contactName)
            } else if (!incomingNumber.isNullOrBlank()) {
                aliasDao.findByPhoneNumber(incomingNumber)
            } else {
                null
            }

            // 3. Construct Announcement using Active Stark Persona
            val persona = PersonaManager.getActivePersona()
            val relationTag = alias?.relationshipTag
            val targetName = relationTag ?: displayName
            val isBengali = relationTag != null

            val announcementText = persona.formatCallerAnnouncement(targetName, isBengali)
            val speechLang = if (isBengali) SupportedLanguage.BENGALI else SupportedLanguage.ENGLISH

            // 4. Announce Caller
            Log.d(TAG, "Announcing caller via ${persona.displayName}: $announcementText")
            val speechManager = JarvisApp.instance.speechManager
            speechManager.speak(
                text = announcementText,
                language = speechLang,
                streamType = AudioManager.STREAM_RING
            )

            // 5. Start Unattended Call Countdown
            val thresholdSeconds = JarvisApp.instance.getRingThresholdSeconds()
            Log.d(TAG, "Waiting $thresholdSeconds seconds before autonomous answering...")
            delay(thresholdSeconds * 1000L)

            // If still ringing and answering machine is enabled -> Pick up autonomously
            if (isCurrentlyRinging && JarvisApp.instance.isAnsweringMachineEnabled()) {
                Log.d(TAG, "Call remained unattended for $thresholdSeconds s. Triggering answering machine.")
                AnsweringMachineManager.executeAutonomousPickup(context, relationTag ?: displayName)
            }
        }
    }

    private fun handleCallAnswered() {
        Log.d(TAG, "Call answered (OFFHOOK). Canceling ringing timer.")
        isCurrentlyRinging = false
        activeRingingJob?.cancel()
    }

    private fun handleCallEnded() {
        Log.d(TAG, "Call ended or idle (IDLE). Resetting state.")
        isCurrentlyRinging = false
        activeRingingJob?.cancel()
    }

    companion object {
        private const val TAG = "JarvisCallReceiver"
        private val scope = CoroutineScope(Dispatchers.Main)

        @Volatile
        private var isCurrentlyRinging = false
        private var activeRingingJob: Job? = null
    }
}
