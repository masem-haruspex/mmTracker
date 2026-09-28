package com.mtracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object Audio {
	private const val TAG = "Audio"
	private const val SAMPLE_RATE = 16000
	private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
	private const val FORMAT = AudioFormat.ENCODING_PCM_16BIT
	private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, FORMAT)

	private var recorder: AudioRecord? = null
	private var isRecording = false
	private var currentFilePath: String? = null

	var onRecordingComplete: ((String) -> Unit)? = null
	var onRecordingFailed: ((Exception) -> Unit)? = null

	fun canRecord(context: Context): Boolean {
		return ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
	}

	fun startRecording(context: Context): String {
		if (!canRecord(context)) throw SecurityException("Record permission denied")

		val dir = File(context.cacheDir, "recordings").apply { mkdirs() }
		val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
		val file = File(dir, "rec_$timestamp.wav")
		currentFilePath = file.absolutePath

		recorder = AudioRecord(
			MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, FORMAT, BUFFER_SIZE * 2
		).also {
			if (it.state != AudioRecord.STATE_INITIALIZED) throw Exception("Failed to initialize AudioRecord")
		}

		isRecording = true
		Thread {
			var fos: FileOutputStream? = null
			var totalAudioLen = 0L
			try {
				fos = FileOutputStream(file)
				fos.write(ByteArray(44)) 
				recorder?.startRecording()
				val buffer = ByteArray(BUFFER_SIZE)
				while (isRecording) {
					val read = recorder?.read(buffer, 0, buffer.size) ?: break
					if (read > 0) {
						fos.write(buffer, 0, read)
						totalAudioLen += read
					}
				}
			} catch (e: Exception) {
				Log.e(TAG, "Recording error", e)
				onRecordingFailed?.invoke(e)
			} finally {
				recorder?.let { rec ->
					try { if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) rec.stop() } catch (_: IllegalStateException) {}
					rec.release()
				}
				recorder = null
				fos?.let {
					try {
						writeWavHeader(it, totalAudioLen, SAMPLE_RATE, 1, 16)
						it.close()
						val f = File(currentFilePath!!)
						if (f.exists() && f.length() > 44) {
							onRecordingComplete?.invoke(currentFilePath!!)
						} else {
							onRecordingFailed?.invoke(Exception("Recording failed or file empty"))
						}
					} catch (e: Exception) {
						Log.e(TAG, "Finalization error", e)
						onRecordingFailed?.invoke(e)
					} finally {
						onRecordingComplete = null
						onRecordingFailed = null
					}
				}
			}
		}.start()
		return currentFilePath!!
	}

	fun stopRecording() {
		isRecording = false 
	}

	fun getCurrentFilePath(): String? = currentFilePath

	fun compress(file: File): File {
		if (!file.exists()) throw Exception("File missing: ${file.absolutePath}")
		val output = File(file.parent, "${file.name}.gz")
		FileInputStream(file).use { fis ->
			GZIPOutputStream(FileOutputStream(output), 8192).use { gzos ->
				val buffer = ByteArray(8192)
				var len: Int
				while (fis.read(buffer).also { len = it } > 0) {
					gzos.write(buffer, 0, len)
				}
			}
		}
		if (output.exists() && output.length() > 0 && file.delete()) return output
		throw Exception("Compression failed")
	}

	fun decompress(file: File): File {
		if (!file.exists()) {
			val orig = File(file.absolutePath.removeSuffix(".gz"))
			if (orig.exists()) return orig
			throw Exception("File missing: ${file.absolutePath}")
		}
		val output = File(file.parent, file.name.removeSuffix(".gz"))
		if (output.exists()) output.delete()
		GZIPInputStream(FileInputStream(file)).use { gzis ->
			FileOutputStream(output).use { fos ->
				val buffer = ByteArray(8192)
				var len: Int
				while (gzis.read(buffer).also { len = it } > 0) {
					fos.write(buffer, 0, len)
				}
			}
		}
		return output
	}

	fun isCompressed(file: File): Boolean = file.name.endsWith(".gz")

	private fun writeWavHeader(fos: FileOutputStream, dataLen: Long, sampleRate: Int, channels: Int, bits: Int) {
		val totalLen = dataLen + 36
		val byteRate = sampleRate * channels * bits / 8
		val header = ByteArray(44)
		fun writeString(offset: Int, str: String) { str.forEachIndexed { i, c -> header[offset + i] = c.code.toByte() } }
		fun writeInt(offset: Int, value: Int) {
			header[offset] = (value and 0xff).toByte()
			header[offset + 1] = ((value shr 8) and 0xff).toByte()
			header[offset + 2] = ((value shr 16) and 0xff).toByte()
			header[offset + 3] = ((value shr 24) and 0xff).toByte()
		}
		fun writeShort(offset: Int, value: Short) {
			header[offset] = (value.toInt() and 0xff).toByte()
			header[offset + 1] = ((value.toInt() shr 8) and 0xff).toByte()
		}
		writeString(0, "RIFF"); writeInt(4, totalLen.toInt()); writeString(8, "WAVE"); writeString(12, "fmt ")
		writeInt(16, 16); writeShort(20, 1); writeShort(22, channels.toShort()); writeInt(24, sampleRate)
		writeInt(28, byteRate.toInt()); writeShort(32, (channels * bits / 8).toShort()); writeShort(34, bits.toShort())
		writeString(36, "data"); writeInt(40, dataLen.toInt())
		fos.channel.position(0); fos.write(header); fos.flush()
	}
}
