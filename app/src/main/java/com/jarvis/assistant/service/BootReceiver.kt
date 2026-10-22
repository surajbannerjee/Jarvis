package com.jarvis.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jarvis.assistant.JarvisApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed. Checking Friday Wake Word status...")
            if (JarvisApp.instance.isWakeWordEnabled()) {
                FridayWakeWordService.start(context)
            }
        }
    }
}
