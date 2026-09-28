package com.mtracker.ui

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.mlib.future.FutureTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.mlib.future.components.*
import androidx.navigation.NavController
import com.mtracker.AI
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import com.mtracker.Type

@Composable
fun DataScreen(navController: NavController, viewModel: MainViewModel) {
	val entries by viewModel.entries.collectAsState()
	val selectedIds by viewModel.selectedIds.collectAsState()
	val selectedCategory by viewModel.selectedCategory.collectAsState()
	val searchQuery by viewModel.searchQuery.collectAsState()
	val isSelectionMode = selectedIds.isNotEmpty()
	val clipboard = LocalClipboardManager.current

	LaunchedEffect(searchQuery, selectedCategory) {
		viewModel.refreshEntries()
	}

	Column(modifier = Modifier.fillMaxSize()) {

		Column(
			modifier = Modifier
			.weight(1f)
			.padding(start = 16.dp, end = 16.dp)
		) {

			Spacer(modifier = Modifier.height(8.dp))

			if (entries.isEmpty()) {
				FutureText(text = "No entries found", variant = TextVariant.Muted)
			} else {
				LazyColumn(
					verticalArrangement = Arrangement.spacedBy(2.dp),
					modifier = Modifier.fillMaxSize()
				) {
					itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
						val isSelected = selectedIds.contains(entry.id)
						Column(
							modifier = Modifier
							.fillMaxWidth()
							.pointerInput(Unit) {
								detectTapGestures(
									onTap = {
										if (isSelectionMode) viewModel.toggleSelection(entry.id)
										else clipboard.setText(AnnotatedString(entry.rawInput))
									},
									onLongPress = { viewModel.toggleSelection(entry.id) }
								)
							}
							.padding(vertical = 8.dp, horizontal = 4.dp)
						) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween,
								verticalAlignment = Alignment.CenterVertically
							) {
								Row(
									horizontalArrangement = Arrangement.spacedBy(8.dp),
									verticalAlignment = Alignment.CenterVertically
								) {
									if (isSelectionMode) {
										FutureCheckbox(
											checked = isSelected,
											onCheckedChange = { viewModel.toggleSelection(entry.id) }
										)
									}
									FutureBadge(
										text = entry.type.name,
										color = when (entry.type) {
											Type.FOOD -> Color(0xFF00F3FF)
											Type.WORKOUT -> Color(0xFFB24CFF)
											Type.HEALTH -> Color(0xFF00FF9D)
											Type.HABIT -> Color(0xFFFF9D00)
											Type.MONEY -> Color(0xFFFFEB3B)
											else -> Color(0xFF448AFF)
										}
									)
									FutureText(
										text = entry.getFormattedDate(AI.getDateFormat(LocalContext.current)),
										variant = TextVariant.Muted
									)
								}
								if (entry.location != "Unspecified") {
									FutureText(
										text = entry.location,
										variant = TextVariant.Muted,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
								}
							}
							Spacer(modifier = Modifier.height(4.dp))
							FutureText(
								text = entry.rawInput,
								variant = TextVariant.Primary,
								maxLines = 2,
								overflow = TextOverflow.Ellipsis
							)
						}
						if (index < entries.lastIndex){
							Spacer(modifier = Modifier.height(4.dp))
							FutureSeparator(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
						}
					}
				}
			}
		}

		FutureSeparator(modifier = Modifier.fillMaxWidth())

		Column(
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
		) {
			FutureSearch(
				value = searchQuery,
				onValueChange = {
					viewModel.setSearchQuery(it)
					viewModel.refreshEntries()
				},
				placeholder = "SEARCH ENTRIES...",
				modifier = Modifier.fillMaxWidth()
			)
			Spacer(modifier = Modifier.height(8.dp))
			var dropdownExpanded by remember { mutableStateOf(false) }
			val categories = listOf("ALL", "FOOD", "WORKOUT", "HEALTH", "HABIT", "DIARY", "IDEAS", "MONEY")
			FutureDropdown(
				expanded = dropdownExpanded,
				onExpandedChange = { dropdownExpanded = it },
				selectedText = selectedCategory?.name ?: "ALL",
				options = categories,
				onOptionSelected = {
					viewModel.setSelectedCategory(if (it == "ALL") null else Type.valueOf(it))
					dropdownExpanded = false
					viewModel.refreshEntries()
				}
			)
		}

		FutureHeader(
			title = "DATA",
			isBottom = true,
			actions = {
				if (isSelectionMode) {
					FutureButtonRow {
						FutureButton(onClick = { viewModel.selectAll() }, text = "ALL", type = ButtonType.Primary)
						FutureButton(onClick = { viewModel.deselectAll() }, text = "NONE", type = ButtonType.Primary)
						FutureButton(onClick = { viewModel.deleteSelected() }, text = "DELETE", type = ButtonType.Destructive)
					}
				} else {
					FutureButton(
						onClick = { navController.popBackStack() },
						icon = { FutureIconArrowLeft() }
					)
				}
			}
		)
	}
}
