package com.mtracker

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Type {
	FOOD, HABIT, WORKOUT, HEALTH, IDEAS, DIARY, MONEY
}

data class Entry(
	val id: Long = 0,
	val type: Type,
	val timestamp: Long,
	val rawInput: String,
	val details: JSONObject,
	val location: String = "Unspecified"
) {
	fun getFormattedDate(pattern: String = "dd.MM.yyyy"): String {
		return try {
			SimpleDateFormat(pattern, Locale.US).format(Date(timestamp))
		} catch (e: Exception) {
			SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date(timestamp))
		}
	}
}
