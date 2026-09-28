package com.mtracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.mlib.future.components.*
import com.mtracker.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.Executors

enum class OcrScreenState {
    CAMERA, PROCESSING, REVIEW, DONE
}

@Composable
fun OcrScreen(navController: NavController, viewModel: MainViewModel) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(OcrScreenState.CAMERA) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rawOcrText by remember { mutableStateOf("") }
    var parsedItems by remember { mutableStateOf<List<ReceiptParser.ParsedItem>>(emptyList()) }
    var receiptDate by remember { mutableStateOf<String?>(null) }
    var receiptTime by remember { mutableStateOf<String?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) state = OcrScreenState.CAMERA
    }

    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    val activity = context as? androidx.activity.ComponentActivity
    val shouldShowRationale = activity?.let {
        androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
            it, Manifest.permission.CAMERA
        )
    } ?: false

    val permanentlyDenied = !hasPermission && !shouldShowRationale

    Column(modifier = Modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (!hasPermission) {
                PermissionView(permanentlyDenied, context, permissionLauncher)
            } else when (state) {
                OcrScreenState.CAMERA -> CameraView(
                    onCapture = { bitmap ->
                        capturedBitmap = bitmap
                        state = OcrScreenState.PROCESSING
                        CoroutineScope(Dispatchers.IO).launch {
                            val text = OcrEngine.useOcr(bitmap)
                            val result = ReceiptParser.parse(text)
                            rawOcrText = text
                            parsedItems = result.items
                            receiptDate = result.receiptDate
                            receiptTime = result.receiptTime
                            withContext(Dispatchers.Main) { state = OcrScreenState.REVIEW }
                        }
                    }
                )
                OcrScreenState.PROCESSING -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FutureLoading(text = "READING RECEIPT")
                        Spacer(modifier = Modifier.height(16.dp))
                        FutureText("Processing Cyrillic text…", variant = TextVariant.Muted)
                    }
                }
                OcrScreenState.REVIEW -> {
                    ReviewOcrView(
                        items = parsedItems,
                        rawText = rawOcrText,
                        receiptDate = receiptDate,
                        receiptTime = receiptTime,
                        onSave = { finalItems ->
                            val timestamp = ReceiptParser.parseReceiptTimestamp(receiptDate, receiptTime)
                                ?: System.currentTimeMillis()
                            finalItems.forEach { item ->
                                val details = JSONObject().apply {
                                    put("item", item.name)
                                    put("price", item.price)
                                    put("currency", "MKD")
                                }
                                val entry = Entry(
                                    type = Type.MONEY,
                                    timestamp = timestamp,
                                    rawInput = "${item.name}: ${item.price} ден.",
                                    details = details,
                                    location = "Unspecified"
                                )
                                viewModel.insertOcrEntry(entry)
                            }
                            toastMessage = "Saved ${finalItems.size} items"
                            state = OcrScreenState.DONE
                        },
                        onRetake = {
                            state = OcrScreenState.CAMERA
                            capturedBitmap = null
                            receiptDate = null
                            receiptTime = null
                        }
                    )
                }
                OcrScreenState.DONE -> {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }
        }
        FutureHeader(
            title = "SCAN RECEIPT",
			isBottom = true,
            actions = {
                FutureButton(
                    onClick = { navController.popBackStack() },
                    text = "BACK",
                    type = ButtonType.Primary
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

@Composable
private fun PermissionView(
    permanentlyDenied: Boolean,
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<String>
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FutureText("Camera permission required", variant = TextVariant.Heading)
        Spacer(modifier = Modifier.height(16.dp))

        if (permanentlyDenied) {
            FutureText(
                "Permission denied. Open app settings to enable camera.",
                variant = TextVariant.Muted
            )
            Spacer(modifier = Modifier.height(16.dp))
            FutureButton(
                onClick = {
                    val intent = android.content.Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                },
                text = "OPEN SETTINGS",
                type = ButtonType.Primary
            )
        } else {
            FutureButton(
                onClick = { launcher.launch(Manifest.permission.CAMERA) },
                text = "GRANT PERMISSION",
                type = ButtonType.Primary
            )
        }
    }
}

@Composable
private fun CameraView(onCapture: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture
                    val selector = CameraSelector.DEFAULT_BACK_CAMERA
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FutureButton(
                onClick = {
                    val capture = imageCapture ?: return@FutureButton
                    capture.takePicture(
                        executor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bitmap = image.toBitmap()
                                image.close()
                                bitmap?.let { bmp ->
                                    val finalBmp = bmp.rotateIfNeeded(image.imageInfo.rotationDegrees)
                                    CoroutineScope(Dispatchers.Main).launch {
                                        onCapture(finalBmp)
                                    }
                                }
                            }
                            override fun onError(exc: ImageCaptureException) {
                                Log.e("OCR", "Capture failed: ${exc.message}", exc)
                            }
                        }
                    )
                },
                text = "CAPTURE",
                type = ButtonType.Highlighted,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }
}

