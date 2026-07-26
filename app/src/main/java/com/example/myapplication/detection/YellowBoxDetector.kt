package com.example.myapplication.detection

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

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
            .sortedByDescending { it.width * it.height }

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

        val crops = boxes.mapNotNull { rect ->
            val cropRect = rect.expand(
                paddingX = settings.cropPaddingX,
                paddingY = settings.cropPaddingY,
                maxWidth = bitmap.width,
                maxHeight = bitmap.height
            )
            bitmap.cropSafely(cropRect)?.let { croppedBitmap ->
                YellowBoxCrop(
                    rect = rect,
                    cropRect = cropRect,
                    bitmap = croppedBitmap,
                    ocrBitmap = croppedBitmap.scaleForOcr(settings.ocrScale)
                )
            }
        }

        rgba.release()
        rgb.release()
        hsv.release()
        mask.release()
        kernel.release()
        hierarchy.release()
        debugMat.release()
        contours.forEach { it.release() }

        return DetectionResult(
            crops = crops,
            debugBitmap = debugBitmap,
            maskBitmap = maskBitmap
        )
    }

    private fun Bitmap.cropSafely(rect: Rect): Bitmap? {
        val left = rect.x.coerceIn(0, width - 1)
        val top = rect.y.coerceIn(0, height - 1)
        val right = (rect.x + rect.width).coerceIn(left + 1, width)
        val bottom = (rect.y + rect.height).coerceIn(top + 1, height)
        val cropWidth = right - left
        val cropHeight = bottom - top

        return if (cropWidth > 0 && cropHeight > 0) {
            Bitmap.createBitmap(this, left, top, cropWidth, cropHeight)
        } else {
            null
        }
    }

    private fun Rect.expand(
        paddingX: Int,
        paddingY: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Rect {
        val left = (x - paddingX).coerceAtLeast(0)
        val top = (y - paddingY).coerceAtLeast(0)
        val right = (x + width + paddingX).coerceAtMost(maxWidth)
        val bottom = (y + height + paddingY).coerceAtMost(maxHeight)
        return Rect(left, top, right - left, bottom - top)
    }

    private fun Bitmap.scaleForOcr(scale: Double): Bitmap {
        if (scale <= 1.0) return this
        val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(
            this,
            scaledWidth,
            scaledHeight,
            true
        )
    }
}
