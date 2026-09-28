package com.mtracker

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.Date

object Json {
	private const val TAG = "Json"

	fun exportToStream(db: Database, out: OutputStream, context: Context) {
		Log.d(TAG, "Starting export")
		val root = JSONObject().apply {
			put("export_date", Date().toString())
			put("entries", exportAllEntries(db))
			put("food_specs", AI.getFoodSpecsAll(context))
		}
		out.bufferedWriter().use { it.write(root.toString(2)) }
		Log.d(TAG, "Export completed")
	}

	fun importFromStream(context: Context, inputStream: InputStream, db: Database) {
		val text = inputStream.bufferedReader().use(BufferedReader::readText)
		val root = JSONObject(text)

		root.optJSONArray("entries")?.let { entries ->
			db.writableDatabase.beginTransaction()
			try {
				for (i in 0 until entries.length()) {
					val obj = entries.getJSONObject(i)
					var typeStr = obj.getString("type")
					if (typeStr == "INFO") typeStr = "IDEAS"
					val type = try {
						Type.valueOf(typeStr)
					} catch (e: IllegalArgumentException) {
						Type.DIARY
					}
					db.insertEntry(
						Entry(
							type = type,
							timestamp = obj.getLong("timestamp"),
							rawInput = obj.optString("raw", ""),
							details = obj.optJSONObject("details") ?: JSONObject(),
							location = obj.optString("location", "Unspecified")
						)
					)
				}
				db.writableDatabase.setTransactionSuccessful()
				Log.d(TAG, "Imported ${entries.length()} entries")
			} finally {
				db.writableDatabase.endTransaction()
			}
		}

		root.optJSONObject("food_specs")?.let { specs ->
			val keys = specs.keys()
			while (keys.hasNext()) {
				val key = keys.next()
				AI.saveFoodSpec(context, key, specs.getJSONObject(key))
			}
			Log.d(TAG, "Imported food specs")
		}
	}

	private fun exportAllEntries(db: Database): JSONArray {
		val arr = JSONArray()
		db.readableDatabase.rawQuery("SELECT * FROM ${Database.TABLE_ENTRIES} ORDER BY ${Database.COL_ENTRY_TIMESTAMP} DESC", null).use { cursor ->
			while (cursor.moveToNext()) {
				try {
					val obj = JSONObject().apply {
						put("type", cursor.getString(cursor.getColumnIndexOrThrow(Database.COL_ENTRY_TYPE)))
						put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(Database.COL_ENTRY_TIMESTAMP)))
						put("raw", cursor.getString(cursor.getColumnIndexOrThrow(Database.COL_ENTRY_RAW)))
						put("details", JSONObject(cursor.getString(cursor.getColumnIndexOrThrow(Database.COL_ENTRY_DETAILS))))
						put("location", cursor.getString(cursor.getColumnIndexOrThrow(Database.COL_ENTRY_LOCATION)))
					}
					arr.put(obj)
				} catch (e: Exception) {
					Log.e(TAG, "Export row failed", e)
				}
			}
		}
		return arr
	}
}
