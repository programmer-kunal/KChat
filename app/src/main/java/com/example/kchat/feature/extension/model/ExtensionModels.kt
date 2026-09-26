package com.example.kchat.feature.extension.model

import android.graphics.Bitmap
import android.net.Uri
import com.example.kchat.model.Message

/**
 * Temporary, in-memory representation of an imported screenshot within a KChat Extension session.
 * Never uploaded or persisted to Firebase, Supabase, or RTDB.
 */
data class ExtensionScreenshot(
    val id: String, // e.g. "image_001", "image_002"
    val uri: Uri,
    val bitmap: Bitmap? = null,
    val fileName: String = ""
)

/**
 * Result returned by [com.example.kchat.feature.extension.parser.ExternalMessageParser].
 */
data class ParsedConversation(
    val messages: List<Message> = emptyList(),
    val participants: List<String> = emptyList(),
    val formatDescription: String = "Text imported",
    val isFormatDetected: Boolean = false,
    val rawText: String = ""
)

/**
 * Available AI intelligence modes in KChat Extension workbench.
 */
enum class ExtensionAiFeature(val displayName: String, val subtitle: String) {
    SMART_REPLY("Smart Reply", "Context-aware reply suggestions"),
    THREAD_SUMMARY("Thread Summary", "Key conversation highlights"),
    CONTEXT_SEARCH("Context Search", "Ask questions over context"),
    SCAM_GUARD("Scam Guard", "Detect fraud & phishing risks")
}

/**
 * Complete in-memory state of an active KChat Extension session.
 * All temporary IDs and loaded data are erased when the user clears or exits.
 */
data class ExtensionSessionState(
    val pastedText: String = "",
    val parsedMessages: List<Message> = emptyList(),
    val screenshots: List<ExtensionScreenshot> = emptyList(),
    val participants: List<String> = emptyList(),
    val isFormatDetected: Boolean = false,
    val statusMessage: String = "",
    val temporaryMessageMap: Map<String, Message> = emptyMap(),
    val temporaryScreenshotMap: Map<String, ExtensionScreenshot> = emptyMap()
) {
    val hasContent: Boolean
        get() = parsedMessages.isNotEmpty() || screenshots.isNotEmpty() || pastedText.isNotBlank()

    val totalItemCount: Int
        get() = parsedMessages.size + screenshots.size
}

/**
 * Section-specific AI result and execution state for section isolation.
 */
data class ExtensionSectionResult(
    val selectedFeature: ExtensionAiFeature? = null,
    val isAiLoading: Boolean = false,
    val searchQuery: String = "",
    val smartReplyResult: com.example.kchat.feature.smartreply.SmartReplyResult? = null,
    val threadSummaryResult: com.example.kchat.feature.threadsummary.ThreadSummaryResult? = null,
    val contextSearchResult: com.example.kchat.feature.contextsearch.ContextSearchResult? = null,
    val scamGuardResult: com.example.kchat.feature.scamguard.ScamGuardResult? = null,
    val errorMessage: String? = null
) {
    val hasResult: Boolean
        get() = smartReplyResult != null ||
                threadSummaryResult != null ||
                contextSearchResult != null ||
                scamGuardResult != null
}

/**
 * UI State container for KChat Extension screen with isolated sections.
 */
data class ExtensionUiState(
    val activeInputTab: Int = 0,

    // Section 0: Paste Messages
    val pastedText: String = "",
    val parsedConversation: ParsedConversation? = null,
    val pasteSectionState: ExtensionSectionResult = ExtensionSectionResult(),

    // Section 1: Screenshots
    val screenshots: List<ExtensionScreenshot> = emptyList(),
    val screenshotsSectionState: ExtensionSectionResult = ExtensionSectionResult(),

    // Section 2: Import Text
    val importedText: String = "",
    val importedParsedConversation: ParsedConversation? = null,
    val importSectionState: ExtensionSectionResult = ExtensionSectionResult(),

    // Backward-compatibility properties
    val rawInputText: String = "",
    val selectedFeature: ExtensionAiFeature? = null,
    val isAiLoading: Boolean = false,
    val searchQuery: String = "",
    val smartReplyResult: com.example.kchat.feature.smartreply.SmartReplyResult? = null,
    val threadSummaryResult: com.example.kchat.feature.threadsummary.ThreadSummaryResult? = null,
    val contextSearchResult: com.example.kchat.feature.contextsearch.ContextSearchResult? = null,
    val scamGuardResult: com.example.kchat.feature.scamguard.ScamGuardResult? = null,
    val errorMessage: String? = null
) {
    val hasPasteInput: Boolean
        get() = pastedText.isNotBlank()

    val hasScreenshotInput: Boolean
        get() = screenshots.isNotEmpty()

    val hasImportInput: Boolean
        get() = importedText.isNotBlank()

    val activeInputSectionsCount: Int
        get() = (if (hasPasteInput) 1 else 0) +
                (if (hasScreenshotInput) 1 else 0) +
                (if (hasImportInput) 1 else 0)

    val showAllClearButton: Boolean
        get() = activeInputSectionsCount >= 2

    val showClearButton: Boolean
        get() = activeInputSectionsCount >= 1 || hasContentToClear

    val hasContentToClear: Boolean
        get() = pastedText.isNotBlank() ||
                rawInputText.isNotBlank() ||
                screenshots.isNotEmpty() ||
                importedText.isNotBlank() ||
                parsedConversation != null ||
                importedParsedConversation != null ||
                pasteSectionState.hasResult ||
                screenshotsSectionState.hasResult ||
                importSectionState.hasResult

    fun getSectionState(tabIndex: Int): ExtensionSectionResult {
        return when (tabIndex) {
            0 -> pasteSectionState
            1 -> screenshotsSectionState
            2 -> importSectionState
            else -> ExtensionSectionResult()
        }
    }
}
