package com.mtracker.ui

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mlib.future.components.*
import com.mtracker.Audio
import com.mtracker.PendingRecording

@Composable
fun MainScreen(navController: NavController, viewModel: MainViewModel) {
    val queue by viewModel.queue.collectAsState()
    val isProcessingQueue by viewModel.isProcessingQueue.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
            navController.navigate("entry/-1")
        } else {
            Log.e("MainScreen", "Recording permission denied by user")
        }
    }

    var pendingNavId by remember { mutableStateOf<Long?>(null) }
    val pendingPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingNavId?.let { id ->
            navController.navigate("entry/$id")
            pendingNavId = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            FutureText(text = "QUEUE", variant = TextVariant.Heading)
            Spacer(modifier = Modifier.height(8.dp))
            if (queue.isEmpty()) {
                FutureText(text = "No pending items", variant = TextVariant.Muted)
            } else {
                queue.forEach { item ->
                    FutureCard(
                        onClick = {
                            if (Audio.canRecord(context)) {
                                navController.navigate("entry/${item.id}")
                            } else {
                                pendingNavId = item.id
                                pendingPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                FutureText(
                                    text = item.transcribedText ?: "Recording #${item.id}",
                                    variant = if (item.status == PendingRecording.Status.PROCESSING)
                                        TextVariant.Glow else TextVariant.Primary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                FutureText(text = item.status.name, variant = TextVariant.Muted)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FutureButton(
                                onClick = { viewModel.deletePendingRecording(item.id) },
                                text = "X",
                                type = ButtonType.Destructive
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        FutureGrid(
            modifier = Modifier.padding(16.dp),
            columns = 2,
            items = listOf(
                {
                    FutureButton(
                        onClick = { viewModel.showSettings = true },
                        text = "SETTINGS",
                        type = ButtonType.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                {
                    FutureButton(
                        onClick = { navController.navigate("data") },
                        text = "DATA",
                        type = ButtonType.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                {
                    FutureButton(
                        onClick = { navController.navigate("ocr") },
                        text = "SCAN RECEIPT",
                        type = ButtonType.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                {
                    FutureButton(
                        onClick = {
                            if (Audio.canRecord(context)) {
                                viewModel.startRecording()
                                navController.navigate("entry/-1")
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        text = "RECORD",
                        type = ButtonType.Highlighted,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                {
                    FutureButton(
                        onClick = { viewModel.showSummarySelector = true },
                        text = "SUMMARY",
                        type = ButtonType.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
				{
    val canProcess = queue.isNotEmpty() && !isProcessingQueue
    FutureButton(
        onClick = { if (canProcess) viewModel.processQueue() },
        text = if (isProcessingQueue) "PROCESSING…" else "RUN QUEUE",
        type = if (canProcess) ButtonType.Highlighted else ButtonType.Disabled,
        modifier = Modifier.fillMaxWidth()
    )
}
            )
        )
        FutureHeader(title = "MTRACKER", isBottom = true)
    }
}
