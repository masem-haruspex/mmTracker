package com.mtracker.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtracker.AI
import com.mtracker.Audio
import com.mtracker.Database
import com.mtracker.Entry
import com.mtracker.Type
import com.mtracker.Json
import com.mtracker.Location
import com.mtracker.PendingRecording
import com.mtracker.SyncManager
import com.mtracker.Vosk
import com.mtracker.DailySummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

enum class EntryScreenState {
	IDLE, RECORDING, TRANSCRIBING, REVIEWING, AI_PROCESSING, AI_REVIEW, DONE
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
	private val context: Context get() = getApplication<Application>().applicationContext
	private val db = Database(context)
	private val syncManager = SyncManager(context, db)

	var showSettings by mutableStateOf(false)
	var showSummary by mutableStateOf(false)
	var showSummarySelector by mutableStateOf(false)
	var summaryPeriod by mutableStateOf("YESTERDAY")
	var summaryText by mutableStateOf("")
	var editingEntryIndex by mutableStateOf(-1)
	private set

	fun startEditingEntry(index: Int) { editingEntryIndex = index }
	fun stopEditingEntry() { editingEntryIndex = -1 }

	var isFreeFormAiMode by mutableStateOf(false)
	var freeFormAiResponse by mutableStateOf("")

	private val _queue = MutableStateFlow<List<PendingRecording>>(emptyList())
	val queue: StateFlow<List<PendingRecording>> = _queue.asStateFlow()
	private val _isProcessingQueue = MutableStateFlow(false)
	val isProcessingQueue: StateFlow<Boolean> = _isProcessingQueue.asStateFlow()

	private val _entries = MutableStateFlow<List<Entry>>(emptyList())
	val entries: StateFlow<List<Entry>> = _entries.asStateFlow()
	private val _searchQuery = MutableStateFlow("")
	val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
	private val _selectedCategory = MutableStateFlow<Type?>(null)
	val selectedCategory: StateFlow<Type?> = _selectedCategory.asStateFlow()
	private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
	val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

	private val _entryScreenState = MutableStateFlow(EntryScreenState.IDLE)
	val entryScreenState: StateFlow<EntryScreenState> = _entryScreenState.asStateFlow()
	var currentRecordingId by mutableStateOf(-1L)
	var currentTranscription by mutableStateOf("")
	var currentAiResult by mutableStateOf<List<Entry>>(emptyList())

	init {
		loadHardcodedNutrition()
		loadHardcodedWorkouts()
		loadHardcodedHabits()
		cleanupStaleRecordings()
		refreshQueue()
		refreshEntries()
		syncManager.startQueueProcessing()
	}

