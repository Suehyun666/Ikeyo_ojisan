package com.example.myapplication.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.detection.DetectionResult
import com.example.myapplication.detection.YellowDetectionSettings
import com.example.myapplication.ocr.CropOcrState
import com.example.myapplication.ui.theme.MyApplicationTheme

@Composable
fun PermissionScreen(
    canDrawOverlays: Boolean,
    screenCaptureGranted: Boolean,
    statusMessage: String,
    opencvReady: Boolean,
    detectionResult: DetectionResult?,
    detectionMessage: String,
    detectionSettings: YellowDetectionSettings,
    cropOcrStates: Map<Int, CropOcrState>,
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
            DetectionImages(
                result = result,
                cropOcrStates = cropOcrStates
            )
        }
    }
}

@Composable
private fun DetectionImages(
    result: DetectionResult,
    cropOcrStates: Map<Int, CropOcrState>
) {
    Text(text = "Detected area crops")
    if (result.crops.isEmpty()) {
        Text(text = "No crop available.")
    } else {
        result.crops.forEachIndexed { index, crop ->
            Text(
                text = "Crop ${index + 1}: x=${crop.rect.x}, y=${crop.rect.y}, " +
                    "w=${crop.rect.width}, h=${crop.rect.height}"
            )
            Text(
                text = "OCR crop: x=${crop.cropRect.x}, y=${crop.cropRect.y}, " +
                    "w=${crop.cropRect.width}, h=${crop.cropRect.height}"
            )
            Text(text = "Size: ${crop.bitmap.width} x ${crop.bitmap.height}")
            Text(text = "OCR input: ${crop.ocrBitmap.width} x ${crop.ocrBitmap.height}")
            Image(
                bitmap = crop.bitmap.asImageBitmap(),
                contentDescription = "Detected yellow area crop ${index + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
            val ocrState = cropOcrStates[index]
            Text(text = "OCR:")
            Text(text = ocrState?.text ?: "OCR pending...")
            ocrState?.errorMessage?.let { message ->
                Text(text = "Error: $message")
            }
        }
    }

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
    TuningSlider(
        label = "Pad X",
        value = settings.cropPaddingX.toFloat(),
        valueRange = 0f..80f,
        onValueChange = { onSettingsChanged(settings.copy(cropPaddingX = it.toInt())) }
    )
    TuningSlider(
        label = "Pad Y",
        value = settings.cropPaddingY.toFloat(),
        valueRange = 0f..60f,
        onValueChange = { onSettingsChanged(settings.copy(cropPaddingY = it.toInt())) }
    )
    TuningSlider(
        label = "OCR scale",
        value = settings.ocrScale.toFloat(),
        valueRange = 1f..4f,
        onValueChange = { onSettingsChanged(settings.copy(ocrScale = it.toDouble())) }
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
            cropOcrStates = emptyMap(),
            onRequestOverlayPermission = {},
            onRequestScreenCapturePermission = {},
            onLoadScreenshot = {},
            onDetectionSettingsChanged = {}
        )
    }
}
