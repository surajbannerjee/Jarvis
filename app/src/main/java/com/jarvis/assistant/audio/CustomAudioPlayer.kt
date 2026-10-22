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
     * Attempts to play custom audio clip from app's raw resources, assets,
     * or local /sdcard/Jarvis/voices/ directory.
     * Returns true if audio file was found and played, false otherwise.
     */
    fun playCustomAudio(
        context: Context,
        audioFileNameWithoutExtension: String,
        streamType: Int = AudioManager.STREAM_MUSIC,
        onComplete: (() -> Unit)? = null
    ): Boolean {
        stopAudio()

        // 1. Check res/raw for resource (e.g. R.raw.jarvis_greeting)
        val resId = context.resources.getIdentifier(
            audioFileNameWithoutExtension.lowercase(),
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
                Log.d(TAG, "Playing custom audio from res/raw: $audioFileNameWithoutExtension")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error playing raw audio resource", e)
                false
            }
        }

        // 2. Check external/internal voices directory (e.g. /Android/data/.../files/voices/ or /sdcard/Jarvis/voices/)
        val voiceDirs = listOf(
            File(context.getExternalFilesDir(null), "voices"),
            File("/sdcard/Jarvis/voices")
        )

        for (dir in voiceDirs) {
            val audioExtensions = listOf(".mp3", ".wav", ".m4a", ".ogg")
            for (ext in audioExtensions) {
                val file = File(dir, audioFileNameWithoutExtension + ext)
                if (file.exists() && file.canRead()) {
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
                        Log.d(TAG, "Playing custom audio file from storage: ${file.absolutePath}")
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error playing audio file from storage", e)
                        false
                    }
                }
            }
        }

        return false
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
