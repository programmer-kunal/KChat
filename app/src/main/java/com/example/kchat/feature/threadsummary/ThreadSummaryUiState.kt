package com.example.kchat.feature.threadsummary

/**
 * UI State representation for KChat Smart Thread Summarization.
 */
sealed interface ThreadSummaryUiState {
    object Idle : ThreadSummaryUiState
    object Loading : ThreadSummaryUiState
    data class Success(val result: ThreadSummaryResult) : ThreadSummaryUiState
    data class Error(val message: String) : ThreadSummaryUiState
}
