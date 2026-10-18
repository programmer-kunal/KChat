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

    /**
     * Generates smart reply suggestions using the recent conversation context
     * (last 12 messages), without requiring the user to manually select messages.
     * Used when Smart Reply is triggered from the KChat AI bottom sheet.
     */
    fun generateRepliesFromRecentContext(
        messages: List<Message>,
        currentUserId: String,
        isDirectChat: Boolean
    ) {
        if (_uiState.value is SmartReplyUiState.Loading) return
        val recentContext = messages.takeLast(12)
        val hasOtherUserMessages = recentContext.any { msg ->
            msg.senderId.isNotEmpty() && msg.senderId != currentUserId
        }
        if (!hasOtherUserMessages) {
            _uiState.value = SmartReplyUiState.Error("No recent conversation to reply to.")
            return
        }
        _uiState.value = SmartReplyUiState.Loading
        viewModelScope.launch {
            val result = smartReplyService.generateSmartReplies(recentContext, currentUserId)
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

    fun generateRepliesFromRecentContext(allMessages: List<Message>, currentUserId: String) {
        if (_uiState.value is SmartReplyUiState.Loading) return

        val meaningfulMessages = allMessages.filter { msg ->
            !msg.message.isNullOrBlank() ||
            !msg.imageUrl.isNullOrBlank() ||
            !msg.audioUrl.isNullOrBlank() ||
            !msg.videoUrl.isNullOrBlank() ||
            !msg.fileUrl.isNullOrBlank()
        }.sortedBy { it.createdAt }

        val lastOtherIndex = meaningfulMessages.indexOfLast { it.senderId != currentUserId }
        if (meaningfulMessages.isEmpty() || lastOtherIndex == -1) {
            _uiState.value = SmartReplyUiState.Error("No recent conversation from other participants to reply to.")
            return
        }

        // Window of the most recent relevant messages (up to 12), ensuring the other participant's last turn is captured
        val recentMessages = if (meaningfulMessages.size <= 12) {
            meaningfulMessages
        } else if (lastOtherIndex >= meaningfulMessages.size - 12) {
            meaningfulMessages.takeLast(12)
        } else {
            val end = minOf(meaningfulMessages.size, lastOtherIndex + 12)
            meaningfulMessages.subList(lastOtherIndex, end)
        }

        _uiState.value = SmartReplyUiState.Loading
        viewModelScope.launch {
            val result = smartReplyService.generateSmartReplies(recentMessages, currentUserId)
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