private fun ImageProxy.toBitmap(): Bitmap? {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val yuvImage = android.graphics.YuvImage(
        bytes, ImageFormat.NV21,
        width, height, null
    )
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
    val jpegBytes = out.toByteArray()
    return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
}

private fun Bitmap.rotateIfNeeded(rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return this
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

@Composable
private fun ReviewOcrView(
    items: List<ReceiptParser.ParsedItem>,
    rawText: String,
    receiptDate: String?,
    receiptTime: String?,
    onSave: (List<ReceiptParser.ParsedItem>) -> Unit,
    onRetake: () -> Unit
) {
    val scrollState = rememberScrollState()
    var editedItems by remember(items) { mutableStateOf(items) }
    var showRaw by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        FutureText("REVIEW ITEMS", variant = TextVariant.Heading)
        Spacer(modifier = Modifier.height(8.dp))

        if (receiptDate != null) {
            FutureText(
                "Receipt: $receiptDate ${receiptTime ?: ""}",
                variant = TextVariant.Muted
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (editedItems.isEmpty()) {
            FutureText("No items detected. Try retaking.", variant = TextVariant.Muted)
            Spacer(modifier = Modifier.height(16.dp))
            FutureButton(
                onClick = onRetake,
                text = "RETAKE",
                type = ButtonType.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                editedItems.forEachIndexed { index, item ->
                    var name by remember { mutableStateOf(item.name) }
                    var priceStr by remember { mutableStateOf(item.price.toString()) }

                    FutureCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FutureText("Item ${index + 1}", variant = TextVariant.Heading)
                                FutureButton(
                                    onClick = {
                                        editedItems = editedItems.toMutableList().apply { removeAt(index) }
                                    },
                                    text = "REMOVE",
                                    type = ButtonType.Destructive
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            FutureTextField(
                                value = name,
                                onValueChange = { newName ->
                                    name = newName
                                    editedItems = editedItems.toMutableList().apply {
                                        set(index, item.copy(name = newName))
                                    }
                                },
                                label = "ITEM",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FutureNumberInput(
                                value = priceStr,
                                onValueChange = { newPrice ->
                                    priceStr = newPrice
                                    newPrice.toIntOrNull()?.let { p ->
                                        editedItems = editedItems.toMutableList().apply {
                                            set(index, item.copy(price = p))
                                        }
                                    }
                                },
                                label = "PRICE (MKD)",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                FutureButton(
                    onClick = {
                        editedItems = editedItems + ReceiptParser.ParsedItem("", 0)
                    },
                    text = "+ ADD ITEM",
                    type = ButtonType.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (showRaw) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FutureCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            FutureText("RAW OCR:", variant = TextVariant.Heading)
                            FutureText(rawText, variant = TextVariant.Muted)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FutureButton(
                onClick = { showRaw = !showRaw },
                text = if (showRaw) "HIDE RAW" else "SHOW RAW OCR",
                type = ButtonType.Primary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            FutureButtonRow(modifier = Modifier.fillMaxWidth()) {
                FutureButton(
                    onClick = onRetake,
                    text = "RETAKE",
                    type = ButtonType.Destructive,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                FutureButton(
                    onClick = { onSave(editedItems.filter { it.name.isNotBlank() }) },
                    text = "SAVE ITEMS",
                    type = ButtonType.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
