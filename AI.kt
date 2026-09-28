package com.mtracker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.mtracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object AI {
	private const val TAG = "AI"
	private const val BASE_URL = "https://api.groq.com/openai/v1"
	//private const val MODEL = "llama-3.1-8b-instant"
	private const val MODEL = "openai/gpt-oss-20b"

	private val client = OkHttpClient.Builder()
	.connectTimeout(10, TimeUnit.SECONDS)
	.readTimeout(30, TimeUnit.SECONDS)
	.build()

	private const val PREFS_NAME = "tracker_prefs"
	private const val KEY_EXTRACTION_PROMPT = "extraction_prompt"
	private const val KEY_SUMMARY_PROMPT = "summary_prompt"
	private const val KEY_FOOD_SPECS = "food_specs"
	private const val KEY_WORKOUT_SPECS = "workout_specs"
	private const val KEY_HABITS = "habits_list"
	private const val KEY_LOCATION_ENABLED = "location_enabled"
	private const val KEY_DATE_FORMAT = "date_format"

	private const val KEY_CURRENT_MISSION = "current_mission"
	private const val KEY_MISSION_COMPLETED_DATE = "mission_completed_date"
	private const val KEY_TREAT_COUNT = "treat_count"
	private const val KEY_TREAT_DATE = "treat_date"

	fun getApiKey(): String = BuildConfig.AI_API_KEY
	fun hasApiKey(): Boolean = getApiKey().length >= 20

	fun getExtractionPrompt(context: Context): String {
		return prefs(context).getString(KEY_EXTRACTION_PROMPT,
		"You are a precise data extraction assistant. Analyze the transcript and split it into specific, trackable entries.\n\n" +
		"PRIORITY RULES:\n" +
		"1. ALWAYS prioritize extracting data into: FOOD, HABIT, WORKOUT, HEALTH, MONEY.\n" +
		"2. DIARY and IDEAS are STRICT LAST RESORTS — only used when nothing else fits.\n" +
		"3. If one sentence contains multiple trackable facts, CREATE SEPARATE JSON ENTRIES for each fact.\n\n" +
		"DIARY / IDEAS TEXT RULE (CRITICAL — DO NOT VIOLATE):\n" +
		"- For type DIARY or IDEAS, the 'text' field MUST contain the user's FULL, VERBATIM sentence(s) exactly as transcribed. " +
		"Do NOT summarize, shorten, paraphrase, condense, or omit any part of what was said. " +
		"If the user spoke three sentences about their day, the 'text' field contains all three sentences, unedited.\n" +
		"- DIARY is a personal journal. A summary is worthless to the user. Preserve their own words.\n" +
		"- This rule ONLY applies to DIARY and IDEAS. For FOOD / HABIT / WORKOUT / HEALTH / MONEY, keep 'text' concise as usual.\n\n" +
		"MISSION SYSTEM:\n" +
		"- If the user states a mission for today (e.g., \"my mission today is to...\"), create a DIARY entry with text \"Mission: [mission]\".\n" +
		"- If the user says they finished yesterday's mission and want a treat today, create a FOOD entry for the treat with text \"[food] (todays treat)\".\n" +
		"- Only ONE treat per day is allowed. If the user mentions multiple treats, only mark the first one as \"(todays treat)\" and treat the rest as normal food.\n\n" +
		"LOCATION: If a location is mentioned in the text, ALWAYS extract it into the 'location' field.\n\n" +
		"Output format - STRICT JSON array only, no markdown, no explanations:\n" +
		"[{\"type\":\"FOOD|HABIT|WORKOUT|HEALTH|DIARY|IDEAS|MONEY\",\"item\":\"optional_name\",\"quantity\":1,\"text\":\"concise fact OR full verbatim sentence(s) for Diary/Ideas\",\"location\":\"extracted location or Unspecified\",\"details\":{}}]"
	)!!
}

fun setExtractionPrompt(context: Context, prompt: String) {
	prefs(context).edit().putString(KEY_EXTRACTION_PROMPT, prompt).apply()
	Log.d(TAG, "Extraction prompt updated")
}

fun getSummaryPrompt(context: Context): String {
	return prefs(context).getString(KEY_SUMMARY_PROMPT,
	"You are a strict hesychast Christian spiritual father — an experienced staretz — speaking to your spiritual child. " +
	"Your voice is that of a deeply serious, sober, prayerful elder whose single concern is the salvation of the soul.\n\n" +
	"TONE:\n" +
	"- Grave, sober, quiet. No jokes, no modern slang, no therapy-speak, no exclamation marks, no emoji.\n" +
	"- Speak as a father worried for his child's soul: concerned, direct, sometimes severe, always out of love.\n" +
	"- Refer where they apply to the passions, the demons, the nous, the heart, watchfulness (nepsis), the Jesus Prayer, and repentance.\n" +
	"- When the child has been slacking — gluttony, idleness, neglected prayer, self-indulgence, excess treats, skipped habits — " +
	"name it plainly as a spiritual danger. Warn that the demons find purchase in unguarded hours. Call to repentance without despair, " +
	"and always to hope in Christ. And let the child know that without watchfulness his soul is at risk.\n" +
	"- When the child has been diligent, do not flatter. Acknowledge briefly, and warn against vainglory.\n\n" +
	"FORM (STRICT):\n" +
	"- EXACTLY THREE SHORT PARAGRAPHS. No more. No fewer.\n" +
	"- Paragraph 1: what was done well or neglected, named soberly.\n" +
	"- Paragraph 2: the spiritual danger revealed - the passion, the demon, the unguarded hour.\n" +
	"- Paragraph 3: the call to repentance and hope in Christ.\n" +
	"- Each paragraph 2–3 sentences. Be terse. Every word must carry weight; cut anything that does not.\n" +
	"- Begin directly. No greetings. No meta-commentary such as 'Here is your summary' or 'Let me review your data'.\n" +
	"- Address the child as 'you'. Use 'my child' at most once.\n\n" +
	"CONTENT RULES:\n" +
	"1. Review the tracked data soberly. Name what was done, what was neglected, and what it reveals about the state of the soul.\n" +
	"2. Nutrition: judge not only the single day but the whole week. Gluttony and indulgence in sweets are to be named as passions, " +
	"not merely 'bad choices'.\n" +
	"3. Missing habits: name each one and warn that the unguarded hour belongs to the enemy.\n\n" +
	"MISSION & TREAT RULES:\n" +
	"- If yesterday's mission was completed, exactly ONE treat was earned. If more were taken, name this as gluttony and a yielding " +
	"to the enemy's suggestion.\n" +
	"- If yesterday's mission was NOT completed, no treats were permitted. If any were taken, name this as a small betrayal, " +
	"call it to repentance, and do not drive the child to despair.\n" +
	"- Acknowledge mission completion as obedience, and warn against pride."
)!!
}

fun setSummaryPrompt(context: Context, prompt: String) {
	prefs(context).edit().putString(KEY_SUMMARY_PROMPT, prompt).apply()
	Log.d(TAG, "Summary prompt updated")
}

fun saveFoodSpec(context: Context, foodName: String, spec: JSONObject) {
	try {
		val map = JSONObject(prefs(context).getString(KEY_FOOD_SPECS, "{}"))
		map.put(foodName.lowercase(), spec)
		prefs(context).edit().putString(KEY_FOOD_SPECS, map.toString()).apply()
	} catch (e: Exception) {
		Log.e(TAG, "Failed to save food spec", e)
	}
}

fun getFoodSpec(context: Context, foodName: String): JSONObject? {
	return try {
		val map = JSONObject(prefs(context).getString(KEY_FOOD_SPECS, "{}"))
		map.optJSONObject(foodName.lowercase())
	} catch (e: Exception) { null }
}

fun getFoodSpecsAll(context: Context): JSONObject {
	return try {
		JSONObject(prefs(context).getString(KEY_FOOD_SPECS, "{}"))
	} catch (e: Exception) { JSONObject() }
}

fun saveWorkoutSpec(context: Context, workoutName: String, spec: JSONObject) {
	try {
		val map = JSONObject(prefs(context).getString(KEY_WORKOUT_SPECS, "{}"))
		map.put(workoutName.lowercase(), spec)
		prefs(context).edit().putString(KEY_WORKOUT_SPECS, map.toString()).apply()
	} catch (e: Exception) {
		Log.e(TAG, "Failed to save workout spec", e)
	}
}

fun getWorkoutSpec(context: Context, workoutName: String): JSONObject? {
	return try {
		val map = JSONObject(prefs(context).getString(KEY_WORKOUT_SPECS, "{}"))
		map.optJSONObject(workoutName.lowercase())
	} catch (e: Exception) { null }
}

fun getWorkoutSpecsAll(context: Context): JSONObject {
	return try {
		JSONObject(prefs(context).getString(KEY_WORKOUT_SPECS, "{}"))
	} catch (e: Exception) { JSONObject() }
}

fun saveHabit(context: Context, habitName: String) {
	try {
		val current = getHabitsList(context).toMutableSet()
		current.add(habitName.lowercase().trim())
		prefs(context).edit().putString(KEY_HABITS, JSONArray(current.toList()).toString()).apply()
	} catch (e: Exception) {
		Log.e(TAG, "Failed to save habit", e)
	}
}

fun removeHabit(context: Context, habitName: String) {
	try {
		val current = getHabitsList(context).toMutableSet()
		current.remove(habitName.lowercase().trim())
		prefs(context).edit().putString(KEY_HABITS, JSONArray(current.toList()).toString()).apply()
	} catch (e: Exception) {
		Log.e(TAG, "Failed to remove habit", e)
	}
}

fun getHabitsList(context: Context): List<String> {
	return try {
		val arr = JSONArray(prefs(context).getString(KEY_HABITS, "[]"))
		val list = mutableListOf<String>()
		for (i in 0 until arr.length()) list.add(arr.getString(i))
		list
	} catch (e: Exception) { emptyList() }
}

fun getHabitsContext(context: Context): String {
	val habits = getHabitsList(context)
	if (habits.isEmpty()) return ""
	return "\n\nThe user has defined these DAILY HABITS that they want to maintain: " + habits.joinToString(", ") +
	". When reviewing their data, STRICTLY check if these habits appear in recent entries. " +
	"If any habit is missing or infrequent, SCOLD the user with tough love. " +
	"Do not be gentle — they want to be held accountable."
}

fun isLocationEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_LOCATION_ENABLED, true)
fun setLocationEnabled(context: Context, enabled: Boolean) {
	prefs(context).edit().putBoolean(KEY_LOCATION_ENABLED, enabled).apply()
}

