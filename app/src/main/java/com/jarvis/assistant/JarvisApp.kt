package com.jarvis.assistant

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.jarvis.assistant.data.JarvisDatabase
import com.jarvis.assistant.speech.JarvisSpeechManager

class JarvisApp : Application() {

    lateinit var database: JarvisDatabase
        private set

    lateinit var speechManager: JarvisSpeechManager
        private set

    lateinit var preferences: SharedPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = JarvisDatabase.getDatabase(this)
        speechManager = JarvisSpeechManager.getInstance(this)
        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAnsweringMachineEnabled(): Boolean {
        return preferences.getBoolean(KEY_ANSWERING_MACHINE_ENABLED, true)
    }

    fun setAnsweringMachineEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ANSWERING_MACHINE_ENABLED, enabled).apply()
    }

    fun getRingThresholdSeconds(): Int {
        return preferences.getInt(KEY_RING_THRESHOLD_SECONDS, 15)
    }

    fun setRingThresholdSeconds(seconds: Int) {
        preferences.edit().putInt(KEY_RING_THRESHOLD_SECONDS, seconds).apply()
    }

    companion object {
        private const val PREFS_NAME = "jarvis_settings"
        private const val KEY_ANSWERING_MACHINE_ENABLED = "key_answering_machine_enabled"
        private const val KEY_RING_THRESHOLD_SECONDS = "key_ring_threshold_seconds"

        lateinit var instance: JarvisApp
            private set
    }
}
