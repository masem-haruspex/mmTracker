package com.mtracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mlib.future.components.*
import com.mtracker.Entry
import com.mtracker.Type
import org.json.JSONObject
import androidx.compose.runtime.rememberUpdatedState

@Composable
fun EntryScreen(navController: NavController, viewModel: MainViewModel) {
	val state by viewModel.entryScreenState.collectAsState()

	Column(modifier = Modifier.fillMaxSize()) {

		Box(
			modifier = Modifier
			.fillMaxWidth()
			.weight(1f)
			.padding(16.dp),
			contentAlignment = Alignment.Center
		) {
			when (state) {
				EntryScreenState.RECORDING -> RecordingView(viewModel)
				EntryScreenState.TRANSCRIBING -> TranscribingView()
				EntryScreenState.REVIEWING -> ReviewView(viewModel)
				EntryScreenState.AI_PROCESSING -> AiProcessingView()
				EntryScreenState.AI_REVIEW -> AiReviewView(viewModel, navController)
				EntryScreenState.IDLE, EntryScreenState.DONE -> {
					LaunchedEffect(Unit) {
						navController.popBackStack()
					}
				}
			}
		}

		FutureHeader(
			title = when (state) {
				EntryScreenState.RECORDING -> "RECORDING"
				EntryScreenState.TRANSCRIBING -> "TRANSCRIBING"
				EntryScreenState.REVIEWING -> if (viewModel.isFreeFormAiMode) "REVIEW QUESTION" else "REVIEW"
				EntryScreenState.AI_PROCESSING -> "THINKING"
				EntryScreenState.AI_REVIEW -> if (viewModel.isFreeFormAiMode) "RESPONSE" else "CONFIRM"
				else -> "ENTRY"
			},
			isBottom = true,
			actions = {
				FutureButton(
					onClick = {
						if (state == EntryScreenState.RECORDING) viewModel.stopRecording()

						if (state == EntryScreenState.AI_REVIEW && viewModel.isFreeFormAiMode) {
							viewModel.currentRecordingId = -1L
							viewModel.currentTranscription = ""
							viewModel.currentAiResult = emptyList()
							viewModel.isFreeFormAiMode = false
							viewModel.freeFormAiResponse = ""
							viewModel.refreshQueue()
						}

						navController.popBackStack()
					},
					text = "BACK",
					type = ButtonType.Primary
				)
			}
		)

	}

	if (viewModel.editingEntryIndex != -1) {
		AiEditDialog(viewModel, viewModel.editingEntryIndex)
	}
}

@Composable
private fun RecordingView(viewModel: MainViewModel) {
	Column(
		modifier = Modifier.fillMaxSize(),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.SpaceBetween
	) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			modifier = Modifier.padding(top = 32.dp)
		) {
			FutureText("● Recording", variant = TextVariant.Heading)
			Spacer(modifier = Modifier.height(8.dp))
			FutureText("Speak now. Tap STOP when done.", variant = TextVariant.Muted)
		}

		FutureButton(
			onClick = { viewModel.stopRecording() },
			text = "STOP RECORDING",
			type = ButtonType.Destructive,
			modifier = Modifier.fillMaxWidth()
		)
	}
}

@Composable
private fun TranscribingView() {
	Column(horizontalAlignment = Alignment.CenterHorizontally) {
		FutureLoading(text = "TRANSCRIBING")
		Spacer(modifier = Modifier.height(16.dp))
		FutureText("Converting speech to text…", variant = TextVariant.Muted)
	}
}

@Composable
private fun ReviewView(viewModel: MainViewModel) {
	var text by remember(viewModel.currentTranscription) {
		mutableStateOf(viewModel.currentTranscription)
	}

	val currentText by rememberUpdatedState(text)
	DisposableEffect(Unit) {
		onDispose {
			viewModel.updateTranscription(currentText)
		}
	}

	Column(modifier = Modifier.fillMaxSize()) {
		if (viewModel.isFreeFormAiMode) {
			FutureText("Review your question before sending:")
		} else {
			FutureText("Edit before sending to AI:")
		}
		Spacer(modifier = Modifier.height(8.dp))
		FutureTextArea(
			value = text,
			onValueChange = { text = it },
			modifier = Modifier.weight(1f),
			label = if (viewModel.isFreeFormAiMode) "QUESTION" else "TRANSCRIPTION"
		)
		Spacer(modifier = Modifier.height(16.dp))
		FutureButtonRow(modifier = Modifier.fillMaxWidth()) {
			FutureButton(
				onClick = { viewModel.discardEntry() },
				text = "DISCARD",
				type = ButtonType.Destructive,
				modifier = Modifier.weight(1f)
			)
			Spacer(modifier = Modifier.width(12.dp))
			FutureButton(
				onClick = {
					if (viewModel.isFreeFormAiMode) {
						viewModel.askAiFreeForm(text)
					} else {
						viewModel.sendToAi(text)
					}
				},
				text = if (viewModel.isFreeFormAiMode) "ASK AI" else "SEND TO AI",
				type = ButtonType.Primary,
				modifier = Modifier.weight(1f)
			)
		}
	}
}

@Composable
private fun AiProcessingView() {
	Column(horizontalAlignment = Alignment.CenterHorizontally) {
		FutureLoading(text = "THINKING")
		Spacer(modifier = Modifier.height(16.dp))
		FutureText("AI is processing…", variant = TextVariant.Muted)
	}
}

