package com.example.kchat.feature.extension

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.kchat.R
import com.example.kchat.feature.contextsearch.ContextSearchResult
import com.example.kchat.feature.extension.model.ExtensionAiFeature
import com.example.kchat.feature.extension.model.ExtensionScreenshot
import com.example.kchat.feature.extension.model.ExtensionSectionResult
import com.example.kchat.feature.scamguard.ScamGuardResult
import com.example.kchat.feature.smartreply.SmartReplyResult
import com.example.kchat.feature.threadsummary.ThreadSummaryResult

private val KChatNavy = Color(0xFF0F172A)
private val KChatCardBg = Color(0xFF1E293B)
private val KChatAccent = Color(0xFF38BDF8)
private val KChatSurface = Color(0xFF334155)
private val KChatTextSecondary = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KChatExtensionScreen(
    navController: NavController,
    viewModel: KChatExtensionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var previewScreenshot by remember { mutableStateOf<ExtensionScreenshot?>(null) }

    val screenshotPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            viewModel.addScreenshotUri(context, uri)
        }
    }

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_kchat_extension),
                            contentDescription = null,
                            tint = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "KChat Extension",
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (uiState.showClearButton) {
                            TextButton(
                                onClick = {
                                    val clearedTabName = when (uiState.activeInputTab) {
                                        0 -> "Paste Messages"
                                        1 -> "Screenshots"
                                        2 -> "Import Text"
                                        else -> "Section"
                                    }
                                    viewModel.clearCurrentSection()
                                    Toast.makeText(context, "$clearedTabName cleared", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Clear",
                                    color = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        if (uiState.showAllClearButton) {
                            TextButton(
                                onClick = {
                                    viewModel.clearAllSections()
                                    Toast.makeText(context, "All sections cleared", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "All Clear",
                                    color = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) KChatNavy else MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = if (isDark) KChatNavy else MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Input Tabs
            TabRow(
                selectedTabIndex = uiState.activeInputTab,
                containerColor = if (isDark) KChatCardBg else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
            ) {
                Tab(
                    selected = uiState.activeInputTab == 0,
                    onClick = { viewModel.setActiveInputTab(0) },
                    text = {
                        Text(
                            "Paste Messages",
                            fontSize = 12.sp,
                            fontWeight = if (uiState.activeInputTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (uiState.activeInputTab == 0) {
                                if (isDark) KChatAccent else MaterialTheme.colorScheme.primary
                            } else {
                                if (isDark) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                )
                Tab(
                    selected = uiState.activeInputTab == 1,
                    onClick = { viewModel.setActiveInputTab(1) },
                    text = {
                        Text(
                            "Screenshots",
                            fontSize = 12.sp,
                            fontWeight = if (uiState.activeInputTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (uiState.activeInputTab == 1) {
                                if (isDark) KChatAccent else MaterialTheme.colorScheme.primary
                            } else {
                                if (isDark) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                )
                Tab(
                    selected = uiState.activeInputTab == 2,
                    onClick = { viewModel.setActiveInputTab(2) },
                    text = {
                        Text(
                            "Import Text",
                            fontSize = 12.sp,
                            fontWeight = if (uiState.activeInputTab == 2) FontWeight.Bold else FontWeight.Medium,
                            color = if (uiState.activeInputTab == 2) {
                                if (isDark) KChatAccent else MaterialTheme.colorScheme.primary
                            } else {
                                if (isDark) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                )
            }

            // Tab Content - Section Isolated
            when (uiState.activeInputTab) {
                0 -> {
                    // SECTION A: PASTE MESSAGES
                    OutlinedTextField(
                        value = uiState.pastedText,
                        onValueChange = { viewModel.updatePastedText(it) },
                        placeholder = {
                            Text(
                                "Paste WhatsApp, Telegram, or conversation messages here...\n\nExample:\n[12/04, 10:15] Alice: Hey, are we meeting?\n[12/04, 10:16] Bob: Yes, at 3 PM at Central Park!",
                                color = if (isDark) KChatTextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isDark) KChatCardBg else MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = if (isDark) KChatCardBg else MaterialTheme.colorScheme.surface,
                            focusedBorderColor = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (isDark) KChatSurface else MaterialTheme.colorScheme.outline,
                            focusedTextColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    )

                    // Responsive Parsed Conversation Status - ONLY in Paste Messages Section!
                    uiState.parsedConversation?.let { parsed ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E293B) else MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) KChatSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${parsed.messages.size} msgs parsed · ${parsed.participants.size} participant${if (parsed.participants.size != 1) "s" else ""}",
                                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        softWrap = true
                                    )
                                }
                                Text(
                                    text = parsed.formatDescription,
                                    color = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    softWrap = true,
                                    modifier = Modifier.padding(start = 24.dp)
                                )
                            }
                        }
                    }

                    // Section AI Features & Results
                    ExtensionSectionContent(
                        sectionState = uiState.pasteSectionState,
                        onSelectFeature = { viewModel.selectFeature(it, targetTab = 0) },
                        onSearchQueryChange = { viewModel.setSearchQuery(it, targetTab = 0) },
                        onRunSearch = { viewModel.runContextSearch(it, targetTab = 0) },
                        context = context
                    )
                }

                1 -> {
                    // SECTION B: SCREENSHOTS
                    if (uiState.screenshots.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isDark) KChatCardBg else MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                                .border(1.dp, if (isDark) KChatSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Attach Conversation Screenshots",
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Supports chat screenshots from any app. Gemini AI reads visible dialogue and text directly from the images.",
                                color = if (isDark) KChatTextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { screenshotPickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, tint = if (isDark) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select Screenshot(s)", color = if (isDark) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    } else {
                        // Attached Screenshots Preview
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${uiState.screenshots.size} screenshot${if (uiState.screenshots.size > 1) "s" else ""} attached",
                                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(
                                    onClick = { screenshotPickerLauncher.launch("image/*") },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("+ Add More", color = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                }
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(uiState.screenshots, key = { it.id }) { sc ->
                                    ScreenshotThumbnail(
                                        screenshot = sc,
                                        onClick = { previewScreenshot = sc },
                                        onRemove = { viewModel.removeScreenshot(sc.id) }
                                    )
                                }
                            }
                        }
                    }

                    // Section AI Features & Results
                    ExtensionSectionContent(
                        sectionState = uiState.screenshotsSectionState,
                        onSelectFeature = { viewModel.selectFeature(it, targetTab = 1) },
                        onSearchQueryChange = { viewModel.setSearchQuery(it, targetTab = 1) },
                        onRunSearch = { viewModel.runContextSearch(it, targetTab = 1) },
                        context = context
                    )
                }

                2 -> {
                    // SECTION C: IMPORT TEXT
                    OutlinedTextField(
                        value = uiState.importedText,
                        onValueChange = { viewModel.updateImportedText(it) },
                        placeholder = {
                            Text(
                                "Paste any raw email, notes, or message context...",
                                color = if (isDark) KChatTextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isDark) KChatCardBg else MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = if (isDark) KChatCardBg else MaterialTheme.colorScheme.surface,
                            focusedBorderColor = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (isDark) KChatSurface else MaterialTheme.colorScheme.outline,
                            focusedTextColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    )

                    // Section AI Features & Results
                    ExtensionSectionContent(
                        sectionState = uiState.importSectionState,
                        onSelectFeature = { viewModel.selectFeature(it, targetTab = 2) },
                        onSearchQueryChange = { viewModel.setSearchQuery(it, targetTab = 2) },
                        onRunSearch = { viewModel.runContextSearch(it, targetTab = 2) },
                        context = context
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Full-Screen / Large Screenshot Preview Dialog
    if (previewScreenshot != null) {
        ScreenshotPreviewDialog(
            screenshot = previewScreenshot!!,
            onDismiss = { previewScreenshot = null }
        )
    }
}

@Composable
private fun ExtensionSectionContent(
    sectionState: ExtensionSectionResult,
    onSelectFeature: (ExtensionAiFeature) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRunSearch: (String) -> Unit,
    context: Context
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // AI Features Grid Header
        Text(
            text = "Choose KChat AI Feature",
            color = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        // 2x2 Feature Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FeatureCard(
                title = ExtensionAiFeature.SMART_REPLY.displayName,
                description = "Tone, sentiment, intent & reply suggestions",
                icon = Icons.Default.AutoAwesome,
                isSelected = sectionState.selectedFeature == ExtensionAiFeature.SMART_REPLY,
                modifier = Modifier.weight(1f),
                onClick = { onSelectFeature(ExtensionAiFeature.SMART_REPLY) }
            )
            FeatureCard(
                title = ExtensionAiFeature.THREAD_SUMMARY.displayName,
                description = "Key points, decisions, tasks & deadlines",
                icon = Icons.Default.Description,
                isSelected = sectionState.selectedFeature == ExtensionAiFeature.THREAD_SUMMARY,
                modifier = Modifier.weight(1f),
                onClick = { onSelectFeature(ExtensionAiFeature.THREAD_SUMMARY) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FeatureCard(
                title = ExtensionAiFeature.CONTEXT_SEARCH.displayName,
                description = "Semantic search across external dialogue",
                icon = Icons.Default.Search,
                isSelected = sectionState.selectedFeature == ExtensionAiFeature.CONTEXT_SEARCH,
                modifier = Modifier.weight(1f),
                onClick = { onSelectFeature(ExtensionAiFeature.CONTEXT_SEARCH) }
            )
            FeatureCard(
                title = ExtensionAiFeature.SCAM_GUARD.displayName,
                description = "Phishing, fraud & scam risk detection",
                icon = Icons.Default.Security,
                isSelected = sectionState.selectedFeature == ExtensionAiFeature.SCAM_GUARD,
                modifier = Modifier.weight(1f),
                onClick = { onSelectFeature(ExtensionAiFeature.SCAM_GUARD) }
            )
        }

        // Context Search Query Bar (Only if Context Search selected)
        if (sectionState.selectedFeature == ExtensionAiFeature.CONTEXT_SEARCH) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = sectionState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search question or keyword...", color = if (isDark) KChatTextSecondary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = if (isDark) KChatCardBg else MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = if (isDark) KChatCardBg else MaterialTheme.colorScheme.surface,
                        focusedBorderColor = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (isDark) KChatSurface else MaterialTheme.colorScheme.outline,
                        focusedTextColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                )
                Button(
                    onClick = { onRunSearch(sectionState.searchQuery) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Search", color = if (isDark) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Section Error Message Banner
        sectionState.errorMessage?.let { error ->
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF7F1D1D) else Color(0xFFFEE2E2)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626))
                    Text(text = error, color = if (isDark) Color.White else Color(0xFF991B1B), fontSize = 12.sp)
                }
            }
        }

        // Section AI Loading Spinner
        if (sectionState.isAiLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Text(
                        text = "Analyzing external content with KChat AI...",
                        color = if (isDark) KChatTextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Section AI Results Area
        when (sectionState.selectedFeature) {
            ExtensionAiFeature.SMART_REPLY -> {
                sectionState.smartReplyResult?.let { result ->
                    SmartReplyResultView(result = result, context = context)
                }
            }
            ExtensionAiFeature.THREAD_SUMMARY -> {
                sectionState.threadSummaryResult?.let { result ->
                    ThreadSummaryResultView(result = result, context = context)
                }
            }
            ExtensionAiFeature.CONTEXT_SEARCH -> {
                sectionState.contextSearchResult?.let { result ->
                    ContextSearchResultView(result = result, context = context)
                }
            }
            ExtensionAiFeature.SCAM_GUARD -> {
                sectionState.scamGuardResult?.let { result ->
                    ScamGuardResultView(result = result, context = context)
                }
            }
            null -> {
                // No feature selected yet
            }
        }
    }
}

@Composable
private fun ScreenshotThumbnail(
    screenshot: ExtensionScreenshot,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
    Box(
        modifier = Modifier
            .width(88.dp)
            .height(110.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isDark) KChatCardBg else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (isDark) KChatSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        if (screenshot.bitmap != null) {
            Image(
                bitmap = screenshot.bitmap.asImageBitmap(),
                contentDescription = screenshot.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Image, contentDescription = null, tint = if (isDark) KChatTextSecondary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ID Badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(topEnd = 6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(text = screenshot.id, color = if (isDark) KChatAccent else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }

        // Close/Remove Button
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun ScreenshotPreviewDialog(
    screenshot: ExtensionScreenshot,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            // Main Image Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                if (screenshot.bitmap != null) {
                    Image(
                        bitmap = screenshot.bitmap.asImageBitmap(),
                        contentDescription = screenshot.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    offset = if (scale > 1f) {
                                        offset + pan
                                    } else {
                                        Offset.Zero
                                    }
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                } else {
                    Text("Image unavailable", color = Color.White)
                }
            }

            // Top Header: ID badge, filename & Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(KChatAccent, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = screenshot.id,
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (screenshot.fileName.isNotBlank()) {
                        Text(
                            text = screenshot.fileName,
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 220.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Preview",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Bottom hint
            Text(
                text = "Pinch to zoom · Tap outside to close",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            )
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                if (isDark) Color(0xFF0369A1).copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                if (isDark) KChatCardBg else MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(10.dp),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, if (isDark) KChatAccent else MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, if (isDark) KChatSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        },
        modifier = modifier.height(115.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) {
                    if (isDark) KChatAccent else MaterialTheme.colorScheme.primary
                } else {
                    if (isDark) Color.White else MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = if (isDark) KChatTextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

// ---------------- RESULT VIEWS ----------------

@Composable
private fun SmartReplyResultView(result: SmartReplyResult, context: Context) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
    ResultCard(
        title = "Smart Reply Results",
        onCopy = {
            val text = buildString {
                appendLine("Smart Reply Analysis:")
                appendLine("• Tone: ${result.tone}")
                appendLine("• Sentiment: ${result.sentiment}")
                appendLine("• Inferred Intent: ${result.intent}")
                appendLine("• Urgency: ${result.urgency}")
                appendLine("\nSuggested Replies:")
                result.suggestions.forEachIndexed { i, s -> appendLine("${i + 1}. $s") }
            }
            copyToClipboard(context, text)
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (result.tone.isNotBlank()) {
                    ResultBadge(label = "Tone", value = result.tone)
                }
                if (result.sentiment.isNotBlank()) {
                    ResultBadge(label = "Sentiment", value = result.sentiment)
                }
                if (result.urgency.isNotBlank()) {
                    ResultBadge(label = "Urgency", value = result.urgency)
                }
                if (result.intent.isNotBlank()) {
                    ResultBadge(label = "Intent", value = result.intent)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Suggested Replies (Tap to copy individual):",
                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            result.suggestions.forEach { reply ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDark) KChatSurface else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .clickable { copyToClipboard(context, reply) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = reply,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadSummaryResultView(result: ThreadSummaryResult, context: Context) {
    ResultCard(
        title = "Thread Summary Results",
        onCopy = {
            val text = buildString {
                appendLine("Thread Summary:")
                if (result.keyPoints.isNotEmpty()) {
                    appendLine("\nKey Points:")
                    result.keyPoints.forEach { appendLine("• $it") }
                }
                if (result.decisions.isNotEmpty()) {
                    appendLine("\nDecisions:")
                    result.decisions.forEach { appendLine("• $it") }
                }
                if (result.tasks.isNotEmpty()) {
                    appendLine("\nTasks / Action Items:")
                    result.tasks.forEach { appendLine("• $it") }
                }
                if (result.deadlines.isNotEmpty()) {
                    appendLine("\nDeadlines:")
                    result.deadlines.forEach { appendLine("• $it") }
                }
                if (result.importantDetails.isNotEmpty()) {
                    appendLine("\nImportant Details:")
                    result.importantDetails.forEach { appendLine("• $it") }
                }
            }
            copyToClipboard(context, text)
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (result.keyPoints.isNotEmpty()) {
                SummarySection(title = "Key Points", items = result.keyPoints)
            }
            if (result.decisions.isNotEmpty()) {
                SummarySection(title = "Decisions", items = result.decisions)
            }
            if (result.tasks.isNotEmpty()) {
                SummarySection(title = "Tasks & Action Items", items = result.tasks)
            }
            if (result.deadlines.isNotEmpty()) {
                SummarySection(title = "Deadlines", items = result.deadlines)
            }
            if (result.importantDetails.isNotEmpty()) {
                SummarySection(title = "Important Details", items = result.importantDetails)
            }
        }
    }
}

@Composable
private fun ContextSearchResultView(result: ContextSearchResult, context: Context) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
    ResultCard(
        title = "Context Search Results",
        onCopy = {
            copyToClipboard(context, result.toFormattedText())
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (result.answer.isNotBlank()) {
                Text(
                    "Answer:",
                    color = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = result.answer,
                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp
                )
            }

            if (result.matchingImageIds.isNotEmpty()) {
                Text(
                    "Matching Screenshots:",
                    color = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    result.matchingImageIds.forEach { imgId ->
                        Box(
                            modifier = Modifier
                                .background(if (isDark) KChatSurface else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = imgId,
                                color = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (result.items.isNotEmpty()) {
                Text(
                    "Matching Messages (${result.items.size}):",
                    color = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                result.items.forEach { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isDark) KChatSurface else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "${item.senderName.ifBlank { "User" }}: ${item.messageText}",
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScamGuardResultView(result: ScamGuardResult, context: Context) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
    val riskColor = when (result.riskLevel) {
        com.example.kchat.feature.scamguard.ScamRiskLevel.HIGH -> Color(0xFFEF4444)
        com.example.kchat.feature.scamguard.ScamRiskLevel.MEDIUM -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    ResultCard(
        title = "Scam Guard Assessment",
        onCopy = {
            copyToClipboard(context, result.toFormattedText())
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(riskColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .border(1.dp, riskColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "RISK: ${result.riskLevel.name}",
                        color = riskColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = if (result.isSuspicious) "Deceptive indicators detected" else "No significant risk detected",
                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp
                )
            }

            Text(
                text = result.summary,
                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            )

            if (result.indicators.isNotEmpty()) {
                SummarySection(title = "Detected Indicators", items = result.indicators)
            }
            if (result.recommendedActions.isNotEmpty()) {
                SummarySection(title = "Recommended Actions", items = result.recommendedActions)
            }
        }
    }
}

@Composable
private fun SummarySection(title: String, items: List<String>) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            color = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                Text(
                    text = "•",
                    color = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
                Text(
                    text = item,
                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun ResultBadge(label: String, value: String) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
    Box(
        modifier = Modifier
            .background(if (isDark) KChatSurface else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = "$label: $value",
            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            softWrap = true
        )
    }
}

@Composable
private fun ResultCard(
    title: String,
    onCopy: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) KChatCardBg else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDark) KChatSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onCopy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) KChatAccent else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = if (isDark) Color.Black else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Copy Result",
                        color = if (isDark) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
            HorizontalDivider(
                color = if (isDark) KChatSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
            content()
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("KChat AI Result", text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "Copied AI result to clipboard! Ready to paste into external app.", Toast.LENGTH_SHORT).show()
}
