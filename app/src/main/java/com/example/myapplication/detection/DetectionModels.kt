package com.example.myapplication.detection

import android.graphics.Bitmap
import org.opencv.core.Rect

data class YellowDetectionSettings(
    val lowerHue: Double = 24.3,
    val upperHue: Double = 77.6,
    val lowerSaturation: Double = 28.5,
    val lowerValue: Double = 161.4,
    val minWidth: Int = 880,
    val minHeight: Int = 50,
    val minAspectRatio: Double = 1.5,
    val cropPaddingX: Int = 24,
    val cropPaddingY: Int = 12,
    val ocrScale: Double = 1.7
) {
    fun normalized(): YellowDetectionSettings {
        return copy(
            lowerHue = lowerHue.coerceAtMost(upperHue - 1.0),
            upperHue = upperHue.coerceAtLeast(lowerHue + 1.0),
            ocrScale = ocrScale.coerceIn(1.0, 4.0)
        )
    }
}

data class YellowBoxCrop(
    val rect: Rect,
    val cropRect: Rect,
    val bitmap: Bitmap,
    val ocrBitmap: Bitmap
)

data class DetectionResult(
    val crops: List<YellowBoxCrop>,
    val debugBitmap: Bitmap,
    val maskBitmap: Bitmap
) {
    val boxes: List<Rect> = crops.map { it.rect }
}
