package com.example.kchat.feature.scamguard

/**
 * UI State representation for KChat AI Scam & Phishing Guard.
 */
sealed interface ScamGuardUiState {
    object Idle : ScamGuardUiState
    object Loading : ScamGuardUiState
    data class Success(val result: ScamGuardResult) : ScamGuardUiState
    data class Error(val message: String) : ScamGuardUiState
}
