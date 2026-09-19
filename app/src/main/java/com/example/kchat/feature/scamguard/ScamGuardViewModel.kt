package com.example.kchat.feature.scamguard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kchat.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing UI state for KChat AI Scam & Phishing Guard.
 * Operates strictly on-demand on user-selected messages.
 */
@HiltViewModel
class ScamGuardViewModel @Inject constructor(
    private val scamGuardService: GeminiScamGuardService
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScamGuardUiState>(ScamGuardUiState.Idle)
    val uiState: StateFlow<ScamGuardUiState> = _uiState.asStateFlow()

    fun analyzeSelectedMessages(messages: List<Message>, currentUserId: String) {
        if (_uiState.value is ScamGuardUiState.Loading) return
        if (messages.isEmpty()) {
            _uiState.value = ScamGuardUiState.Error("Select one or more messages to scan for scam or phishing risks.")
            return
        }

        _uiState.value = ScamGuardUiState.Loading

        viewModelScope.launch {
            val result = scamGuardService.analyzeMessages(messages, currentUserId)
            result.fold(
                onSuccess = { scamResult ->
                    _uiState.value = ScamGuardUiState.Success(scamResult)
                },
                onFailure = {
                    _uiState.value = ScamGuardUiState.Error(
                        "Scam Guard couldn't analyze these messages right now."
                    )
                }
            )
        }
    }

    fun reset() {
        _uiState.value = ScamGuardUiState.Idle
    }
}