	private fun loadHardcodedNutrition() {
		AI.saveFoodSpec(context, "sardine", JSONObject().apply {
			put("kcal", 208); put("fats", 14.0); put("carbs", 2.0); put("saturated", 9.0)
			put("fibers", 0.0); put("salt", 0.0); put("protein", 9.0)
			put("portionGrams", 78.0); put("measurementGrams", 100.0)
		})
		AI.saveFoodSpec(context, "decaffeinated coffee", JSONObject().apply {
			put("kcal", 16.0); put("carbs", 4.0); put("sugars", 4.0)
			put("fats", 0.0); put("protein", 0.0); put("salt", 0.0)
			put("portionGrams", 100.0); put("measurementGrams", 100.0)
		})
		AI.saveFoodSpec(context, "tonus bread", JSONObject().apply {
			put("kcal", 116.6); put("fats", 1.1); put("carbs", 23.2)
			put("fibers", 8.0); put("protein", 3.5); put("salt", 0.6)
			put("portionGrams", 45.0); put("measurementGrams", 100.0)
		})
		AI.saveFoodSpec(context, "cherry cola", JSONObject().apply {
			put("kj", 21.0); put("kcal", 5.0); put("fats", 0.0); put("saturated", 0.0)
			put("carbs", 1.0); put("sugars", 1.0); put("protein", 0.0); put("salt", 0.03)
			put("portionGrams", 330.0); put("measurementGrams", 100.0)
		})
		AI.saveFoodSpec(context, "greek yoghurt", JSONObject().apply {
			put("kj", 300.0); put("kcal", 71.0); put("fats", 2.0); put("saturated", 1.5)
			put("carbs", 3.5); put("sugars", 3.5); put("protein", 9.8); put("salt", 0.14)
			put("portionGrams", 150.0); put("measurementGrams", 100.0)
		})
		AI.saveFoodSpec(context, "sugar free cockta", JSONObject().apply {
			put("kj", 8.0); put("kcal", 2.0); put("fats", 0.0); put("saturated", 0.0)
			put("carbs", 0.0); put("sugars", 0.0); put("protein", 0.0); put("salt", 0.07)
			put("portionGrams", 100.0); put("measurementGrams", 100.0)
		})
		AI.saveFoodSpec(context, "ice coffee", JSONObject().apply {
			put("kj", 212.0); put("kcal", 50.0); put("fats", 1.1); put("saturated", 0.7)
			put("carbs", 7.6); put("sugars", 7.3); put("protein", 2.5); put("salt", 0.2)
			put("portionGrams", 500.0); put("measurementGrams", 100.0)
		})
		AI.saveFoodSpec(context, "pudding", JSONObject().apply {
			put("kj", 361.0); put("kcal", 85.0); put("fats", 1.6); put("saturated", 1.1)
			put("carbs", 15.8); put("sugars", 13.0); put("fibers", 0.1); put("protein", 1.9); put("salt", 0.2)
			put("portionGrams", 200.0); put("measurementGrams", 100.0)
		})
		AI.saveFoodSpec(context, "cheese cake", JSONObject().apply {
			put("kj", 1025.0); put("kcal", 247.0); put("fats", 15.0); put("saturated", 0.17)
			put("carbs", 23.2); put("sugars", 15.0); put("protein", 5.5); put("salt", 0.44)
			put("portionGrams", 150.0); put("measurementGrams", 100.0)
		})
		AI.saveFoodSpec(context, "pivarki", JSONObject().apply {
			put("kj", 1835.0); put("kcal", 439.0); put("fats", 17.9); put("saturated", 1.9)
			put("carbs", 64.8); put("sugars", 27.6); put("protein", 4.6); put("salt", 0.1)
			put("portionGrams", 25.0); put("measurementGrams", 100.0)
		})
		AI.saveFoodSpec(context, "gurabii", JSONObject().apply {
			put("kj", 2118.0); put("kcal", 506.0); put("fats", 26.6); put("saturated", 14.6)
			put("carbs", 59.24); put("sugars", 14.8); put("fibers", 2.0); put("protein", 7.43); put("salt", 0.0)
			put("portionGrams", 20.5); put("measurementGrams", 100.0)
		})
		AI.saveFoodSpec(context, "vegeterian fish paste", JSONObject().apply {
			put("kj", 1038.0); put("kcal", 251.0); put("fats", 23.0); put("saturated", 2.8)
			put("carbs", 8.6); put("sugars", 0.9); put("protein", 2.4); put("salt", 1.1)
			put("portionGrams", 100.0); put("measurementGrams", 100.0)
		})
		AI.saveFoodSpec(context, "peanut butter", JSONObject().apply {
			put("kj", 2692.0); put("kcal", 649.0); put("fats", 53.3); put("saturated", 13.6)
			put("carbs", 19.3); put("sugars", 6.0); put("protein", 25.0); put("salt", 0.7)
			put("portionGrams", 15.0); put("measurementGrams", 100.0)
		})
	}

