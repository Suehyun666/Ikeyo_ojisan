package com.example.myapplication.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.myapplication.detection.DetectionResult
import com.example.myapplication.detection.YellowDetectionSettings
import com.example.myapplication.ocr.CropOcrState
import com.example.myapplication.translation.CropTranslationState
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultArtifactSaver(private val context: Context) {
    fun save(
        originalBitmap: Bitmap,
        detectionResult: DetectionResult,
        detectionSettings: YellowDetectionSettings,
        cropOcrStates: Map<Int, CropOcrState>,
        cropTranslationStates: Map<Int, CropTranslationState>
    ): SaveResult {
        val sessionName = "case_${timestamp()}"
        val imageUris = mutableListOf<Uri>()

        imageUris += saveImage(
            bitmap = originalBitmap,
            sessionName = sessionName,
            fileName = "original.png"
        )
        imageUris += saveImage(
            bitmap = detectionResult.debugBitmap,
            sessionName = sessionName,
            fileName = "detected.png"
        )
        imageUris += saveImage(
            bitmap = detectionResult.maskBitmap,
            sessionName = sessionName,
            fileName = "mask.png"
        )

        detectionResult.crops.forEachIndexed { index, crop ->
            val number = (index + 1).toString().padStart(2, '0')
            imageUris += saveImage(
                bitmap = crop.bitmap,
                sessionName = sessionName,
                fileName = "crop_$number.png"
            )
            imageUris += saveImage(
                bitmap = crop.ocrBitmap,
                sessionName = sessionName,
                fileName = "ocr_input_$number.png"
            )
        }

        val summaryUri = saveText(
            text = buildSummary(
                sessionName = sessionName,
                detectionResult = detectionResult,
                detectionSettings = detectionSettings,
                cropOcrStates = cropOcrStates,
                cropTranslationStates = cropTranslationStates
            ),
            sessionName = sessionName,
            fileName = "summary.txt"
        )

        return SaveResult(
            sessionName = sessionName,
            imageCount = imageUris.size,
            summaryUri = summaryUri
        )
    }

    private fun saveImage(
        bitmap: Bitmap,
        sessionName: String,
        fileName: String
    ): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "$PICTURES_DIRECTORY/$sessionName"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Failed to create image: $fileName")
            resolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            } ?: error("Failed to open image output stream: $fileName")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val file = legacyFile(sessionName, fileName)
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            Uri.fromFile(file)
        }
    }

    private fun saveText(
        text: String,
        sessionName: String,
        fileName: String
    ): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "$DOCUMENTS_DIRECTORY/$sessionName"
                )
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: error("Failed to create summary file")
            resolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(text)
            } ?: error("Failed to open summary output stream")
            uri
        } else {
            val file = legacyFile(sessionName, fileName)
            file.writeText(text, Charsets.UTF_8)
            Uri.fromFile(file)
        }
    }

    private fun legacyFile(sessionName: String, fileName: String): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: context.filesDir
        val directory = File(root, "ReelsOverlayResults/$sessionName")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return File(directory, fileName)
    }

    private fun buildSummary(
        sessionName: String,
        detectionResult: DetectionResult,
        detectionSettings: YellowDetectionSettings,
        cropOcrStates: Map<Int, CropOcrState>,
        cropTranslationStates: Map<Int, CropTranslationState>
    ): String {
        return buildString {
            appendLine("Session: $sessionName")
            appendLine()
            appendLine("Detection settings")
            appendLine("lowerHue=${detectionSettings.lowerHue}")
            appendLine("upperHue=${detectionSettings.upperHue}")
            appendLine("lowerSaturation=${detectionSettings.lowerSaturation}")
            appendLine("lowerValue=${detectionSettings.lowerValue}")
            appendLine("minWidth=${detectionSettings.minWidth}")
            appendLine("minHeight=${detectionSettings.minHeight}")
            appendLine("minAspectRatio=${detectionSettings.minAspectRatio}")
            appendLine("cropPaddingX=${detectionSettings.cropPaddingX}")
            appendLine("cropPaddingY=${detectionSettings.cropPaddingY}")
            appendLine("ocrScale=${detectionSettings.ocrScale}")
            appendLine()
            appendLine("Detected crops: ${detectionResult.crops.size}")

            detectionResult.crops.forEachIndexed { index, crop ->
                val ocr = cropOcrStates[index]
                val translation = cropTranslationStates[index]
                appendLine()
                appendLine("Crop ${index + 1}")
                appendLine("rect=x=${crop.rect.x}, y=${crop.rect.y}, w=${crop.rect.width}, h=${crop.rect.height}")
                appendLine("ocrCrop=x=${crop.cropRect.x}, y=${crop.cropRect.y}, w=${crop.cropRect.width}, h=${crop.cropRect.height}")
                appendLine("cropSize=${crop.bitmap.width}x${crop.bitmap.height}")
                appendLine("ocrInputSize=${crop.ocrBitmap.width}x${crop.ocrBitmap.height}")
                appendLine("ocrText=${ocr?.text.orEmpty()}")
                appendLine("normalized=${ocr?.normalizedText.orEmpty()}")
                appendLine("decision=${ocr?.titleDecision ?: "unknown"}")
                appendLine("similarity=${ocr?.similarityToPrevious ?: "-"}")
                appendLine("translation=${translation?.text.orEmpty()}")
                appendLine("translationReused=${translation?.wasReused ?: false}")
                translation?.errorMessage?.let { error ->
                    appendLine("translationError=$error")
                }
                ocr?.errorMessage?.let { error ->
                    appendLine("ocrError=$error")
                }
            }
        }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    private companion object {
        const val PICTURES_DIRECTORY = "Pictures/ReelsOverlayResults"
        const val DOCUMENTS_DIRECTORY = "Documents/ReelsOverlayResults"
    }
}

data class SaveResult(
    val sessionName: String,
    val imageCount: Int,
    val summaryUri: Uri
)
