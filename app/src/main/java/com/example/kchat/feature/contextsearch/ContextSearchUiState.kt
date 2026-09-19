package com.example.kchat.feature.contextsearch

/**
 * UI State representation for KChat Context-Aware Conversation Search.
 */
sealed interface ContextSearchUiState {
    object Idle : ContextSearchUiState
    object Loading : ContextSearchUiState
    data class Success(val result: ContextSearchResult) : ContextSearchUiState
    data class Error(val message: String) : ContextSearchUiState
}
