package com.mtracker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SyncManager(
    private val context: Context,
    private val db: Database
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _queue = MutableStateFlow<List<PendingRecording>>(emptyList())
    val queue: StateFlow<List<PendingRecording>> = _queue.asStateFlow()

    private var isProcessing = false

    companion object {
        private const val TAG = "SyncManager"
        private const val MAX_RETRIES = 10
        private const val BASE_RETRY_DELAY = 15_000L
    }

    fun startQueueProcessing() {
        if (isProcessing) return
        isProcessing = true
        scope.launch { processLoop() }
    }

    fun refreshQueue() {
        _queue.value = db.getPendingQueue()
    }

    fun shutdown() {
        scope.cancel()
        isProcessing = false
    }

suspend fun processQueueSynchronously() {
    while (true) {
        val rec = db.getOldestPending() ?: break

        if (!rec.isTranscribed) {
            if (!transcribeOne(rec)) break
            continue
        }

        if (rec.status != PendingRecording.Status.COMPLETED) {
            if (!processAiOne(rec)) break
        }
    }
}

private suspend fun transcribeOne(rec: PendingRecording): Boolean {
    return try {
        db.updatePendingStatus(rec.id, PendingRecording.Status.PROCESSING, rec.retryCount, null)
        refreshQueue()

        val file = File(rec.filePath)
        if (!file.exists()) throw Exception("Audio file missing: ${rec.filePath}")

        val text = if (Audio.isCompressed(file)) {
            Audio.decompress(file).let { Vosk.transcribe(it) }
        } else {
            Vosk.transcribe(file)
        }

        db.updatePendingTranscription(rec.id, text)
        refreshQueue()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Manual transcription failed for ${rec.id}", e)
        val newRetry = rec.retryCount + 1
        val newStatus = if (newRetry >= MAX_RETRIES) PendingRecording.Status.FAILED
                        else PendingRecording.Status.PENDING
        db.updatePendingStatus(rec.id, newStatus, newRetry, "Transcription failed: ${e.message}")
        refreshQueue()
        false
    }
}

private suspend fun processAiOne(rec: PendingRecording): Boolean {
    return try {
        db.updatePendingStatus(rec.id, PendingRecording.Status.PROCESSING, rec.retryCount, null)
        refreshQueue()

        val text = rec.transcribedText
            ?: throw AI.AIException("Empty transcription", AI.AIException.Reason.PARSING_ERROR)

        val extracted = AI.extractFromTranscript(context, text)
        saveExtractedEntries(extracted, text)
        db.markPendingComplete(rec.id, text)
        cleanup(rec)
        refreshQueue()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Manual AI processing failed for ${rec.id}", e)
        val newRetry = rec.retryCount + 1
        val newStatus = if (newRetry >= MAX_RETRIES) PendingRecording.Status.FAILED
                        else PendingRecording.Status.PENDING
        db.updatePendingStatus(rec.id, newStatus, newRetry, "AI failed: ${e.message}")
        refreshQueue()
        false
    }
}

suspend fun completeEntry(recId: Long, aiResult: List<Entry>) {
    withContext(Dispatchers.IO) {
        val rec = db.getPendingById(recId) ?: return@withContext
        db.writableDatabase.beginTransaction()
        try {
            aiResult.forEach { db.insertEntry(it) }
            db.markPendingComplete(recId, rec.transcribedText ?: "")
            db.writableDatabase.setTransactionSuccessful()
        } finally {
            db.writableDatabase.endTransaction()
        }
        cleanup(rec)
        refreshQueue()
    }
}

    private suspend fun processLoop() {
        while (scope.isActive) {
            val rec = db.getOldestPending()
            if (rec == null) {
                isProcessing = false
                refreshQueue()
                return
            }

            if (rec.isPermanentlyFailed()) {
                cleanup(rec)
                continue
            }

            if (!rec.isTranscribed) {
                try {
                    db.updatePendingStatus(rec.id, PendingRecording.Status.PROCESSING, rec.retryCount, null)
                    refreshQueue()

                    val file = File(rec.filePath)
                    val text = if (Audio.isCompressed(file)) {
                        Audio.decompress(file).let { Vosk.transcribe(it) }
                    } else {
                        Vosk.transcribe(file)
                    }

                    db.updatePendingTranscription(rec.id, text)
                    refreshQueue()

                    isProcessing = false
                    return

                } catch (e: Exception) {
                    handleFailure(rec, e, "Transcription failed: ${e.message}")
                    continue
                }
            }

            if (rec.status != PendingRecording.Status.COMPLETED) {
                if (!isNetworkAvailable()) {
                    delay(BASE_RETRY_DELAY)
                    continue
                }

                try {
                    db.updatePendingStatus(rec.id, PendingRecording.Status.PROCESSING, rec.retryCount, null)
                    refreshQueue()

                    val text = rec.transcribedText
                        ?: throw AI.AIException("Empty transcription", AI.AIException.Reason.PARSING_ERROR)

                    val extracted = AI.extractFromTranscript(context, text)
                    saveExtractedEntries(extracted, text)
                    db.markPendingComplete(rec.id, text)
                    cleanup(rec)
                    refreshQueue()

                } catch (e: AI.AIException) {
                    handleFailure(rec, e, "AI Error: ${e.message}")
                } catch (e: Exception) {
                    handleFailure(rec, e, "Processing Error: ${e.message}")
                }
            }
        }
    }

    private fun saveExtractedEntries(extracted: JSONArray, rawText: String) {
        db.writableDatabase.beginTransaction()
        try {
            val timestamp = System.currentTimeMillis()

            for (i in 0 until extracted.length()) {
                val obj = extracted.getJSONObject(i)

                val aiLocation = obj.optString("location", "").trim()
                val location = when {
                    aiLocation.isNotEmpty() && !aiLocation.equals("Unspecified", ignoreCase = true) -> aiLocation
                    AI.isLocationEnabled(context) -> Location.getCurrentLocation(context)
                    else -> "Unspecified"
                }

                var typeStr = obj.optString("type", "DIARY").uppercase()
                if (typeStr == "INFO") typeStr = "IDEAS"

                val type = try {
                    Type.valueOf(typeStr)
                } catch (e: IllegalArgumentException) {
                    Type.DIARY
                }

                val details = obj.optJSONObject("details") ?: JSONObject()

                if (type == Type.FOOD) {
                    val item = obj.optString("item", "").lowercase()
                    val qty = obj.optInt("quantity", 1)
                    if (item.isNotEmpty()) {
                        AI.getFoodSpec(context, item)?.let { spec ->
                            val keys = spec.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val value = spec.opt(key)
                                if (value is Number) details.put(key, value.toDouble() * qty)
                            }
                        }
                        details.put("item", item)
                    }
                    details.put("quantity", qty)
                }

                val entryText = obj.optString("text", rawText)
                db.insertEntry(
                    Entry(
                        type = type,
                        timestamp = timestamp,
                        rawInput = entryText,
                        details = details,
                        location = location
                    )
                )
            }
            db.writableDatabase.setTransactionSuccessful()
        } finally {
            db.writableDatabase.endTransaction()
        }
    }

    private suspend fun handleFailure(rec: PendingRecording, e: Exception, errorMsg: String) {
        val newRetry = rec.retryCount + 1
        val isRetryable = when (e) {
            is AI.AIException -> e.isRetryable()
            is java.io.IOException -> true
            else -> false
        }

        val newStatus = if (!isRetryable || newRetry >= MAX_RETRIES) {
            PendingRecording.Status.FAILED
        } else {
            PendingRecording.Status.PENDING
        }

        db.updatePendingStatus(rec.id, newStatus, newRetry, errorMsg)
        refreshQueue()

        if (isRetryable && newRetry < MAX_RETRIES) {
            val delay = (BASE_RETRY_DELAY * Math.pow(1.5, newRetry - 1.0)).toLong().coerceAtMost(5 * 60 * 1000)
            delay(delay)
        } else {
            if (newStatus == PendingRecording.Status.FAILED) cleanup(rec)
            isProcessing = false
        }
    }

    internal fun cleanup(rec: PendingRecording) {
        File(rec.filePath).delete()
        db.deletePending(rec.id)
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}
