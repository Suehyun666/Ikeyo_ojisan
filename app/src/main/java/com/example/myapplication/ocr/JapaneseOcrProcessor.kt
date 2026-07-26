package com.example.myapplication.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

class JapaneseOcrProcessor {
    private val recognizer = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build()
    )

    fun recognize(
        bitmap: Bitmap,
        onSuccess: (String) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(inputImage)
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                Log.d(TAG, "OCR result: $text")
                onSuccess(text)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "OCR failed", error)
                onFailure(error)
            }
    }

    fun close() {
        recognizer.close()
    }

    private companion object {
        const val TAG = "JapaneseOCR"
    }
}
