package com.example.myapplication.ocr

object TitleChangeDetector {
    const val DEFAULT_SIMILARITY_THRESHOLD = 0.85

    fun normalize(text: String): String {
        return text.trim().replace("\\s+".toRegex(), "")
    }

    fun compare(
        previousNormalizedText: String?,
        currentText: String,
        threshold: Double = DEFAULT_SIMILARITY_THRESHOLD
    ): TitleComparison {
        val currentNormalizedText = normalize(currentText)
        if (currentNormalizedText.isBlank()) {
            return TitleComparison(
                normalizedText = currentNormalizedText,
                similarityToPrevious = null,
                decision = TitleDecision.Unknown
            )
        }

        if (previousNormalizedText.isNullOrBlank()) {
            return TitleComparison(
                normalizedText = currentNormalizedText,
                similarityToPrevious = null,
                decision = TitleDecision.FirstTitle
            )
        }

        if (previousNormalizedText == currentNormalizedText) {
            return TitleComparison(
                normalizedText = currentNormalizedText,
                similarityToPrevious = 1.0,
                decision = TitleDecision.SameTitle
            )
        }

        val similarity = similarity(previousNormalizedText, currentNormalizedText)
        return TitleComparison(
            normalizedText = currentNormalizedText,
            similarityToPrevious = similarity,
            decision = if (similarity >= threshold) {
                TitleDecision.SameTitle
            } else {
                TitleDecision.NewTitle
            }
        )
    }

    private fun similarity(left: String, right: String): Double {
        val maxLength = maxOf(left.length, right.length)
        if (maxLength == 0) return 1.0
        val distance = levenshteinDistance(left, right)
        return 1.0 - distance.toDouble() / maxLength.toDouble()
    }

    private fun levenshteinDistance(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)

        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            val swap = previous
            previous = current
            current = swap
        }

        return previous[right.length]
    }
}

data class TitleComparison(
    val normalizedText: String,
    val similarityToPrevious: Double?,
    val decision: TitleDecision
)
