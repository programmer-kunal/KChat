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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import com.example.kchat.feature.chat.audio.AudioMessageBubble
import com.example.kchat.feature.chat.audio.AudioPlayerHelper
import com.example.kchat.feature.chat.audio.AudioRecorderHelper
import com.example.kchat.feature.chat.audio.AudioUtils
import com.example.kchat.feature.chat.file.FileMessageBubble
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
fun ChatScreen(navController: NavController,channelId:String,channelName:String){
    Log.d("NotificationDebug", "ChatScreen composable entered. Parameters: channelId=$channelId, channelName=$channelName")
    Scaffold(
        containerColor = colorResource(id =R.color.dark_blue)
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

        DisposableEffect(Unit) {
            onDispose {
                audioPlayerHelper.release()
                audioRecorderHelper.cancelRecording()
                videoPlayerHelper.release()
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
                cameraImageUri.value?.let{
                    val uid = com.google.firebase.Firebase.auth.currentUser?.uid
                    val reply = replyingToMessage
                    val replyId = reply?.id
                    val replySender = if (reply != null) {
                        if (reply.senderId == uid) "You" else (reply.senderName?.ifBlank { "User" } ?: "User")
                    } else null
                    val replyText = if (reply != null) {
                        if (!reply.message.isNullOrBlank()) reply.message.trim()
                        else if (!reply.imageUrl.isNullOrBlank()) "[Photo]"
                        else if (!reply.audioUrl.isNullOrBlank()) "🎤 Voice message"
                        else if (!reply.videoUrl.isNullOrBlank()) "🎥 Video message"
                        else if (!reply.fileUrl.isNullOrBlank()) "📎 File"
                        else ""
                    } else null

                    viewModel.sendImageMessage(
                        uri = it,
                        channelID = channelId,
                        channelName = channelName,
                        replyToMessageId = replyId,
                        replyToSenderName = replySender,
                        replyToMessageText = replyText
                    )
                    replyingToMessage = null
                }

            }
        }
        val imageLauncher=rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
                uri?.let{
                    val uid = com.google.firebase.Firebase.auth.currentUser?.uid
                    val reply = replyingToMessage
                    val replyId = reply?.id
                    val replySender = if (reply != null) {
                        if (reply.senderId == uid) "You" else (reply.senderName?.ifBlank { "User" } ?: "User")
                    } else null
                    val replyText = if (reply != null) {
                        if (!reply.message.isNullOrBlank()) reply.message.trim()
                        else if (!reply.imageUrl.isNullOrBlank()) "[Photo]"
                        else if (!reply.audioUrl.isNullOrBlank()) "🎤 Voice message"
                        else if (!reply.videoUrl.isNullOrBlank()) "🎥 Video message"
                        else if (!reply.fileUrl.isNullOrBlank()) "📎 File"
                        else ""
                    } else null

                    viewModel.sendImageMessage(
                        uri = it,
                        channelID = channelId,
                        channelName = channelName,
                        replyToMessageId = replyId,
                        replyToSenderName = replySender,
                        replyToMessageText = replyText
                    )
                    replyingToMessage = null
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

        // --- Video Messaging States & Launchers ---
        var pendingVideo by remember { mutableStateOf<PendingVideo?>(null) }
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
                            pendingVideo = PendingVideo(
                                file = file,
                                durationMs = metadata.durationMs,
                                thumbnail = metadata.thumbnail
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
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { videoUri ->
                coroutineScope.launch {
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
                            pendingVideo = PendingVideo(
                                file = tempFile,
                                durationMs = metadata.durationMs,
                                thumbnail = metadata.thumbnail
                            )
                        }
                    } else {
                        Toast.makeText(context, "Could not open selected video", Toast.LENGTH_SHORT).show()
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
        var pendingFile by remember { mutableStateOf<PendingFile?>(null) }

        val documentPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let { docUri ->
                coroutineScope.launch {
                    val metadata = FileUtils.extractFileMetadata(context, docUri)
                    if (metadata.fileSizeBytes > FileUtils.MAX_FILE_SIZE_BYTES) {
                        Toast.makeText(context, "File exceeds 25 MB limit", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val tempFile = FileUtils.copyUriToTempFile(context, docUri, metadata.fileName)
                    if (tempFile != null) {
                        pendingFile = PendingFile(
                            file = tempFile,
                            fileName = metadata.fileName,
                            mimeType = metadata.mimeType,
                            fileSizeBytes = tempFile.length()
                        )
                    } else {
                        Toast.makeText(context, "Could not open selected file", Toast.LENGTH_SHORT).show()
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
        var targetScrollMessageId by remember { mutableStateOf<String?>(null) }
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
                pendingVideo != null ||
                pendingFile != null

        BackHandler(enabled = fullScreenVideoUrl != null) {
            fullScreenVideoUrl = null
        }

        BackHandler(enabled = pendingVideo != null && fullScreenVideoUrl == null) {
            pendingVideo?.file?.delete()
            pendingVideo = null
        }

        BackHandler(enabled = pendingFile != null && fullScreenVideoUrl == null && pendingVideo == null) {
            pendingFile?.file?.delete()
            pendingFile = null
        }

        BackHandler(enabled = isRecordingAudio && !hasActiveOverlay) {
            audioRecorderHelper.cancelRecording()
        }

        BackHandler(enabled = selectedMessages.isNotEmpty() && !hasActiveOverlay) {
            selectedMessages = emptyList()
        }

        val currentUid = Firebase.auth.currentUser?.uid ?: return@Scaffold
        val isDirectChat = remember(channelId, currentUid) {
            channelId.contains("_") && channelId.split("_").let { parts ->
                parts.size == 2 && currentUid in parts
            }
        }
        val db = Firebase.database
        var otherUserEmail by remember { mutableStateOf<String?>(null) }
        var otherUserImage by remember { mutableStateOf<String?>(null) }
        var otherUserName by remember { mutableStateOf(channelName) }
        var otherUserStatus by remember { mutableStateOf("Offline") }
        var otherUserOnline by remember { mutableStateOf(false) }
        var otherUserLastSeen by remember { mutableStateOf<Long?>(null) }

        DisposableEffect(channelId) {
            if (!isDirectChat) {
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
            val presenceSubtitle = if (isDirectChat) {
                DateTimeUtils.formatPresence(otherUserOnline, otherUserLastSeen)
            } else {
                null
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
                otherUserImage = otherUserImage,
                onProfileClicked = { showProfileDialog = true },
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
                replyingToMessage = replyingToMessage,
                onReply = { msg -> replyingToMessage = msg },
                onDismissReply = { replyingToMessage = null },
                onQuotedMessageClick = { quotedId -> targetScrollMessageId = quotedId },
                presenceSubtitle = presenceSubtitle,
                isRecordingAudio = isRecordingAudio,
                recordingDurationMs = recordingDurationMs,
                onCancelAudioRecording = { audioRecorderHelper.cancelRecording() },
                onSendAudioRecording = sendAudioAction,
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
                    if (navController.context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        val started = audioRecorderHelper.startRecording()
                        if (!started) {
                            Toast.makeText(context, "Could not start audio recording", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
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

        if (pendingVideo != null) {
            val video = pendingVideo!!
            VideoPreviewDialog(
                file = video.file,
                durationMs = video.durationMs,
                thumbnail = video.thumbnail,
                onDismiss = {
                    video.file.delete()
                    pendingVideo = null
                },
                onSend = { file, durationMs ->
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

                    viewModel.sendVideoMessage(
                        file = file,
                        durationMs = durationMs,
                        channelID = channelId,
                        channelName = channelName,
                        replyToMessageId = replyId,
                        replyToSenderName = replySender,
                        replyToMessageText = replyText
                    )
                    replyingToMessage = null
                    pendingVideo = null
                }
            )
        }

        if (pendingFile != null) {
            val fileData = pendingFile!!
            FilePreviewDialog(
                file = fileData.file,
                fileName = fileData.fileName,
                mimeType = fileData.mimeType,
                fileSizeBytes = fileData.fileSizeBytes,
                onDismiss = {
                    fileData.file.delete()
                    pendingFile = null
                },
                onSend = { file, fileName, mimeType, fileSizeBytes ->
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

                    viewModel.sendFileMessage(
                        file = file,
                        fileName = fileName,
                        mimeType = mimeType,
                        fileSizeBytes = fileSizeBytes,
                        channelID = channelId,
                        channelName = channelName,
                        replyToMessageId = replyId,
                        replyToSenderName = replySender,
                        replyToMessageText = replyText
                    )
                    replyingToMessage = null
                    pendingFile = null
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
                containerColor = colorResource(id = R.color.dark_blue),
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        text = if (isSingle) "Delete Message" else "Delete Messages (${selectedMessages.size})",
                        color = colorResource(id = R.color.light_blue),
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
                            color = Color.White,
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
                                        viewModel.deleteMessage(channelId, msg.id)
                                    }
                                    selectedMessages = emptyList()
                                    showDeleteOptionsDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Red.copy(alpha = 0.85f)
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Delete for Everyone",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Delete for Me button
                        Button(
                            onClick = {
                                selectedMessages.forEach { msg ->
                                    viewModel.deleteMessageForMe(channelId, msg.id)
                                }
                                selectedMessages = emptyList()
                                showDeleteOptionsDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.DarkGray
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Delete for Me",
                                color = Color.White,
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
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                confirmButton = {} // confirmation handled inside custom column content
            )
        }

        // --- User Profile/Group Info Dialog ---
        if (showProfileDialog) {
            val isDirect = isDirectChat
            val nameToShow = if (isDirect) otherUserName else channelName
            val emailToShow = if (isDirect) otherUserEmail else null
            val imageToShow = if (isDirect) otherUserImage else null
            val statusToShow = if (isDirect) otherUserStatus else "Group Chat"

            AlertDialog(
                onDismissRequest = { showProfileDialog = false },
                confirmButton = {
                    TextButton(onClick = { showProfileDialog = false }) {
                        Text("Close", color = colorResource(id = R.color.light_blue))
                    }
                },
                containerColor = colorResource(id = R.color.dark_blue),
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        text = if (isDirect) "User Profile" else "Group Info",
                        color = colorResource(id = R.color.light_blue),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar Box
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(colorResource(id = R.color.light_blue)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!imageToShow.isNullOrEmpty()) {
                                AsyncImage(
                                    model = imageToShow,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = nameToShow.firstOrNull()?.uppercase() ?: "G",
                                    color = Color.White,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Name
                        Text(
                            text = nameToShow,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Email (if available)
                        if (!emailToShow.isNullOrEmpty()) {
                            Text(
                                text = emailToShow,
                                color = Color.LightGray,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (statusToShow == "Online") Color(0f, 0.55f, 0.65f, 0.2f)
                                    else Color.DarkGray
                                )
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (statusToShow == "Online") {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0f, 0.55f, 0.65f, 1f))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = statusToShow,
                                    color = if (statusToShow == "Online") Color(0f, 0.55f, 0.65f, 1f) else Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
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
    replyingToMessage: Message? = null,
    onReply: (Message) -> Unit = {},
    onDismissReply: () -> Unit = {},
    onQuotedMessageClick: (String) -> Unit = {},
    presenceSubtitle: String? = null,
    isRecordingAudio: Boolean = false,
    recordingDurationMs: Long = 0L,
    onCancelAudioRecording: () -> Unit = {},
    onSendAudioRecording: () -> Unit = {},
    audioPlayerHelper: AudioPlayerHelper? = null,
    videoPlayerHelper: VideoPlayerHelper? = null,
    onFullScreenVideoClick: (String) -> Unit = {}
){
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(targetScrollMessageId) {
        val targetId = targetScrollMessageId
        if (!targetId.isNullOrBlank() && messages.isNotEmpty()) {
            val targetIndex = messages.indexOfFirst { it.id == targetId }
            if (targetIndex != -1) {
                listState.animateScrollToItem(targetIndex)
            } else {
                Toast.makeText(context, "Original message not found", Toast.LENGTH_SHORT).show()
            }
            onScrollComplete()
        }
    }

    val hideKeyboardController= LocalSoftwareKeyboardController.current
    val invitees = remember { mutableStateListOf<com.zegocloud.uikit.service.defines.ZegoUIKitUser>() }
    LaunchedEffect(channelID) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        val currentEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: return@LaunchedEffect

        if (channelID.contains("_")) {
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
            viewModel.getAllUserEmails(channelID) { emailList ->
                val list = emailList
                    .filter { it.isNotBlank() && it != currentEmail }
                    .map { email -> com.zegocloud.uikit.service.defines.ZegoUIKitUser(email, email) }
                invitees.clear()
                invitees.addAll(list)
                Log.d("NotificationDebug", "Pre-loaded call invitees for group chat: ${list.size} users")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()){
        Box(modifier = Modifier.fillMaxWidth()) {
            ChannelItem(
                channel = HomeChannel(
                    id = channelID,
                    name = channelName
                ),
                modifier = Modifier,
                shouldShowCallButtons = true,
                imageUrl = otherUserImage,
                subtitle = presenceSubtitle,
                onClick = onProfileClicked,
                invitees = invitees
            )

            // Selection toolbar: Smart Reply (for direct 1-to-1), Copy, Delete, and Clear
            if (selectedMessages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 110.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isDirectChat) {
                        IconButton(onClick = onSmartReplyClicked) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_kchat_smart_reply),
                                contentDescription = "Smart Reply",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    val hasCopyableText = selectedMessages.any { !it.message.isNullOrBlank() }
                    if (hasCopyableText) {
                        IconButton(onClick = {
                            val formatted = MessageCopyFormatter.format(selectedMessages)
                            if (formatted.isNotEmpty()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("KChat Message", formatted)
                                clipboard.setPrimaryClip(clip)
                                val toastMsg = if (selectedMessages.size > 1) "Messages copied" else "Message copied"
                                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                            }
                            onClearSelection()
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Messages",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    IconButton(onClick = onDeleteClicked) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Options",
                            tint = Color.Red,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
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
                val isSelected = selectedMessages.any { it.id == message.id }
                Column(modifier = Modifier.fillMaxWidth()) {
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
                        onLongClick = { onToggleMessageSelection(message) },
                        onClick = { onToggleMessageSelection(message) },
                        onReply = onReply,
                        onQuotedMessageClick = onQuotedMessageClick,
                        audioPlayerHelper = audioPlayerHelper,
                        videoPlayerHelper = videoPlayerHelper,
                        onFullScreenVideoClick = onFullScreenVideoClick
                    )
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

        if (isRecordingAudio) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.DarkGray)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Recording ${AudioUtils.formatDuration(recordingDurationMs)}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancelAudioRecording) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Cancel recording",
                            tint = Color.LightGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onSendAudioRecording) {
                        Icon(
                            painter = painterResource(R.drawable.send),
                            contentDescription = "Send voice message",
                            tint = colorResource(id = R.color.light_blue),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        } else {
            Row(modifier = Modifier
                .fillMaxWidth()
                .background(Color.DarkGray)
                .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ){
                IconButton(onClick = {
                    onComposerTextChange("")
                    onImageClicked()
                }) {
                    Icon(
                        painter = painterResource(R.drawable.attach),
                        contentDescription = "attach",
                        modifier = Modifier,
                        tint = colorResource(id = R.color.light_blue)
                    )
                }
                KChatAiEntryPoint(
                    onClick = onAiMenuClicked
                )
                TextField(
                    value = composerText,
                    onValueChange = onComposerTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(text = "Type your message...")},
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            hideKeyboardController?.hide()
                        }),
                    colors = TextFieldDefaults.colors().copy(
                        focusedContainerColor =Color.DarkGray,
                        unfocusedContainerColor =Color.DarkGray,
                        focusedTextColor =Color.White,
                        unfocusedTextColor = Color.White,
                        focusedPlaceholderColor = Color.LightGray,
                        unfocusedPlaceholderColor =Color.LightGray
                    )
                )
                IconButton(onClick = {
                    onSendMessage(composerText)
                    onComposerTextChange("")
                }){
                    Icon(
                        painter = painterResource(R.drawable.send),
                        contentDescription = "send",
                        modifier = Modifier,
                        tint = colorResource(id = R.color.light_blue)
                    )
                }
            }
        }
    }
}

@Composable
fun DateSeparator(dateText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E2D4A))
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                text = dateText,
                color = Color.White.copy(alpha = 0.8f),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF262D37))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .background(
                    color = colorResource(id = R.color.light_blue),
                    shape = RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Replying to $senderName",
                color = colorResource(id = R.color.light_blue),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = messageSnippet.ifBlank { "[Message]" },
                color = Color.LightGray,
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
                tint = Color.LightGray,
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
    val accentColor = if (isCurrentUserBubble) Color.White else colorResource(id = R.color.light_blue)
    val cardBgColor = if (isCurrentUserBubble) Color.Black.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.25f)
    val snippetColor = if (isCurrentUserBubble) Color.White.copy(alpha = 0.85f) else Color.LightGray

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
    onLongClick: () -> Unit,
    onClick: () -> Unit = {},
    onReply: (Message) -> Unit = {},
    onQuotedMessageClick: (String) -> Unit = {},
    audioPlayerHelper: AudioPlayerHelper? = null,
    videoPlayerHelper: VideoPlayerHelper? = null,
    onFullScreenVideoClick: (String) -> Unit = {}
) {

    val isCurrentUser = message.senderId == Firebase.auth.currentUser?.uid

    val bubbleColor = if (isSelected) {
        Color.Gray.copy(alpha = 0.5f)
    } else if (isCurrentUser) {
        colorResource(id = R.color.light_blue)
    } else {
        Color.DarkGray
    }

    var showFullImage by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val thresholdPx = with(density) { 52.dp.toPx() }
    val maxDragPx = with(density) { 72.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    val hasQuotedReply = !message.replyToSenderName.isNullOrBlank() || !message.replyToMessageText.isNullOrBlank()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
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

        val alignment =
            if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart

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
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {

            // 👤 Show profile only for other user
            if (!isCurrentUser) {

                if (!message.senderImage.isNullOrEmpty()) {
                    AsyncImage(
                        model = message.senderImage,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(50)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.friend),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
            }

            Column {

                // 🔹 Show username for group
                if (!isCurrentUser) {
                    Text(
                        text = message.senderName ?: "",
                        color = colorResource(id = R.color.light_blue),
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = bubbleColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .combinedClickable(
                            onLongClick = onLongClick,
                            onClick = {
                                if (isInSelectionMode) {
                                    onClick()
                                } else if (message.imageUrl != null) {
                                    showFullImage = true
                                }
                            }
                        )
                        .padding(12.dp)
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
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        if (message.imageUrl != null) {

                            AsyncImage(
                                model = message.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )

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
                                color = Color.White
                            )
                        }

                        val formattedMessageTime = if (message.createdAt > 0L) DateTimeUtils.formatMessageTime(message.createdAt) else ""
                        if (formattedMessageTime.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formattedMessageTime,
                                color = if (isCurrentUser) Color.White.copy(alpha = 0.7f) else Color.LightGray.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.End)
                            )
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
fun CallButton(isVideoCall: Boolean, invitees: List<com.zegocloud.uikit.service.defines.ZegoUIKitUser>) {
    AndroidView(factory = { context ->
        val button = ZegoSendCallInvitationButton(context)
        button.setIsVideoCall(isVideoCall)
        button.resourceID = "zego_data"
        button
    }, modifier = Modifier.size(50.dp)) { zegoCallButton ->
        zegoCallButton.setInvitees(invitees)
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
