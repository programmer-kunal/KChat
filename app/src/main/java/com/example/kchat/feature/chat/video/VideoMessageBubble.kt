package com.example.kchat.feature.chat.video

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.kchat.R

@Composable
fun VideoMessageBubble(
    videoUrl: String,
    durationMs: Long,
    isCurrentUser: Boolean,
    isInSelectionMode: Boolean,
    videoPlayerHelper: VideoPlayerHelper,
    onBubbleClick: () -> Unit = {},
    onFullScreenClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentPlayingUrl by videoPlayerHelper.currentPlayingUrl.collectAsState()
    val isPlaying by videoPlayerHelper.isPlaying.collectAsState()
    val currentPositionMs by videoPlayerHelper.currentPositionMs.collectAsState()
    val activeDurationMs by videoPlayerHelper.durationMs.collectAsState()
    val isBuffering by videoPlayerHelper.isBuffering.collectAsState()
    val hasError by videoPlayerHelper.hasError.collectAsState()

    val isThisActive = currentPlayingUrl == videoUrl
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

    var thumbnail by remember(videoUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(videoUrl) {
        thumbnail = VideoThumbnailLoader.loadThumbnail(videoUrl)
    }

    val primaryColor = colorResource(id = R.color.light_blue)
    val darkBlueColor = colorResource(id = R.color.dark_blue)

    Box(
        modifier = modifier
            .width(220.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isInSelectionMode) {
                    onBubbleClick()
                } else {
                    videoPlayerHelper.togglePlayPause(videoUrl, durationMs)
                }
            }
    ) {
        if (isThisActive && !hasError) {
            // Active TextureView playback surface
            AndroidView(
                factory = { context ->
                    TextureView(context).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                videoPlayerHelper.attachSurface(Surface(st))
                            }

                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                videoPlayerHelper.detachSurface()
                                return true
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Idle Thumbnail
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = "Video Thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E2633)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = primaryColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        // Center Play/Pause / Buffering Indicator
        if (isBuffering && isThisActive) {
            CircularProgressIndicator(
                color = primaryColor,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center),
                strokeWidth = 3.dp
            )
        } else if (!isThisPlaying) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasError && isThisActive) Icons.Default.ErrorOutline else Icons.Default.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Top Right: Fullscreen Action
        IconButton(
            onClick = {
                if (isInSelectionMode) {
                    onBubbleClick()
                } else {
                    onFullScreenClick(videoUrl)
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "Full screen",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        // Bottom Metadata Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (isThisActive && effectiveTotalDuration > 0L) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = primaryColor,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(3.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayTime = if (isThisActive && currentPositionMs > 0L) {
                    VideoUtils.formatDuration(currentPositionMs)
                } else {
                    VideoUtils.formatDuration(effectiveTotalDuration)
                }
                Text(
                    text = displayTime,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
