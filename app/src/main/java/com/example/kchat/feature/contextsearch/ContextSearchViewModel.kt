package com.example.kchat.feature.contextsearch

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
 * ViewModel managing UI state and search execution for Context-Aware Conversation Search.
 * Strictly operates on user-submitted queries and grounded local conversation messages.
 */
@HiltViewModel
class ContextSearchViewModel @Inject constructor(
    private val searchService: GeminiContextSearchService
) : ViewModel() {

    private val _uiState = MutableStateFlow<ContextSearchUiState>(ContextSearchUiState.Idle)
    val uiState: StateFlow<ContextSearchUiState> = _uiState.asStateFlow()

    /**
     * Executes context-aware conversation search for the given query against local messages.
     */
    fun searchConversation(query: String, messages: List<Message>, currentUserId: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        if (_uiState.value is ContextSearchUiState.Loading) return
        _uiState.value = ContextSearchUiState.Loading

        viewModelScope.launch {
            val result = searchService.searchConversation(trimmed, messages, currentUserId)
            result.fold(
                onSuccess = { searchResult ->
                    _uiState.value = ContextSearchUiState.Success(searchResult)
                },
                onFailure = { error ->
                    _uiState.value = ContextSearchUiState.Error(
                        error.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Unable to search conversation context. Please try again."
                    )
                }
            )
        }
    }

    /**
     * Resets search state back to Idle.
     */
    fun reset() {
        _uiState.value = ContextSearchUiState.Idle
    }
}