fun getDateFormat(context: Context): String = prefs(context).getString(KEY_DATE_FORMAT, "dd.MM.yyyy")!!
fun setDateFormat(context: Context, format: String) {
	prefs(context).edit().putString(KEY_DATE_FORMAT, format).apply()
}

fun saveMission(context: Context, mission: String) {
	prefs(context).edit().putString(KEY_CURRENT_MISSION, mission).apply()
}

fun getMission(context: Context): String? = prefs(context).getString(KEY_CURRENT_MISSION, null)

fun completeMission(context: Context) {
	prefs(context).edit().putLong(KEY_MISSION_COMPLETED_DATE, System.currentTimeMillis()).apply()
}

fun isYesterdayMissionCompleted(context: Context): Boolean {
	val completed = prefs(context).getLong(KEY_MISSION_COMPLETED_DATE, 0)
	if (completed == 0L) return false
	val dayMs = 86400000L
	val yesterdayStart = ((System.currentTimeMillis() / dayMs) - 1) * dayMs
	val yesterdayEnd = yesterdayStart + dayMs
	return completed in yesterdayStart..yesterdayEnd
}

fun isTodayMissionCompleted(context: Context): Boolean {
	val completed = prefs(context).getLong(KEY_MISSION_COMPLETED_DATE, 0)
	val dayMs = 86400000L
	val todayStart = (System.currentTimeMillis() / dayMs) * dayMs
	return completed >= todayStart
}

