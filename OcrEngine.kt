package com.mtracker

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object OcrEngine {

    private const val TAG = "OcrEngine"
    private const val TESSDATA_DIR = "tessdata"
    private const val LANG = "mkd"

    private var tessApi: TessBaseAPI? = null

    fun init(context: Context) {
        if (tessApi != null) return

        val dataPath = context.filesDir.absolutePath
        val tessDir = File(dataPath, TESSDATA_DIR)
        extractTessDataIfNeeded(context, tessDir)

        val api = TessBaseAPI()
        if (!api.init(dataPath, LANG)) {
            throw IllegalStateException("Tesseract init failed for lang: $LANG")
        }
        api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
        tessApi = api
        Log.d(TAG, "Tesseract initialized at $dataPath/$TESSDATA_DIR")
    }

    fun useOcr(bitmap: Bitmap): String {
        val api = tessApi ?: throw IllegalStateException("OcrEngine not initialized. Call init() first.")
        return try {
            api.setImage(bitmap)
            val text = api.utF8Text ?: ""
            api.clear()
            text.trim()
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed", e)
            ""
        }
    }

    fun shutdown() {
        tessApi?.recycle()
        tessApi = null
    }

    private fun extractTessDataIfNeeded(context: Context, tessDir: File) {
        if (!tessDir.exists()) tessDir.mkdirs()

        val assetManager = context.assets
        val files = assetManager.list(TESSDATA_DIR) ?: return

        files.forEach { fileName ->
            val outFile = File(tessDir, fileName)
            if (outFile.exists()) return@forEach

            try {
                assetManager.open("$TESSDATA_DIR/$fileName").use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Extracted $fileName")
            } catch (e: IOException) {
                Log.e(TAG, "Failed to extract $fileName", e)
            }
        }
    }
}
