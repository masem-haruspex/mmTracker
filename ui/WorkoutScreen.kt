package com.mtracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mlib.future.components.*
import com.mtracker.AI
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun WorkoutScreen(navController: NavController, viewModel: MainViewModel) {
	val context = LocalContext.current
	val scrollState = rememberScrollState()

	var name by remember { mutableStateOf("") }
	var sets by remember { mutableStateOf("3") }
	var exercises by remember { mutableStateOf(listOf(ExerciseInput())) }
	var specs by remember { mutableStateOf(AI.getWorkoutSpecsAll(context)) }
	var toastMessage by remember { mutableStateOf<String?>(null) }

	fun refreshSpecs() {
		specs = AI.getWorkoutSpecsAll(context)
	}

	fun save() {
		if (name.isBlank()) return
		val exerciseArray = JSONArray()
		exercises.filter { it.name.isNotBlank() }.forEach { ex ->
			exerciseArray.put(JSONObject().apply {
				put("name", ex.name.trim())
				put("work", ex.work.toIntOrNull() ?: 40)
				put("rest", ex.rest.toIntOrNull() ?: 20)
			})
		}
		if (exerciseArray.length() == 0) return

		val spec = JSONObject().apply {
			put("sets", sets.toIntOrNull() ?: 3)
			put("exercises", exerciseArray)
		}
		AI.saveWorkoutSpec(context, name.lowercase(), spec)
		name = ""; sets = "3"; exercises = listOf(ExerciseInput())
		refreshSpecs()
		toastMessage = "Workout plan saved"
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
				FutureText("NEW WORKOUT", variant = TextVariant.Heading)
				Spacer(modifier = Modifier.height(4.dp))

				FutureTextField(value = name, onValueChange = { name = it }, label = "WORKOUT NAME", modifier = Modifier.fillMaxWidth())
				FutureNumberInput(value = sets, onValueChange = { sets = it }, label = "SETS", modifier = Modifier.fillMaxWidth())

				Spacer(modifier = Modifier.height(8.dp))
				FutureText("EXERCISES")

				exercises.forEachIndexed { index, exercise ->
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						FutureTextField(
							value = exercise.name,
							onValueChange = { newName ->
								exercises = exercises.toMutableList().apply {
									set(index, exercise.copy(name = newName))
								}
							},
							label = "EXERCISE",
							modifier = Modifier.weight(2f)
						)
						FutureNumberInput(
							value = exercise.work,
							onValueChange = { newWork ->
								exercises = exercises.toMutableList().apply {
									set(index, exercise.copy(work = newWork))
								}
							},
							label = "WORK",
							modifier = Modifier.weight(1f)
						)
						FutureNumberInput(
							value = exercise.rest,
							onValueChange = { newRest ->
								exercises = exercises.toMutableList().apply {
									set(index, exercise.copy(rest = newRest))
								}
							},
							label = "REST",
							modifier = Modifier.weight(1f)
						)
					}
					Spacer(modifier = Modifier.height(4.dp))
				}

				FutureButton(
					onClick = { exercises = exercises + ExerciseInput() },
					text = "ADD EXERCISE",
					type = ButtonType.Primary,
					modifier = Modifier.fillMaxWidth()
				)

				Spacer(modifier = Modifier.height(8.dp))
				FutureButton(
					onClick = ::save,
					text = "SAVE WORKOUT",
					type = ButtonType.Primary,
					modifier = Modifier.fillMaxWidth()
				)

				Spacer(modifier = Modifier.height(20.dp))
				FutureText("ALL WORKOUTS", variant = TextVariant.Heading)
				Spacer(modifier = Modifier.height(8.dp))

				val keys = mutableListOf<String>()
				val it = specs.keys()
				while (it.hasNext()) keys.add(it.next())
				keys.sort()

				if (keys.isEmpty()) {
					FutureText("No workouts yet", variant = TextVariant.Muted)
				} else {
					keys.forEachIndexed { index, key  ->
						val spec = specs.optJSONObject(key) ?: JSONObject()
						val exerciseList = spec.optJSONArray("exercises") ?: JSONArray()
						val setsCount = spec.optInt("sets", 3)

						FutureCard(modifier = Modifier.fillMaxWidth()) {
							Column(modifier = Modifier.padding(8.dp)) {
								FutureText(text = key.uppercase(), variant = TextVariant.Glow)
								FutureText("Sets: $setsCount")
								Spacer(modifier = Modifier.height(4.dp))
								for (i in 0 until exerciseList.length()) {
									val ex = exerciseList.optJSONObject(i) ?: JSONObject()
									val exName = ex.optString("name", "Unknown")
									val exWork = ex.optInt("work", 40)
									val exRest = ex.optInt("rest", 20)
									FutureText(
										text = "• $exName (${exWork}s : ${exRest}s)",
										variant = TextVariant.Muted,
										maxLines = 2
									)
								}
							}
						}
						Spacer(modifier = Modifier.height(4.dp))
					}
				}
			}
			FutureHeader(
				title = "WORKOUT PLANS",
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

data class ExerciseInput(val name: String = "", val work: String = "40", val rest: String = "20")
