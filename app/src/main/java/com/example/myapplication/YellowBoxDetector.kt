package com.example.myapplication

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class DetectionResult(
    val boxes: List<Rect>,
    val debugBitmap: Bitmap,
    val maskBitmap: Bitmap
)

data class YellowDetectionSettings(
    val lowerHue: Double = 23.6,
    val upperHue: Double = 48.8,
    val lowerSaturation: Double = 55.4,
    val lowerValue: Double = 103.1,
    val minWidth: Int = 233,
    val minHeight: Int = 50,
    val minAspectRatio: Double = 1.5
)

object YellowBoxDetector {
    fun detectYellowBoxes(
        bitmap: Bitmap,
        settings: YellowDetectionSettings = YellowDetectionSettings()
    ): DetectionResult {
        val rgba = Mat()
        val rgb = Mat()
        val hsv = Mat()
        val mask = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()

        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)

        Core.inRange(
            hsv,
            Scalar(settings.lowerHue, settings.lowerSaturation, settings.lowerValue),
            Scalar(settings.upperHue, 255.0, 255.0),
            mask
        )

        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(7.0, 5.0)
        )
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)

        Imgproc.findContours(
            mask,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        val boxes = contours
            .map { Imgproc.boundingRect(it) }
            .filter { rect ->
                rect.width >= settings.minWidth &&
                    rect.height >= settings.minHeight &&
                    rect.width.toDouble() / rect.height >= settings.minAspectRatio
            }

        val debugMat = rgba.clone()
        boxes.forEach { rect ->
            Imgproc.rectangle(
                debugMat,
                rect.tl(),
                rect.br(),
                Scalar(0.0, 255.0, 0.0, 255.0),
                5
            )
        }

        val debugBitmap = Bitmap.createBitmap(
            debugMat.cols(),
            debugMat.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(debugMat, debugBitmap)

        val maskBitmap = Bitmap.createBitmap(
            mask.cols(),
            mask.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(mask, maskBitmap)

        rgba.release()
        rgb.release()
        hsv.release()
        mask.release()
        kernel.release()
        hierarchy.release()
        debugMat.release()
        contours.forEach { it.release() }

        return DetectionResult(
            boxes = boxes,
            debugBitmap = debugBitmap,
            maskBitmap = maskBitmap
        )
    }
}
