package com.mtracker.ui

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface 
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mlib.future.FutureTheme
import com.mlib.future.components.*
import com.mtracker.Audio
import com.mtracker.ThemeManager

@Composable
fun MTrackerApp(initialSummaryDate: String? = null) {
    val viewModel: MainViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            com.mtracker.App.instance
        )
    )
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    val isSystemDark = isSystemInDarkTheme()
    var themeName by remember { mutableStateOf(ThemeManager.getThemeName(context)) }
    val config = remember(themeName, isSystemDark) {
        ThemeManager.getThemeConfig(themeName, isSystemDark)
    }

    DisposableEffect(activity) {
        val listener: (android.content.Intent) -> Unit = { intent ->
            intent.getStringExtra("show_summary_date")?.let { date ->
                viewModel.checkPendingSummary(context, date)
            }
        }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }

    LaunchedEffect(Unit) {
        viewModel.checkPendingSummary(context, initialSummaryDate)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
            navController.navigate("entry/-1")
        }
    }

    FutureTheme(config = config) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = FutureTheme.config.colors.globalBg
        ) {
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {

                NavHost(
                    navController = navController,
                    startDestination = "main",
                    modifier = Modifier.weight(1f), 
                    enterTransition = { slideInHorizontally { it } },
                    exitTransition = { slideOutHorizontally { -it } },
                    popEnterTransition = { slideInHorizontally { -it } },
                    popExitTransition = { slideOutHorizontally { it } }
                ) {
                    composable("main") { MainScreen(navController, viewModel) }
                    composable("data") { DataScreen(navController, viewModel) }
                    composable("nutrition") { NutritionScreen(navController, viewModel) }
                    composable("workouts") { WorkoutScreen(navController, viewModel) }
					composable("ocr") { OcrScreen(navController, viewModel) }
                    composable("habits") { HabitsScreen(navController, viewModel) }
                    composable(
                        route = "entry/{entryId}",
                        arguments = listOf(navArgument("entryId") { type = NavType.LongType; defaultValue = -1L })
                    ) { backStackEntry ->
                        val entryId = backStackEntry.arguments?.getLong("entryId") ?: -1L
                        if (entryId != -1L) viewModel.resumeEntry(entryId)
                        EntryScreen(navController, viewModel)
                    }
                }
            }

            if (viewModel.showSettings) {
                SettingsScreen(
                    navController = navController,
                    viewModel = viewModel,
                    currentThemeName = themeName,
                    onThemeSelected = {
                        themeName = it
                        ThemeManager.setThemeName(context, it)
                    }
                ) {
                    viewModel.showSettings = false
                }
            }

            if (viewModel.showSummarySelector) {
                FutureDialog(
                    onDismissRequest = { viewModel.showSummarySelector = false },
                    title = ""
                ) {
                    Column {
                        listOf(
                            "YESTERDAY" to "YESTERDAY SUMMARY",
                            "PAST_WEEK" to "PAST WEEK SUMMARY",
                            "PAST_MONTH" to "PAST MONTH SUMMARY"
                        ).forEach { (period, label) ->
                            FutureButton(
                                onClick = {
                                    viewModel.showSummarySelector = false
                                    viewModel.generateSummary(period)
                                },
                                text = label,
                                type = ButtonType.Primary,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FutureButton(
                            onClick = {
                                viewModel.showSummarySelector = false
                                viewModel.isFreeFormAiMode = true
                                if (Audio.canRecord(context)) {
                                    viewModel.startRecording()
                                    navController.navigate("entry/-1")
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            text = "ASK AI ANYTHING",
                            type = ButtonType.Primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (viewModel.showSummary) {
                FutureDialog(
                    onDismissRequest = { viewModel.showSummary = false },
                    title = "${viewModel.summaryPeriod.replace("_", " ")} SUMMARY"
                ) {
                    FutureText(text = viewModel.summaryText, variant = TextVariant.Primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    FutureButton(onClick = { viewModel.showSummary = false }, text = "CLOSE")
                }
            }

            viewModel.pendingDailySummaryText?.let { summary ->
                FutureDialog(
                    onDismissRequest = { viewModel.dismissPendingDailySummary() },
                    title = "DAILY SUMMARY"
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .heightIn(max = 400.dp)
                                .verticalScroll(scrollState)
                        ) {
                            FutureText(text = summary, variant = TextVariant.Primary)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        FutureButton(
                            onClick = { viewModel.dismissPendingDailySummary() },
                            text = "CLOSE",
                            type = ButtonType.Primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
