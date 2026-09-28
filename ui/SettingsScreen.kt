package com.mtracker.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mlib.future.components.*
import com.mtracker.AI
import com.mtracker.ThemeManager
import androidx.navigation.NavController

@Composable
fun SettingsScreen(
	navController: NavController,
	viewModel: MainViewModel,
	currentThemeName: String,
	onThemeSelected: (String) -> Unit,
	onDismiss: () -> Unit
) {
	val context = LocalContext.current
	val scrollState = rememberScrollState()
	var toastMessage by remember { mutableStateOf<String?>(null) }

	val importLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.GetContent()
	) { uri ->
		uri?.let { viewModel.importJson(it) { msg -> toastMessage = msg } }
	}

	val exportLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("application/json")
	) { uri ->
		uri?.let { viewModel.exportJson(it) { msg -> toastMessage = msg } }
	}

	var summariesOpen by remember { mutableStateOf(false) }
	if (summariesOpen) viewModel.loadDailySummaries()

	var themeDropdownExpanded by remember { mutableStateOf(false) }

	Box(modifier = Modifier.fillMaxSize()) {
		FutureDialog(
			onDismissRequest = onDismiss,
			title = "SETTINGS",
			modifier = Modifier.wrapContentHeight().padding(horizontal = 16.dp)
		) {
			Column(
				modifier = Modifier
				.verticalScroll(scrollState)
				.padding(vertical = 8.dp)
			) {
				FutureText("THEME", variant = TextVariant.Heading)
				Spacer(modifier = Modifier.height(8.dp))
				FutureDropdown(
					expanded = themeDropdownExpanded,
					onExpandedChange = { themeDropdownExpanded = it },
					selectedText = currentThemeName,
					options = ThemeManager.themeNames,
					onOptionSelected = { selected ->
						onThemeSelected(selected)
						themeDropdownExpanded = false
					}
				)
				Spacer(modifier = Modifier.height(16.dp))
				FutureSeparator(modifier = Modifier.fillMaxWidth())
				Spacer(modifier = Modifier.height(16.dp))

				var promptsOpen by remember { mutableStateOf(false) }
				var extractionPrompt by remember { mutableStateOf(AI.getExtractionPrompt(context)) }
				var summaryPrompt by remember { mutableStateOf(AI.getSummaryPrompt(context)) }

				FutureAccordion(
					expanded = promptsOpen,
					onToggle = { promptsOpen = !promptsOpen },
					title = "AI PROMPTS"
				) {
					FutureTextArea(
						value = extractionPrompt,
						onValueChange = { extractionPrompt = it },
						label = "EXTRACTION PROMPT",
						minLines = 4
					)
					Spacer(modifier = Modifier.height(8.dp))
					FutureTextArea(
						value = summaryPrompt,
						onValueChange = { summaryPrompt = it },
						label = "SUMMARY PROMPT",
						minLines = 3
					)
					Spacer(modifier = Modifier.height(8.dp))
					FutureButton(
						onClick = {
							viewModel.savePrompts(extractionPrompt, summaryPrompt)
							toastMessage = "Prompts saved"
						},
						text = "SAVE PROMPTS",
						type = ButtonType.Primary
					)
				}

				Spacer(modifier = Modifier.height(12.dp))

				var prefsOpen by remember { mutableStateOf(false) }
				var locationEnabled by remember { mutableStateOf(AI.isLocationEnabled(context)) }
				val formats = listOf("dd.MM.yyyy", "MM/dd/yyyy", "yyyy-MM-dd")
				var selectedFormat by remember {
					mutableStateOf(formats.indexOf(AI.getDateFormat(context)).coerceAtLeast(0))
				}

				FutureAccordion(
					expanded = prefsOpen,
					onToggle = { prefsOpen = !prefsOpen },
					title = "PREFERENCES"
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						FutureText("Location tracking", variant = TextVariant.Primary)
						FutureToggle(
							checked = locationEnabled,
							onCheckedChange = {
								locationEnabled = it
								viewModel.setLocationEnabled(it)
							}
						)
					}
					Spacer(modifier = Modifier.height(12.dp))
					FutureText("Date format:")
					Spacer(modifier = Modifier.height(4.dp))
					FutureTabs(
						selected = selectedFormat,
						onTabSelected = {
							selectedFormat = it
							viewModel.setDateFormat(formats[it])
						},
						tabs = formats
					)
				}

				Spacer(modifier = Modifier.height(12.dp))

				var dataOpen by remember { mutableStateOf(false) }
				FutureAccordion(
					expanded = dataOpen,
					onToggle = { dataOpen = !dataOpen },
					title = "DATA MANAGEMENT"
				) {
					FutureButtonRow(modifier = Modifier.fillMaxWidth()) {
						FutureButton(
							modifier = Modifier.weight(1f),
							onClick = { importLauncher.launch("application/json") },
							text = "IMPORT",
							type = ButtonType.Primary,
						)
						FutureButton(
							modifier = Modifier.weight(1f),
							onClick = { exportLauncher.launch("tracker_export_${System.currentTimeMillis()}.json") },
							text = "EXPORT",
							type = ButtonType.Primary
						)
					}
				}

				Spacer(modifier = Modifier.height(12.dp))

				FutureAccordion(
					expanded = summariesOpen,
					onToggle = { summariesOpen = !summariesOpen },
					title = "PAST SUMMARIES"
				) {
					if (viewModel.dailySummaries.isEmpty()) {
						FutureText("No summaries yet", variant = TextVariant.Muted)
					} else {
						viewModel.dailySummaries.forEach { summary ->
							FutureCard(modifier = Modifier.fillMaxWidth()) {
								Column(modifier = Modifier.padding(8.dp)) {
									FutureText(
										text = summary.date,
										variant = TextVariant.Glow
									)
									Spacer(modifier = Modifier.height(4.dp))
									FutureText(
										text = summary.summaryText,
										variant = TextVariant.Primary,
										maxLines = 3,
										overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
									)
								}
							}
							Spacer(modifier = Modifier.height(8.dp))
						}
					}
				}

				Spacer(modifier = Modifier.height(12.dp))

				FutureButton(
					onClick = {
						onDismiss()
						navController.navigate("nutrition")
					},
					text = "NUTRITION DATA",
					type = ButtonType.Primary,
					modifier = Modifier.fillMaxWidth()
				)
				Spacer(modifier = Modifier.height(8.dp))
				FutureButton(
					onClick = {
						onDismiss()
						navController.navigate("workouts")
					},
					text = "WORKOUT PLANS",
					type = ButtonType.Primary,
					modifier = Modifier.fillMaxWidth()
				)
				Spacer(modifier = Modifier.height(8.dp))
				FutureButton(
					onClick = {
						onDismiss()
						navController.navigate("habits")
					},
					text = "DAILY HABITS",
					type = ButtonType.Primary,
					modifier = Modifier.fillMaxWidth()
				)
				Spacer(modifier = Modifier.height(20.dp))
				FutureButton(
					onClick = onDismiss,
					text = "CLOSE",
					type = ButtonType.Primary,
					modifier = Modifier.fillMaxWidth()
				)
			}
		}

		toastMessage?.let { msg ->
			FutureToast(message = msg, type = ToastType.SUCCESS)
			LaunchedEffect(msg) {
				kotlinx.coroutines.delay(2000)
				toastMessage = null
			}
		}
	}
}
