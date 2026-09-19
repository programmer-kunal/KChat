package com.example.kchat.feature.smartreply

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
 * ViewModel managing the UI state of KChat Smart Reply.
 */
@HiltViewModel
class SmartReplyViewModel @Inject constructor(
    private val smartReplyService: GeminiSmartReplyService
) : ViewModel() {

    private val _uiState = MutableStateFlow<SmartReplyUiState>(SmartReplyUiState.Idle)
    val uiState: StateFlow<SmartReplyUiState> = _uiState.asStateFlow()

    fun generateReplies(messages: List<Message>, currentUserId: String) {
        if (_uiState.value is SmartReplyUiState.Loading) return
        _uiState.value = SmartReplyUiState.Loading
        viewModelScope.launch {
            val result = smartReplyService.generateSmartReplies(messages, currentUserId)
            result.fold(
                onSuccess = { _uiState.value = SmartReplyUiState.Success(it) },
                onFailure = {
                    _uiState.value = SmartReplyUiState.Error(
                        it.localizedMessage?.takeIf { msg -> msg.isNotBlank() }
                            ?: "Unable to generate smart replies. Please try again."
                    )
                }
            )
        }
    }

    fun reset() {
        _uiState.value = SmartReplyUiState.Idle
    }
}
