package com.example.kchat.feature.chat.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import com.example.kchat.VaultMediaManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kchat.R

/**
 * Modern voice message bubble for KChat.
 * Displays Play/Pause, interactive progress slider, audio duration, and a mic badge.
 * Integrates directly with [AudioPlayerHelper] for single-instance playback.
 */
@Composable
fun AudioMessageBubble(
    audioUrl: String,
    durationMs: Long,
    isCurrentUser: Boolean,
    isInSelectionMode: Boolean,
    audioPlayerHelper: AudioPlayerHelper,
    onBubbleClick: () -> Unit = {},
    message: com.example.kchat.model.Message? = null,
    channelId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentPlayingUrl by audioPlayerHelper.currentPlayingUrl.collectAsState()
    val currentCacheKey by audioPlayerHelper.currentCacheKey.collectAsState()
    val isPlaying by audioPlayerHelper.isPlaying.collectAsState()
    val currentPositionMs by audioPlayerHelper.currentPositionMs.collectAsState()
    val activeDurationMs by audioPlayerHelper.durationMs.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val messageKey = message?.id?.ifBlank { audioUrl } ?: audioUrl
    var isDownloaded by remember(messageKey, currentUid) {
        mutableStateOf(isCurrentUser || com.example.kchat.data.MediaDownloadTracker.isDownloaded(context, currentUid, messageKey, "audio"))
    }
    var isDownloading by remember { mutableStateOf(false) }

    var resolvedAudioUrl by remember(audioUrl) {
        mutableStateOf<String?>(if (!VaultMediaManager.isVaultUrl(audioUrl)) audioUrl else VaultMediaManager.getCachedUrlOnly(audioUrl))
    }
    var localDecryptedPath by remember(message?.id) { mutableStateOf<String?>(null) }

    val isThisActive = currentPlayingUrl == audioUrl ||
        (resolvedAudioUrl != null && currentPlayingUrl == resolvedAudioUrl) ||
        (currentCacheKey != null && currentCacheKey == audioUrl) ||
        (localDecryptedPath != null && currentPlayingUrl == localDecryptedPath)
    val isThisPlaying = isThisActive && isPlaying

    val effectiveTotalDuration = if (isThisActive && activeDurationMs > 0L) {
        activeDurationMs
    } else if (durationMs > 0L) {
        durationMs
    } else {
        0L
    }

    val progress = if (isThisActive && effectiveTotalDuration > 0L) {
        (currentPositionMs.toFloat() / effectiveTotalDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val currentFormatted = AudioUtils.formatDuration(if (isThisActive) currentPositionMs else 0L)
    val totalFormatted = AudioUtils.formatDuration(effectiveTotalDuration)
    val displayTimeText = "$currentFormatted / $totalFormatted"

    // Color theme matching KChat's design system
    val isDark = MaterialTheme.colorScheme.background == com.example.kchat.ui.theme.KChatDarkBackground
    val primaryColor = MaterialTheme.colorScheme.primary

    val playButtonBg = if (isCurrentUser) Color.White else primaryColor
    val playButtonIconTint = if (isCurrentUser) primaryColor else MaterialTheme.colorScheme.onPrimary

    val activeTrackColor = if (isCurrentUser) Color.White else primaryColor
    val inactiveTrackColor = if (isCurrentUser) Color.White.copy(alpha = 0.35f) else (if (isDark) Color.Gray.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    val thumbColor = if (isCurrentUser) Color.White else primaryColor

    val timerTextColor = if (isCurrentUser) Color.White.copy(alpha = 0.9f) else (MaterialTheme.colorScheme.onSurfaceVariant)
    val micBadgeTint = if (isCurrentUser) Color.White.copy(alpha = 0.75f) else primaryColor

    Row(
        modifier = modifier
            .widthIn(min = 190.dp, max = 215.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play / Pause / Download Circle Button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(playButtonBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (isInSelectionMode) {
                        onBubbleClick()
                    } else if (!isCurrentUser && !isDownloaded) {
                        if (!isDownloading) {
                            coroutineScope.launch {
                                isDownloading = true
                                try {
                                    val decFile: java.io.File? = if (message?.mediaEncrypted == true && channelId != null) {
                                        com.example.kchat.crypto.EncryptedMediaManager.getOrDecryptAudioFile(context, channelId, message)
                                    } else {
                                        val resolved = VaultMediaManager.resolveMediaUrl(audioUrl) ?: audioUrl
                                        val tempFile = java.io.File(context.noBackupFilesDir, "temp_dec_audio_${messageKey}.m4a")
                                        if (tempFile.exists() && tempFile.length() > 0L) {
                                            tempFile
                                        } else {
                                            try {
                                                val conn = java.net.URL(resolved).openConnection()
                                                conn.connectTimeout = 15000
                                                conn.readTimeout = 30000
                                                conn.getInputStream().use { input ->
                                                    java.io.FileOutputStream(tempFile).use { output ->
                                                        input.copyTo(output)
                                                    }
                                                }
                                                tempFile
                                            } catch (e: Exception) {
                                                tempFile.delete()
                                                null
                                            }
                                        }
                                    }

                                    if (decFile != null && decFile.exists() && decFile.length() > 0L) {
                                        localDecryptedPath = decFile.absolutePath
                                        com.example.kchat.data.MediaDownloadTracker.setDownloaded(context, currentUid, messageKey, "audio", true)
                                        isDownloaded = true
                                    } else {
                                        android.widget.Toast.makeText(context, "Download failed, tap to retry", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                } finally {
                                    isDownloading = false
                                }
                            }
                        }
                    } else if (isThisPlaying) {
                        audioPlayerHelper.pause()
                    } else if (isThisActive) {
                        audioPlayerHelper.resume()
                    } else {
                        coroutineScope.launch {
                            if (message?.mediaEncrypted == true && channelId != null) {
                                val decFile = com.example.kchat.crypto.EncryptedMediaManager.getOrDecryptAudioFile(context, channelId, message)
                                if (decFile != null) {
                                    localDecryptedPath = decFile.absolutePath
                                    audioPlayerHelper.play(decFile.absolutePath, durationMs, cacheKey = audioUrl)
                                }
                            } else {
                                val urlToPlay = resolvedAudioUrl ?: VaultMediaManager.resolveMediaUrl(audioUrl)
                                resolvedAudioUrl = urlToPlay
                                if (urlToPlay != null) {
                                    audioPlayerHelper.play(urlToPlay, durationMs, cacheKey = audioUrl)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (!isCurrentUser && !isDownloaded) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        color = playButtonIconTint,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download voice message",
                        tint = playButtonIconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isThisPlaying) "Pause voice message" else "Play voice message",
                    tint = playButtonIconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Slider and Time / Mic metadata
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Slider(
                value = progress,
                onValueChange = { newProgress ->
                    if (!isInSelectionMode && effectiveTotalDuration > 0L) {
                        val seekTarget = (newProgress * effectiveTotalDuration).toLong()
                        if (isThisActive) {
                            audioPlayerHelper.seekTo(seekTarget)
                        } else {
                            coroutineScope.launch {
                                if (message?.mediaEncrypted == true && channelId != null) {
                                    val decFile = com.example.kchat.crypto.EncryptedMediaManager.getOrDecryptAudioFile(context, channelId, message)
                                    if (decFile != null) {
                                        localDecryptedPath = decFile.absolutePath
                                        audioPlayerHelper.play(decFile.absolutePath, durationMs, cacheKey = audioUrl)
                                        audioPlayerHelper.seekTo(seekTarget)
                                    }
                                } else {
                                    val urlToPlay = resolvedAudioUrl ?: VaultMediaManager.resolveMediaUrl(audioUrl)
                                    resolvedAudioUrl = urlToPlay
                                    if (urlToPlay != null) {
                                        audioPlayerHelper.play(urlToPlay, durationMs, cacheKey = audioUrl)
                                        audioPlayerHelper.seekTo(seekTarget)
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = !isInSelectionMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = thumbColor,
                    activeTrackColor = activeTrackColor,
                    inactiveTrackColor = inactiveTrackColor,
                    disabledThumbColor = thumbColor.copy(alpha = 0.6f),
                    disabledActiveTrackColor = activeTrackColor.copy(alpha = 0.6f),
                    disabledInactiveTrackColor = inactiveTrackColor.copy(alpha = 0.6f)
                )
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayTimeText,
                    color = timerTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice note",
                    tint = micBadgeTint,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
