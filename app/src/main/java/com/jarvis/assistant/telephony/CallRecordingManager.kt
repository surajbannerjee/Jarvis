package com.jarvis.assistant.telephony

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordedCall(
    val id: String,
    val caller: String,
    val filePath: String,
    val timestamp: Long,
    val durationSec: Int,
    val fileSizeKb: Long
) {
    val formattedDate: String
        get() = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(timestamp))
}

object CallRecordingManager {

    private const val TAG = "CallRecordingManager"
    private const val PREFS_RECORDINGS = "friday_recordings_meta"
    private const val KEY_RECORDINGS_JSON = "recordings_list"

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var recordingStartTime: Long = 0L
    private var currentCaller: String = "Unknown"
    private var mediaPlayer: MediaPlayer? = null

    @Volatile
    var isRecording = false
        private set

    fun getRecordingsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "call_recordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Starts recording the unattended call when Friday answers
     */
    fun startRecording(context: Context, caller: String?): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording an active call.")
            return true
        }

        currentCaller = caller?.ifBlank { "Unknown" } ?: "Unknown"
        val safeName = currentCaller.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val timestamp = System.currentTimeMillis()
        val outputFile = File(getRecordingsDir(context), "Call_${safeName}_${timestamp}.m4a")
        currentRecordingFile = outputFile

        return try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            try {
                // Try voice communication source first
                recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            } catch (e: Exception) {
                Log.w(TAG, "VOICE_COMMUNICATION audio source not supported, fallback to MIC", e)
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }

            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(64000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(outputFile.absolutePath)

            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            recordingStartTime = System.currentTimeMillis()
            isRecording = true
            Log.d(TAG, "Call recording started for $currentCaller: ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            false
        }
    }

    /**
     * Stops recording and saves metadata
     */
    fun stopRecording(context: Context): RecordedCall? {
        if (!isRecording || mediaRecorder == null) return null

        return try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error during mediaRecorder.stop()", e)
                }
                release()
            }
            mediaRecorder = null
            isRecording = false

            val file = currentRecordingFile
            if (file != null && file.exists() && file.length() > 0) {
                val durationSec = ((System.currentTimeMillis() - recordingStartTime) / 1000L).toInt()
                val sizeKb = file.length() / 1024L
                val entry = RecordedCall(
                    id = file.name,
                    caller = currentCaller,
                    filePath = file.absolutePath,
                    timestamp = recordingStartTime,
                    durationSec = durationSec,
                    fileSizeKb = sizeKb
                )
                saveRecordingMetadata(context, entry)
                Log.d(TAG, "Call recording saved: ${entry.filePath} (${entry.durationSec}s, ${entry.fileSizeKb}KB)")
                entry
            } else {
                Log.w(TAG, "Call recording file empty or missing.")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping call recording", e)
            null
        } finally {
            mediaRecorder = null
            isRecording = false
            currentRecordingFile = null
        }
    }

    fun getRecordedCalls(context: Context): List<RecordedCall> {
        val prefs = context.getSharedPreferences(PREFS_RECORDINGS, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_RECORDINGS_JSON, "[]") ?: "[]"
        val list = mutableListOf<RecordedCall>()

        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val file = File(obj.getString("filePath"))
                if (file.exists()) {
                    list.add(
                        RecordedCall(
                            id = obj.getString("id"),
                            caller = obj.getString("caller"),
                            filePath = obj.getString("filePath"),
                            timestamp = obj.getLong("timestamp"),
                            durationSec = obj.getInt("durationSec"),
                            fileSizeKb = obj.getLong("fileSizeKb")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing recordings list", e)
        }

        val knownPaths = list.map { it.filePath }.toSet()
        val dirFiles = getRecordingsDir(context).listFiles { f -> f.isFile && f.length() > 0 }
        dirFiles?.forEach { file ->
            if (file.absolutePath !in knownPaths) {
                val caller = file.nameWithoutExtension.replace("Call_", "").substringBeforeLast("_")
                list.add(
                    RecordedCall(
                        id = file.name,
                        caller = if (caller.isBlank()) "Recorded Call" else caller,
                        filePath = file.absolutePath,
                        timestamp = file.lastModified(),
                        durationSec = 0,
                        fileSizeKb = file.length() / 1024L
                    )
                )
            }
        }

        return list.sortedByDescending { it.timestamp }
    }

    private fun saveRecordingMetadata(context: Context, newEntry: RecordedCall) {
        val currentList = getRecordedCalls(context).toMutableList()
        currentList.add(0, newEntry)

        val prefs = context.getSharedPreferences(PREFS_RECORDINGS, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (item in currentList.take(50)) { // Keep latest 50
            jsonArray.put(JSONObject().apply {
                put("id", item.id)
                put("caller", item.caller)
                put("filePath", item.filePath)
                put("timestamp", item.timestamp)
                put("durationSec", item.durationSec)
                put("fileSizeKb", item.fileSizeKb)
            })
        }
        prefs.edit().putString(KEY_RECORDINGS_JSON, jsonArray.toString()).apply()
    }

    fun deleteRecording(context: Context, id: String): Boolean {
        stopPlayback()
        val currentList = getRecordedCalls(context).toMutableList()
        val item = currentList.find { it.id == id }

        if (item != null) {
            try {
                val file = File(item.filePath)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting audio file: ${item.filePath}", e)
            }
            currentList.remove(item)

            val prefs = context.getSharedPreferences(PREFS_RECORDINGS, Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            for (rec in currentList) {
                jsonArray.put(JSONObject().apply {
                    put("id", rec.id)
                    put("caller", rec.caller)
                    put("filePath", rec.filePath)
                    put("timestamp", rec.timestamp)
                    put("durationSec", rec.durationSec)
                    put("fileSizeKb", rec.fileSizeKb)
                })
            }
            prefs.edit().putString(KEY_RECORDINGS_JSON, jsonArray.toString()).apply()
            return true
        }
        return false
    }

    fun playRecording(context: Context, filePath: String, onComplete: () -> Unit): Boolean {
        stopPlayback()
        val file = File(filePath)
        if (!file.exists()) return false

        return try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(file))
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                    onComplete()
                }
                start()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error playing recorded call", e)
            false
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping media player", e)
        } finally {
            mediaPlayer = null
        }
    }

    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }
}
