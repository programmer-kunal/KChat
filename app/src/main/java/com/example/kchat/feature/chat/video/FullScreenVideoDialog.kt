package com.example.kchat.feature.chat.video

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
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
import androidx.compose.foundation.shape.CircleShape
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.kchat.R

@Composable
fun FullScreenVideoDialog(
    videoUrl: String,
    videoPlayerHelper: VideoPlayerHelper,
    onDismiss: () -> Unit,
    message: com.example.kchat.model.Message? = null,
    channelId: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var resolvedVideoUrl by remember(videoUrl) {
        mutableStateOf<String?>(if (!VaultMediaManager.isVaultUrl(videoUrl)) videoUrl else VaultMediaManager.getCachedUrlOnly(videoUrl))
    }

    val isPlaying by videoPlayerHelper.isPlaying.collectAsState()
    val currentPositionMs by videoPlayerHelper.currentPositionMs.collectAsState()
    val durationMs by videoPlayerHelper.durationMs.collectAsState()
    val isBuffering by videoPlayerHelper.isBuffering.collectAsState()

    val progress = if (durationMs > 0L) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    val targetDuration = message?.videoDurationMs?.takeIf { it > 0L } ?: durationMs

    Dialog(
        onDismissRequest = {
            videoPlayerHelper.stop()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { context ->
                    TextureView(context).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                videoPlayerHelper.attachSurface(Surface(st))
                                coroutineScope.launch {
                                    if (message?.mediaEncrypted == true && channelId != null) {
                                        val decFile = com.example.kchat.crypto.EncryptedMediaManager.getOrDecryptVideoFile(context, channelId, message)
                                        if (decFile != null) {
                                            videoPlayerHelper.play(decFile.absolutePath, targetDuration)
                                        }
                                    } else {
                                        val urlToPlay = resolvedVideoUrl ?: VaultMediaManager.resolveMediaUrl(videoUrl) ?: videoUrl
                                        resolvedVideoUrl = urlToPlay
                                        videoPlayerHelper.play(urlToPlay, targetDuration)
                                    }
                                }
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
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        coroutineScope.launch {
                            if (message?.mediaEncrypted == true && channelId != null) {
                                val decFile = com.example.kchat.crypto.EncryptedMediaManager.getOrDecryptVideoFile(context, channelId, message)
                                if (decFile != null) {
                                    videoPlayerHelper.togglePlayPause(decFile.absolutePath, durationMs)
                                }
                            } else {
                                val urlToPlay = resolvedVideoUrl ?: VaultMediaManager.resolveMediaUrl(videoUrl) ?: videoUrl
                                resolvedVideoUrl = urlToPlay
                                videoPlayerHelper.togglePlayPause(urlToPlay, durationMs)
                            }
                        }
                    }
            )

            // Close button (Top-Left)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            // Download button (Top-Right)
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        saveVideoToMovies(context, videoUrl, message, channelId)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download Video",
                    tint = Color.White
                )
            }

            // Center Buffering / Play indicator
            if (isBuffering) {
                CircularProgressIndicator(
                    color = primaryColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Bottom Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Slider(
                    value = progress,
                    onValueChange = { newProgress ->
                        if (durationMs > 0L) {
                            videoPlayerHelper.seekTo((newProgress * durationMs).toLong())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = primaryColor,
                        activeTrackColor = primaryColor,
                        inactiveTrackColor = Color.Gray
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { videoPlayerHelper.togglePlayPause(videoUrl, durationMs) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = "${VideoUtils.formatDuration(currentPositionMs)} / ${VideoUtils.formatDuration(durationMs)}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private suspend fun saveVideoToMovies(
    context: android.content.Context,
    videoUrl: String,
    message: com.example.kchat.model.Message?,
    channelId: String?
) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val sourceFile: java.io.File? = if (message?.mediaEncrypted == true && channelId != null) {
                com.example.kchat.crypto.EncryptedMediaManager.getOrDecryptVideoFile(context, channelId, message)
            } else {
                val resolved = VaultMediaManager.resolveMediaUrl(videoUrl) ?: videoUrl
                val tempDownload = java.io.File(context.cacheDir, "temp_download_vid_${System.currentTimeMillis()}.mp4")
                try {
                    val conn = java.net.URL(resolved).openConnection()
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.getInputStream().use { input ->
                        java.io.FileOutputStream(tempDownload).use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempDownload
                } catch (e: Exception) {
                    tempDownload.delete()
                    null
                }
            }

            if (sourceFile == null || !sourceFile.exists() || sourceFile.length() == 0L) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "Failed to prepare video for saving", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            val filename = "kchat_video_${System.currentTimeMillis()}.mp4"
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/KChat")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val videoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (videoUri != null) {
                resolver.openOutputStream(videoUri)?.use { out ->
                    java.io.FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(videoUri, contentValues, null, null)
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "Video saved to Movies/KChat", Toast.LENGTH_SHORT).show()
                }
            } else {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "Failed to save video: storage error", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "Failed to save video: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
