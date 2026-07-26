package com.example.myapplication.ocr

data class CropOcrState(
    val text: String,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        val Loading = CropOcrState(text = "OCR running...", isLoading = true)
        val Empty = CropOcrState(text = "No OCR result")
    }
}
