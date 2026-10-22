package com.jarvis.assistant.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

enum class AudioSlot(
    val slotKey: String,
    val fileName: String,
    val title: String,
    val description: String
) {
    GREETING(
        slotKey = "slot_greeting",
        fileName = "custom_greeting.mp3",
        title = "Assistant Greeting Voice",
        description = "Plays when activating voice assistant or tapping preview"
    ),
    VOICEMAIL(
        slotKey = "slot_voicemail",
        fileName = "custom_voicemail.mp3",
        title = "Answering Machine Voicemail",
        description = "Plays on speakerphone when unattended call is auto-answered"
    ),
    CALLER_ALERT(
        slotKey = "slot_caller",
        fileName = "custom_caller.mp3",
        title = "Incoming Caller Alert",
        description = "Plays custom audio alert when an incoming call arrives"
    );

    companion object {
        fun fromKey(key: String): AudioSlot? {
            return entries.find { it.slotKey == key }
        }
    }
}

object CustomVoiceStorage {

    private const val TAG = "CustomVoiceStorage"

    fun getVoicesDir(context: Context): File {
        val dir = File(context.filesDir, "voices")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun hasCustomAudio(context: Context, slot: AudioSlot): Boolean {
        val file = File(getVoicesDir(context), slot.fileName)
        return file.exists() && file.length() > 0
    }

    fun getAudioFile(context: Context, slot: AudioSlot): File? {
        val file = File(getVoicesDir(context), slot.fileName)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun saveAudioFromUri(context: Context, uri: Uri, slot: AudioSlot): Boolean {
        return try {
            val targetFile = File(getVoicesDir(context), slot.fileName)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d(TAG, "Successfully saved custom audio for ${slot.name} to ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving custom audio from URI: $uri", e)
            false
        }
    }

    fun clearAudio(context: Context, slot: AudioSlot): Boolean {
        val file = File(getVoicesDir(context), slot.fileName)
        return if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }
}
