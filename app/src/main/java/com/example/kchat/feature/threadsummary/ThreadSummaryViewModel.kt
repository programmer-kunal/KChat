package com.example.kchat.feature.threadsummary

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
 * ViewModel managing the UI state for KChat Smart Thread Summarization.
 */
@HiltViewModel
class ThreadSummaryViewModel @Inject constructor(
    private val summaryService: GeminiThreadSummaryService
) : ViewModel() {

    private val _uiState = MutableStateFlow<ThreadSummaryUiState>(ThreadSummaryUiState.Idle)
    val uiState: StateFlow<ThreadSummaryUiState> = _uiState.asStateFlow()

    fun summarizeThread(messages: List<Message>, currentUserId: String) {
        if (_uiState.value is ThreadSummaryUiState.Loading) return
        _uiState.value = ThreadSummaryUiState.Loading

        viewModelScope.launch {
            val result = summaryService.summarizeThread(messages, currentUserId)
            result.fold(
                onSuccess = { summaryResult ->
                    _uiState.value = ThreadSummaryUiState.Success(summaryResult)
                },
                onFailure = { error ->
                    _uiState.value = ThreadSummaryUiState.Error(
                        error.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Unable to summarize this conversation. Please try again."
                    )
                }
            )
        }
    }

    fun reset() {
        _uiState.value = ThreadSummaryUiState.Idle
    }
}