fun addTreat(context: Context) {
	val dayMs = 86400000L
	val currentDate = (System.currentTimeMillis() / dayMs) * dayMs
	val savedDate = prefs(context).getLong(KEY_TREAT_DATE, 0)
	if (savedDate != currentDate) {
		prefs(context).edit().putLong(KEY_TREAT_DATE, currentDate).putInt(KEY_TREAT_COUNT, 1).apply()
	} else {
		val count = prefs(context).getInt(KEY_TREAT_COUNT, 0) + 1
		prefs(context).edit().putInt(KEY_TREAT_COUNT, count).apply()
	}
}

fun getTreatCountToday(context: Context): Int {
	val dayMs = 86400000L
	val currentDate = (System.currentTimeMillis() / dayMs) * dayMs
	val savedDate = prefs(context).getLong(KEY_TREAT_DATE, 0)
	return if (savedDate == currentDate) prefs(context).getInt(KEY_TREAT_COUNT, 0) else 0
}

fun getMissionContext(context: Context): String {
	val mission = getMission(context)
	val completedYesterday = isYesterdayMissionCompleted(context)
	val treats = getTreatCountToday(context)
	val sb = StringBuilder()
	if (!mission.isNullOrBlank()) sb.append("\n\nCURRENT DAILY MISSION: $mission")
	if (completedYesterday) {
		sb.append("\nYesterday's mission was COMPLETED. Today the user is allowed exactly ONE treat.")
	} else {
		sb.append("\nYesterday's mission was NOT completed. NO treats allowed today.")
	}
	sb.append("\nTreats consumed today so far: $treats.")
	return sb.toString()
}

