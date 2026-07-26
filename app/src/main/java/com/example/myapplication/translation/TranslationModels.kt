package com.example.myapplication.translation

data class CropTranslationState(
    val text: String,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val wasReused: Boolean = false
) {
    companion object {
        val Loading = CropTranslationState(text = "Translating...", isLoading = true)
        val Empty = CropTranslationState(text = "No translation")
    }
}
