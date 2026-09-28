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
import org.json.JSONObject

@Composable
fun NutritionScreen(navController: NavController, viewModel: MainViewModel) {
	val context = LocalContext.current
	val scrollState = rememberScrollState()

	var name by remember { mutableStateOf("") }
	var kj by remember { mutableStateOf("") }
	var kcal by remember { mutableStateOf("") }
	var fats by remember { mutableStateOf("") }
	var saturated by remember { mutableStateOf("") }
	var carbs by remember { mutableStateOf("") }
	var sugars by remember { mutableStateOf("") }
	var protein by remember { mutableStateOf("") }
	var salt by remember { mutableStateOf("") }
	var fibers by remember { mutableStateOf("") }
	var portion by remember { mutableStateOf("") }
	var measurement by remember { mutableStateOf("") }

	var specs by remember { mutableStateOf(AI.getFoodSpecsAll(context)) }
	var toastMessage by remember { mutableStateOf<String?>(null) }

	fun refreshSpecs() {
		specs = AI.getFoodSpecsAll(context)
	}

	fun save() {
		if (name.isBlank()) return
		val spec = JSONObject().apply {
			put("kj", kj.toDoubleOrNull() ?: 0.0)
			put("kcal", kcal.toDoubleOrNull() ?: 0.0)
			put("fats", fats.toDoubleOrNull() ?: 0.0)
			put("saturated", saturated.toDoubleOrNull() ?: 0.0)
			put("carbs", carbs.toDoubleOrNull() ?: 0.0)
			put("sugars", sugars.toDoubleOrNull() ?: 0.0)
			put("protein", protein.toDoubleOrNull() ?: 0.0)
			put("salt", salt.toDoubleOrNull() ?: 0.0)
			put("fibers", fibers.toDoubleOrNull() ?: 0.0)
			put("portionGrams", portion.toDoubleOrNull() ?: 100.0)
			put("measurementGrams", measurement.toDoubleOrNull() ?: 100.0)
		}
		AI.saveFoodSpec(context, name.lowercase(), spec)
		name = ""; kj = ""; kcal = ""; fats = ""; saturated = ""; carbs = ""; sugars = ""
		protein = ""; salt = ""; fibers = ""; portion = ""; measurement = ""
		refreshSpecs()
		toastMessage = "Nutrition entry saved"
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
				FutureText("NEW ENTRY", variant = TextVariant.Heading)
				Spacer(modifier = Modifier.height(4.dp))

				FutureTextField(value = name, onValueChange = { name = it }, label = "FOOD NAME", modifier = Modifier.fillMaxWidth())
				FutureNumberInput(value = kj, onValueChange = { kj = it }, label = "kJ", modifier = Modifier.fillMaxWidth())
				FutureNumberInput(value = kcal, onValueChange = { kcal = it }, label = "kcal", modifier = Modifier.fillMaxWidth())
				FutureNumberInput(value = fats, onValueChange = { fats = it }, label = "FATS (g)", modifier = Modifier.fillMaxWidth())
				FutureNumberInput(value = saturated, onValueChange = { saturated = it }, label = "OF WHICH SATURATED (g)", modifier = Modifier.fillMaxWidth())
				FutureNumberInput(value = carbs, onValueChange = { carbs = it }, label = "CARBS (g)", modifier = Modifier.fillMaxWidth())
				FutureNumberInput(value = sugars, onValueChange = { sugars = it }, label = "OF WHICH SUGARS (g)", modifier = Modifier.fillMaxWidth())
				FutureNumberInput(value = protein, onValueChange = { protein = it }, label = "PROTEIN (g)", modifier = Modifier.fillMaxWidth())
				FutureNumberInput(value = salt, onValueChange = { salt = it }, label = "SALT (g)", modifier = Modifier.fillMaxWidth())
				FutureNumberInput(value = fibers, onValueChange = { fibers = it }, label = "FIBERS (g)", modifier = Modifier.fillMaxWidth())
				FutureNumberInput(value = portion, onValueChange = { portion = it }, label = "PORTION (g)", modifier = Modifier.fillMaxWidth())
				FutureNumberInput(value = measurement, onValueChange = { measurement = it }, label = "PER HOW MANY GRAMS", modifier = Modifier.fillMaxWidth())

				Spacer(modifier = Modifier.height(8.dp))
				FutureButton(
					onClick = ::save,
					text = "SAVE ENTRY",
					type = ButtonType.Primary,
					modifier = Modifier.fillMaxWidth()
				)

				Spacer(modifier = Modifier.height(20.dp))
				FutureText("ALL ENTRIES", variant = TextVariant.Heading)
				Spacer(modifier = Modifier.height(8.dp))

				val keys = mutableListOf<String>()
				val it = specs.keys()
				while (it.hasNext()) keys.add(it.next())
				keys.sort()

				if (keys.isEmpty()) {
					FutureText("No entries yet", variant = TextVariant.Muted)
				} else {
					keys.forEachIndexed { index, key ->
						val spec = specs.optJSONObject(key) ?: JSONObject()
						FutureCard(modifier = Modifier.fillMaxWidth()) {
							Column(modifier = Modifier.padding(8.dp)) {
								FutureText(text = key.uppercase(), variant = TextVariant.Glow)
								Spacer(modifier = Modifier.height(4.dp))
								val info = buildString {
									append("kJ: ${spec.optDouble("kj", 0.0)} | ")
									append("kcal: ${spec.optDouble("kcal", 0.0)} | ")
									append("Fats: ${spec.optDouble("fats", 0.0)}g | ")
									append("Sat: ${spec.optDouble("saturated", 0.0)}g | ")
									append("Carbs: ${spec.optDouble("carbs", 0.0)}g | ")
									append("Sugars: ${spec.optDouble("sugars", 0.0)}g | ")
									append("Protein: ${spec.optDouble("protein", 0.0)}g | ")
									append("Salt: ${spec.optDouble("salt", 0.0)}g | ")
									append("Fibers: ${spec.optDouble("fibers", 0.0)}g | ")
									append("Portion: ${spec.optDouble("portionGrams", 100.0)}g | ")
									append("Per: ${spec.optDouble("measurementGrams", 100.0)}g")
								}
								FutureText(text = info, variant = TextVariant.Muted, maxLines = 3)
							}
						}
						Spacer(modifier = Modifier.height(4.dp))
					}
				}
			}
			FutureHeader(
				title = "NUTRITION DATA",
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