fun processEntriesForMissions(context: Context, entries: List<Entry>) {
	entries.forEach { entry ->
		if (entry.type == Type.DIARY) {
			val text = entry.rawInput.trim()
			if (text.startsWith("Mission:", ignoreCase = true)) {
				saveMission(context, text.removePrefix("Mission:").trim())
			}
			if (text.contains("completed mission", ignoreCase = true) || text.contains("finished mission", ignoreCase = true)) {
				completeMission(context)
			}
		}
		if (entry.type == Type.FOOD && entry.rawInput.contains("(todays treat)", ignoreCase = true)) {
			addTreat(context)
		}
	}
}

suspend fun extractFromTranscript(context: Context, transcript: String): JSONArray {
	return withContext(Dispatchers.IO) {
		val apiKey = getApiKey()
		if (apiKey.isEmpty()) throw AIException("API key not configured", AIException.Reason.AUTH_ERROR)

		val systemPrompt = getExtractionPrompt(context) + getMissionContext(context) +
		"\nOutput format - STRICT JSON array only, no markdown:\n" +
		"[{\"type\":\"FOOD|HABIT|WORKOUT|HEALTH|DIARY|IDEAS|MONEY\",\"item\":\"optional_item_name\",\"quantity\":1,\"text\":\"concise fact OR full sentence for Diary/Ideas\",\"location\":\"extracted location or Unspecified\",\"details\":{}}]"

		val payload = JSONObject().apply {
			put("model", MODEL)
			put("messages", JSONArray().apply {
				put(JSONObject().put("role", "system").put("content", systemPrompt))
				put(JSONObject().put("role", "user").put("content", "Extract from: $transcript"))
			})
			put("temperature", 0.1)
		}

		executeRequest(apiKey, payload).let { content ->
			val jsonStr = when {
				content.startsWith("```json") -> content.substring(7, content.lastIndexOf("```")).trim()
				content.startsWith("```") -> content.substring(3, content.lastIndexOf("```")).trim()
				else -> content
			}
			JSONArray(jsonStr)
		}
	}
}

suspend fun generateSummary(context: Context, entries: List<Entry>): String {
	if (entries.isEmpty()) return "No entries found for the selected period."
	return withContext(Dispatchers.IO) {
		val apiKey = getApiKey()
		if (apiKey.isEmpty()) throw AIException("API key not configured", AIException.Reason.AUTH_ERROR)

		val sb = StringBuilder()
		entries.forEach { entry ->
			sb.append("- [${entry.type}] ${entry.getFormattedDate("dd.MM.yyyy HH:mm")}: ${entry.rawInput}")
			if (entry.details.length() > 0) sb.append(" {${entry.details}}")
			sb.append("\n")
		}

		val payload = JSONObject().apply {
			put("model", MODEL)
			put("messages", JSONArray().apply {
				put(JSONObject().put("role", "system").put("content", getSummaryPrompt(context) + getHabitsContext(context) + getMissionContext(context)))
				put(JSONObject().put("role", "user").put("content", "My tracked entries for yesterday:\n$sb"))
			})
			put("temperature", 0.7)
		}

		executeRequest(apiKey, payload)
	}
}

