package com.example.kchat.feature.scamguard

/**
 * Risk levels evaluated by KChat Scam Guard.
 * Always adheres to anti-false-positive principles by describing risk objectively.
 */
enum class ScamRiskLevel(val displayName: String) {
    LOW("Low Risk"),
    MEDIUM("Potential Risk"),
    HIGH("High Risk");

    companion object {
        fun fromString(value: String?): ScamRiskLevel {
            return when (value?.trim()?.uppercase()) {
                "HIGH" -> HIGH
                "MEDIUM" -> MEDIUM
                else -> LOW
            }
        }
    }
}

/**
 * Structured analysis result produced by Gemini for selected messages.
 */
data class ScamGuardResult(
    val riskLevel: ScamRiskLevel = ScamRiskLevel.LOW,
    val isSuspicious: Boolean = false,
    val summary: String = "",
    val indicators: List<String> = emptyList(),
    val recommendedActions: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = summary.isBlank() && indicators.isEmpty() && recommendedActions.isEmpty()

    /**
     * Formats the analysis into clean, readable text suitable for copying to clipboard.
     */
    fun toFormattedText(): String = buildString {
        appendLine("🛡️ KChat Scam Guard Analysis")
        appendLine()
        appendLine("Risk Assessment: ${riskLevel.displayName}")
        if (summary.isNotBlank()) {
            appendLine()
            appendLine("Summary:")
            appendLine(summary)
        }
        if (indicators.isNotEmpty()) {
            appendLine()
            appendLine("Suspicious Indicators:")
            indicators.forEach { appendLine("• $it") }
        }
        if (recommendedActions.isNotEmpty()) {
            appendLine()
            appendLine("Recommended Actions:")
            recommendedActions.forEach { appendLine("• $it") }
        }
    }.trim()
}