	private fun loadHardcodedWorkouts() {
		AI.saveWorkoutSpec(context, "chest & triceps", JSONObject().apply {
			put("sets", 3)
			put("exercises", JSONArray().apply {
				put(JSONObject().apply { put("name", "Push-ups"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Dumbbell Floor Press (L)"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Dumbbell Floor Press (R)"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Close-Grip Push-ups"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Dumbbell Pullover"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Overhead Triceps Extension"); put("work", 40); put("rest", 20) })
			})
		})
		AI.saveWorkoutSpec(context, "back & biceps", JSONObject().apply {
			put("sets", 3)
			put("exercises", JSONArray().apply {
				put(JSONObject().apply { put("name", "Single-Arm Row (L)"); put("work", 45); put("rest", 15) })
				put(JSONObject().apply { put("name", "Single-Arm Row (R)"); put("work", 45); put("rest", 15) })
				put(JSONObject().apply { put("name", "Superman to Row (squeeze 2 sec at top)"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Dumbbell Pullover"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Bicep Curl (L)"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Bicep Curl (R)"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Waiter Curl or Hammer Curl"); put("work", 40); put("rest", 20) })
			})
		})
		AI.saveWorkoutSpec(context, "legs", JSONObject().apply {
			put("sets", 3)
			put("exercises", JSONArray().apply {
				put(JSONObject().apply { put("name", "Bulgarian Split Squat (L)"); put("work", 45); put("rest", 15) })
				put(JSONObject().apply { put("name", "Bulgarian Split Squat (R)"); put("work", 45); put("rest", 15) })
				put(JSONObject().apply { put("name", "Single-Leg Romanian Deadlift (L)"); put("work", 45); put("rest", 15) })
				put(JSONObject().apply { put("name", "Single-Leg Romanian Deadlift (R)"); put("work", 45); put("rest", 15) })
				put(JSONObject().apply { put("name", "Goblet Squat"); put("work", 45); put("rest", 15) })
				put(JSONObject().apply { put("name", "Single-Leg Glute Bridge, dumbbell on hip (L)"); put("work", 45); put("rest", 15) })
				put(JSONObject().apply { put("name", "Single-Leg Glute Bridge, dumbbell on hip (R)"); put("work", 45); put("rest", 15) })
				put(JSONObject().apply { put("name", "Single-Leg Calf Raise (L)"); put("work", 45); put("rest", 15) })
				put(JSONObject().apply { put("name", "Single-Leg Calf Raise (R)"); put("work", 45); put("rest", 15) })
			})
		})
		AI.saveWorkoutSpec(context, "shoulder & core", JSONObject().apply {
			put("sets", 3)
			put("exercises", JSONArray().apply {
				put(JSONObject().apply { put("name", "Seated Shoulder Press (L)"); put("work", 45); put("rest", 15) })
				put(JSONObject().apply { put("name", "Seated Shoulder Press (R)"); put("work", 45); put("rest", 15) })
				put(JSONObject().apply { put("name", "Pike Push-ups"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Upright Row (both hands on dumbbell)"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Lateral Raise (L) — slow 3-sec lowering"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Lateral Raise (R) — slow 3-sec lowering"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Plank Shoulder Taps"); put("work", 30); put("rest", 30) })
				put(JSONObject().apply { put("name", "Leg Raises"); put("work", 30); put("rest", 30) })
			})
		})
		AI.saveWorkoutSpec(context, "full body", JSONObject().apply {
			put("sets", 3)
			put("exercises", JSONArray().apply {
				put(JSONObject().apply { put("name", "Dumbbell Thrusters"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Burpees"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Renegade Row (single-dumbbell version: one hand on db, one on floor)"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Mountain Climbers"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Jump Rope (shadow)"); put("work", 40); put("rest", 20) })
				put(JSONObject().apply { put("name", "Plank to Push-up"); put("work", 30); put("rest", 30) })
			})
		})
	}

	private fun loadHardcodedHabits() {
		listOf(
			"brush teeth", 
			"go for a walk", 
			"have tea"
		).forEach {
			AI.saveHabit(context, it)
		}
	}


	fun refreshQueue() { _queue.value = db.getPendingQueue() }


	fun processQueue() {
		if (_isProcessingQueue.value) return
		viewModelScope.launch(Dispatchers.IO) {
			_isProcessingQueue.value = true
			try {
				syncManager.processQueueSynchronously()
			} catch (e: Exception) {
				e.printStackTrace()
			} finally {
				_isProcessingQueue.value = false
				withContext(Dispatchers.Main) {
					refreshQueue()
					refreshEntries()
				}
			}
		}
	}

	fun refreshEntries() {
		viewModelScope.launch(Dispatchers.IO) {
			val cat = _selectedCategory.value
			val all = if (cat == null) db.getAllEntries() else db.getEntriesByType(cat)
			val filtered = if (_searchQuery.value.isBlank()) all else {
				val q = _searchQuery.value.lowercase().trim()
				all.filter { e ->
					e.rawInput.lowercase().contains(q) ||
					e.type.name.lowercase().contains(q) ||
					e.location.lowercase().contains(q)
				}
			}
			_entries.value = filtered
		}
	}

	fun startRecording() {
		_entryScreenState.value = EntryScreenState.RECORDING
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val path = Audio.startRecording(context)
				val rec = PendingRecording(filePath = path)
				val id = db.insertPending(rec)
				currentRecordingId = id

				Audio.onRecordingComplete = { _ ->
					viewModelScope.launch(Dispatchers.IO) { processTranscription() }
				}
				Audio.onRecordingFailed = { _ ->
					viewModelScope.launch(Dispatchers.Main) {
						_entryScreenState.value = EntryScreenState.IDLE
						currentRecordingId = -1
					}
				}
			} catch (e: Exception) {
				e.printStackTrace()
				_entryScreenState.value = EntryScreenState.IDLE
			}
		}
	}

	fun stopRecording() {
		Audio.stopRecording()
		_entryScreenState.value = EntryScreenState.TRANSCRIBING
	}

	private fun processTranscription() {
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val rec = db.getPendingById(currentRecordingId) ?: return@launch
				val file = File(rec.filePath)
				val text = if (Audio.isCompressed(file)) Audio.decompress(file).let { Vosk.transcribe(it) } else Vosk.transcribe(file)

				db.updatePendingTranscription(rec.id, text)
				currentTranscription = text

				withContext(Dispatchers.Main) { _entryScreenState.value = EntryScreenState.REVIEWING }
				refreshQueue()
			} catch (e: Exception) {
				e.printStackTrace()
				db.updatePendingStatus(currentRecordingId, PendingRecording.Status.FAILED, 0, e.message)
				withContext(Dispatchers.Main) { _entryScreenState.value = EntryScreenState.IDLE }
				refreshQueue()
			}
		}
	}

	fun resumeEntry(entryId: Long) {
		val rec = db.getPendingById(entryId) ?: return
		currentRecordingId = entryId
		currentTranscription = rec.transcribedText ?: ""

		if (!rec.aiResultJson.isNullOrEmpty()) {
			currentAiResult = deserializeAiResult(rec.aiResultJson)
			_entryScreenState.value = EntryScreenState.AI_REVIEW
		} else {
			currentAiResult = emptyList()
			_entryScreenState.value = if (rec.isTranscribed) EntryScreenState.REVIEWING else EntryScreenState.IDLE
		}
	}

	fun updateTranscription(text: String) {
		currentTranscription = text
		val id = currentRecordingId
		if (id == -1L) return
		viewModelScope.launch(Dispatchers.IO) {
			db.updatePendingTranscription(id, text)
			refreshQueue()
		}
	}

	fun sendToAi(text: String) {
		updateTranscription(text)

		_entryScreenState.value = EntryScreenState.AI_PROCESSING
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val result = AI.extractFromTranscript(context, text)
				val extractedEntries = mutableListOf<Entry>()
				for (i in 0 until result.length()) {
					val obj = result.getJSONObject(i)
					var typeStr = obj.optString("type", "DIARY").uppercase()
					if (typeStr == "INFO") typeStr = "IDEAS"
					val type = try { Type.valueOf(typeStr) } catch (_: IllegalArgumentException) { Type.DIARY }
					val aiLocation = obj.optString("location", "").trim()
					val location = when {
						aiLocation.isNotEmpty() && !aiLocation.equals("Unspecified", ignoreCase = true) -> aiLocation
						AI.isLocationEnabled(context) -> Location.getCurrentLocation(context)
						else -> "Unspecified"
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
					extractedEntries.add(Entry(type = type, timestamp = System.currentTimeMillis(), rawInput = obj.optString("text", text), details = details, location = location))
				}
				currentAiResult = extractedEntries
				db.updatePendingAiResult(currentRecordingId, serializeAiResult(extractedEntries))
				withContext(Dispatchers.Main) { _entryScreenState.value = EntryScreenState.AI_REVIEW }
			} catch (e: Exception) {
				e.printStackTrace()
				withContext(Dispatchers.Main) {
					currentRecordingId = -1
					currentTranscription = ""
					currentAiResult = emptyList()
					_entryScreenState.value = EntryScreenState.IDLE
				}
				refreshQueue()
			}
		}
	}

	fun deletePendingRecording(id: Long) {
		viewModelScope.launch(Dispatchers.IO) {
			val rec = db.getPendingById(id) ?: return@launch
			runCatching { File(rec.filePath).delete() }
			db.deletePending(id)
			withContext(Dispatchers.Main) { refreshQueue() }
		}
	}

	fun askAiFreeForm(text: String) {
		_entryScreenState.value = EntryScreenState.AI_PROCESSING
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val dayMs = 86400000L
				val since = System.currentTimeMillis() - 30 * dayMs
				val allEntries = mutableListOf<Entry>()
				Type.values().forEach { type ->
					db.getEntriesSince(type, since).forEach { allEntries.add(it) }
				}

				val result = AI.askFreeForm(context, text, allEntries)
				freeFormAiResponse = result
				db.getPendingById(currentRecordingId)?.let {
					File(it.filePath).delete()
				}
				db.deletePending(currentRecordingId)

				withContext(Dispatchers.Main) { _entryScreenState.value = EntryScreenState.AI_REVIEW }
			} catch (e: Exception) {
				e.printStackTrace()
				freeFormAiResponse = "Error: ${e.message}"
				withContext(Dispatchers.Main) { _entryScreenState.value = EntryScreenState.AI_REVIEW }
			}
		}
	}

	fun submitEntry() {
		viewModelScope.launch(Dispatchers.IO) {
			AI.processEntriesForMissions(context, currentAiResult)
			syncManager.completeEntry(currentRecordingId, currentAiResult)   
			withContext(Dispatchers.Main) {
				currentRecordingId = -1
				currentTranscription = ""
				currentAiResult = emptyList()
				_entryScreenState.value = EntryScreenState.IDLE
				refreshQueue()
				refreshEntries()
			}
		}
	}

	fun discardEntry() {
		viewModelScope.launch(Dispatchers.IO) {
			val rec = db.getPendingById(currentRecordingId) ?: return@launch
			File(rec.filePath).delete()
			db.deletePending(currentRecordingId)
			withContext(Dispatchers.Main) {
				currentRecordingId = -1; currentTranscription = ""; currentAiResult = emptyList()
				isFreeFormAiMode = false; freeFormAiResponse = ""
				_entryScreenState.value = EntryScreenState.IDLE
				refreshQueue()
			}
		}
	}

	fun updateAiEntry(index: Int, newEntry: Entry) {
		currentAiResult = currentAiResult.toMutableList().apply { set(index, newEntry) }
	}

	fun toggleSelection(id: Long) { _selectedIds.value = _selectedIds.value.toMutableSet().apply { if (contains(id)) remove(id) else add(id) } }
	fun selectAll() { _selectedIds.value = _entries.value.map { it.id }.toSet() }
	fun deselectAll() { _selectedIds.value = emptySet() }
	fun deleteSelected() {
		viewModelScope.launch(Dispatchers.IO) {
			db.deleteEntries(_selectedIds.value.toList())
			withContext(Dispatchers.Main) { _selectedIds.value = emptySet(); refreshEntries() }
		}
	}

	fun generateSummary(period: String) {
		summaryPeriod = period
		viewModelScope.launch {
			showSummary = true
			summaryText = "Analyzing your data…"
			try {
				val result = withContext(Dispatchers.IO) {
					val now = System.currentTimeMillis()
					val dayMs = 86400000L
					val (start, end) = when (period) {
						"YESTERDAY" -> { val s = ((now / dayMs) - 1) * dayMs; s to s + dayMs - 1 }
						"PAST_WEEK" -> { val s = ((now / dayMs) - 7) * dayMs; val e = ((now / dayMs) - 1) * dayMs + dayMs - 1; s to e }
						"PAST_MONTH" -> { val s = ((now / dayMs) - 30) * dayMs; val e = ((now / dayMs) - 1) * dayMs + dayMs - 1; s to e }
						else -> { val s = ((now / dayMs) - 1) * dayMs; s to s + dayMs - 1 }
					}
					val allEntries = mutableListOf<Entry>()
					Type.values().forEach { type ->
						db.getEntriesSince(type, start).forEach { e -> if (e.timestamp in start..end) allEntries.add(e) }
					}
					if (allEntries.isEmpty()) return@withContext "No data found for $period."
					AI.generateSummary(context, allEntries)
				}
				summaryText = result
			} catch (e: Exception) {
				summaryText = "Error: ${e.message}"
			}
		}
	}

	fun savePrompts(extraction: String, summary: String) { AI.setExtractionPrompt(context, extraction); AI.setSummaryPrompt(context, summary) }
	fun setLocationEnabled(enabled: Boolean) { AI.setLocationEnabled(context, enabled) }
	fun setDateFormat(format: String) { AI.setDateFormat(context, format) }
	fun importJson(uri: Uri, onResult: (String) -> Unit) {
		viewModelScope.launch(Dispatchers.IO) {
			try { context.contentResolver.openInputStream(uri)?.use { Json.importFromStream(context, it, db) }; withContext(Dispatchers.Main) { refreshEntries(); refreshQueue(); onResult("Imported successfully") } }
			catch (e: Exception) { withContext(Dispatchers.Main) { onResult("Import failed: ${e.message}") } }
		}
	}
	fun exportJson(uri: Uri, onResult: (String) -> Unit) {
		viewModelScope.launch(Dispatchers.IO) {
			try { context.contentResolver.openOutputStream(uri)?.use { Json.exportToStream(db, it, context) }; withContext(Dispatchers.Main) { onResult("Exported successfully") } }
			catch (e: Exception) { withContext(Dispatchers.Main) { onResult("Export failed: ${e.message}") } }
		}
	}
	override fun onCleared() { Audio.stopRecording(); super.onCleared(); syncManager.shutdown() }
	fun setSelectedCategory(type: Type?) { _selectedCategory.value = type }
	fun setSearchQuery(query: String) { _searchQuery.value = query }

	private fun serializeAiResult(entries: List<Entry>): String {
		val arr = org.json.JSONArray()
		entries.forEach { e ->
			arr.put(org.json.JSONObject().apply {
				put("type", e.type.name)
				put("timestamp", e.timestamp)
				put("raw", e.rawInput)
				put("details", e.details)
				put("location", e.location)
			})
		}
		return arr.toString()
	}

	private fun deserializeAiResult(json: String): List<Entry> {
		val list = mutableListOf<Entry>()
		val arr = org.json.JSONArray(json)
		for (i in 0 until arr.length()) {
			val obj = arr.getJSONObject(i)
			list.add(Entry(
				type = try { Type.valueOf(obj.getString("type")) } catch (_: Exception) { Type.DIARY },
				timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
				rawInput = obj.optString("raw", ""),
				details = obj.optJSONObject("details") ?: org.json.JSONObject(),
				location = obj.optString("location", "Unspecified")
			))
		}
		return list
	}

	private fun cleanupStaleRecordings() {
		viewModelScope.launch(Dispatchers.IO) {
			val dayMs = 86400000L
			val cutoff = System.currentTimeMillis() - dayMs
			db.getPendingQueue().filter { it.createdAt < cutoff }.forEach { stale ->
				try { java.io.File(stale.filePath).delete() } catch (_: Exception) {}
				db.deletePending(stale.id)
			}
		}
	}

	var pendingDailySummaryText by mutableStateOf<String?>(null)
	private set

	fun checkPendingSummary(context: Context, intentDate: String? = null) {
		viewModelScope.launch(Dispatchers.IO) {
			val summary = if (intentDate != null) {
				db.getDailySummaryForDate(intentDate)
			} else {
				db.getUnviewedDailySummary()
			}
			summary?.let {
				if (!it.viewed) {
					withContext(Dispatchers.Main) {
						pendingDailySummaryText = it.summaryText
					}
					db.markSummaryAsViewed(it.id)
				}
			}
		}
	}

	fun dismissPendingDailySummary() {
		pendingDailySummaryText = null
	}

	var dailySummaries by mutableStateOf<List<DailySummary>>(emptyList())
	private set

	fun loadDailySummaries() {
		viewModelScope.launch(Dispatchers.IO) {
			dailySummaries = db.getAllDailySummaries()
		}
	}

	fun insertOcrEntry(entry: Entry) {
		viewModelScope.launch(Dispatchers.IO) {
			db.insertEntry(entry)
			withContext(Dispatchers.Main) { refreshEntries() }
		}
	}
}
