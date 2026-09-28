package com.mtracker

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.text.TextUtils
import org.json.JSONObject

class Database(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

	companion object {
		private const val DB_NAME = "tracker.db"
		private const val DB_VERSION = 6

		const val TABLE_ENTRIES = "entries"
		const val COL_ENTRY_ID = "id"
		const val COL_ENTRY_TYPE = "type"
		const val COL_ENTRY_TIMESTAMP = "timestamp"
		const val COL_ENTRY_RAW = "raw_input"
		const val COL_ENTRY_DETAILS = "details_json"
		const val COL_ENTRY_LOCATION = "location"

		private const val TABLE_PENDING = "pending_recordings"
		private const val COL_PENDING_ID = "id"
		private const val COL_PENDING_PATH = "file_path"
		private const val COL_PENDING_STATUS = "status"
		private const val COL_PENDING_RETRIES = "retry_count"
		private const val COL_PENDING_ERROR = "last_error"
		private const val COL_PENDING_CREATED = "created_at"
		private const val COL_PENDING_IS_TRANSCRIBED = "is_transcribed"
		private const val COL_PENDING_TEXT = "transcribed_text"
		private const val COL_PENDING_AI_RESULT = "ai_result"

		private const val TABLE_SUMMARIES = "daily_summaries"
		private const val COL_SUMMARY_ID = "id"
		private const val COL_SUMMARY_DATE = "date"
		private const val COL_SUMMARY_TEXT = "summary_text"
		private const val COL_SUMMARY_VIEWED = "viewed"
		private const val COL_SUMMARY_CREATED = "created_at"
	}

	override fun onCreate(db: SQLiteDatabase) {
		db.execSQL(
			"""CREATE TABLE $TABLE_ENTRIES (
				$COL_ENTRY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
				$COL_ENTRY_TYPE TEXT NOT NULL,
				$COL_ENTRY_TIMESTAMP INTEGER NOT NULL,
				$COL_ENTRY_RAW TEXT,
				$COL_ENTRY_DETAILS TEXT,
				$COL_ENTRY_LOCATION TEXT DEFAULT 'Unspecified'
			)"""
		)
		db.execSQL(
			"""CREATE TABLE $TABLE_PENDING (
				$COL_PENDING_ID INTEGER PRIMARY KEY AUTOINCREMENT,
				$COL_PENDING_PATH TEXT UNIQUE NOT NULL,
				$COL_PENDING_STATUS TEXT DEFAULT 'PENDING',
				$COL_PENDING_RETRIES INTEGER DEFAULT 0,
				$COL_PENDING_ERROR TEXT,
				$COL_PENDING_CREATED INTEGER NOT NULL,
				$COL_PENDING_IS_TRANSCRIBED INTEGER DEFAULT 0,
				$COL_PENDING_TEXT TEXT,
				$COL_PENDING_AI_RESULT TEXT
			)"""
		)
		db.execSQL(
			"""CREATE TABLE $TABLE_SUMMARIES (
				$COL_SUMMARY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
				$COL_SUMMARY_DATE TEXT UNIQUE NOT NULL,
				$COL_SUMMARY_TEXT TEXT NOT NULL,
				$COL_SUMMARY_VIEWED INTEGER DEFAULT 0,
				$COL_SUMMARY_CREATED INTEGER NOT NULL
			)"""
		)
		db.execSQL("CREATE INDEX idx_entries_timestamp ON $TABLE_ENTRIES($COL_ENTRY_TIMESTAMP)")
		db.execSQL("CREATE INDEX idx_pending_status ON $TABLE_PENDING($COL_PENDING_STATUS)")
	}

	override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
		if (oldVersion < 6) {
			try {
				db.execSQL(
					"""CREATE TABLE IF NOT EXISTS $TABLE_SUMMARIES (
						$COL_SUMMARY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
						$COL_SUMMARY_DATE TEXT UNIQUE NOT NULL,
						$COL_SUMMARY_TEXT TEXT NOT NULL,
						$COL_SUMMARY_VIEWED INTEGER DEFAULT 0,
						$COL_SUMMARY_CREATED INTEGER NOT NULL
					)"""
				)
			} catch (_: Exception) { }
		}
		if (oldVersion < 5) {
			try {
				db.execSQL("ALTER TABLE $TABLE_PENDING ADD COLUMN $COL_PENDING_AI_RESULT TEXT")
			} catch (_: Exception) { }
		}
		if (oldVersion < 4) {
			db.execSQL("DROP TABLE IF EXISTS $TABLE_ENTRIES")
			db.execSQL("DROP TABLE IF EXISTS $TABLE_PENDING")
			db.execSQL("DROP TABLE IF EXISTS $TABLE_SUMMARIES")
			onCreate(db)
		}
	}

	fun updatePendingAiResult(id: Long, aiResultJson: String) {
		val values = ContentValues().apply { put(COL_PENDING_AI_RESULT, aiResultJson) }
		writableDatabase.update(TABLE_PENDING, values, "$COL_PENDING_ID = ?", arrayOf(id.toString()))
	}

	fun insertEntry(entry: Entry): Long {
		return writableDatabase.insert(TABLE_ENTRIES, null, entry.toContentValues())
	}

	fun deleteEntries(ids: List<Long>) {
		if (ids.isEmpty()) return
		val idStrs = ids.map { it.toString() }.toTypedArray()
		writableDatabase.delete(
			TABLE_ENTRIES,
			"$COL_ENTRY_ID IN (${TextUtils.join(",", idStrs)})",
			null
		)
	}

	fun getEntriesSince(type: Type, sinceTimestamp: Long): List<Entry> {
		return queryEntries(
			"$COL_ENTRY_TYPE = ? AND $COL_ENTRY_TIMESTAMP >= ?",
			arrayOf(type.name, sinceTimestamp.toString())
		)
	}

	fun getEntriesByType(type: Type): List<Entry> {
		return queryEntries("$COL_ENTRY_TYPE = ?", arrayOf(type.name))
	}

	fun getAllEntries(): List<Entry> {
		return queryEntries(null, null)
	}

	fun getLatestEntryTimestamp(): Long {
		readableDatabase.rawQuery(
			"SELECT MAX($COL_ENTRY_TIMESTAMP) FROM $TABLE_ENTRIES",
			null
		).use { cursor ->
			return if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
		}
	}

	private fun queryEntries(where: String?, args: Array<String>?): List<Entry> {
		val list = mutableListOf<Entry>()
		val query = "SELECT * FROM $TABLE_ENTRIES" +
		(where?.let { " WHERE $it" } ?: "") +
		" ORDER BY $COL_ENTRY_TIMESTAMP DESC"

		readableDatabase.rawQuery(query, args).use { cursor ->
			while (cursor.moveToNext()) {
				cursor.toEntry()?.let { list.add(it) }
			}
		}
		return list
	}

	fun insertPending(rec: PendingRecording): Long {
		val values = ContentValues().apply {
			put(COL_PENDING_PATH, rec.filePath)
			put(COL_PENDING_STATUS, rec.status.name)
			put(COL_PENDING_CREATED, rec.createdAt)
			put(COL_PENDING_IS_TRANSCRIBED, if (rec.isTranscribed) 1 else 0)
			put(COL_PENDING_TEXT, rec.transcribedText)
		}
		return writableDatabase.insertWithOnConflict(TABLE_PENDING, null, values, SQLiteDatabase.CONFLICT_IGNORE)
	}

	fun getPendingQueue(): List<PendingRecording> {
		val list = mutableListOf<PendingRecording>()
		val query = "SELECT * FROM $TABLE_PENDING WHERE $COL_PENDING_STATUS != 'COMPLETED' ORDER BY $COL_PENDING_CREATED ASC"
		readableDatabase.rawQuery(query, null).use { cursor ->
			while (cursor.moveToNext()) list.add(cursor.toPending())
		}
		return list
	}

	fun getOldestPending(): PendingRecording? {
		val query = "SELECT * FROM $TABLE_PENDING WHERE $COL_PENDING_STATUS != 'COMPLETED' AND $COL_PENDING_STATUS != 'FAILED' ORDER BY $COL_PENDING_CREATED ASC LIMIT 1"
		readableDatabase.rawQuery(query, null).use { cursor ->
			return if (cursor.moveToFirst()) cursor.toPending() else null
		}
	}

	fun getPendingById(id: Long): PendingRecording? {
		readableDatabase.rawQuery(
			"SELECT * FROM $TABLE_PENDING WHERE $COL_PENDING_ID = ?",
			arrayOf(id.toString())
		).use { cursor ->
			return if (cursor.moveToFirst()) cursor.toPending() else null
		}
	}

	fun updatePendingStatus(id: Long, status: PendingRecording.Status, retryCount: Int, error: String?) {
		val values = ContentValues().apply {
			put(COL_PENDING_STATUS, status.name)
			put(COL_PENDING_RETRIES, retryCount)
			if (error != null) put(COL_PENDING_ERROR, error) else putNull(COL_PENDING_ERROR)
		}
		writableDatabase.update(TABLE_PENDING, values, "$COL_PENDING_ID = ?", arrayOf(id.toString()))
	}

	fun updatePendingTranscription(id: Long, text: String) {
		val values = ContentValues().apply {
			put(COL_PENDING_TEXT, text)
			put(COL_PENDING_IS_TRANSCRIBED, 1)
			put(COL_PENDING_STATUS, PendingRecording.Status.PENDING.name)
			put(COL_PENDING_RETRIES, 0)
			putNull(COL_PENDING_ERROR)
		}
		writableDatabase.update(TABLE_PENDING, values, "$COL_PENDING_ID = ?", arrayOf(id.toString()))
	}

	fun markPendingComplete(id: Long, transcribedText: String) {
		val values = ContentValues().apply {
			put(COL_PENDING_STATUS, PendingRecording.Status.COMPLETED.name)
			put(COL_PENDING_IS_TRANSCRIBED, 1)
			put(COL_PENDING_TEXT, transcribedText)
			put(COL_PENDING_RETRIES, 0)
			putNull(COL_PENDING_ERROR)
		}
		writableDatabase.update(TABLE_PENDING, values, "$COL_PENDING_ID = ?", arrayOf(id.toString()))
	}

	fun deletePending(id: Long) {
		writableDatabase.delete(TABLE_PENDING, "$COL_PENDING_ID = ?", arrayOf(id.toString()))
	}

	fun insertDailySummary(summary: DailySummary): Long {
		val values = ContentValues().apply {
			put(COL_SUMMARY_DATE, summary.date)
			put(COL_SUMMARY_TEXT, summary.summaryText)
			put(COL_SUMMARY_VIEWED, if (summary.viewed) 1 else 0)
			put(COL_SUMMARY_CREATED, summary.createdAt)
		}
		return writableDatabase.insertWithOnConflict(TABLE_SUMMARIES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
	}

	fun getDailySummaryForDate(date: String): DailySummary? {
		readableDatabase.rawQuery(
			"SELECT * FROM $TABLE_SUMMARIES WHERE $COL_SUMMARY_DATE = ?",
			arrayOf(date)
		).use { cursor ->
			return if (cursor.moveToFirst()) cursor.toDailySummary() else null
		}
	}

	fun getUnviewedDailySummary(): DailySummary? {
		readableDatabase.rawQuery(
			"SELECT * FROM $TABLE_SUMMARIES WHERE $COL_SUMMARY_VIEWED = 0 ORDER BY $COL_SUMMARY_DATE DESC LIMIT 1",
			null
		).use { cursor ->
			return if (cursor.moveToFirst()) cursor.toDailySummary() else null
		}
	}

	fun getAllDailySummaries(): List<DailySummary> {
		val list = mutableListOf<DailySummary>()
		readableDatabase.rawQuery(
			"SELECT * FROM $TABLE_SUMMARIES ORDER BY $COL_SUMMARY_DATE DESC",
			null
		).use { cursor ->
			while (cursor.moveToNext()) {
				cursor.toDailySummary()?.let { list.add(it) }
			}
		}
		return list
	}

	fun markSummaryAsViewed(id: Long) {
		val values = ContentValues().apply { put(COL_SUMMARY_VIEWED, 1) }
		writableDatabase.update(TABLE_SUMMARIES, values, "$COL_SUMMARY_ID = ?", arrayOf(id.toString()))
	}

	private fun Entry.toContentValues(): ContentValues {
		return ContentValues().apply {
			put(COL_ENTRY_TYPE, type.name)
			put(COL_ENTRY_TIMESTAMP, timestamp)
			put(COL_ENTRY_RAW, rawInput)
			put(COL_ENTRY_DETAILS, details.toString())
			put(COL_ENTRY_LOCATION, location)
		}
	}

	private fun Cursor.toEntry(): Entry? = try {
		Entry(
			id = getLong(getColumnIndexOrThrow(COL_ENTRY_ID)),
			type = parseType(getString(getColumnIndexOrThrow(COL_ENTRY_TYPE))),
			timestamp = getLong(getColumnIndexOrThrow(COL_ENTRY_TIMESTAMP)),
			rawInput = getString(getColumnIndexOrThrow(COL_ENTRY_RAW)),
			details = JSONObject(getString(getColumnIndexOrThrow(COL_ENTRY_DETAILS))),
			location = getString(getColumnIndexOrThrow(COL_ENTRY_LOCATION))
		)
	} catch (e: Exception) {
		e.printStackTrace()
		null
	}

	private fun Cursor.toPending(): PendingRecording = PendingRecording(
		id = getLong(getColumnIndexOrThrow(COL_PENDING_ID)),
		filePath = getString(getColumnIndexOrThrow(COL_PENDING_PATH)),
		status = PendingRecording.Status.valueOf(getString(getColumnIndexOrThrow(COL_PENDING_STATUS))),
		retryCount = getInt(getColumnIndexOrThrow(COL_PENDING_RETRIES)),
		lastError = getString(getColumnIndexOrThrow(COL_PENDING_ERROR)),
		createdAt = getLong(getColumnIndexOrThrow(COL_PENDING_CREATED)),
		isTranscribed = getInt(getColumnIndexOrThrow(COL_PENDING_IS_TRANSCRIBED)) == 1,
		transcribedText = getString(getColumnIndexOrThrow(COL_PENDING_TEXT)),
		aiResultJson = getString(getColumnIndexOrThrow(COL_PENDING_AI_RESULT))
	)

	private fun Cursor.toDailySummary(): DailySummary? = try {
		DailySummary(
			id = getLong(getColumnIndexOrThrow(COL_SUMMARY_ID)),
			date = getString(getColumnIndexOrThrow(COL_SUMMARY_DATE)),
			summaryText = getString(getColumnIndexOrThrow(COL_SUMMARY_TEXT)),
			viewed = getInt(getColumnIndexOrThrow(COL_SUMMARY_VIEWED)) == 1,
			createdAt = getLong(getColumnIndexOrThrow(COL_SUMMARY_CREATED))
		)
	} catch (e: Exception) {
		e.printStackTrace()
		null
	}

	private fun parseType(typeStr: String): Type {
		var str = typeStr
		if (str == "INFO") str = "IDEAS"
		return try {
			Type.valueOf(str)
		} catch (e: IllegalArgumentException) {
			Type.DIARY
		}
	}
}
