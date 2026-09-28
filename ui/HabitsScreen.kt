package com.mtracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mlib.future.components.*
import com.mtracker.AI

@Composable
fun HabitsScreen(navController: NavController, viewModel: MainViewModel) {
	val context = LocalContext.current
	val scrollState = rememberScrollState()

	var habitName by remember { mutableStateOf("") }
	var habits by remember { mutableStateOf(AI.getHabitsList(context)) }
	var toastMessage by remember { mutableStateOf<String?>(null) }

	fun refreshHabits() {
		habits = AI.getHabitsList(context)
	}

	fun save() {
		if (habitName.isBlank()) return
		AI.saveHabit(context, habitName)
		habitName = ""
		refreshHabits()
		toastMessage = "Habit added"
	}

	fun delete(habit: String) {
		AI.removeHabit(context, habit)
		refreshHabits()
		toastMessage = "Habit removed"
	}

	Box(modifier = Modifier.fillMaxSize()) {

		Column(modifier = Modifier.fillMaxSize()) {

			Column(
				modifier = Modifier
				.weight(1f)
				.padding(start = 16.dp, end = 16.dp, top = 16.dp)
				.verticalScroll(scrollState),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				FutureText("NEW HABIT", variant = TextVariant.Heading)
				Spacer(modifier = Modifier.height(4.dp))

				FutureTextField(
					value = habitName,
					onValueChange = { habitName = it },
					label = "HABIT NAME",
					modifier = Modifier.fillMaxWidth()
				)
				Spacer(modifier = Modifier.height(8.dp))
				FutureButton(
					onClick = ::save,
					text = "ADD HABIT",
					type = ButtonType.Primary,
					modifier = Modifier.fillMaxWidth()
				)

				Spacer(modifier = Modifier.height(20.dp))
				FutureText("YOUR HABITS", variant = TextVariant.Heading)
				Spacer(modifier = Modifier.height(8.dp))

				if (habits.isEmpty()) {
					FutureText("No habits yet", variant = TextVariant.Muted)
				} else {
					habits.sorted().forEach { habit ->
						FutureCard(
							modifier = Modifier.fillMaxWidth(),
							onClick = { delete(habit) }
						) {
							Row(
								modifier = Modifier
								.fillMaxWidth()
								.padding(12.dp),
								horizontalArrangement = Arrangement.SpaceBetween,
								verticalAlignment = Alignment.CenterVertically
							) {
								FutureText(
									text = habit.replaceFirstChar { it.uppercase() },
									variant = TextVariant.Primary
								)
								FutureText(
									text = "TAP TO REMOVE",
									variant = TextVariant.Muted
								)
							}
						}
						Spacer(modifier = Modifier.height(4.dp))
					}
				}
			}
			FutureHeader(
				title = "DAILY HABITS",
				isBottom = true,
				actions = {
					FutureButton(
						onClick = { navController.popBackStack() },
						icon = { FutureIconArrowLeft() }
					)
				}
			)
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
