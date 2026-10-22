package com.jarvis.assistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.io.File

object CustomAudioPlayer {

    private const val TAG = "CustomAudioPlayer"
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Attempts to play custom audio clip from:
     * 1. In-app user-uploaded slots (CustomVoiceStorage)
     * 2. App's res/raw/ resources
     * 3. Device storage directories (/sdcard/Jarvis/voices/ or app internal files)
     * Returns true if audio file was found and played, false otherwise.
     */
    fun playCustomAudio(
        context: Context,
        audioFileNameWithoutExtension: String,
        streamType: Int = AudioManager.STREAM_MUSIC,
        onComplete: (() -> Unit)? = null
    ): Boolean {
        stopAudio()

        val lowerName = audioFileNameWithoutExtension.lowercase()

        // 1. Check if it matches an in-app uploaded AudioSlot
        val matchedSlot = when {
            lowerName.contains("greeting") -> AudioSlot.GREETING
            lowerName.contains("voicemail") -> AudioSlot.VOICEMAIL
            lowerName.contains("caller") -> AudioSlot.CALLER_ALERT
            else -> null
        }

        if (matchedSlot != null) {
            val slotFile = CustomVoiceStorage.getAudioFile(context, matchedSlot)
            if (slotFile != null) {
                Log.d(TAG, "Playing user-uploaded audio for slot ${matchedSlot.name}: ${slotFile.absolutePath}")
                return playAudioFile(context, slotFile, streamType, onComplete)
            }
        }

        // 2. Check res/raw for resource (e.g. R.raw.jarvis_greeting)
        val resId = context.resources.getIdentifier(
            lowerName,
            "raw",
            context.packageName
        )

        if (resId != 0) {
            return try {
                mediaPlayer = MediaPlayer.create(context, resId).apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setLegacyStreamType(streamType)
                            .build()
                    )
                    setOnCompletionListener {
                        stopAudio()
                        onComplete?.invoke()
                    }
                    start()
                }
                Log.d(TAG, "Playing custom audio from res/raw: $lowerName")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error playing raw audio resource", e)
                false
            }
        }

        // 3. Check external/internal voices directory (e.g. /Android/data/.../files/voices/ or /sdcard/Jarvis/voices/)
        val voiceDirs = listOf(
            CustomVoiceStorage.getVoicesDir(context),
            File(context.getExternalFilesDir(null), "voices"),
            File("/sdcard/Jarvis/voices")
        )

        for (dir in voiceDirs) {
            val audioExtensions = listOf(".mp3", ".wav", ".m4a", ".ogg")
            for (ext in audioExtensions) {
                val file = File(dir, audioFileNameWithoutExtension + ext)
                if (file.exists() && file.canRead()) {
                    Log.d(TAG, "Playing custom audio file from storage: ${file.absolutePath}")
                    return playAudioFile(context, file, streamType, onComplete)
                }
            }
        }

        return false
    }

    fun playAudioFile(
        context: Context,
        file: File,
        streamType: Int = AudioManager.STREAM_MUSIC,
        onComplete: (() -> Unit)? = null
    ): Boolean {
        stopAudio()
        return try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setLegacyStreamType(streamType)
                        .build()
                )
                setDataSource(context, Uri.fromFile(file))
                prepare()
                setOnCompletionListener {
                    stopAudio()
                    onComplete?.invoke()
                }
                start()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file: ${file.absolutePath}", e)
            false
        }
    }

    fun playAudioSlot(
        context: Context,
        slot: AudioSlot,
        streamType: Int = AudioManager.STREAM_MUSIC,
        onComplete: (() -> Unit)? = null
    ): Boolean {
        val slotFile = CustomVoiceStorage.getAudioFile(context, slot)
        return if (slotFile != null) {
            playAudioFile(context, slotFile, streamType, onComplete)
        } else {
            false
        }
    }

    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio player", e)
        } finally {
            mediaPlayer = null
        }
    }
}
