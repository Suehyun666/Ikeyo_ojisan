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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.myapplication.detection.DetectionResult
import com.example.myapplication.detection.YellowBoxDetector
import com.example.myapplication.detection.YellowDetectionSettings
import com.example.myapplication.media.ReelsOverlayCaptureService
import com.example.myapplication.ocr.CropOcrState
import com.example.myapplication.ocr.JapaneseOcrProcessor
import com.example.myapplication.ocr.TitleChangeDetector
import com.example.myapplication.ocr.TitleDecision
import com.example.myapplication.translation.CropTranslationState
import com.example.myapplication.translation.JapaneseKoreanTranslator
import com.example.myapplication.ui.PermissionScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    private val ocrProcessor = JapaneseOcrProcessor()
    private val translator = JapaneseKoreanTranslator()

    private var canDrawOverlays by mutableStateOf(false)
    private var screenCaptureGranted by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Check permissions.")
    private var waitingForNotificationPermission by mutableStateOf(false)
    private var opencvReady by mutableStateOf(false)
    private var detectionResult by mutableStateOf<DetectionResult?>(null)
    private var detectionMessage by mutableStateOf("Load a screenshot to test yellow box detection.")
    private var selectedBitmap by mutableStateOf<Bitmap?>(null)
    private var detectionSettings by mutableStateOf(YellowDetectionSettings())
    private var cropOcrStates by mutableStateOf<Map<Int, CropOcrState>>(emptyMap())
    private var cropTranslationStates by mutableStateOf<Map<Int, CropTranslationState>>(emptyMap())
    private var previousNormalizedTitle: String? = null
    private var previousTranslation: String? = null

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
                        cropOcrStates = cropOcrStates,
                        cropTranslationStates = cropTranslationStates,
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

    override fun onDestroy() {
        ocrProcessor.close()
        translator.close()
        super.onDestroy()
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
        runOcrForCrops(detectionResult!!)
    }

    private fun updateDetectionSettings(settings: YellowDetectionSettings) {
        detectionSettings = settings.normalized()
        selectedBitmap?.let { bitmap ->
            detectionResult = YellowBoxDetector.detectYellowBoxes(bitmap, detectionSettings)
            detectionMessage = buildDetectionMessage(detectionResult!!)
            runOcrForCrops(detectionResult!!)
        }
    }

    private fun runOcrForCrops(result: DetectionResult) {
        cropOcrStates = result.crops.indices.associateWith { CropOcrState.Loading }
        cropTranslationStates = emptyMap()
        if (result.crops.isEmpty()) return

        result.crops.forEachIndexed { index, crop ->
            ocrProcessor.recognize(
                bitmap = crop.ocrBitmap,
                onSuccess = { text ->
                    val comparison = TitleChangeDetector.compare(
                        previousNormalizedText = previousNormalizedTitle,
                        currentText = text
                    )
                    if (comparison.decision == TitleDecision.FirstTitle ||
                        comparison.decision == TitleDecision.NewTitle
                    ) {
                        previousNormalizedTitle = comparison.normalizedText
                    }

                    cropOcrStates = cropOcrStates + (
                        index to if (text.isBlank()) {
                            CropOcrState.Empty
                        } else {
                            CropOcrState(
                                text = text,
                                normalizedText = comparison.normalizedText,
                                similarityToPrevious = comparison.similarityToPrevious,
                                titleDecision = comparison.decision
                            )
                        }
                        )
                    handleTranslation(index, text, comparison.decision)
                },
                onFailure = { error ->
                    cropOcrStates = cropOcrStates + (
                        index to CropOcrState(
                            text = "OCR failed",
                            errorMessage = error.message
                        )
                        )
                }
            )
        }
    }

    private fun handleTranslation(
        index: Int,
        sourceText: String,
        decision: TitleDecision
    ) {
        when (decision) {
            TitleDecision.FirstTitle,
            TitleDecision.NewTitle -> translateNewTitle(index, sourceText)
            TitleDecision.SameTitle -> {
                val translation = previousTranslation
                cropTranslationStates = cropTranslationStates + (
                    index to if (translation.isNullOrBlank()) {
                        CropTranslationState.Empty
                    } else {
                        CropTranslationState(
                            text = translation,
                            wasReused = true
                        )
                    }
                    )
            }
            TitleDecision.Unknown -> {
                cropTranslationStates = cropTranslationStates + (
                    index to CropTranslationState.Empty
                    )
            }
        }
    }

    private fun translateNewTitle(index: Int, sourceText: String) {
        cropTranslationStates = cropTranslationStates + (index to CropTranslationState.Loading)
        translator.translate(
            text = sourceText,
            onSuccess = { translatedText ->
                previousTranslation = translatedText
                cropTranslationStates = cropTranslationStates + (
                    index to if (translatedText.isBlank()) {
                        CropTranslationState.Empty
                    } else {
                        CropTranslationState(text = translatedText)
                    }
                    )
            },
            onFailure = { error ->
                cropTranslationStates = cropTranslationStates + (
                    index to CropTranslationState(
                        text = "Translation failed",
                        errorMessage = error.message
                    )
                    )
            }
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
        val coordinates = result.crops.joinToString(separator = "\n") { crop ->
            val rect = crop.rect
            "x=${rect.x}, y=${rect.y}, w=${rect.width}, h=${rect.height}"
        }
        return if (coordinates.isBlank()) {
            "Detected boxes: 0"
        } else {
            "Detected boxes: ${result.crops.size}\n$coordinates"
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
