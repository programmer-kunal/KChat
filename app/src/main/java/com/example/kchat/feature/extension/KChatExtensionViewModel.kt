package com.example.kchat.feature.extension

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kchat.feature.contextsearch.GeminiContextSearchService
import com.example.kchat.feature.extension.model.ExtensionAiFeature
import com.example.kchat.feature.extension.model.ExtensionScreenshot
import com.example.kchat.feature.extension.model.ExtensionSectionResult
import com.example.kchat.feature.extension.model.ExtensionUiState
import com.example.kchat.feature.extension.parser.ExternalMessageParser
import com.example.kchat.feature.scamguard.GeminiScamGuardService
import com.example.kchat.feature.smartreply.GeminiSmartReplyService
import com.example.kchat.feature.threadsummary.GeminiThreadSummaryService
import com.example.kchat.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class KChatExtensionViewModel @Inject constructor(
    private val smartReplyService: GeminiSmartReplyService,
    private val threadSummaryService: GeminiThreadSummaryService,
    private val contextSearchService: GeminiContextSearchService,
    private val scamGuardService: GeminiScamGuardService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtensionUiState())
    val uiState: StateFlow<ExtensionUiState> = _uiState.asStateFlow()

    private var screenshotCounter = 0

    fun updatePastedText(text: String) {
        val parsed = if (text.isNotBlank()) {
            ExternalMessageParser.parse(text)
        } else {
            null
        }
        _uiState.update { current ->
            current.copy(
                pastedText = text,
                rawInputText = text,
                parsedConversation = parsed,
                pasteSectionState = current.pasteSectionState.copy(errorMessage = null)
            )
        }
    }

    fun updateImportedText(text: String) {
        val parsed = if (text.isNotBlank()) {
            ExternalMessageParser.parse(text)
        } else {
            null
        }
        _uiState.update { current ->
            current.copy(
                importedText = text,
                importedParsedConversation = parsed,
                importSectionState = current.importSectionState.copy(errorMessage = null)
            )
        }
    }

    fun updateRawInputText(text: String) {
        if (_uiState.value.activeInputTab == 2) {
            updateImportedText(text)
        } else {
            updatePastedText(text)
        }
    }

    fun setActiveInputTab(tabIndex: Int) {
        _uiState.update { it.copy(activeInputTab = tabIndex) }
    }

    fun setSearchQuery(query: String, targetTab: Int = _uiState.value.activeInputTab) {
        updateSectionState(targetTab) {
            it.copy(searchQuery = query)
        }
    }

    fun addScreenshotUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val downscaled = decodeDownscaledBitmap(context, uri, maxDimension = 1024)
            if (downscaled != null) {
                screenshotCounter++
                val id = String.format("image_%03d", screenshotCounter)
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "screenshot_$screenshotCounter.png"
                val newScreenshot = ExtensionScreenshot(
                    id = id,
                    uri = uri,
                    bitmap = downscaled,
                    fileName = fileName
                )
                withContext(Dispatchers.Main) {
                    _uiState.update { current ->
                        current.copy(
                            screenshots = current.screenshots + newScreenshot,
                            screenshotsSectionState = current.screenshotsSectionState.copy(errorMessage = null)
                        )
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    updateSectionState(1) {
                        it.copy(errorMessage = "Unable to load or decode image.")
                    }
                }
            }
        }
    }

    fun removeScreenshot(id: String) {
        _uiState.update { current ->
            val updated = current.screenshots.filterNot { it.id == id }
            current.copy(screenshots = updated)
        }
    }

    fun clearCurrentSection(targetTab: Int = _uiState.value.activeInputTab) {
        val state = _uiState.value
        val currentHasContent = when (targetTab) {
            0 -> state.pastedText.isNotBlank() || state.pasteSectionState.hasResult
            1 -> state.screenshots.isNotEmpty() || state.screenshotsSectionState.hasResult
            2 -> state.importedText.isNotBlank() || state.importSectionState.hasResult
            else -> false
        }
        val actualTab = if (currentHasContent) {
            targetTab
        } else {
            when {
                state.pastedText.isNotBlank() || state.pasteSectionState.hasResult -> 0
                state.screenshots.isNotEmpty() || state.screenshotsSectionState.hasResult -> 1
                state.importedText.isNotBlank() || state.importSectionState.hasResult -> 2
                else -> targetTab
            }
        }

        _uiState.update { current ->
            when (actualTab) {
                0 -> current.copy(
                    pastedText = "",
                    rawInputText = "",
                    parsedConversation = null,
                    pasteSectionState = ExtensionSectionResult()
                )
                1 -> current.copy(
                    screenshots = emptyList(),
                    screenshotsSectionState = ExtensionSectionResult()
                )
                2 -> current.copy(
                    importedText = "",
                    importedParsedConversation = null,
                    importSectionState = ExtensionSectionResult()
                )
                else -> current
            }
        }
    }

    fun clearAllSections() {
        screenshotCounter = 0
        _uiState.value = ExtensionUiState()
    }

    fun clearSession() {
        clearAllSections()
    }

    fun selectFeature(feature: ExtensionAiFeature, targetTab: Int = _uiState.value.activeInputTab) {
        updateSectionState(targetTab) {
            it.copy(
                selectedFeature = feature,
                errorMessage = null
            )
        }
        when (feature) {
            ExtensionAiFeature.SMART_REPLY -> runSmartReply(targetTab)
            ExtensionAiFeature.THREAD_SUMMARY -> runThreadSummary(targetTab)
            ExtensionAiFeature.SCAM_GUARD -> runScamGuard(targetTab)
            ExtensionAiFeature.CONTEXT_SEARCH -> {
                val query = _uiState.value.getSectionState(targetTab).searchQuery
                if (query.isNotBlank()) {
                    runContextSearch(query, targetTab)
                }
            }
        }
    }

    private fun updateSectionState(targetTab: Int, transform: (ExtensionSectionResult) -> ExtensionSectionResult) {
        _uiState.update { current ->
            when (targetTab) {
                0 -> current.copy(pasteSectionState = transform(current.pasteSectionState))
                1 -> current.copy(screenshotsSectionState = transform(current.screenshotsSectionState))
                2 -> current.copy(importSectionState = transform(current.importSectionState))
                else -> current
            }
        }
    }

    private fun getContextForTab(targetTab: Int): Pair<List<Message>, List<ExtensionScreenshot>> {
        val state = _uiState.value
        val pasted = state.parsedConversation?.messages ?: emptyList()
        val imported = state.importedParsedConversation?.messages ?: emptyList()
        val allScreenshots = state.screenshots

        return when (targetTab) {
            0 -> {
                // Primary is pasted messages, combined with screenshots if attached
                Pair(pasted, allScreenshots)
            }
            1 -> {
                // Primary is screenshots, combined with any available messages
                Pair(pasted + imported, allScreenshots)
            }
            2 -> {
                // Primary is imported text, combined with screenshots if attached
                Pair(imported, allScreenshots)
            }
            else -> Pair(pasted + imported, allScreenshots)
        }
    }

    fun runSmartReply(targetTab: Int = _uiState.value.activeInputTab) {
        val (messages, screenshots) = getContextForTab(targetTab)
        if (messages.isEmpty() && screenshots.isEmpty()) {
            val msg = if (targetTab == 1) "Please attach a screenshot to generate Smart Replies."
            else "Please enter messages or attach a screenshot to generate Smart Replies."
            updateSectionState(targetTab) { it.copy(errorMessage = msg) }
            return
        }

        viewModelScope.launch {
            updateSectionState(targetTab) { it.copy(isAiLoading = true, errorMessage = null) }
            val result = smartReplyService.generateSmartReplies(
                selectedMessages = messages,
                currentUserId = "me",
                screenshots = screenshots
            )
            updateSectionState(targetTab) { current ->
                result.fold(
                    onSuccess = { data ->
                        current.copy(
                            isAiLoading = false,
                            smartReplyResult = data,
                            errorMessage = null
                        )
                    },
                    onFailure = { err ->
                        current.copy(
                            isAiLoading = false,
                            errorMessage = err.localizedMessage ?: "Failed to generate Smart Replies."
                        )
                    }
                )
            }
        }
    }

    fun runThreadSummary(targetTab: Int = _uiState.value.activeInputTab) {
        val (messages, screenshots) = getContextForTab(targetTab)
        if (messages.isEmpty() && screenshots.isEmpty()) {
            val msg = if (targetTab == 1) "Please attach a screenshot to generate Thread Summary."
            else "Please enter messages or attach a screenshot to generate Thread Summary."
            updateSectionState(targetTab) { it.copy(errorMessage = msg) }
            return
        }

        viewModelScope.launch {
            updateSectionState(targetTab) { it.copy(isAiLoading = true, errorMessage = null) }
            val result = threadSummaryService.summarizeThread(
                messages = messages,
                currentUserId = "me",
                screenshots = screenshots
            )
            updateSectionState(targetTab) { current ->
                result.fold(
                    onSuccess = { data ->
                        current.copy(
                            isAiLoading = false,
                            threadSummaryResult = data,
                            errorMessage = null
                        )
                    },
                    onFailure = { err ->
                        current.copy(
                            isAiLoading = false,
                            errorMessage = err.localizedMessage ?: "Failed to generate Thread Summary."
                        )
                    }
                )
            }
        }
    }

    fun runContextSearch(query: String, targetTab: Int = _uiState.value.activeInputTab) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            updateSectionState(targetTab) { it.copy(errorMessage = "Please enter a search query.") }
            return
        }
        val (messages, screenshots) = getContextForTab(targetTab)
        if (messages.isEmpty() && screenshots.isEmpty()) {
            val msg = if (targetTab == 1) "Please attach a screenshot to search."
            else "Please enter messages or attach a screenshot to search."
            updateSectionState(targetTab) { it.copy(errorMessage = msg) }
            return
        }

        viewModelScope.launch {
            updateSectionState(targetTab) { it.copy(isAiLoading = true, errorMessage = null, searchQuery = trimmed) }
            val result = contextSearchService.searchConversation(
                query = trimmed,
                messages = messages,
                currentUserId = "me",
                screenshots = screenshots
            )
            updateSectionState(targetTab) { current ->
                result.fold(
                    onSuccess = { data ->
                        val validImageIds = screenshots.map { it.id }.toSet()
                        val validMessageIds = messages.map { it.id }.toSet()
                        val validatedImageIds = data.matchingImageIds.filter { validImageIds.contains(it) }
                        val validatedItems = data.items.filter { validMessageIds.contains(it.messageId) }
                        val sanitizedResult = data.copy(
                            items = validatedItems,
                            matchingImageIds = validatedImageIds
                        )
                        current.copy(
                            isAiLoading = false,
                            contextSearchResult = sanitizedResult,
                            errorMessage = null
                        )
                    },
                    onFailure = { err ->
                        current.copy(
                            isAiLoading = false,
                            errorMessage = err.localizedMessage ?: "Search failed."
                        )
                    }
                )
            }
        }
    }

    fun runScamGuard(targetTab: Int = _uiState.value.activeInputTab) {
        val (messages, screenshots) = getContextForTab(targetTab)
        if (messages.isEmpty() && screenshots.isEmpty()) {
            val msg = if (targetTab == 1) "Please attach a screenshot to run Scam Guard."
            else "Please enter messages or attach a screenshot to run Scam Guard."
            updateSectionState(targetTab) { it.copy(errorMessage = msg) }
            return
        }

        viewModelScope.launch {
            updateSectionState(targetTab) { it.copy(isAiLoading = true, errorMessage = null) }
            val result = scamGuardService.analyzeMessages(
                messages = messages,
                currentUserId = "me",
                screenshots = screenshots
            )
            updateSectionState(targetTab) { current ->
                result.fold(
                    onSuccess = { data ->
                        current.copy(
                            isAiLoading = false,
                            scamGuardResult = data,
                            errorMessage = null
                        )
                    },
                    onFailure = { err ->
                        current.copy(
                            isAiLoading = false,
                            errorMessage = err.localizedMessage ?: "Scam assessment failed."
                        )
                    }
                )
            }
        }
    }

    private fun decodeDownscaledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        return runCatching {
            val contentResolver = context.contentResolver
            contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)
                val origW = options.outWidth
                val origH = options.outHeight
                if (origW <= 0 || origH <= 0) return null

                var inSampleSize = 1
                while (origW / inSampleSize > maxDimension * 2 || origH / inSampleSize > maxDimension * 2) {
                    inSampleSize *= 2
                }

                contentResolver.openInputStream(uri)?.use { stream2 ->
                    val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
                    val decoded = BitmapFactory.decodeStream(stream2, null, decodeOptions) ?: return null
                    if (decoded.width <= maxDimension && decoded.height <= maxDimension) {
                        decoded
                    } else {
                        val ratio = decoded.width.toFloat() / decoded.height.toFloat()
                        val newW: Int
                        val newH: Int
                        if (decoded.width > decoded.height) {
                            newW = maxDimension
                            newH = (maxDimension / ratio).toInt().coerceAtLeast(1)
                        } else {
                            newH = maxDimension
                            newW = (maxDimension * ratio).toInt().coerceAtLeast(1)
                        }
                        Bitmap.createScaledBitmap(decoded, newW, newH, true)
                    }
                }
            }
        }.getOrNull()
    }
}
