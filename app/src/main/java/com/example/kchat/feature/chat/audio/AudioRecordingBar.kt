package com.example.kchat.feature.chat.audio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kchat.R
import kotlin.math.sin

/**
 * Custom audio composer bar with dual states matching KChat design:
 * 1. Active Recording state with elapsed duration, live animated waveform, delete, pause, and send.
 * 2. Paused / Review state with play/pause, scrub track, duration, delete, resume, and send.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRecordingBar(
    isPaused: Boolean,
    recordingDurationMs: Long,
    pausedDurationMs: Long,
    currentAmplitude: Float,
    isPlaying: Boolean,
    playPositionMs: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancelOrDelete: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isDark) Color(0xFF141C2A) else MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (!isPaused) {
                // ==========================================
                // 1. ACTIVE RECORDING STATE
                // ==========================================

                // Top Line: Elapsed duration (left) + Animated Waveform Visualizer (center/right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = AudioUtils.formatDuration(recordingDurationMs),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Animated Waveform Bars
                    AnimatedWaveformView(
                        amplitude = currentAmplitude,
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp),
                        barColor = if (isDark) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Line: [ 🗑 Trash ]  ...  [ ⏸ Pause ]  ...  [ ➤ Send ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Red Trash / Delete Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDC2626))
                            .clickable { onCancelOrDelete() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Cancel Recording",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Center Pill Button: [ ⏸ Pause ]
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (isDark) Color(0xFF263246) else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onPause() }
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = null,
                                tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pause",
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Circular Green Send Button: [ ➤ ]
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                            .clickable { onSend() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.send),
                            contentDescription = "Send Voice Message",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                // ==========================================
                // 2. PAUSED / REVIEW STATE
                // ==========================================

                // Top Line: [ ▶/⏸ Play/Pause ] + [ ---●--- Scrub Track ] + [ Duration ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play / Pause Icon Button
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause Preview" else "Play Preview",
                            tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Scrub Track Slider
                    val effectiveMax = if (pausedDurationMs > 0L) pausedDurationMs.toFloat() else 1f
                    val currentPos = playPositionMs.toFloat().coerceIn(0f, effectiveMax)

                    Slider(
                        value = currentPos,
                        onValueChange = { newPos ->
                            onSeekTo(newPos.toLong())
                        },
                        valueRange = 0f..effectiveMax,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF22C55E),
                            activeTrackColor = Color(0xFF22C55E),
                            inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Duration display
                    val displayTimeMs = if (isPlaying && playPositionMs > 0L) playPositionMs else pausedDurationMs
                    Text(
                        text = AudioUtils.formatDuration(displayTimeMs),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Line: [ 🗑 Trash ]  ...  [ 🎙 Resume ]  ...  [ ➤ Send ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Red Trash / Delete Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDC2626))
                            .clickable { onCancelOrDelete() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Voice Note",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Center Pill Button: [ 🎙 Resume ]
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (isDark) Color(0xFF263246) else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onResume() }
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Resume",
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Circular Green Send Button: [ ➤ ]
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                            .clickable { onSend() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.send),
                            contentDescription = "Send Voice Message",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated audio waveform visualizer composed of vertical rounded bars.
 * Bar heights respond to the audio amplitude and phase animation.
 */
@Composable
private fun AnimatedWaveformView(
    amplitude: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 32,
    barColor: Color = Color.White.copy(alpha = 0.85f)
) {
    val phaseAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        phaseAnim.animateTo(
            targetValue = 6.28318f, // 2 * PI
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val baseAmp = amplitude.coerceIn(0.12f, 1f)
        for (i in 0 until barCount) {
            val wave = (sin(phaseAnim.value + i * 0.4) * 0.5 + 0.5).toFloat()
            val heightFraction = (0.2f + 0.8f * wave * baseAmp).coerceIn(0.15f, 1f)
            val barHeight = (heightFraction * 26).dp

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor)
            )
        }
    }
}
