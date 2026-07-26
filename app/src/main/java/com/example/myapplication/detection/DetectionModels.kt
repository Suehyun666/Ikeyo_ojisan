package com.example.myapplication.detection

import android.graphics.Bitmap
import org.opencv.core.Rect

data class YellowDetectionSettings(
    val lowerHue: Double = 23.6,
    val upperHue: Double = 48.8,
    val lowerSaturation: Double = 55.4,
    val lowerValue: Double = 103.1,
    val minWidth: Int = 233,
    val minHeight: Int = 50,
    val minAspectRatio: Double = 1.5
) {
    fun normalized(): YellowDetectionSettings {
        return copy(
            lowerHue = lowerHue.coerceAtMost(upperHue - 1.0),
            upperHue = upperHue.coerceAtLeast(lowerHue + 1.0)
        )
    }
}

data class YellowBoxCrop(
    val rect: Rect,
    val bitmap: Bitmap
)

data class DetectionResult(
    val crops: List<YellowBoxCrop>,
    val debugBitmap: Bitmap,
    val maskBitmap: Bitmap
) {
    val boxes: List<Rect> = crops.map { it.rect }
}
