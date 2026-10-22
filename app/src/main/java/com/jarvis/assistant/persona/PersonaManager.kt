package com.jarvis.assistant.persona

import android.content.Context
import android.content.SharedPreferences
import com.jarvis.assistant.JarvisApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PersonaManager {

    private const val KEY_ACTIVE_PERSONA = "key_active_persona_id"
    private val preferences: SharedPreferences
        get() = JarvisApp.instance.preferences

    private val _activePersonaFlow = MutableStateFlow(getActivePersona())
    val activePersonaFlow: StateFlow<StarkPersona> = _activePersonaFlow.asStateFlow()

    fun getActivePersona(): StarkPersona {
        val id = preferences.getString(KEY_ACTIVE_PERSONA, StarkPersona.JARVIS.id)
        return StarkPersona.fromId(id)
    }

    fun setActivePersona(persona: StarkPersona) {
        preferences.edit().putString(KEY_ACTIVE_PERSONA, persona.id).apply()
        _activePersonaFlow.value = persona
        // Apply persona voice settings to Speech Manager
        JarvisApp.instance.speechManager.applyPersona(persona)
    }
}
