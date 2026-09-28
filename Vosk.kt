package com.mtracker

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream

object Vosk {
	private const val TAG = "Vosk"
	private const val SAMPLE_RATE = 16000f  
	private var model: Model? = null

	fun init(context: Context) {
		if (model != null) return
		synchronized(this) {
			if (model != null) return

			LibVosk.setLogLevel(LogLevel.WARNINGS)
			val modelDir = File(context.filesDir, "vosk-model-en-us-0.22-lgraph")

			if (!modelDir.exists() || modelDir.list()?.isEmpty() != false) {
				extractModelFromAssets(context, modelDir)
			}

			if (!modelDir.exists() || modelDir.list()?.isEmpty() != false) {
				throw Exception("Vosk model missing at ${modelDir.absolutePath}")
			}
			model = Model(modelDir.absolutePath)
			Log.d(TAG, "Model loaded")
		}
	}

	private fun extractModelFromAssets(context: Context, targetDir: File) {
		try {
			val assetManager = context.assets
			val modelFiles = assetManager.list("vosk-model-small-en-us") ?: return

			targetDir.mkdirs()

			fun copyAssetDir(assetPath: String, targetPath: String) {
				val files = assetManager.list(assetPath)
				if (files.isNullOrEmpty()) {
					assetManager.open(assetPath).use { input ->
						File(targetPath).outputStream().use { output ->
							input.copyTo(output)
						}
					}
				} else {
					File(targetPath).mkdirs()
					files.forEach { filename ->
						copyAssetDir("$assetPath/$filename", "$targetPath/$filename")
					}
				}
			}

			copyAssetDir("vosk-model-en-us-0.22-lgraph", targetDir.absolutePath)
			Log.d(TAG, "Model extracted to ${targetDir.absolutePath}")
		} catch (e: Exception) {
			Log.e(TAG, "Failed to extract Vosk model", e)
			throw e
		}
	}

	fun transcribe(audioFile: File): String {
		val m = model ?: throw IllegalStateException("Vosk not initialized")
		if (!audioFile.exists()) throw Exception("Audio file not found: ${audioFile.absolutePath}")

		val sb = StringBuilder()
		FileInputStream(audioFile).use { fis ->
			Recognizer(m, SAMPLE_RATE).use { recognizer ->
				val buffer = ByteArray(4096)
				var nbytes: Int
				while (fis.read(buffer).also { nbytes = it } >= 0) {
					if (recognizer.acceptWaveForm(buffer, nbytes)) {
						appendText(sb, recognizer.result)
					}
				}
				appendText(sb, recognizer.finalResult)
			}
		}
		return sb.toString().trim()
	}

	private fun appendText(sb: StringBuilder, jsonChunk: String) {
		try {
			val obj = JSONObject(jsonChunk)
			val text = obj.optString("text", "").trim()
			if (text.isNotEmpty()) {
				if (sb.isNotEmpty()) sb.append(" ")
				sb.append(text)
			}
		} catch (e: Exception) {
			val raw = jsonChunk.trim()
			if (raw.isNotEmpty()) {
				if (sb.isNotEmpty()) sb.append(" ")
				sb.append(raw)
			}
		}
	}
}
