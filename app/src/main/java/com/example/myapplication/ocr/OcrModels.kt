package com.example.myapplication.ocr

data class CropOcrState(
    val text: String,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val normalizedText: String = "",
    val similarityToPrevious: Double? = null,
    val titleDecision: TitleDecision = TitleDecision.Unknown
) {
    companion object {
        val Loading = CropOcrState(text = "OCR running...", isLoading = true)
        val Empty = CropOcrState(text = "No OCR result")
    }
}

enum class TitleDecision {
    Unknown,
    FirstTitle,
    SameTitle,
    NewTitle
}
