package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    private var canDrawOverlays by mutableStateOf(false)
    private var screenCaptureGranted by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Check permissions.")
    private var waitingForNotificationPermission by mutableStateOf(false)
    private var opencvReady by mutableStateOf(false)
    private var detectionResult by mutableStateOf<DetectionResult?>(null)
    private var detectionMessage by mutableStateOf("Load a screenshot to test yellow box detection.")
    private var selectedBitmap by mutableStateOf<Bitmap?>(null)
    private var detectionSettings by mutableStateOf(YellowDetectionSettings())

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateOverlayPermissionState()
    }

    private val screenCapturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        screenCaptureGranted = result.resultCode == Activity.RESULT_OK && result.data != null
        if (screenCaptureGranted) {
            startOverlayCaptureService(result.resultCode, result.data!!)
            statusMessage = "Overlay capture test started."
        } else {
            statusMessage = "Screen capture permission denied."
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        waitingForNotificationPermission = false
        launchScreenCaptureConsent()
    }

    private val screenshotPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            detectionMessage = "Image selection canceled."
            return@registerForActivityResult
        }
        runYellowDetection(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        opencvReady = OpenCVLoader.initLocal()
        updateOverlayPermissionState()

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PermissionScreen(
                        canDrawOverlays = canDrawOverlays,
                        screenCaptureGranted = screenCaptureGranted,
                        statusMessage = statusMessage,
                        opencvReady = opencvReady,
                        detectionResult = detectionResult,
                        detectionMessage = detectionMessage,
                        detectionSettings = detectionSettings,
                        onRequestOverlayPermission = ::requestOverlayPermission,
                        onRequestScreenCapturePermission = ::requestScreenCapturePermission,
                        onLoadScreenshot = ::loadScreenshot,
                        onDetectionSettingsChanged = ::updateDetectionSettings,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateOverlayPermissionState()
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            updateOverlayPermissionState()
            return
        }

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestScreenCapturePermission() {
        if (!Settings.canDrawOverlays(this)) {
            statusMessage = "Allow overlay permission first."
            requestOverlayPermission()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !waitingForNotificationPermission) {
            waitingForNotificationPermission = true
            statusMessage = "Notification permission requested."
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        launchScreenCaptureConsent()
    }

    private fun launchScreenCaptureConsent() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            as MediaProjectionManager
        screenCapturePermissionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startOverlayCaptureService(resultCode: Int, resultData: Intent) {
        val intent = Intent(this, ReelsOverlayCaptureService::class.java).apply {
            action = ReelsOverlayCaptureService.ACTION_START
            putExtra(ReelsOverlayCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ReelsOverlayCaptureService.EXTRA_RESULT_DATA, resultData)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun loadScreenshot() {
        if (!opencvReady) {
            detectionMessage = "OpenCV failed to initialize."
            return
        }
        screenshotPickerLauncher.launch("image/*")
    }

    private fun runYellowDetection(uri: Uri) {
        detectionMessage = "Running yellow HSV detection..."
        val bitmap = loadBitmap(uri)
        if (bitmap == null) {
            detectionMessage = "Failed to load image."
            return
        }

        selectedBitmap = bitmap
        detectionResult = YellowBoxDetector.detectYellowBoxes(bitmap, detectionSettings)
        detectionMessage = buildDetectionMessage(detectionResult!!)
    }

    private fun updateDetectionSettings(settings: YellowDetectionSettings) {
        detectionSettings = settings.normalized()
        selectedBitmap?.let { bitmap ->
            detectionResult = YellowBoxDetector.detectYellowBoxes(bitmap, detectionSettings)
            detectionMessage = buildDetectionMessage(detectionResult!!)
        }
    }

    private fun YellowDetectionSettings.normalized(): YellowDetectionSettings {
        return copy(
            lowerHue = lowerHue.coerceAtMost(upperHue - 1.0),
            upperHue = upperHue.coerceAtLeast(lowerHue + 1.0)
        )
    }

    @Suppress("DEPRECATION")
    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildDetectionMessage(result: DetectionResult): String {
        val coordinates = result.boxes.joinToString(separator = "\n") { rect ->
            "x=${rect.x}, y=${rect.y}, w=${rect.width}, h=${rect.height}"
        }
        return if (coordinates.isBlank()) {
            "Detected boxes: 0"
        } else {
            "Detected boxes: ${result.boxes.size}\n$coordinates"
        }
    }

    private fun updateOverlayPermissionState() {
        canDrawOverlays = Settings.canDrawOverlays(this)
        statusMessage = if (canDrawOverlays) {
            "Overlay permission allowed."
        } else {
            "Overlay permission needed."
        }
    }
}

@Composable
fun PermissionScreen(
    canDrawOverlays: Boolean,
    screenCaptureGranted: Boolean,
    statusMessage: String,
    opencvReady: Boolean,
    detectionResult: DetectionResult?,
    detectionMessage: String,
    detectionSettings: YellowDetectionSettings,
    onRequestOverlayPermission: () -> Unit,
    onRequestScreenCapturePermission: () -> Unit,
    onLoadScreenshot: () -> Unit,
    onDetectionSettingsChanged: (YellowDetectionSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Reels Overlay Test",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(text = "Overlay: ${if (canDrawOverlays) "allowed" else "needed"}")
        Text(text = "Screen capture: ${if (screenCaptureGranted) "allowed" else "needed"}")
        Text(text = statusMessage)
        Button(
            onClick = onRequestOverlayPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Allow overlay")
        }
        Button(
            onClick = onRequestScreenCapturePermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Start overlay capture test")
        }
        Text(
            text = "Yellow Box Detector",
            style = MaterialTheme.typography.titleLarge
        )
        Text(text = "OpenCV: ${if (opencvReady) "ready" else "failed"}")
        Button(
            onClick = onLoadScreenshot,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Load screenshot")
        }
        Text(text = detectionMessage)
        DetectionTuningControls(
            settings = detectionSettings,
            onSettingsChanged = onDetectionSettingsChanged
        )
        detectionResult?.let { result ->
            Text(text = "Original + boundingRect")
            Image(
                bitmap = result.debugBitmap.asImageBitmap(),
                contentDescription = "Yellow detection debug image",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
            Text(text = "HSV yellow mask")
            Image(
                bitmap = result.maskBitmap.asImageBitmap(),
                contentDescription = "HSV yellow mask image",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PermissionScreenPreview() {
    MyApplicationTheme {
        PermissionScreen(
            canDrawOverlays = false,
            screenCaptureGranted = false,
            statusMessage = "Check permissions.",
            opencvReady = true,
            detectionResult = null,
            detectionMessage = "Load a screenshot to test yellow box detection.",
            detectionSettings = YellowDetectionSettings(),
            onRequestOverlayPermission = {},
            onRequestScreenCapturePermission = {},
            onLoadScreenshot = {},
            onDetectionSettingsChanged = {}
        )
    }
}

@Composable
private fun DetectionTuningControls(
    settings: YellowDetectionSettings,
    onSettingsChanged: (YellowDetectionSettings) -> Unit
) {
    Text(
        text = "HSV tuning",
        style = MaterialTheme.typography.titleMedium
    )
    TuningSlider(
        label = "Lower H",
        value = settings.lowerHue.toFloat(),
        valueRange = 0f..179f,
        onValueChange = { onSettingsChanged(settings.copy(lowerHue = it.toDouble())) }
    )
    TuningSlider(
        label = "Upper H",
        value = settings.upperHue.toFloat(),
        valueRange = 0f..179f,
        onValueChange = { onSettingsChanged(settings.copy(upperHue = it.toDouble())) }
    )
    TuningSlider(
        label = "Lower S",
        value = settings.lowerSaturation.toFloat(),
        valueRange = 0f..255f,
        onValueChange = { onSettingsChanged(settings.copy(lowerSaturation = it.toDouble())) }
    )
    TuningSlider(
        label = "Lower V",
        value = settings.lowerValue.toFloat(),
        valueRange = 0f..255f,
        onValueChange = { onSettingsChanged(settings.copy(lowerValue = it.toDouble())) }
    )
    Text(
        text = "Shape filter",
        style = MaterialTheme.typography.titleMedium
    )
    TuningSlider(
        label = "Min W",
        value = settings.minWidth.toFloat(),
        valueRange = 20f..900f,
        onValueChange = { onSettingsChanged(settings.copy(minWidth = it.toInt())) }
    )
    TuningSlider(
        label = "Min H",
        value = settings.minHeight.toFloat(),
        valueRange = 10f..250f,
        onValueChange = { onSettingsChanged(settings.copy(minHeight = it.toInt())) }
    )
    TuningSlider(
        label = "Min ratio",
        value = settings.minAspectRatio.toFloat(),
        valueRange = 1f..8f,
        onValueChange = { onSettingsChanged(settings.copy(minAspectRatio = it.toDouble())) }
    )
}

@Composable
private fun TuningSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Text(text = "$label: ${"%.1f".format(value)}")
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = Modifier.fillMaxWidth()
    )
}
