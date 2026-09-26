@file:Suppress("DEPRECATION")

package com.example.kchat.feature.chat
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Download
import android.content.ContentValues
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.OutputStream
import android.widget.Toast
import android.net.Uri
import android.util.Log
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextAlign
import com.example.kchat.model.User
import com.example.kchat.feature.home.HomeViewModel
import com.example.kchat.feature.home.rememberGroupOnlineCount
import com.example.kchat.feature.chat.audio.AudioMessageBubble
import com.example.kchat.feature.chat.audio.AudioPlayerHelper
import com.example.kchat.feature.chat.audio.AudioRecorderHelper
import com.example.kchat.feature.chat.audio.AudioUtils
import com.example.kchat.feature.chat.file.FileMessageBubble
import com.example.kchat.feature.chat.attachment.AttachmentItem
import com.example.kchat.feature.chat.attachment.AttachmentType
import com.example.kchat.feature.chat.attachment.AttachmentPreviewDialog
import com.example.kchat.feature.chat.audio.AudioRecordingBar
import com.example.kchat.feature.chat.file.FilePreviewDialog
import com.example.kchat.feature.chat.file.FileUtils
import com.example.kchat.feature.chat.file.PendingFile
import com.example.kchat.feature.chat.video.CaptureVideoContract
import com.example.kchat.feature.chat.video.FullScreenVideoDialog
import com.example.kchat.feature.chat.video.PendingVideo
import com.example.kchat.feature.chat.video.VideoMessageBubble
import com.example.kchat.feature.chat.video.VideoMetadata
import com.example.kchat.feature.chat.video.VideoPlayerHelper
import com.example.kchat.feature.chat.video.VideoPreviewDialog
import com.example.kchat.feature.chat.video.VideoUtils
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.example.kchat.feature.smartreply.KChatAiBottomSheet
import com.example.kchat.feature.smartreply.KChatAiEntryPoint
import com.example.kchat.feature.smartreply.SmartReplyBottomSheet
import com.example.kchat.feature.smartreply.SmartReplyViewModel
import com.example.kchat.feature.threadsummary.ThreadSummaryBottomSheet
import com.example.kchat.feature.threadsummary.ThreadSummaryViewModel
import com.example.kchat.feature.contextsearch.ContextSearchBottomSheet
import com.example.kchat.feature.contextsearch.ContextSearchViewModel
import com.example.kchat.feature.scamguard.ScamGuardBottomSheet
import com.example.kchat.feature.scamguard.ScamGuardViewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.kchat.R
import com.example.kchat.feature.home.ChannelItem
import com.example.kchat.feature.home.HomeChannel
import com.example.kchat.model.Message
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.zegocloud.uikit.prebuilt.call.invite.widget.ZegoSendCallInvitationButton
import com.zegocloud.uikit.service.defines.ZegoUIKitUser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(navController: NavController, channelId: String, channelName: String, initialTargetMessageId: String? = null) {
    Log.d("NotificationDebug", "ChatScreen composable entered. Parameters: channelId=$channelId, channelName=$channelName")
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) {
        val context = LocalContext.current
        LaunchedEffect(channelId) {
            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val notificationId = channelId.hashCode()
            Log.d("NotificationDebug", "ChatScreen entered, dismissing notification. channelId=$channelId, notificationId=$notificationId")
            notificationManager.cancel(notificationId)
        }
        val coroutineScope = rememberCoroutineScope()
        val audioPlayerHelper = remember { AudioPlayerHelper(coroutineScope) }
        val audioRecorderHelper = remember { AudioRecorderHelper(context, coroutineScope) }
        val videoPlayerHelper = remember { VideoPlayerHelper(coroutineScope) }

        // Mutual playback coordination: playing one pauses the other
        LaunchedEffect(audioPlayerHelper) {
            audioPlayerHelper.isPlaying.collect { isPlaying ->
                if (isPlaying) {
                    videoPlayerHelper.pause()
                }
            }
        }
        LaunchedEffect(videoPlayerHelper) {
            videoPlayerHelper.isPlaying.collect { isPlaying ->
                if (isPlaying) {
                    audioPlayerHelper.pause()
                }
            }
        }

        val isRecordingAudio by audioRecorderHelper.isRecording.collectAsState()
        val recordingDurationMs by audioRecorderHelper.recordingDurationMs.collectAsState()
        val recordingAmplitude by audioRecorderHelper.currentAmplitude.collectAsState()
        var pausedAudioFile by remember { mutableStateOf<File?>(null) }
        var pausedAudioDurationMs by remember { mutableStateOf(0L) }
        val isAudioPlaying by audioPlayerHelper.isPlaying.collectAsState()
        val audioPlayPositionMs by audioPlayerHelper.currentPositionMs.collectAsState()

        // --- Unified Pending Attachments State ---
        var pendingAttachments by remember { mutableStateOf<List<AttachmentItem>>(emptyList()) }

        DisposableEffect(Unit) {
            onDispose {
                audioPlayerHelper.release()
                audioRecorderHelper.cancelRecording()
                videoPlayerHelper.release()
                pausedAudioFile?.delete()
                pausedAudioFile = null
            }
        }

        val viewModel:ChatViewModel= hiltViewModel()
        var showAttachmentSheet by remember { mutableStateOf(false) }
        val cameraImageUri=remember{
            mutableStateOf<Uri?>(null)
        }
        var replyingToMessage by remember { mutableStateOf<Message?>(null) }
        val cameraImageLauncher=rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) {
                cameraImageUri.value?.let { uri ->
                    pendingAttachments = pendingAttachments + AttachmentItem(
                        type = AttachmentType.IMAGE,
                        uri = uri,
                        isFromCamera = true
                    )
                }
            }
        }
        val imageLauncher=rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents()
        ) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                val newItems = uris.map { uri ->
                    AttachmentItem(
                        type = AttachmentType.IMAGE,
                        uri = uri
                    )
                }
                pendingAttachments = pendingAttachments + newItems
            }
        }
        fun createImageUri(): Uri {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir=
                ContextCompat.getExternalFilesDirs(
                    navController.context,
                    Environment.DIRECTORY_PICTURES
                ).first()
            return FileProvider.getUriForFile(
                navController.context,
                "${navController.context.packageName}.provider",
                File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply{
                    cameraImageUri.value=Uri.fromFile(this)
                }
            )
        }
        val permissionLauncher=
            rememberLauncherForActivityResult(contract =ActivityResultContracts.RequestPermission()){isGranted->
                if(isGranted){
                    cameraImageLauncher.launch(createImageUri())
                }
            }

        val audioPermissionLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    val started = audioRecorderHelper.startRecording()
                    if (!started) {
                        Toast.makeText(context, "Could not start audio recording", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Microphone permission is required to record voice messages", Toast.LENGTH_SHORT).show()
                }
            }

        val startAudioRecordingAction: () -> Unit = {
            if (navController.context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                val started = audioRecorderHelper.startRecording()
                if (!started) {
                    Toast.makeText(context, "Could not start audio recording", Toast.LENGTH_SHORT).show()
                }
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        // --- Video Messaging States & Launchers ---
        var fullScreenVideoUrl by remember { mutableStateOf<String?>(null) }
        val cameraVideoUri = remember { mutableStateOf<Uri?>(null) }
        val cameraVideoFile = remember { mutableStateOf<File?>(null) }

        fun createVideoUri(): Uri {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val videoDir = File(context.cacheDir, "video_messages").apply { if (!exists()) mkdirs() }
            val file = File.createTempFile("VID_${timeStamp}_", ".mp4", videoDir)
            cameraVideoFile.value = file
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            cameraVideoUri.value = uri
            return uri
        }

        val cameraVideoLauncher = rememberLauncherForActivityResult(
            contract = CaptureVideoContract()
        ) { success ->
            if (success) {
                val file = cameraVideoFile.value
                if (file != null && file.exists() && file.length() > 0L) {
                    coroutineScope.launch {
                        val metadata = VideoUtils.extractMetadata(file)
                        if (metadata == null || metadata.durationMs < VideoUtils.MIN_VIDEO_DURATION_MS) {
                            Toast.makeText(context, "Video recording too short or invalid", Toast.LENGTH_SHORT).show()
                            file.delete()
                        } else if (metadata.durationMs > VideoUtils.MAX_VIDEO_DURATION_MS) {
                            Toast.makeText(context, "Video exceeds 2 minute limit", Toast.LENGTH_SHORT).show()
                            file.delete()
                        } else {
                            pendingAttachments = pendingAttachments + AttachmentItem(
                                type = AttachmentType.VIDEO,
                                file = file,
                                durationMs = metadata.durationMs,
                                thumbnail = metadata.thumbnail,
                                fileSizeBytes = file.length(),
                                isFromCamera = true
                            )
                        }
                    }
                } else {
                    cameraVideoFile.value?.delete()
                }
            } else {
                cameraVideoFile.value?.delete()
                cameraVideoFile.value = null
            }
        }

        val videoGalleryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents()
        ) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                coroutineScope.launch {
                    val newItems = mutableListOf<AttachmentItem>()
                    for (videoUri in uris) {
                        val tempFile = VideoUtils.copyUriToTempFile(context, videoUri)
                        if (tempFile != null) {
                            val metadata = VideoUtils.extractMetadata(tempFile)
                            if (metadata == null || metadata.durationMs < VideoUtils.MIN_VIDEO_DURATION_MS) {
                                Toast.makeText(context, "Selected video is too short or invalid", Toast.LENGTH_SHORT).show()
                                tempFile.delete()
                            } else if (metadata.durationMs > VideoUtils.MAX_VIDEO_DURATION_MS) {
                                Toast.makeText(context, "Selected video exceeds 2 minute limit (${VideoUtils.formatDuration(metadata.durationMs)})", Toast.LENGTH_LONG).show()
                                tempFile.delete()
                            } else {
                                newItems.add(
                                    AttachmentItem(
                                        type = AttachmentType.VIDEO,
                                        file = tempFile,
                                        durationMs = metadata.durationMs,
                                        thumbnail = metadata.thumbnail,
                                        fileSizeBytes = tempFile.length()
                                    )
                                )
                            }
                        } else {
                            Toast.makeText(context, "Could not open selected video", Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (newItems.isNotEmpty()) {
                        pendingAttachments = pendingAttachments + newItems
                    }
                }
            }
        }

        val videoCameraPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                cameraVideoLauncher.launch(createVideoUri())
            } else {
                Toast.makeText(context, "Camera permission is required to record video", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Document / File Messaging States & Launchers ---
        val documentPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments()
        ) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                coroutineScope.launch {
                    val newItems = mutableListOf<AttachmentItem>()
                    for (docUri in uris) {
                        val metadata = FileUtils.extractFileMetadata(context, docUri)
                        if (metadata.fileSizeBytes > FileUtils.MAX_FILE_SIZE_BYTES) {
                            Toast.makeText(context, "${metadata.fileName} exceeds 25 MB limit", Toast.LENGTH_SHORT).show()
                            continue
                        }
                        val tempFile = FileUtils.copyUriToTempFile(context, docUri, metadata.fileName)
                        if (tempFile != null) {
                            newItems.add(
                                AttachmentItem(
                                    type = AttachmentType.DOCUMENT,
                                    file = tempFile,
                                    fileName = metadata.fileName,
                                    mimeType = metadata.mimeType,
                                    fileSizeBytes = tempFile.length()
                                )
                            )
                        } else {
                            Toast.makeText(context, "Could not open selected file", Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (newItems.isNotEmpty()) {
                        pendingAttachments = pendingAttachments + newItems
                    }
                }
            }
        }

        // --- States for Deletion, Profile, and Smart Reply Features ---
        var selectedMessages by remember { mutableStateOf<List<Message>>(emptyList()) }
        var showDeleteOptionsDialog by remember { mutableStateOf(false) }
        var showProfileDialog by remember { mutableStateOf(false) }
        var showSmartReplySheet by remember { mutableStateOf(false) }
        var showAiMenuSheet by remember { mutableStateOf(false) }
        var showThreadSummarySheet by remember { mutableStateOf(false) }
        var showContextSearchSheet by remember { mutableStateOf(false) }
        var showScamGuardSheet by remember { mutableStateOf(false) }
        var targetScrollMessageId by remember(initialTargetMessageId) { mutableStateOf<String?>(initialTargetMessageId) }
        var highlightedMessageId by remember { mutableStateOf<String?>(null) }
        var composerText by remember { mutableStateOf("") }
        val smartReplyViewModel: SmartReplyViewModel = hiltViewModel()
        val threadSummaryViewModel: ThreadSummaryViewModel = hiltViewModel()
        val contextSearchViewModel: ContextSearchViewModel = hiltViewModel()
        val scamGuardViewModel: ScamGuardViewModel = hiltViewModel()

        val hasActiveOverlay = showAiMenuSheet ||
                showSmartReplySheet ||
                showThreadSummarySheet ||
                showContextSearchSheet ||
                showScamGuardSheet ||
                showAttachmentSheet ||
                showDeleteOptionsDialog ||
                showProfileDialog ||
                fullScreenVideoUrl != null ||
                pendingAttachments.isNotEmpty() ||
                pausedAudioFile != null

        BackHandler(enabled = fullScreenVideoUrl != null) {
            fullScreenVideoUrl = null
        }

        BackHandler(enabled = pendingAttachments.isNotEmpty() && fullScreenVideoUrl == null) {
            for (item in pendingAttachments) {
                if (item.file != null && (item.isFromCamera || item.type == AttachmentType.VIDEO || item.type == AttachmentType.DOCUMENT)) {
                    try { item.file.delete() } catch (e: Exception) {}
                }
            }
            pendingAttachments = emptyList()
        }

        BackHandler(enabled = pausedAudioFile != null && pendingAttachments.isEmpty() && fullScreenVideoUrl == null) {
            audioPlayerHelper.stop()
            pausedAudioFile?.delete()
            pausedAudioFile = null
            pausedAudioDurationMs = 0L
        }

        BackHandler(enabled = isRecordingAudio && !hasActiveOverlay) {
            audioRecorderHelper.cancelRecording()
        }

        BackHandler(enabled = selectedMessages.isNotEmpty() && !hasActiveOverlay) {
            selectedMessages = emptyList()
        }

        val currentUid = Firebase.auth.currentUser?.uid ?: return@Scaffold
        val isSelfChat = remember(channelId) { channelId.startsWith("self_chat_") }
        val isDirectChat = remember(channelId, currentUid) {
            !isSelfChat && !channelId.startsWith("-") && channelId != HomeViewModel.WORLD_CHAT_ID && channelId.contains("_") && channelId.split("_").let { parts ->
                parts.size == 2 && currentUid in parts
            }
        }
        val db = Firebase.database
        var otherUserEmail by remember { mutableStateOf<String?>(null) }
        var otherUserImage by remember { mutableStateOf<String?>(null) }
        var otherUserName by remember { mutableStateOf(channelName) }
        var otherUserAbout by remember { mutableStateOf<String?>(null) }
        var otherUserStatus by remember { mutableStateOf("Offline") }
        var otherUserOnline by remember { mutableStateOf(false) }
        var otherUserLastSeen by remember { mutableStateOf<Long?>(null) }
        var groupImageUrl by remember { mutableStateOf<String?>(null) }


        DisposableEffect(channelId) {
            if (isSelfChat) {
                return@DisposableEffect onDispose {}
            }
            if (!isDirectChat) {
                if (channelId != HomeViewModel.WORLD_CHAT_ID) {
                    val groupImgRef = db.reference.child("channel_image").child(channelId)
                    val groupImgListener = object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            groupImageUrl = snapshot.getValue(String::class.java)
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    }
                    groupImgRef.addValueEventListener(groupImgListener)
                    return@DisposableEffect onDispose {
                        groupImgRef.removeEventListener(groupImgListener)
                    }
                }
                return@DisposableEffect onDispose {}
            }

            val ids = channelId.split("_")
            val otherUid = ids.firstOrNull { it != currentUid } ?: return@DisposableEffect onDispose {}

            val userRef = db.reference.child("users").child(otherUid)
            val userListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    otherUserEmail = snapshot.child("email").getValue(String::class.java)
                    otherUserImage = snapshot.child("imageUrl").getValue(String::class.java)
                    otherUserName = snapshot.child("name").getValue(String::class.java) ?: channelName
                    otherUserAbout = snapshot.child("about").getValue(String::class.java)
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            userRef.addValueEventListener(userListener)

            val statusRef = db.reference.child("status").child(otherUid)
            val statusListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isOnline = snapshot.child("online").getValue(Boolean::class.java) ?: false
                    val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java)
                    otherUserOnline = isOnline
                    otherUserLastSeen = lastSeen
                    otherUserStatus = if (isOnline) "Online" else "Offline"
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            statusRef.addValueEventListener(statusListener)

            onDispose {
                userRef.removeEventListener(userListener)
                statusRef.removeEventListener(statusListener)
            }
        }

        val messages = viewModel.message.collectAsState()
        val isAccessDenied by viewModel.isAccessDenied.collectAsState()
        val isMessagesLoaded by viewModel.isMessagesLoaded.collectAsState()

        val onJumpToOriginalMessage: (String, String, String) -> Unit = { origChatId, origChatName, origMsgId ->
            if (origChatId == channelId) {
                targetScrollMessageId = origMsgId
            } else {
                try {
                    val encodedChatId = java.net.URLEncoder.encode(origChatId, "UTF-8")
                    val encodedChatName = java.net.URLEncoder.encode(origChatName, "UTF-8")
                    val encodedMsgId = java.net.URLEncoder.encode(origMsgId, "UTF-8")
                    navController.navigate("chat/$encodedChatId&$encodedChatName?targetMsgId=$encodedMsgId")
                } catch (e: Exception) {
                    navController.navigate("chat/$origChatId&$origChatName?targetMsgId=$origMsgId")
                }
            }
        }

        LaunchedEffect(isAccessDenied) {
            if (isAccessDenied) {
                Toast.makeText(context, "You are not a member of this group", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        }

        Column(modifier = Modifier
            .fillMaxSize()
            .padding(it)
        ) {
            LaunchedEffect(channelId) {
                viewModel.listenForMessages(channelId)
            }
            LaunchedEffect(messages.value) {
                if (messages.value.isNotEmpty()) {
                    viewModel.markMessagesAsRead(channelId)
                }
            }
            val groupOnlineCount = if (!isDirectChat && !isSelfChat) rememberGroupOnlineCount(channelId) else 0
            val presenceSubtitle = if (isSelfChat) {
                "Messages with yourself"
            } else if (isDirectChat) {
                DateTimeUtils.formatPresence(otherUserOnline, otherUserLastSeen)
            } else {
                DateTimeUtils.formatGroupOnlineCount(groupOnlineCount)
            }

            val sendAudioAction: () -> Unit = {
                val reply = replyingToMessage
                val replyId = reply?.id
                val replySender = if (reply != null) {
                    if (reply.senderId == currentUid) "You" else (reply.senderName?.ifBlank { "User" } ?: "User")
                } else null
                val replyText = if (reply != null) {
                    if (!reply.message.isNullOrBlank()) reply.message.trim()
                    else if (!reply.imageUrl.isNullOrBlank()) "[Photo]"
                    else if (!reply.audioUrl.isNullOrBlank()) "🎤 Voice message"
                    else if (!reply.videoUrl.isNullOrBlank()) "🎥 Video message"
                    else if (!reply.fileUrl.isNullOrBlank()) "📎 File"
                    else ""
                } else null

                if (pausedAudioFile != null) {
                    audioPlayerHelper.stop()
                    val file = pausedAudioFile!!
                    val durationMs = pausedAudioDurationMs
                    pausedAudioFile = null
                    pausedAudioDurationMs = 0L
                    viewModel.sendAudioMessage(
                        file = file,
                        durationMs = durationMs,
                        channelID = channelId,
                        channelName = channelName,
                        replyToMessageId = replyId,
                        replyToSenderName = replySender,
                        replyToMessageText = replyText
                    )
                    replyingToMessage = null
                } else {
                    val recordResult = audioRecorderHelper.stopRecording()
                    if (recordResult != null) {
                        val (file, durationMs) = recordResult
                        viewModel.sendAudioMessage(
                            file = file,
                            durationMs = durationMs,
                            channelID = channelId,
                            channelName = channelName,
                            replyToMessageId = replyId,
                            replyToSenderName = replySender,
                            replyToMessageText = replyText
                        )
                        replyingToMessage = null
                    } else {
                        Toast.makeText(context, "Recording too short", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val pauseAudioAction: () -> Unit = {
                val recordResult = audioRecorderHelper.stopRecording()
                if (recordResult != null) {
                    pausedAudioFile = recordResult.first
                    pausedAudioDurationMs = recordResult.second
                } else {
                    Toast.makeText(context, "Recording too short", Toast.LENGTH_SHORT).show()
                }
            }

            val resumeAudioAction: () -> Unit = {
                audioPlayerHelper.stop()
                pausedAudioFile?.delete()
                pausedAudioFile = null
                pausedAudioDurationMs = 0L
                audioRecorderHelper.startRecording()
            }

            val cancelOrDeleteAudioAction: () -> Unit = {
                audioPlayerHelper.stop()
                if (isRecordingAudio) {
                    audioRecorderHelper.cancelRecording()
                }
                if (pausedAudioFile != null) {
                    pausedAudioFile?.delete()
                    pausedAudioFile = null
                    pausedAudioDurationMs = 0L
                }
            }

            LaunchedEffect(audioRecorderHelper, channelId, channelName, replyingToMessage) {
                audioRecorderHelper.onMaxDurationReached = {
                    sendAudioAction()
                    Toast.makeText(context, "Voice message sent (max duration reached)", Toast.LENGTH_SHORT).show()
                }
            }

            ChatMessages(
                messages = messages.value,
                selectedMessages = selectedMessages,
                isDirectChat = isDirectChat,
                onToggleMessageSelection = { msg ->
                    selectedMessages = if (selectedMessages.any { it.id == msg.id }) {
                        selectedMessages.filter { it.id != msg.id }
                    } else {
                        selectedMessages + msg
                    }
                },
                onClearSelection = { selectedMessages = emptyList() },
                onDeleteClicked = { showDeleteOptionsDialog = true },
                onSmartReplyClicked = {
                    if (selectedMessages.isNotEmpty()) {
                        smartReplyViewModel.generateReplies(selectedMessages, currentUid)
                        showSmartReplySheet = true
                    }
                },
                onAiMenuClicked = {
                    showAiMenuSheet = true
                },
                otherUserImage = if (isSelfChat) null else if (isDirectChat) otherUserImage else if (channelId == HomeViewModel.WORLD_CHAT_ID) null else groupImageUrl,
                onProfileClicked = { if (!isSelfChat) showProfileDialog = true },
                composerText = composerText,
                onComposerTextChange = { composerText = it },
                onSendMessage = { message ->
                    val reply = replyingToMessage
                    val replyId = reply?.id
                    val replySender = if (reply != null) {
                        if (reply.senderId == currentUid) "You" else (reply.senderName?.ifBlank { "User" } ?: "User")
                    } else null
                    val replyText = if (reply != null) {
                        if (!reply.message.isNullOrBlank()) reply.message.trim()
                        else if (!reply.imageUrl.isNullOrBlank()) "[Photo]"
                        else if (!reply.audioUrl.isNullOrBlank()) "🎤 Voice message"
                        else if (!reply.videoUrl.isNullOrBlank()) "🎥 Video message"
                        else if (!reply.fileUrl.isNullOrBlank()) "📎 File"
                        else ""
                    } else null

                    viewModel.sendMessage(
                        channelID = channelId,
                        messageText = message,
                        channelName = channelName,
                        replyToMessageId = replyId,
                        replyToSenderName = replySender,
                        replyToMessageText = replyText
                    )
                    replyingToMessage = null
                },
                onImageClicked = {
                    showAttachmentSheet = true
                },
                channelName = channelName,
                viewModel = viewModel,
                channelID = channelId,
                targetScrollMessageId = targetScrollMessageId,
                onScrollComplete = { targetScrollMessageId = null },
                highlightedMessageId = highlightedMessageId,
                isMessagesLoaded = isMessagesLoaded,
                onHighlightMessage = { highlightedMessageId = it },
                onJumpToOriginalMessage = onJumpToOriginalMessage,
                replyingToMessage = replyingToMessage,
                onReply = { msg -> replyingToMessage = msg },
                onDismissReply = { replyingToMessage = null },
                onQuotedMessageClick = { quotedId -> targetScrollMessageId = quotedId },
                presenceSubtitle = presenceSubtitle,
                isRecordingAudio = isRecordingAudio,
                recordingDurationMs = recordingDurationMs,
                recordingAmplitude = recordingAmplitude,
                isAudioPaused = (pausedAudioFile != null),
                pausedAudioDurationMs = pausedAudioDurationMs,
                isAudioPlaying = isAudioPlaying,
                audioPlayPositionMs = audioPlayPositionMs,
                onPauseAudioRecording = pauseAudioAction,
                onResumeAudioRecording = resumeAudioAction,
                onCancelOrDeleteAudioRecording = cancelOrDeleteAudioAction,
                onTogglePlayPauseAudioPreview = {
                    pausedAudioFile?.let { file ->
                        audioPlayerHelper.togglePlayPause(file.absolutePath, pausedAudioDurationMs)
                    }
                },
                onSeekAudioPreview = { pos ->
                    audioPlayerHelper.seekTo(pos)
                },
                onSendAudioRecording = sendAudioAction,
                onStartAudioRecording = startAudioRecordingAction,
                audioPlayerHelper = audioPlayerHelper,
                videoPlayerHelper = videoPlayerHelper,
                onFullScreenVideoClick = { url -> fullScreenVideoUrl = url }
            )
        }

        if (showAiMenuSheet) {
            KChatAiBottomSheet(
                onDismiss = { showAiMenuSheet = false },
                onSmartReplyClicked = {
                    showAiMenuSheet = false
                    if (selectedMessages.isNotEmpty()) {
                        smartReplyViewModel.generateReplies(selectedMessages, currentUid)
                        showSmartReplySheet = true
                    } else {
                        Toast.makeText(
                            context,
                            "Select one or more messages to use Smart Reply.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onThreadSummaryClicked = {
                    showAiMenuSheet = false
                    if (messages.value.isNotEmpty()) {
                        threadSummaryViewModel.summarizeThread(messages.value, currentUid)
                        showThreadSummarySheet = true
                    } else {
                        Toast.makeText(
                            context,
                            "No messages available to summarize.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onContextSearchClicked = {
                    showAiMenuSheet = false
                    if (messages.value.isNotEmpty()) {
                        showContextSearchSheet = true
                    } else {
                        Toast.makeText(
                            context,
                            "No messages available to search.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onScamGuardClicked = {
                    showAiMenuSheet = false
                    if (selectedMessages.isNotEmpty()) {
                        scamGuardViewModel.analyzeSelectedMessages(selectedMessages, currentUid)
                        showScamGuardSheet = true
                    } else {
                        Toast.makeText(
                            context,
                            "Select one or more messages to scan for scam or phishing risks.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        if (showThreadSummarySheet) {
            val summaryState by threadSummaryViewModel.uiState.collectAsState()
            ThreadSummaryBottomSheet(
                uiState = summaryState,
                onDismiss = {
                    showThreadSummarySheet = false
                    threadSummaryViewModel.reset()
                },
                onRetry = {
                    threadSummaryViewModel.summarizeThread(messages.value, currentUid)
                }
            )
        }

        if (showContextSearchSheet) {
            val searchState by contextSearchViewModel.uiState.collectAsState()
            ContextSearchBottomSheet(
                uiState = searchState,
                onSearch = { query ->
                    contextSearchViewModel.searchConversation(query, messages.value, currentUid)
                },
                onMessageSelected = { messageId ->
                    showContextSearchSheet = false
                    contextSearchViewModel.reset()
                    targetScrollMessageId = messageId
                },
                onDismiss = {
                    showContextSearchSheet = false
                    contextSearchViewModel.reset()
                }
            )
        }

        if (showSmartReplySheet) {
            val smartReplyState by smartReplyViewModel.uiState.collectAsState()
            SmartReplyBottomSheet(
                uiState = smartReplyState,
                onDismiss = {
                    showSmartReplySheet = false
                    smartReplyViewModel.reset()
                },
                onSuggestionSelected = { suggestion ->
                    composerText = suggestion
                    showSmartReplySheet = false
                    selectedMessages = emptyList()
                    smartReplyViewModel.reset()
                },
                onRetry = {
                    if (selectedMessages.isNotEmpty()) {
                        smartReplyViewModel.generateReplies(selectedMessages, currentUid)
                    }
                }
            )
        }

        if (showScamGuardSheet) {
            val scamGuardState by scamGuardViewModel.uiState.collectAsState()
            ScamGuardBottomSheet(
                uiState = scamGuardState,
                onDismiss = {
                    showScamGuardSheet = false
                    scamGuardViewModel.reset()
                },
                onRetry = {
                    if (selectedMessages.isNotEmpty()) {
                        scamGuardViewModel.analyzeSelectedMessages(selectedMessages, currentUid)
                    }
                }
            )
        }


        if (showAttachmentSheet) {
            AttachmentBottomSheet(
                onDismiss = { showAttachmentSheet = false },
                onCameraClick = {
                    showAttachmentSheet = false
                    if (navController.context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        cameraImageLauncher.launch(createImageUri())
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onGalleryClick = {
                    showAttachmentSheet = false
                    imageLauncher.launch("image/*")
                },
                onAudioClick = {
                    showAttachmentSheet = false
                    startAudioRecordingAction()
                },
                onVideoCameraClick = {
                    showAttachmentSheet = false
                    if (navController.context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        cameraVideoLauncher.launch(createVideoUri())
                    } else {
                        videoCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onVideoGalleryClick = {
                    showAttachmentSheet = false
                    videoGalleryLauncher.launch("video/*")
                },
                onDocumentClick = {
                    showAttachmentSheet = false
                    documentPickerLauncher.launch(arrayOf("*/*"))
                }
            )
        }

        if (pendingAttachments.isNotEmpty()) {
            AttachmentPreviewDialog(
                attachments = pendingAttachments,
                onRemoveItem = { itemToRemove ->
                    if (itemToRemove.file != null && (itemToRemove.isFromCamera || itemToRemove.type == AttachmentType.VIDEO || itemToRemove.type == AttachmentType.DOCUMENT)) {
                        try { itemToRemove.file.delete() } catch (e: Exception) {}
                    }
                    pendingAttachments = pendingAttachments.filter { it.id != itemToRemove.id }
                },
                onAddMore = {
                    val allImages = pendingAttachments.all { it.type == AttachmentType.IMAGE }
                    val allVideos = pendingAttachments.all { it.type == AttachmentType.VIDEO }
                    val allDocuments = pendingAttachments.all { it.type == AttachmentType.DOCUMENT }
                    when {
                        allImages -> imageLauncher.launch("image/*")
                        allVideos -> videoGalleryLauncher.launch("video/*")
                        allDocuments -> documentPickerLauncher.launch(arrayOf("*/*"))
                        else -> showAttachmentSheet = true
                    }
                },
                onDismiss = {
                    for (item in pendingAttachments) {
                        if (item.file != null && (item.isFromCamera || item.type == AttachmentType.VIDEO || item.type == AttachmentType.DOCUMENT)) {
                            try { item.file.delete() } catch (e: Exception) {}
                        }
                    }
                    pendingAttachments = emptyList()
                },
                onSend = { itemsToSend ->
                    val reply = replyingToMessage
                    val replyId = reply?.id
                    val replySender = if (reply != null) {
                        if (reply.senderId == currentUid) "You" else (reply.senderName?.ifBlank { "User" } ?: "User")
                    } else null
                    val replyText = if (reply != null) {
                        if (!reply.message.isNullOrBlank()) reply.message.trim()
                        else if (!reply.imageUrl.isNullOrBlank()) "[Photo]"
                        else if (!reply.audioUrl.isNullOrBlank()) "🎤 Voice message"
                        else if (!reply.videoUrl.isNullOrBlank()) "🎥 Video message"
                        else if (!reply.fileUrl.isNullOrBlank()) "📎 File"
                        else ""
                    } else null

                    var isFirst = true
                    for (item in itemsToSend) {
                        val curReplyId = if (isFirst) replyId else null
                        val curReplySender = if (isFirst) replySender else null
                        val curReplyText = if (isFirst) replyText else null
                        isFirst = false

                        when (item.type) {
                            AttachmentType.IMAGE -> {
                                val uri = item.uri ?: item.file?.let { Uri.fromFile(it) }
                                if (uri != null) {
                                    viewModel.sendImageMessage(
                                        uri = uri,
                                        channelID = channelId,
                                        channelName = channelName,
                                        replyToMessageId = curReplyId,
                                        replyToSenderName = curReplySender,
                                        replyToMessageText = curReplyText
                                    )
                                }
                            }
                            AttachmentType.VIDEO -> {
                                val file = item.file
                                if (file != null) {
                                    viewModel.sendVideoMessage(
                                        file = file,
                                        durationMs = item.durationMs,
                                        channelID = channelId,
                                        channelName = channelName,
                                        replyToMessageId = curReplyId,
                                        replyToSenderName = curReplySender,
                                        replyToMessageText = curReplyText
                                    )
                                }
                            }
                            AttachmentType.DOCUMENT -> {
                                val file = item.file
                                if (file != null) {
                                    viewModel.sendFileMessage(
                                        file = file,
                                        fileName = item.fileName,
                                        mimeType = item.mimeType,
                                        fileSizeBytes = item.fileSizeBytes,
                                        channelID = channelId,
                                        channelName = channelName,
                                        replyToMessageId = curReplyId,
                                        replyToSenderName = curReplySender,
                                        replyToMessageText = curReplyText
                                    )
                                }
                            }
                        }
                    }
                    replyingToMessage = null
                    pendingAttachments = emptyList()
                }
            )
        }

        if (fullScreenVideoUrl != null) {
            FullScreenVideoDialog(
                videoUrl = fullScreenVideoUrl!!,
                videoPlayerHelper = videoPlayerHelper,
                onDismiss = {
                    fullScreenVideoUrl = null
                }
            )
        }

        // --- Refined Two-Way Deletion Option Dialog ---
        if (showDeleteOptionsDialog && selectedMessages.isNotEmpty()) {
            val isSingle = selectedMessages.size == 1
            val isAllOwnMessages = selectedMessages.all { it.senderId == currentUid }

            AlertDialog(
                onDismissRequest = { 
                    showDeleteOptionsDialog = false 
                    selectedMessages = emptyList()
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        text = if (isSingle) "Delete Message" else "Delete Messages (${selectedMessages.size})",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isSingle) "Choose how you want to delete this message:" else "Choose how you want to delete these ${selectedMessages.size} messages:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (isAllOwnMessages) {
                            // Delete for Everyone button
                            Button(
                                onClick = {
                                    selectedMessages.forEach { msg ->
                                        if (!msg.imageUrl.isNullOrEmpty()) {
                                            deleteSavedImageFromGallery(context, msg.imageUrl)
                                        }
                                        if (msg.isSavedMessage) {
                                            viewModel.deleteSavedMessage(msg.id)
                                        } else {
                                            viewModel.deleteMessage(channelId, msg.id)
                                        }
                                    }
                                    selectedMessages = emptyList()
                                    showDeleteOptionsDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Delete for Everyone",
                                    color = MaterialTheme.colorScheme.onError,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Delete for Me button
                        Button(
                            onClick = {
                                selectedMessages.forEach { msg ->
                                    if (msg.isSavedMessage) {
                                        viewModel.deleteSavedMessage(msg.id)
                                    } else {
                                        viewModel.deleteMessageForMe(channelId, msg.id)
                                    }
                                }
                                selectedMessages = emptyList()
                                showDeleteOptionsDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Delete for Me",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Cancel button
                        TextButton(
                            onClick = {
                                selectedMessages = emptyList()
                                showDeleteOptionsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Cancel",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                confirmButton = {} // confirmation handled inside custom column content
            )
        }

        // --- User Profile / World Chat Info / Custom Group Info Dialogs ---
        if (showProfileDialog && isDirectChat) {
            val ids = channelId.split("_")
            val otherUid = ids.firstOrNull { it != currentUid } ?: ""
            UserProfileDialog(
                user = User(
                    uid = otherUid,
                    name = otherUserName,
                    email = otherUserEmail ?: "",
                    profileImage = otherUserImage,
                    about = otherUserAbout
                ),
                onDismissRequest = { showProfileDialog = false }
            )
        }

        // --- World Chat Info Dialog ---
        if (showProfileDialog && !isDirectChat && channelId == HomeViewModel.WORLD_CHAT_ID) {
            WorldChatInfoDialog(
                onDismissRequest = { showProfileDialog = false }
            )
        }

        // --- Custom Group Info Dialog ---
        if (showProfileDialog && !isDirectChat && channelId != HomeViewModel.WORLD_CHAT_ID) {
            CustomGroupInfoDialog(
                channelId = channelId,
                channelName = channelName,
                onDismissRequest = { showProfileDialog = false }
            )




        }

    }
}



@Composable
fun ChatMessages(
    channelName: String,
    channelID: String,
    isDirectChat: Boolean,
    messages: List<Message>,
    selectedMessages: List<Message>,
    onToggleMessageSelection: (Message) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteClicked: () -> Unit,
    onSmartReplyClicked: () -> Unit,
    onAiMenuClicked: () -> Unit,
    otherUserImage: String?,
    onProfileClicked: () -> Unit,
    composerText: String,
    onComposerTextChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onImageClicked: () -> Unit,
    viewModel: ChatViewModel,
    targetScrollMessageId: String? = null,
    onScrollComplete: () -> Unit = {},
    highlightedMessageId: String? = null,
    isMessagesLoaded: Boolean = false,
    onHighlightMessage: (String?) -> Unit = {},
    onJumpToOriginalMessage: (String, String, String) -> Unit = { _, _, _ -> },
    replyingToMessage: Message? = null,
    onReply: (Message) -> Unit = {},
    onDismissReply: () -> Unit = {},
    onQuotedMessageClick: (String) -> Unit = {},
    presenceSubtitle: String? = null,
    isRecordingAudio: Boolean = false,
    recordingDurationMs: Long = 0L,
    recordingAmplitude: Float = 0f,
    isAudioPaused: Boolean = false,
    pausedAudioDurationMs: Long = 0L,
    isAudioPlaying: Boolean = false,
    audioPlayPositionMs: Long = 0L,
    onPauseAudioRecording: () -> Unit = {},
    onResumeAudioRecording: () -> Unit = {},
    onCancelOrDeleteAudioRecording: () -> Unit = {},
    onTogglePlayPauseAudioPreview: () -> Unit = {},
    onSeekAudioPreview: (Long) -> Unit = {},
    onSendAudioRecording: () -> Unit = {},
    onStartAudioRecording: () -> Unit = {},
    audioPlayerHelper: AudioPlayerHelper? = null,
    videoPlayerHelper: VideoPlayerHelper? = null,
    onFullScreenVideoClick: (String) -> Unit = {}
){
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && targetScrollMessageId == null) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(targetScrollMessageId, messages, isMessagesLoaded) {
        val targetId = targetScrollMessageId
        if (!targetId.isNullOrBlank()) {
            val targetIndex = messages.indexOfFirst { it.id == targetId }
            if (targetIndex != -1) {
                listState.animateScrollToItem(targetIndex)
                onHighlightMessage(targetId)
                onScrollComplete()
                delay(1500)
                onHighlightMessage(null)
            } else if (isMessagesLoaded) {
                Toast.makeText(context, "Original message is no longer available.", Toast.LENGTH_SHORT).show()
                onScrollComplete()
            }
        }
    }

    val hideKeyboardController= LocalSoftwareKeyboardController.current
    val isSelfChat = remember(channelID) { channelID.startsWith("self_chat_") }
    val invitees = remember(channelID) { mutableStateListOf<com.zegocloud.uikit.service.defines.ZegoUIKitUser>() }
    var groupTotalMemberCount by remember(channelID) { mutableStateOf<Int?>(null) }
    val groupMemberUids by viewModel.groupMemberUids.collectAsState()
    var showMessageInfoDialog by remember { mutableStateOf(false) }
    var messageForInfo by remember { mutableStateOf<Message?>(null) }
    val currentUid = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    val directOtherUid = remember(channelID, isDirectChat, currentUid) {
        if (!isSelfChat && isDirectChat && channelID.contains("_") && !channelID.startsWith("-")) {
            channelID.split("_").firstOrNull { it != currentUid }
        } else null
    }
    LaunchedEffect(channelID) {
        invitees.clear()
        if (isSelfChat) {
            groupTotalMemberCount = 1
            return@LaunchedEffect
        }
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        val currentEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: return@LaunchedEffect

        val isDirect = !channelID.startsWith("-") && channelID != HomeViewModel.WORLD_CHAT_ID && channelID.contains("_")
        if (isDirect) {
            val ids = channelID.split("_")
            val otherUid = ids.firstOrNull { it != currentUid } ?: return@LaunchedEffect

            com.google.firebase.database.FirebaseDatabase.getInstance().reference
                .child("users")
                .child(otherUid)
                .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        val otherEmail = snapshot.child("email").getValue(String::class.java)
                        val otherName = snapshot.child("name").getValue(String::class.java) ?: otherEmail ?: ""
                        if (!otherEmail.isNullOrEmpty()) {
                            invitees.clear()
                            invitees.add(com.zegocloud.uikit.service.defines.ZegoUIKitUser(otherEmail, otherName))
                            Log.d("NotificationDebug", "Pre-loaded call invitee: $otherEmail ($otherName)")
                        }
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                })
        } else {
            if (channelID != HomeViewModel.WORLD_CHAT_ID) {
                viewModel.getGroupCallInviteesWithCount(channelID) { groupUsers, totalCount ->
                    invitees.clear()
                    invitees.addAll(groupUsers)
                    groupTotalMemberCount = totalCount
                    Log.d("NotificationDebug", "Pre-loaded call invitees for group chat $channelID: ${groupUsers.size} users, totalCount=$totalCount")
                }
            }
        }
    }

    val isChatDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
    val toolbarContentColor = if (isChatDark) Color.White else MaterialTheme.colorScheme.onSurface

    Column(modifier = Modifier.fillMaxSize()){
        if (selectedMessages.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isChatDark) colorResource(id = R.color.light_blue) else MaterialTheme.colorScheme.surface)
                    .then(
                        if (!isChatDark) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        else Modifier
                    )
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Back button + Selected count
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onClearSelection,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Clear Selection",
                                tint = toolbarContentColor
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${selectedMessages.size}",
                            color = toolbarContentColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Right: 5 Action buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // 1. Smart Reply
                        val hasTextMessage = selectedMessages.any { !it.message.isNullOrBlank() }
                        IconButton(
                            onClick = {
                                if (hasTextMessage) {
                                    onSmartReplyClicked()
                                } else {
                                    Toast.makeText(context, "Select a text message for Smart Reply", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_kchat_smart_reply),
                                contentDescription = "Smart Reply",
                                tint = if (hasTextMessage) (if (isChatDark) Color.White else MaterialTheme.colorScheme.primary) else toolbarContentColor.copy(alpha = 0.4f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 2. Copy
                        IconButton(
                            onClick = {
                                val formatted = MessageCopyFormatter.format(selectedMessages)
                                if (formatted.isNotEmpty()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("KChat Message", formatted)
                                    clipboard.setPrimaryClip(clip)
                                    val toastMsg = if (selectedMessages.size > 1) "Messages copied" else "Message copied"
                                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                }
                                onClearSelection()
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = toolbarContentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 3. Delete
                        IconButton(
                            onClick = onDeleteClicked,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = if (isChatDark) Color.White else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 4. Read Receipt / Info (Sender-only, single selection)
                        val isSingleOwnMessage = selectedMessages.size == 1 &&
                                currentUid.isNotEmpty() &&
                                selectedMessages.first().senderId == currentUid
                        if (isSingleOwnMessage) {
                            IconButton(
                                onClick = {
                                    val sel = selectedMessages.firstOrNull()
                                    if (sel != null && sel.senderId == currentUid) {
                                        messageForInfo = sel
                                        showMessageInfoDialog = true
                                    }
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Read Receipt / Info",
                                    tint = toolbarContentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 5. Saved Messages / Star / Unstar
                        val isAllSavedMessages = selectedMessages.isNotEmpty() && selectedMessages.all { it.isSavedMessage }
                        val isUnstarAction = isSelfChat && isAllSavedMessages

                        if (isUnstarAction) {
                            IconButton(
                                onClick = {
                                    viewModel.unstarSavedMessages(selectedMessages) { count ->
                                        val toastMsg = if (count > 1) "$count messages removed from Saved Messages" else if (count == 1) "Message removed from Saved Messages" else "Failed to remove message"
                                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                    }
                                    onClearSelection()
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Unstar",
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    viewModel.saveMessagesToSavedMessages(selectedMessages, channelID, channelName) { count ->
                                        val toastMsg = if (count > 1) "$count messages saved" else if (count == 1) "Message saved" else "Failed to save message"
                                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                    }
                                    onClearSelection()
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Saved Messages",
                                    tint = toolbarContentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            ChannelItem(
                channel = HomeChannel(
                    id = channelID,
                    name = channelName
                ),
                modifier = Modifier,
                shouldShowCallButtons = (channelID != HomeViewModel.WORLD_CHAT_ID && !isSelfChat),
                imageUrl = if (isSelfChat) null else otherUserImage,
                subtitle = presenceSubtitle,
                onClick = onProfileClicked,
                invitees = invitees,
                totalMemberCount = groupTotalMemberCount,
                trailingContent = null
            )
        }
        if (messages.isEmpty() && isSelfChat) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_saved_messages),
                            contentDescription = "Saved Messages",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Saved Messages",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Use this space to message yourself and save important messages.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onClearSelection()
                    },
                reverseLayout = false
            ) {
                itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                    val shouldShowDateHeader = if (message.createdAt <= 0L) {
                        false
                    } else if (index == 0) {
                        true
                    } else {
                        val prevCreatedAt = messages[index - 1].createdAt
                        if (prevCreatedAt <= 0L) {
                            val prevValid = messages.take(index).lastOrNull { it.createdAt > 0L }
                            if (prevValid == null) true else !DateTimeUtils.isSameCalendarDay(prevValid.createdAt, message.createdAt)
                        } else {
                            !DateTimeUtils.isSameCalendarDay(prevCreatedAt, message.createdAt)
                        }
                    }

                    val prevMessage = if (index > 0) messages[index - 1] else null
                    val nextMessage = if (index < messages.size - 1) messages[index + 1] else null

                    val isFirstInGroup = if (index == 0 || shouldShowDateHeader) {
                        true
                    } else if (prevMessage == null) {
                        true
                    } else if (prevMessage.senderId != message.senderId) {
                        true
                    } else if (message.createdAt > 0L && prevMessage.createdAt > 0L && (message.createdAt - prevMessage.createdAt > 300_000L)) {
                        true
                    } else if (message.isSavedMessage || prevMessage.isSavedMessage) {
                        true
                    } else {
                        false
                    }

                    val isLastInGroup = if (nextMessage == null) {
                        true
                    } else if (nextMessage.senderId != message.senderId) {
                        true
                    } else if (nextMessage.createdAt > 0L && message.createdAt > 0L && !DateTimeUtils.isSameCalendarDay(message.createdAt, nextMessage.createdAt)) {
                        true
                    } else if (nextMessage.createdAt > 0L && message.createdAt > 0L && (nextMessage.createdAt - message.createdAt > 300_000L)) {
                        true
                    } else if (message.isSavedMessage || nextMessage.isSavedMessage) {
                        true
                    } else {
                        false
                    }

                    val isSelected = selectedMessages.any { it.id == message.id }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = if (shouldShowDateHeader) 0.dp else if (isFirstInGroup) (if (index == 0) 4.dp else 5.dp) else 1.5.dp,
                                bottom = if (isLastInGroup) 4.dp else 1.5.dp
                            )
                    ) {
                        if (shouldShowDateHeader) {
                            val headerText = DateTimeUtils.getDateHeader(message.createdAt)
                            if (headerText.isNotEmpty()) {
                                DateSeparator(dateText = headerText)
                            }
                        }
                        ChatBubble(
                            message = message,
                            isSelected = isSelected,
                            isInSelectionMode = selectedMessages.isNotEmpty(),
                            isHighlighted = (message.id == highlightedMessageId),
                            isFirstInGroup = isFirstInGroup,
                            isLastInGroup = isLastInGroup,
                            onLongClick = { onToggleMessageSelection(message) },
                            onClick = { onToggleMessageSelection(message) },
                            onReply = onReply,
                            onQuotedMessageClick = onQuotedMessageClick,
                            onJumpToOriginalMessage = onJumpToOriginalMessage,
                            audioPlayerHelper = audioPlayerHelper,
                            videoPlayerHelper = videoPlayerHelper,
                            onFullScreenVideoClick = onFullScreenVideoClick,
                            isDirectChat = isDirectChat,
                            isWorldChat = (channelID == HomeViewModel.WORLD_CHAT_ID),
                            isSelfChat = isSelfChat,
                            groupMemberUids = groupMemberUids,
                            directOtherUid = directOtherUid
                        )
                    }
                }
            }
        }

        // Reply Preview Bar directly above composer
        if (replyingToMessage != null) {
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            val replySenderName = if (replyingToMessage.senderId == currentUid) {
                "You"
            } else {
                replyingToMessage.senderName?.ifBlank { "User" } ?: "User"
            }
            val replySnippet = if (!replyingToMessage.message.isNullOrBlank()) {
                replyingToMessage.message.trim()
            } else if (!replyingToMessage.imageUrl.isNullOrBlank()) {
                "[Photo]"
            } else if (!replyingToMessage.audioUrl.isNullOrBlank()) {
                "🎤 Voice message"
            } else if (!replyingToMessage.videoUrl.isNullOrBlank()) {
                "🎥 Video message"
            } else if (!replyingToMessage.fileUrl.isNullOrBlank()) {
                "📎 File"
            } else {
                ""
            }

            ReplyPreviewBar(
                senderName = replySenderName,
                messageSnippet = replySnippet,
                onDismiss = onDismissReply
            )
        }

        if (isRecordingAudio || isAudioPaused) {
            AudioRecordingBar(
                isPaused = isAudioPaused,
                recordingDurationMs = recordingDurationMs,
                pausedDurationMs = pausedAudioDurationMs,
                currentAmplitude = recordingAmplitude,
                isPlaying = isAudioPlaying,
                playPositionMs = audioPlayPositionMs,
                onPause = onPauseAudioRecording,
                onResume = onResumeAudioRecording,
                onCancelOrDelete = onCancelOrDeleteAudioRecording,
                onTogglePlayPause = onTogglePlayPauseAudioPreview,
                onSeekTo = onSeekAudioPreview,
                onSend = onSendAudioRecording
            )
        } else {
            val isChatDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
            Row(modifier = Modifier
                .fillMaxWidth()
                .background(if (isChatDark) Color.DarkGray else MaterialTheme.colorScheme.surface)
                .then(
                    if (!isChatDark) Modifier.border(width = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    else Modifier
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom
            ){
                IconButton(
                    onClick = {
                        onComposerTextChange("")
                        onImageClicked()
                    },
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.attach),
                        contentDescription = "attach",
                        modifier = Modifier,
                        tint = if (isChatDark) colorResource(id = R.color.light_blue) else MaterialTheme.colorScheme.primary
                    )
                }
                Box(modifier = Modifier.padding(bottom = 2.dp)) {
                    KChatAiEntryPoint(
                        onClick = onAiMenuClicked
                    )
                }
                TextField(
                    value = composerText,
                    onValueChange = onComposerTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 130.dp),
                    placeholder = { Text(text = "Type your message...", color = if (isChatDark) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant)},
                    maxLines = 5,
                    minLines = 1,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            hideKeyboardController?.hide()
                        }),
                    colors = TextFieldDefaults.colors().copy(
                        focusedContainerColor = if (isChatDark) Color.DarkGray else MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = if (isChatDark) Color.DarkGray else MaterialTheme.colorScheme.surface,
                        focusedTextColor = if (isChatDark) Color.White else MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = if (isChatDark) Color.White else MaterialTheme.colorScheme.onSurface,
                        focusedPlaceholderColor = if (isChatDark) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = if (isChatDark) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                if (composerText.isNotBlank()) {
                    IconButton(
                        onClick = {
                            onSendMessage(composerText)
                            onComposerTextChange("")
                        },
                        modifier = Modifier.padding(bottom = 2.dp)
                    ){
                        Icon(
                            painter = painterResource(R.drawable.send),
                            contentDescription = "send",
                            modifier = Modifier,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    IconButton(
                        onClick = onStartAudioRecording,
                        modifier = Modifier.padding(bottom = 2.dp)
                    ){
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "record audio",
                            modifier = Modifier,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (showMessageInfoDialog && messageForInfo != null && messageForInfo?.senderId == currentUid) {
            val isMsgSavedFromAnotherChat = messageForInfo!!.isSavedMessage &&
                    !messageForInfo!!.originalChatId.isNullOrEmpty() &&
                    !messageForInfo!!.originalChatId!!.startsWith("self_chat_")
            val targetChannelId = if (isMsgSavedFromAnotherChat) messageForInfo!!.originalChatId!! else channelID
            val targetChannelName = if (isMsgSavedFromAnotherChat) (messageForInfo!!.originalChatName ?: channelName) else channelName
            val targetIsDirect = if (isMsgSavedFromAnotherChat) {
                !targetChannelId.startsWith("-") && targetChannelId != HomeViewModel.WORLD_CHAT_ID && targetChannelId.contains("_")
            } else isDirectChat
            val targetIsWorld = if (isMsgSavedFromAnotherChat) {
                targetChannelId == HomeViewModel.WORLD_CHAT_ID
            } else (channelID == HomeViewModel.WORLD_CHAT_ID)
            val targetIsSelfChat = isSelfChat && !isMsgSavedFromAnotherChat

            MessageInfoDialog(
                message = messageForInfo!!,
                channelID = targetChannelId,
                channelName = targetChannelName,
                isDirectChat = targetIsDirect,
                isWorldChat = targetIsWorld,
                isSelfChat = targetIsSelfChat,
                groupMemberUids = groupMemberUids,
                viewModel = viewModel,
                onDismiss = {
                    showMessageInfoDialog = false
                    messageForInfo = null
                    onClearSelection()
                }
            )
        }
    }
}

@Composable
fun DateSeparator(dateText: String) {
    val isChatDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isChatDark) Color(0xFF1E2D4A) else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                text = dateText,
                color = if (isChatDark) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ReplyPreviewBar(
    senderName: String,
    messageSnippet: String,
    onDismiss: () -> Unit
) {
    val isReplyDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isReplyDark) Color(0xFF262D37) else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Replying to $senderName",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = messageSnippet.ifBlank { "[Message]" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel reply",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun QuotedReplyCard(
    senderName: String,
    messageSnippet: String,
    isCurrentUserBubble: Boolean,
    onClick: () -> Unit
) {
    val isQuoteDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
    val accentColor = if (isCurrentUserBubble) Color.White else MaterialTheme.colorScheme.primary
    val cardBgColor = if (isCurrentUserBubble) Color.Black.copy(alpha = 0.15f) else (if (isQuoteDark) Color.Black.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    val snippetColor = if (isCurrentUserBubble) Color.White.copy(alpha = 0.85f) else (if (isQuoteDark) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardBgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .background(accentColor, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = senderName,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = messageSnippet.ifBlank { "[Message]" },
                color = snippetColor,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: Message,
    isSelected: Boolean,
    isInSelectionMode: Boolean = false,
    isHighlighted: Boolean = false,
    isFirstInGroup: Boolean = true,
    isLastInGroup: Boolean = true,
    onLongClick: () -> Unit,
    onClick: () -> Unit = {},
    onReply: (Message) -> Unit = {},
    onQuotedMessageClick: (String) -> Unit = {},
    onJumpToOriginalMessage: (String, String, String) -> Unit = { _, _, _ -> },
    audioPlayerHelper: AudioPlayerHelper? = null,
    videoPlayerHelper: VideoPlayerHelper? = null,
    onFullScreenVideoClick: (String) -> Unit = {},
    isDirectChat: Boolean = false,
    isWorldChat: Boolean = false,
    isSelfChat: Boolean = false,
    groupMemberUids: Set<String> = emptySet(),
    directOtherUid: String? = null
) {
    val currentUid = Firebase.auth.currentUser?.uid ?: ""
    val isSavedFromAnotherChat = isSelfChat && message.isSavedMessage &&
            !message.originalChatId.isNullOrEmpty() &&
            !message.originalChatId.startsWith("self_chat_")

    val isCurrentUser = if (isSavedFromAnotherChat) {
        val origSenderId = message.originalSenderId?.takeIf { it.isNotBlank() } ?: message.senderId
        origSenderId == currentUid
    } else {
        message.senderId == currentUid
    }

    val isChatDark = MaterialTheme.colorScheme.background == Color(0xFF162542)

    val bubbleColor = if (isSelected) {
        Color.Gray.copy(alpha = 0.5f)
    } else if (isCurrentUser) {
        colorResource(id = R.color.light_blue)
    } else {
        if (isChatDark) Color.DarkGray else MaterialTheme.colorScheme.surface
    }

    val highlightBorderColor by animateColorAsState(
        targetValue = if (isHighlighted) colorResource(id = R.color.light_blue) else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "highlight_border"
    )
    val highlightBgOverlay by animateColorAsState(
        targetValue = if (isHighlighted) colorResource(id = R.color.light_blue).copy(alpha = 0.25f) else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "highlight_bg"
    )

    var showFullImage by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val thresholdPx = with(density) { 52.dp.toPx() }
    val maxDragPx = with(density) { 72.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    val hasQuotedReply = !message.replyToSenderName.isNullOrBlank() || !message.replyToMessageText.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isSavedFromAnotherChat) 4.dp else 0.dp)
    ) {
        if (isSavedFromAnotherChat) {
            val origChatId = message.originalChatId ?: ""
            val origChatName = message.originalChatName?.takeIf { it.isNotBlank() } ?: "Chat"
            val origSenderId = message.originalSenderId?.takeIf { it.isNotBlank() } ?: message.senderId
            val origSenderName = message.originalSenderName?.takeIf { it.isNotBlank() } ?: message.senderName.ifBlank { "User" }

            val isOrigSenderYou = origSenderId == currentUid
            val isGroup = origChatId.startsWith("-") || origChatId == HomeViewModel.WORLD_CHAT_ID || (!origChatId.contains("_") && origChatId.isNotEmpty())
            val isOrigWorldChat = origChatId == HomeViewModel.WORLD_CHAT_ID

            val senderLabel = if (isOrigSenderYou) "You" else origSenderName
            val targetLabel = if (isGroup) {
                if (isOrigWorldChat) "World Chat" else origChatName
            } else {
                if (isOrigSenderYou) origChatName else "You"
            }

            val headerDateStr = if (message.createdAt > 0L) {
                DateTimeUtils.formatConversationTime(message.createdAt)
            } else ""

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable(enabled = !isInSelectionMode) {
                        val targetMsgId = message.originalMessageId?.takeIf { it.isNotBlank() } ?: message.id
                        onJumpToOriginalMessage(origChatId, origChatName, targetMsgId)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                val avatarUrl = if (isOrigSenderYou) {
                    Firebase.auth.currentUser?.photoUrl?.toString()
                } else {
                    message.senderImage ?: message.profileImageUrl
                }

                if (!avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.friend),
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = buildAnnotatedString {
                        append(senderLabel)
                        append(" • ")
                        append(targetLabel)
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (headerDateStr.isNotEmpty()) {
                    Text(
                        text = headerDateStr.uppercase(Locale.getDefault()),
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Jump to message",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp, horizontal = 8.dp)
        ) {
            // Reply indicator revealed as bubble drags right
            if (offsetX.value > 0f) {
                val progress = (offsetX.value / thresholdPx).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .graphicsLayer {
                            alpha = progress
                            scaleX = 0.5f + 0.5f * progress
                            scaleY = 0.5f + 0.5f * progress
                        }
                        .size(36.dp)
                        .background(
                            color = colorResource(id = R.color.light_blue).copy(alpha = 0.25f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Reply",
                        tint = colorResource(id = R.color.light_blue),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            val alignment = if (isSavedFromAnotherChat) {
                Alignment.CenterStart
            } else if (isCurrentUser) {
                Alignment.CenterEnd
            } else {
                Alignment.CenterStart
            }

            Row(
                modifier = Modifier
                    .align(alignment)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .then(
                        if (!isInSelectionMode) {
                            Modifier.pointerInput(message.id) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        hasTriggeredHaptic = false
                                    },
                                    onDragEnd = {
                                        val shouldTrigger = offsetX.value >= thresholdPx
                                        coroutineScope.launch {
                                            offsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessLow
                                                )
                                            )
                                        }
                                        if (shouldTrigger) {
                                            onReply(message)
                                        }
                                        hasTriggeredHaptic = false
                                    },
                                    onDragCancel = {
                                        coroutineScope.launch {
                                            offsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessLow
                                                )
                                            )
                                        }
                                        hasTriggeredHaptic = false
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        coroutineScope.launch {
                                            val newOffset = (offsetX.value + dragAmount * 0.5f).coerceIn(0f, maxDragPx)
                                            offsetX.snapTo(newOffset)
                                            if (newOffset >= thresholdPx && !hasTriggeredHaptic) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                hasTriggeredHaptic = true
                                            } else if (newOffset < thresholdPx && hasTriggeredHaptic) {
                                                hasTriggeredHaptic = false
                                            }
                                        }
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                val shouldShowGroupSender = !isCurrentUser && !isDirectChat && !isSelfChat && !isSavedFromAnotherChat
                if (shouldShowGroupSender) {
                    if (isFirstInGroup) {
                        if (!message.senderImage.isNullOrEmpty()) {
                            AsyncImage(
                                model = message.senderImage,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(24.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.friend),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        Spacer(modifier = Modifier.width(28.dp))
                    }
                }

                Column {
                    if (shouldShowGroupSender && isFirstInGroup) {
                        Text(
                            text = message.senderName.ifBlank { "User" },
                            color = if (isChatDark) colorResource(id = R.color.light_blue) else MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                        )
                    }

                    val isImageMessage = message.imageUrl != null
                    val isMediaMessage = isImageMessage || !message.videoUrl.isNullOrBlank() || !message.audioUrl.isNullOrBlank() || !message.fileUrl.isNullOrBlank()
                    val bubblePadding = if (isImageMessage) {
                        PaddingValues(3.dp)
                    } else if (isMediaMessage) {
                        PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    } else {
                        PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    }

                    Box(
                        modifier = Modifier
                            .widthIn(min = 48.dp, max = 290.dp)
                            .background(
                                color = bubbleColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .then(
                                if (!isChatDark && !isCurrentUser && !isSelected) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                } else Modifier
                            )
                            .then(
                                if (isHighlighted) {
                                    Modifier
                                        .border(
                                            width = 2.dp,
                                            color = highlightBorderColor,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .background(
                                            color = highlightBgOverlay,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                } else Modifier
                            )
                            .combinedClickable(
                                onLongClick = onLongClick,
                                onClick = {
                                    if (isInSelectionMode) {
                                        onClick()
                                    } else if (isSavedFromAnotherChat) {
                                        val targetMsgId = message.originalMessageId?.takeIf { it.isNotBlank() } ?: message.id
                                        val origChatId = message.originalChatId ?: ""
                                        val origChatName = message.originalChatName?.takeIf { it.isNotBlank() } ?: "Chat"
                                        onJumpToOriginalMessage(origChatId, origChatName, targetMsgId)
                                    } else if (message.imageUrl != null) {
                                        showFullImage = true
                                    }
                                }
                            )
                            .padding(bubblePadding)
                    ) {
                        Column {
                            if (hasQuotedReply) {
                                QuotedReplyCard(
                                    senderName = message.replyToSenderName ?: "User",
                                    messageSnippet = message.replyToMessageText ?: "",
                                    isCurrentUserBubble = isCurrentUser,
                                    onClick = {
                                        if (isInSelectionMode) {
                                            onClick()
                                        } else {
                                            message.replyToMessageId?.let { targetId ->
                                                onQuotedMessageClick(targetId)
                                            }
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            if (message.imageUrl != null) {
                                var imageAspectRatio by remember(message.imageUrl) { mutableFloatStateOf(4f / 3f) }

                                AsyncImage(
                                    model = message.imageUrl,
                                    contentDescription = null,
                                    onSuccess = { state ->
                                        val intrinsicWidth = state.painter.intrinsicSize.width
                                        val intrinsicHeight = state.painter.intrinsicSize.height
                                        if (intrinsicWidth > 0f && intrinsicHeight > 0f) {
                                            val ratio = intrinsicWidth / intrinsicHeight
                                            imageAspectRatio = ratio.coerceIn(0.55f, 2.0f)
                                        }
                                    },
                                    modifier = Modifier
                                        .widthIn(min = 140.dp, max = 270.dp)
                                        .aspectRatio(imageAspectRatio, matchHeightConstraintsFirst = false)
                                        .clip(RoundedCornerShape(9.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                if (!message.message.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = message.message.trim(),
                                        color = if (isCurrentUser) Color.White else (if (isChatDark) Color.White else MaterialTheme.colorScheme.onSurface),
                                        fontSize = 14.sp,
                                        lineHeight = 18.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            } else if (!message.videoUrl.isNullOrBlank() && videoPlayerHelper != null) {
                                VideoMessageBubble(
                                    videoUrl = message.videoUrl,
                                    durationMs = message.videoDurationMs ?: 0L,
                                    isCurrentUser = isCurrentUser,
                                    isInSelectionMode = isInSelectionMode,
                                    videoPlayerHelper = videoPlayerHelper,
                                    onBubbleClick = onClick,
                                    onFullScreenClick = onFullScreenVideoClick
                                )
                            } else if (!message.audioUrl.isNullOrBlank() && audioPlayerHelper != null) {
                                AudioMessageBubble(
                                    audioUrl = message.audioUrl,
                                    durationMs = message.audioDurationMs ?: 0L,
                                    isCurrentUser = isCurrentUser,
                                    isInSelectionMode = isInSelectionMode,
                                    audioPlayerHelper = audioPlayerHelper,
                                    onBubbleClick = onClick
                                )
                            } else if (!message.fileUrl.isNullOrBlank()) {
                                FileMessageBubble(
                                    fileUrl = message.fileUrl,
                                    fileName = message.fileName ?: "Document",
                                    fileMimeType = message.fileMimeType,
                                    fileSizeBytes = message.fileSizeBytes ?: 0L,
                                    isCurrentUser = isCurrentUser,
                                    isInSelectionMode = isInSelectionMode,
                                    onBubbleClick = onClick
                                )
                            } else {
                                Text(
                                    text = message.message?.trim() ?: "",
                                    color = if (isCurrentUser) Color.White else (if (isChatDark) Color.White else MaterialTheme.colorScheme.onSurface),
                                    fontSize = 14.5.sp,
                                    lineHeight = 19.sp
                                )
                            }

                            val formattedMessageTime = if (message.createdAt > 0L) DateTimeUtils.formatMessageTime(message.createdAt) else ""
                            if (formattedMessageTime.isNotEmpty() || isCurrentUser || message.isSavedMessage) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(end = if (isImageMessage) 4.dp else 0.dp, bottom = if (isImageMessage) 2.dp else 0.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (message.isSavedMessage) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Starred",
                                            tint = Color(0xFFFFC107).copy(alpha = 0.9f),
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                    if (formattedMessageTime.isNotEmpty()) {
                                        Text(
                                            text = formattedMessageTime,
                                            color = if (isCurrentUser) Color.White.copy(alpha = 0.7f) else (if (isChatDark) Color.LightGray.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant),
                                            fontSize = 10.sp
                                        )
                                    }
                                    if (isCurrentUser) {
                                        val receiptStatus = if (isSavedFromAnotherChat) {
                                            val origChatId = message.originalChatId ?: ""
                                            val isOrigDirect = origChatId.contains("_") && !origChatId.startsWith("-") && origChatId != HomeViewModel.WORLD_CHAT_ID
                                            val isOrigWorld = origChatId == HomeViewModel.WORLD_CHAT_ID
                                            MessageReceiptHelper.getReceiptStatus(
                                                message = message,
                                                currentUid = currentUid,
                                                isDirectChat = isOrigDirect,
                                                isWorldChat = isOrigWorld,
                                                isSelfChat = false,
                                                groupMemberUids = groupMemberUids,
                                                directOtherUid = null
                                            )
                                        } else {
                                            MessageReceiptHelper.getReceiptStatus(
                                                message = message,
                                                currentUid = currentUid,
                                                isDirectChat = isDirectChat,
                                                isWorldChat = isWorldChat,
                                                isSelfChat = isSelfChat,
                                                groupMemberUids = groupMemberUids,
                                                directOtherUid = directOtherUid
                                            )
                                        }
                                        KChatReceipt(status = receiptStatus)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ✅ IMPORTANT: outside layout to avoid alignment issues
    if (showFullImage && message.imageUrl != null) {
        FullScreenImage(
            imageUrl = message.imageUrl,
            onDismiss = { showFullImage = false }
        )
    }
}


@Composable
fun CallButton(
    isVideoCall: Boolean,
    invitees: List<com.zegocloud.uikit.service.defines.ZegoUIKitUser>,
    callId: String? = null,
    totalMemberCount: Int? = null
) {
    val currentInvitees = invitees.toList()
    val isGroup = callId != null && callId.startsWith("call_group_")
    val isExceedingLimit = isGroup && ((totalMemberCount != null && totalMemberCount > 10) || currentInvitees.size >= 10)
    val isGroupLoading = isGroup && currentInvitees.isEmpty() && (totalMemberCount == null || totalMemberCount > 1)
    val isDirectLoading = !isGroup && currentInvitees.isEmpty()

    Box(modifier = Modifier.size(50.dp), contentAlignment = Alignment.Center) {
        AndroidView(factory = { context ->
            val button = ZegoSendCallInvitationButton(context)
            button.setIsVideoCall(isVideoCall)
            button.resourceID = "zego_data"
            button
        }, modifier = Modifier.fillMaxSize()) { zegoCallButton ->
            zegoCallButton.setIsVideoCall(isVideoCall)
            zegoCallButton.setInvitees(currentInvitees)
            if (!callId.isNullOrBlank()) {
                zegoCallButton.setCallID(callId)
            }
            android.util.Log.d("NotificationDebug", "CallButton updated: isVideo=$isVideoCall, callId=$callId, inviteesCount=${currentInvitees.size} (${currentInvitees.map { it.userID }}), totalMembers=$totalMemberCount")
        }

        if (isExceedingLimit || isGroupLoading || isDirectLoading) {
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isExceedingLimit) {
                            Toast.makeText(context, "Group calls are limited to 10 participants.", Toast.LENGTH_SHORT).show()
                        } else if (isGroupLoading || isDirectLoading) {
                            Toast.makeText(context, "Loading call info, please wait...", Toast.LENGTH_SHORT).show()
                        }
                    }
            )
        }
    }
}

@Composable
fun FullScreenImage(imageUrl: String?, onDismiss: () -> Unit) {
    if (imageUrl == null) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onDismiss()
                }
        ) {
            // Main Image Container (Pinch to Zoom, Drag to Pan)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // Prevents clicks on image from dismissing dialog
                        },
                    contentScale = ContentScale.Fit
                )
            }

            // Top Control Bar (Back + Download)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                // Download Button
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            saveImageToGallery(context, imageUrl)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download to Gallery",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

fun deleteSavedImageFromGallery(context: android.content.Context, imageUrl: String) {
    try {
        val prefs = context.getSharedPreferences("kchat_saved_images", android.content.Context.MODE_PRIVATE)
        val savedUriStr = prefs.getString(imageUrl, null)
        if (!savedUriStr.isNullOrEmpty()) {
            val uri = android.net.Uri.parse(savedUriStr)
            context.contentResolver.delete(uri, null, null)
            prefs.edit().remove(imageUrl).apply()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

suspend fun saveImageToGallery(context: android.content.Context, imageUrl: String) {
    withContext(Dispatchers.IO) {
        try {
            val loader = context.imageLoader
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .build()
            
            val result = loader.execute(request)
            val drawable = result.drawable
            if (drawable is BitmapDrawable) {
                val bitmap = drawable.bitmap
                val filename = "kchat_${System.currentTimeMillis()}.jpg"
                
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KChat")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                
                val resolver = context.contentResolver
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                
                if (imageUri != null) {
                    val outputStream: OutputStream? = resolver.openOutputStream(imageUri)
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                        outputStream.close()
                    }
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(imageUri, contentValues, null, null)
                    }

                    try {
                        val prefs = context.getSharedPreferences("kchat_saved_images", android.content.Context.MODE_PRIVATE)
                        prefs.edit().putString(imageUrl, imageUri.toString()).apply()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Image saved to Gallery successfully!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save image: URI was null", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to load image cache", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error saving image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

