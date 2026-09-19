package com.example.kchat.feature.smartreply

/**
 * UI State for the KChat Smart Reply feature.
 */
sealed interface SmartReplyUiState {
    object Idle : SmartReplyUiState
    object Loading : SmartReplyUiState
    data class Success(val result: SmartReplyResult) : SmartReplyUiState
    data class Error(val message: String) : SmartReplyUiState
}