suspend fun generateCategorySummary(context: Context, entries: List<Entry>, category: Type): String {
	return withContext(Dispatchers.IO) {
		val apiKey = getApiKey()
		if (apiKey.isEmpty()) throw AIException("API key not configured", AIException.Reason.AUTH_ERROR)

		val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
		val byDate = TreeMap<String, MutableList<Entry>>()
		entries.forEach { entry ->
			byDate.getOrPut(dayFormat.format(Date(entry.timestamp))) { mutableListOf() }.add(entry)
		}

		val contextBuilder = StringBuilder().apply {
			append("USER'S COMPLETE ${category} HISTORY:\n")
			byDate.forEach { (day, dayEntries) ->
				append("\n📅 $day:\n")
				dayEntries.forEach { entry ->
					append("  • ${entry.rawInput}")
					if (entry.details.length() > 0) {
						append(" {")
						val keys = entry.details.keys()
						var first = true
						while (keys.hasNext()) {
							val key = keys.next()
							if (!first) append(", ")
							append("$key:${entry.details.opt(key)}")
							first = false
						}
						append("}")
					}
					append("\n")
				}
			}
		}

		val systemPrompt = getSummaryPrompt(context) +
		"\n\nYou are analyzing the user's COMPLETE historical data for category: $category. " +
		"Identify patterns, trends, strengths, and areas for improvement. " +
		"Be specific, data-driven, and actionable. End with 1-2 concrete suggestions."

		val payload = JSONObject().apply {
			put("model", MODEL)
			put("messages", JSONArray().apply {
				put(JSONObject().put("role", "system").put("content", systemPrompt))
				put(JSONObject().put("role", "user").put("content", contextBuilder.toString()))
			})
			put("temperature", 0.3)
			put("max_tokens", 1000)
		}

		executeRequest(apiKey, payload)
	}
}

