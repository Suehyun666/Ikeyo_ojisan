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
                val text = result.textBlocks
                    .flatMap { block -> block.lines }
                    .map { line -> line.text.trim() }
                    .filter { line -> line.containsJapaneseText() }
                    .joinToString(separator = "\n")
                    .trim()
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

    private fun String.containsJapaneseText(): Boolean {
        return any { char ->
            val block = Character.UnicodeBlock.of(char)
            block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
        }
    }

    private companion object {
        const val TAG = "JapaneseOCR"
    }
}
