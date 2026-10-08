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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
    val isPlaying by audioPlayerHelper.isPlaying.collectAsState()
    val currentPositionMs by audioPlayerHelper.currentPositionMs.collectAsState()
    val activeDurationMs by audioPlayerHelper.durationMs.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var resolvedAudioUrl by remember(audioUrl) {
        mutableStateOf<String?>(if (!VaultMediaManager.isVaultUrl(audioUrl)) audioUrl else VaultMediaManager.getCachedUrlOnly(audioUrl))
    }

    val isThisActive = currentPlayingUrl == audioUrl || (resolvedAudioUrl != null && currentPlayingUrl == resolvedAudioUrl)
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

    val displayTimeText = if (isThisActive && currentPositionMs > 0L) {
        AudioUtils.formatDuration(currentPositionMs)
    } else {
        AudioUtils.formatDuration(effectiveTotalDuration)
    }

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
        // Play / Pause Circle Button
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
                    } else {
                        coroutineScope.launch {
                            if (message?.mediaEncrypted == true && channelId != null) {
                                val decFile = com.example.kchat.crypto.EncryptedMediaManager.getOrDecryptAudioFile(context, channelId, message)
                                if (decFile != null) {
                                    audioPlayerHelper.togglePlayPause(decFile.absolutePath, durationMs, cacheKey = audioUrl)
                                }
                            } else {
                                val urlToPlay = resolvedAudioUrl ?: VaultMediaManager.resolveMediaUrl(audioUrl)
                                resolvedAudioUrl = urlToPlay
                                if (urlToPlay != null) {
                                    audioPlayerHelper.togglePlayPause(urlToPlay, durationMs, cacheKey = audioUrl)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isThisPlaying) "Pause voice message" else "Play voice message",
                tint = playButtonIconTint,
                modifier = Modifier.size(20.dp)
            )
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