suspend fun askFreeForm(context: Context, question: String, allEntries: List<Entry>): String {
	return withContext(Dispatchers.IO) {
		val apiKey = getApiKey()
		if (apiKey.isEmpty()) throw AIException("API key not configured", AIException.Reason.AUTH_ERROR)

		val qLower = question.lowercase()

		val relevantTypes = mutableSetOf<Type>()
		val keywords = mapOf(
			"food" to Type.FOOD, "eat" to Type.FOOD, "meal" to Type.FOOD, "calorie" to Type.FOOD,
			"fruit" to Type.FOOD, "vegetable" to Type.FOOD, "protein" to Type.FOOD, "carb" to Type.FOOD,
			"fat" to Type.FOOD, "diet" to Type.FOOD, "nutrition" to Type.FOOD, "snack" to Type.FOOD,
			"workout" to Type.WORKOUT, "exercise" to Type.WORKOUT, "gym" to Type.WORKOUT,
			"run" to Type.WORKOUT, "training" to Type.WORKOUT, "lift" to Type.WORKOUT, "cardio" to Type.WORKOUT,
			"health" to Type.HEALTH, "sleep" to Type.HEALTH, "weight" to Type.HEALTH, "sick" to Type.HEALTH,
			"symptom" to Type.HEALTH, "blood" to Type.HEALTH, "heart" to Type.HEALTH,
			"habit" to Type.HABIT, "routine" to Type.HABIT, "daily" to Type.HABIT, "track" to Type.HABIT,
			"money" to Type.MONEY, "spend" to Type.MONEY, "cost" to Type.MONEY, "expense" to Type.MONEY,
			"income" to Type.MONEY, "budget" to Type.MONEY, "price" to Type.MONEY, "paid" to Type.MONEY,
			"idea" to Type.IDEAS, "thought" to Type.IDEAS, "plan" to Type.IDEAS, "project" to Type.IDEAS
		)

		keywords.forEach { (keyword, type) ->
			if (qLower.contains(keyword)) relevantTypes.add(type)
		}

		val filteredEntries = if (relevantTypes.isEmpty()) {
			allEntries.sortedByDescending { it.timestamp }.take(100)
		} else {
			allEntries.filter { it.type in relevantTypes }.sortedByDescending { it.timestamp }.take(200)
		}

		val dataBuilder = StringBuilder()
		if (filteredEntries.isEmpty()) {
			dataBuilder.append("No relevant tracked data found.")
		} else {
			dataBuilder.append("RELEVANT TRACKED DATA (${filteredEntries.size} entries):\n\n")
			filteredEntries.forEach { entry ->
				dataBuilder.append("[${entry.type}] ${entry.getFormattedDate("dd.MM.yyyy")}: ${entry.rawInput}")
				if (entry.details.length() > 0) {
					dataBuilder.append(" {")
					val keys = entry.details.keys()
					var first = true
					while (keys.hasNext()) {
						val key = keys.next()
						if (!first) dataBuilder.append(", ")
						dataBuilder.append("$key:${entry.details.opt(key)}")
						first = false
					}
					dataBuilder.append("}")
				}
				dataBuilder.append("\n")
			}
		}

		val systemPrompt = "You are a helpful personal data assistant. The user will ask you a question about their tracked data. " +
		"Use ONLY the provided data to answer. Be concise but thorough. If the data doesn't contain enough information to answer well, say so honestly. " +
		"Do not make up data. Reference specific entries when relevant." + getHabitsContext(context)

		val payload = JSONObject().apply {
			put("model", MODEL)
			put("messages", JSONArray().apply {
				put(JSONObject().put("role", "system").put("content", systemPrompt))
				put(JSONObject().put("role", "user").put("content", "Here is my tracked data:\n\n$dataBuilder\n\nMy question: $question"))
			})
			put("temperature", 0.5)
			put("max_tokens", 1500)
		}

		executeRequest(apiKey, payload)
	}
}

private fun executeRequest(apiKey: String, payload: JSONObject): String {
	val body = payload.toString().toRequestBody("application/json".toMediaType())
	val request = Request.Builder()
	.url("$BASE_URL/chat/completions")
	.addHeader("Authorization", "Bearer $apiKey")
	.addHeader("Content-Type", "application/json")
	.post(body)
	.build()

	client.newCall(request).execute().use { response ->
		val bodyStr = response.body?.string() ?: ""

		if (!response.isSuccessful) {
			when (response.code) {
				401, 403 -> throw AIException("Authentication failed: $bodyStr", AIException.Reason.AUTH_ERROR)
				429 -> throw AIException("Rate limit: $bodyStr", AIException.Reason.CREDIT_LIMIT)
				400 -> throw AIException("Bad request: $bodyStr", AIException.Reason.PARSING_ERROR)
				500, 502, 503 -> throw AIException("Server error: $bodyStr", AIException.Reason.NETWORK_ERROR)
				else -> throw AIException("HTTP ${response.code}: $bodyStr", AIException.Reason.UNKNOWN)
			}
		}

		if (bodyStr.isEmpty()) throw AIException("Empty response", AIException.Reason.PARSING_ERROR)

		val choices = JSONObject(bodyStr).optJSONArray("choices")
		?: throw AIException("No choices in response", AIException.Reason.PARSING_ERROR)
		if (choices.length() == 0) throw AIException("Empty choices", AIException.Reason.PARSING_ERROR)

		return choices.getJSONObject(0)
		.getJSONObject("message")
		.optString("content", "")
		.trim()
		.also { if (it.isEmpty()) throw AIException("Empty content", AIException.Reason.PARSING_ERROR) }
	}
}

private fun prefs(context: Context): SharedPreferences =
context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

class AIException(message: String, val reason: Reason) : Exception(message) {
	enum class Reason { TIMEOUT, CREDIT_LIMIT, NETWORK_ERROR, PARSING_ERROR, AUTH_ERROR, UNKNOWN }
	fun isRetryable(): Boolean = reason == Reason.NETWORK_ERROR || reason == Reason.TIMEOUT
}
}