@Composable
private fun AiReviewView(viewModel: MainViewModel, navController: NavController) {
	if (viewModel.isFreeFormAiMode) {
		val scrollState = rememberScrollState()
		Column(modifier = Modifier.fillMaxSize()) {
			FutureText("AI Response:", variant = TextVariant.Heading)
			Spacer(modifier = Modifier.height(12.dp))
			Column(
				modifier = Modifier
				.weight(1f)
				.verticalScroll(scrollState)
			) {
				FutureText(
					text = viewModel.freeFormAiResponse,
					variant = TextVariant.Primary
				)
			}
			Spacer(modifier = Modifier.height(16.dp))
		}
	} else {
		Column(modifier = Modifier.fillMaxSize()) {
			FutureText("Confirm AI extraction:", variant = TextVariant.Heading)
			Spacer(modifier = Modifier.height(12.dp))

			Column(modifier = Modifier.weight(1f)) {
				viewModel.currentAiResult.forEachIndexed { index, entry ->
					FutureCard(
						onClick = { viewModel.startEditingEntry(index) },   
						modifier = Modifier.fillMaxWidth()
					) {
						Column(modifier = Modifier.padding(12.dp)) {
							FutureBadge(text = entry.type.name)
							Spacer(modifier = Modifier.height(4.dp))
							FutureText(entry.rawInput, variant = TextVariant.Primary)

							if (entry.location != "Unspecified") {
								FutureText(
									text = "📍 ${entry.location}",
									variant = TextVariant.Muted,
									maxLines = 1
								)
							}
							if (entry.details.length() > 0) {
								FutureText(entry.details.toString(), variant = TextVariant.Muted, maxLines = 2)
							}
						}
					}
					Spacer(modifier = Modifier.height(8.dp))
				}
			}

			Spacer(modifier = Modifier.height(16.dp))
			FutureButtonRow(modifier = Modifier.fillMaxWidth()) {
				FutureButton(
					onClick = { viewModel.discardEntry() },
					text = "DISCARD",
					type = ButtonType.Destructive,
					modifier = Modifier.weight(1f)
				)
				Spacer(modifier = Modifier.width(12.dp))
				FutureButton(
					onClick = { viewModel.submitEntry() },
					text = "SUBMIT",
					type = ButtonType.Primary,
					modifier = Modifier.weight(1f)
				)
			}
		}
	}
}

@Composable
private fun AiEditDialog(viewModel: MainViewModel, entryIndex: Int) {
	val entry = viewModel.currentAiResult.getOrNull(entryIndex) ?: return

	var type by remember { mutableStateOf(entry.type) }
	var rawInput by remember { mutableStateOf(entry.rawInput) }
	var location by remember { mutableStateOf(entry.location) }
	var item by remember { mutableStateOf(entry.details.optString("item", "")) }
	var quantity by remember { mutableStateOf(entry.details.optInt("quantity", 1).toString()) }

	var typeDropdownExpanded by remember { mutableStateOf(false) }
	val typeOptions = Type.values().map { it.name }

	FutureDialog(
		onDismissRequest = { viewModel.stopEditingEntry() },
		title = "EDIT ENTRY"
	) {
		Column(
			modifier = Modifier
			.fillMaxWidth()
			.verticalScroll(rememberScrollState())
		) {
			FutureText("TYPE")
			Spacer(modifier = Modifier.height(4.dp))
			FutureDropdown(
				expanded = typeDropdownExpanded,
				onExpandedChange = { typeDropdownExpanded = it },
				selectedText = type.name,
				options = typeOptions,
				onOptionSelected = { selected ->
					type = try { Type.valueOf(selected) } catch (_: Exception) { entry.type }
					typeDropdownExpanded = false
				}
			)

			Spacer(modifier = Modifier.height(12.dp))

			FutureTextField(
				value = location,
				onValueChange = { location = it },
				label = "LOCATION",
				modifier = Modifier.fillMaxWidth()
			)

			Spacer(modifier = Modifier.height(12.dp))

			FutureTextArea(
				value = rawInput,
				onValueChange = { rawInput = it },
				label = "TEXT",
				modifier = Modifier.fillMaxWidth()
			)

			if (type == Type.FOOD) {
				Spacer(modifier = Modifier.height(12.dp))
				FutureTextField(
					value = item,
					onValueChange = { item = it },
					label = "ITEM",
					modifier = Modifier.fillMaxWidth()
				)
				Spacer(modifier = Modifier.height(12.dp))
				FutureNumberInput(
					value = quantity,
					onValueChange = { quantity = it },
					label = "QUANTITY",
					modifier = Modifier.fillMaxWidth()
				)
			}

			Spacer(modifier = Modifier.height(16.dp))
			FutureButtonRow(modifier = Modifier.fillMaxWidth()) {
				FutureButton(
					onClick = { viewModel.stopEditingEntry() },
					text = "CANCEL",
					type = ButtonType.Primary,
					modifier = Modifier.weight(1f)
				)
				FutureButton(
					onClick = {
						val newDetails = JSONObject(entry.details.toString())
						if (type == Type.FOOD) {
							newDetails.put("item", item.lowercase().trim())
							newDetails.put("quantity", quantity.toIntOrNull() ?: 1)
						} else {
							newDetails.remove("item")
							newDetails.remove("quantity")
						}

						viewModel.updateAiEntry(
							entryIndex,
							entry.copy(
								type = type,
								rawInput = rawInput.trim(),
								location = location.trim(),
								details = newDetails
							)
						)
						viewModel.stopEditingEntry()
					},
					text = "SAVE",
					type = ButtonType.Primary,
					modifier = Modifier.weight(1f)
				)
			}
		}
	}
}
