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

    fun getGeminiApiKey(): String {
        val userSavedKey = preferences.getString(KEY_GEMINI_API_KEY, "") ?: ""
        return if (userSavedKey.isNotBlank()) userSavedKey else DEFAULT_GEMINI_KEY
    }

    fun setGeminiApiKey(key: String) {
        preferences.edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
    }

    fun isAiConversationEnabled(): Boolean {
        return preferences.getBoolean(KEY_AI_CONVERSATION_ENABLED, true)
    }

    fun setAiConversationEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AI_CONVERSATION_ENABLED, enabled).apply()
    }

    fun isWakeWordEnabled(): Boolean {
        return preferences.getBoolean(KEY_WAKE_WORD_ENABLED, true)
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_WAKE_WORD_ENABLED, enabled).apply()
    }

    fun isCallRecordingEnabled(): Boolean {
        return preferences.getBoolean(KEY_CALL_RECORDING_ENABLED, true)
    }

    fun setCallRecordingEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_CALL_RECORDING_ENABLED, enabled).apply()
    }

    fun getFridayPitch(): Float {
        return preferences.getFloat(KEY_FRIDAY_PITCH, 1.25f)
    }

    fun setFridayPitch(pitch: Float) {
        preferences.edit().putFloat(KEY_FRIDAY_PITCH, pitch).apply()
    }

    fun getFridaySpeed(): Float {
        return preferences.getFloat(KEY_FRIDAY_SPEED, 1.05f)
    }

    fun setFridaySpeed(speed: Float) {
        preferences.edit().putFloat(KEY_FRIDAY_SPEED, speed).apply()
    }

    fun setFridayPitchAndSpeed(pitch: Float, speed: Float) {
        preferences.edit()
            .putFloat(KEY_FRIDAY_PITCH, pitch)
            .putFloat(KEY_FRIDAY_SPEED, speed)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "jarvis_settings"
        private const val KEY_ANSWERING_MACHINE_ENABLED = "key_answering_machine_enabled"
        private const val KEY_RING_THRESHOLD_SECONDS = "key_ring_threshold_seconds"
        private const val KEY_GEMINI_API_KEY = "key_gemini_api_key"
        private const val KEY_AI_CONVERSATION_ENABLED = "key_ai_conversation_enabled"
        private const val KEY_WAKE_WORD_ENABLED = "key_wake_word_enabled"
        private const val KEY_CALL_RECORDING_ENABLED = "key_call_recording_enabled"
        private const val KEY_FRIDAY_PITCH = "key_friday_pitch"
        private const val KEY_FRIDAY_SPEED = "key_friday_speed"

        /**
         * Paste your Google Gemini API Key here if you want it pre-configured inside the APK:
         * Example: const val DEFAULT_GEMINI_KEY = "AIzaSy..."
         */
        const val DEFAULT_GEMINI_KEY = ""

        lateinit var instance: JarvisApp
            private set
    }
}
