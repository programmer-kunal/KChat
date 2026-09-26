package com.example.kchat.feature.home

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.kchat.*
import com.example.kchat.feature.chat.CallButton
import com.example.kchat.feature.chat.CustomGroupInfoDialog
import com.example.kchat.feature.chat.DirectChatScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.zegocloud.uikit.prebuilt.call.invite.widget.ZegoSendCallInvitationButton
import com.example.kchat.R
import coil.compose.AsyncImage
import com.google.firebase.database.database
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.kchat.feature.chat.DirectChatItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import com.example.kchat.feature.chat.ConversationMuteManager
import com.example.kchat.feature.chat.DateTimeUtils
import com.example.kchat.feature.chat.WorldChatInfoDialog
import androidx.compose.foundation.interaction.MutableInteractionSource


@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(navController: NavController) {


    val context = LocalContext.current

    LaunchedEffect(Unit) {
        Firebase.auth.currentUser?.let { user ->
            val app = context.applicationContext as? KChat
            val uid = user.uid
            val userEmail = user.email ?: "user_${uid.take(5)}@kchat.com"
            Firebase.database.getReference("users").child(uid).child("name")
                .get().addOnSuccessListener { snapshot ->
                    val dbName = snapshot.getValue(String::class.java)
                    val finalName = if (!dbName.isNullOrBlank()) dbName else (user.displayName ?: userEmail)
                    android.util.Log.d("NotificationDebug", "HomeScreen LaunchedEffect -> Initializing Zego with name: $finalName")
                    app?.initZegoService(
                        appID = AppID,
                        appSign = AppSign,
                        userID = userEmail,
                        userName = finalName
                    )
                }.addOnFailureListener {
                    val fallbackName = user.displayName ?: userEmail
                    android.util.Log.w("NotificationDebug", "HomeScreen LaunchedEffect -> Failed to fetch name, using fallback: $fallbackName")
                    app?.initZegoService(
                        appID = AppID,
                        appSign = AppSign,
                        userID = userEmail,
                        userName = fallbackName
                    )
                }
        }
    }

    val viewModel = hiltViewModel<HomeViewModel>()
    val channels = viewModel.channels.collectAsState()
    val deletedChannelIds = viewModel.deletedChannelIds.collectAsState()

    val addChannel = remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // ✅ DIRECT OPEN BY DEFAULT
    val selectedTab = rememberSaveable { mutableStateOf(1) }
    var searchQuery by remember { mutableStateOf("") }
    val plainContext = LocalContext.current
    val scope = rememberCoroutineScope()

    var showProfileOptions by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPhotoPickerDialog by remember { mutableStateOf(false) }
    var selectedUserForSelection by remember { mutableStateOf<DirectChatItem?>(null) }
    var selectedGroupForSelection by remember { mutableStateOf<HomeChannel?>(null) }
    var showWorldChatInfoDialog by remember { mutableStateOf(false) }
    var selectedCustomGroupForInfo by remember { mutableStateOf<HomeChannel?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val db = Firebase.database
    val currentUser = Firebase.auth.currentUser

    var currentUserName by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var currentUserEmail by remember { mutableStateOf(currentUser?.email ?: "") }
    var currentUserAbout by remember { mutableStateOf<String?>(null) }
    var profileImageUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid ?: return@LaunchedEffect
        db.reference.child("users").child(uid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.child("name").getValue(String::class.java)
                    if (!name.isNullOrEmpty()) currentUserName = name
                    val email = snapshot.child("email").getValue(String::class.java)
                    if (!email.isNullOrEmpty()) currentUserEmail = email
                    profileImageUrl = snapshot.child("imageUrl").getValue(String::class.java)
                    currentUserAbout = snapshot.child("about").getValue(String::class.java)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    val storageUtils = SupabaseStorageUtils(plainContext)


// ✅ GALLERY
    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->

            uri?.let { selectedUri ->
                currentUser?.uid?.let { uid ->

                    scope.launch {

                        val publicUrl = storageUtils.uploadImage(selectedUri)

                        publicUrl?.let { url ->
                            db.reference.child("users")
                                .child(uid)
                                .child("imageUrl")
                                .setValue(url)
                        }
                    }
                }
            }
        }


// ✅ CAMERA
    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->

            if (success) {

                tempCameraUri?.let { uri ->
                    currentUser?.uid?.let { uid ->

                        scope.launch {

                            val publicUrl = storageUtils.uploadImage(uri)

                            publicUrl?.let { url ->
                                db.reference.child("users")
                                    .child(uid)
                                    .child("imageUrl")
                                    .setValue(url)
                            }
                        }
                    }
                }
            }
        }


// ✅ CAMERA PERMISSION
    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                tempCameraUri?.let { uri ->
                    cameraLauncher.launch(uri)
                }
            }
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (selectedTab.value == 0) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // KChat Extension Entry Point FAB
                    FloatingActionButton(
                        onClick = { navController.navigate("extension") },
                        modifier = Modifier
                            .size(46.dp)
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), CircleShape),
                        shape = CircleShape,
                        containerColor = Color(0xFF0F172A),
                        contentColor = Color(0xFF38BDF8),
                        elevation = FloatingActionButtonDefaults.elevation(6.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_kchat_extension),
                            contentDescription = "KChat Extension",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colorResource(id = R.color.light_blue))
                            .clickable { addChannel.value = true }
                    ) {
                        Text(
                            text = "Create Group",
                            modifier = Modifier.padding(16.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {

            // 🔹 Back Button Interception when Selection is active
            if (selectedUserForSelection != null) {
                BackHandler {
                    selectedUserForSelection = null
                }
            }
            if (selectedGroupForSelection != null) {
                BackHandler {
                    selectedGroupForSelection = null
                }
            }

            // 🔹 Top Bar
            if (selectedUserForSelection != null) {
                val currentUid = currentUser?.uid ?: ""
                val directChatId = selectedUserForSelection?.let {
                    if (currentUid < it.uid) "${currentUid}_${it.uid}" else "${it.uid}_$currentUid"
                } ?: ""
                val muteVersion by ConversationMuteManager.muteStateVersion.collectAsState()
                val isDirectMuted = remember(directChatId, currentUid, muteVersion) {
                    ConversationMuteManager.isMuted(plainContext, currentUid, directChatId)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedUserForSelection = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit Selection",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedUserForSelection?.name ?: "",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            ConversationMuteManager.toggleMute(plainContext, currentUid, directChatId)
                        }) {
                            Icon(
                                imageVector = if (isDirectMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                contentDescription = if (isDirectMuted) "Unmute conversation" else "Mute conversation",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        var showDropdown by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showDropdown = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menu Options",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            DropdownMenu(
                                expanded = showDropdown,
                                onDismissRequest = { showDropdown = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Clear Chat", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        showDropdown = false
                                        selectedUserForSelection?.let { item ->
                                            currentUser?.uid?.let { uid ->
                                                val chatId = if (uid < item.uid) "${uid}_${item.uid}" else "${item.uid}_$uid"
                                                // 1. Mark all existing messages as deleted inside a single atomic updateChildren map write
                                                db.reference.child("messages").child(chatId).get().addOnSuccessListener { snapshot ->
                                                    val updates = mutableMapOf<String, Any>()
                                                    snapshot.children.forEach { child ->
                                                        val msgId = child.child("id").getValue(String::class.java)
                                                        if (!msgId.isNullOrEmpty()) {
                                                            updates["/deleted_messages/$uid/$chatId/$msgId"] = true
                                                        }
                                                    }
                                                    if (updates.isNotEmpty()) {
                                                        db.reference.updateChildren(updates)
                                                    }
                                                }
                                            }
                                        }
                                        selectedUserForSelection = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Unfriend", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showDropdown = false
                                        selectedUserForSelection?.let { item ->
                                            currentUser?.uid?.let { uid ->
                                                val chatId = if (uid < item.uid) "${uid}_${item.uid}" else "${item.uid}_$uid"
                                                // 1. Remove mutual friendship
                                                db.reference.child("friends").child(uid).child(item.uid).removeValue()
                                                db.reference.child("friends").child(item.uid).child(uid).removeValue()
                                                // 2. Permanently delete direct conversation messages
                                                db.reference.child("messages").child(chatId).removeValue()
                                                // 3. Clear soft deleted message tracking for both users
                                                db.reference.child("deleted_messages").child(uid).child(chatId).removeValue()
                                                db.reference.child("deleted_messages").child(item.uid).child(chatId).removeValue()
                                                // 4. Clear chat hidden tracking for both users
                                                db.reference.child("chat_hidden").child(uid).child(chatId).removeValue()
                                                db.reference.child("chat_hidden").child(item.uid).child(chatId).removeValue()
                                            }
                                        }
                                        selectedUserForSelection = null
                                    }
                                )
                            }
                        }
                    }
                }
            } else if (selectedGroupForSelection != null) {
                val currentUid = currentUser?.uid ?: ""
                val groupChannelId = selectedGroupForSelection?.id ?: ""
                val muteVersion by ConversationMuteManager.muteStateVersion.collectAsState()
                val isGroupMuted = remember(groupChannelId, currentUid, muteVersion) {
                    ConversationMuteManager.isMuted(plainContext, currentUid, groupChannelId)
                }
                var showGroupDropdown by remember { mutableStateOf(false) }
                var showGroupDeleteConfirm by remember { mutableStateOf(false) }
                val isOwner = selectedGroupForSelection?.creatorUid == currentUid
                val isWorldChat = selectedGroupForSelection?.id == HomeViewModel.WORLD_CHAT_ID

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedGroupForSelection = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit Selection",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedGroupForSelection?.name ?: "",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            ConversationMuteManager.toggleMute(plainContext, currentUid, groupChannelId)
                        }) {
                            Icon(
                                imageVector = if (isGroupMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                contentDescription = if (isGroupMuted) "Unmute group" else "Mute group",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Box {
                            IconButton(onClick = { showGroupDropdown = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Group Menu",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            DropdownMenu(
                                expanded = showGroupDropdown,
                                onDismissRequest = { showGroupDropdown = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Clear Chat", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        showGroupDropdown = false
                                        selectedGroupForSelection?.let { group ->
                                            viewModel.clearChat(group.id)
                                        }
                                        selectedGroupForSelection = null
                                    }
                                )

                                if (!isWorldChat && isOwner) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Group", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showGroupDropdown = false
                                            showGroupDeleteConfirm = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (showGroupDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showGroupDeleteConfirm = false },
                        title = { Text("Delete Group", color = MaterialTheme.colorScheme.primary) },
                        text = { Text("Are you sure you want to permanently delete this group?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        confirmButton = {
                            TextButton(onClick = {
                                selectedGroupForSelection?.let { viewModel.deleteGroup(it.id) }
                                selectedGroupForSelection = null
                                showGroupDeleteConfirm = false
                            }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showGroupDeleteConfirm = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Box(
                        modifier = Modifier
                            .size(45.dp)
                            .clip(CircleShape)
                            .background(colorResource(id = R.color.light_blue))
                            .clickable { showProfileOptions = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!profileImageUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = profileImageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = currentUserName.firstOrNull()?.uppercase()
                                    ?: currentUserEmail.firstOrNull()?.uppercase() ?: "U",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }


                    Text(
                        text = "KChat",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    var showHomeMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showHomeMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        DropdownMenu(
                            expanded = showHomeMenu,
                            onDismissRequest = { showHomeMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_theme),
                                        contentDescription = "Change Theme",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                text = { Text("Change Theme", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showHomeMenu = false
                                    showThemeDialog = true
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_saved_messages),
                                        contentDescription = "Saved Messages",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                text = { Text("Saved Messages", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showHomeMenu = false
                                    val currentUid = Firebase.auth.currentUser?.uid
                                    if (!currentUid.isNullOrEmpty()) {
                                        navController.navigate("chat/self_chat_$currentUid&Saved Messages")
                                    }
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_logout),
                                        contentDescription = "Logout",
                                        tint = Color.Red
                                    )
                                },
                                text = { Text("Logout", color = Color.Red) },
                                onClick = {
                                    showHomeMenu = false
                                    Firebase.auth.signOut()
                                    navController.navigate("login") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 🔹 Tabs (Swapped Order)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {

                TabButton("Direct", selectedTab.value == 1) {
                    selectedTab.value = 1
                    selectedGroupForSelection = null
                }

                TabButton("Groups", selectedTab.value == 0) {
                    selectedTab.value = 0
                    selectedUserForSelection = null
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 🔹 Search
            val isSearchDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search...", color = if (isSearchDark) colorResource(id = R.color.dark_blue).copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .then(
                        if (!isSearchDark) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(30.dp))
                        else Modifier
                    ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = if (isSearchDark) colorResource(id = R.color.light_blue) else MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = if (isSearchDark) colorResource(id = R.color.light_blue) else MaterialTheme.colorScheme.surface,
                    focusedTextColor = if (isSearchDark) colorResource(id = R.color.dark_blue) else MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = if (isSearchDark) colorResource(id = R.color.dark_blue) else MaterialTheme.colorScheme.onSurface,
                    focusedPlaceholderColor = if (isSearchDark) colorResource(id = R.color.dark_blue) else MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = if (isSearchDark) colorResource(id = R.color.dark_blue) else MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                trailingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = if (isSearchDark) colorResource(id = R.color.dark_blue) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 🔹 Content
            if (selectedTab.value == 0) {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = selectedGroupForSelection != null
                        ) {
                            selectedGroupForSelection = null
                        }
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            channels.value.filter {
                                it.name.contains(searchQuery, ignoreCase = true) && it.id !in deletedChannelIds.value
                            }
                        ) { channel ->
                            val isSelected = selectedGroupForSelection?.id == channel.id
                            val groupSubtitle = channel.lastMessage?.let {
                                "${channel.lastSenderName ?: ""}: $it"
                            } ?: "Start Chatting"

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) colorResource(id = R.color.light_blue).copy(alpha = 0.25f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        enabled = selectedGroupForSelection != null
                                    ) {
                                        selectedGroupForSelection = null
                                    }
                            ) {
                                ChannelItem(
                                    channel = channel,
                                    modifier = if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(16.dp)) else Modifier,
                                    shouldShowCallButtons = false,
                                    subtitle = groupSubtitle,
                                    onAvatarClick = {
                                        if (selectedGroupForSelection != null) {
                                            if (isSelected) {
                                                selectedGroupForSelection = null
                                            } else {
                                                selectedGroupForSelection = channel
                                            }
                                        } else {
                                            if (channel.id == HomeViewModel.WORLD_CHAT_ID) {
                                                showWorldChatInfoDialog = true
                                            } else if (!channel.id.startsWith("self_chat_")) {
                                                selectedCustomGroupForInfo = channel
                                            }
                                        }
                                    },
                                    onClick = {
                                        if (selectedGroupForSelection != null) {
                                            if (selectedGroupForSelection?.id == channel.id) {
                                                selectedGroupForSelection = null
                                            } else {
                                                selectedGroupForSelection = channel
                                            }
                                        } else {
                                            navController.navigate("chat/${channel.id}&${channel.name}")
                                        }
                                    },
                                    onLongClick = {
                                        selectedGroupForSelection = channel
                                    },
                                    invitees = emptyList()
                                )
                            }
                        }

                        item {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 250.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        enabled = selectedGroupForSelection != null
                                    ) {
                                        selectedGroupForSelection = null
                                    }
                            )
                        }
                    }
                }

            } else {
                DirectChatScreen(
                    navController = navController,
                    searchQuery = searchQuery,
                    selectedUserForSelection = selectedUserForSelection,
                    onUserSelectedForSelection = { selectedUserForSelection = it }
                )
            }
        }
    }

    if (addChannel.value) {
        ModalBottomSheet(
            onDismissRequest = { addChannel.value = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            properties = ModalBottomSheetProperties(
                shouldDismissOnBackPress = false
            )
        ) {
            CreateGroupDialog(
                onDismiss = { addChannel.value = false },
                onCreateGroup = { name, friendUids, imageUri ->
                    viewModel.createGroup(name, friendUids, imageUri, plainContext)
                    addChannel.value = false
                }
            )
        }
    }

    if (showProfileOptions) {
        val isProfileDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
        ModalBottomSheet(
            onDismissRequest = { showProfileOptions = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                // 🔹 Top Profile Info Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(colorResource(id = R.color.light_blue)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!profileImageUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = profileImageUrl,
                                contentDescription = "Profile Picture",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = currentUserName.firstOrNull()?.uppercase()
                                    ?: currentUserEmail.firstOrNull()?.uppercase() ?: "U",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUserName.ifBlank { "User" },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentUserEmail,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }

                    var showProfileDropdown by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showProfileDropdown = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Profile Options Menu",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = showProfileDropdown,
                            onDismissRequest = { showProfileDropdown = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_theme),
                                        contentDescription = "Change Theme",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                text = { Text("Change Theme", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showProfileDropdown = false
                                    showThemeDialog = true
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_saved_messages),
                                        contentDescription = "Saved Messages",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                text = { Text("Saved Messages", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showProfileDropdown = false
                                    val currentUid = Firebase.auth.currentUser?.uid
                                    if (!currentUid.isNullOrEmpty()) {
                                        navController.navigate("chat/self_chat_$currentUid&Saved Messages")
                                    }
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_logout),
                                        contentDescription = "Logout",
                                        tint = Color.Red
                                    )
                                },
                                text = { Text("Logout", color = Color.Red) },
                                onClick = {
                                    showProfileDropdown = false
                                    showProfileOptions = false
                                    Firebase.auth.signOut()
                                    navController.navigate("login") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // About card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isProfileDark) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = "About",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (!currentUserAbout.isNullOrBlank()) currentUserAbout!! else "Tell people something about you...",
                            color = if (!currentUserAbout.isNullOrBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(
                    color = if (isProfileDark) Color(0xFF334155) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    thickness = 0.8.dp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 🔹 EXACTLY 3 Profile Options
                ProfileOptionItem(
                    icon = Icons.Default.CameraAlt,
                    title = "Change Profile Picture",
                    subtitle = "Update your profile image using Gallery or Camera",
                    onClick = {
                        showProfileOptions = false
                        showPhotoPickerDialog = true
                    }
                )

                ProfileOptionItem(
                    icon = Icons.Default.Edit,
                    title = "Change Username",
                    subtitle = "Update your custom display handle shown to contacts",
                    onClick = {
                        showProfileOptions = false
                        showNameDialog = true
                    }
                )

                ProfileOptionItem(
                    icon = Icons.Default.Info,
                    title = "Edit About",
                    subtitle = "Update your status message and info",
                    onClick = {
                        showProfileOptions = false
                        showAboutDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    if (showPhotoPickerDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoPickerDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Profile Picture",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                galleryLauncher.launch("image/*")
                                showPhotoPickerDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Choose from Gallery", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val file = File(plainContext.cacheDir, "camera_img_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(
                                    plainContext,
                                    "${plainContext.packageName}.provider",
                                    file
                                )
                                tempCameraUri = uri
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                showPhotoPickerDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Take Photo", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoPickerDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    if (showNameDialog) {
        var newName by remember { mutableStateOf(currentUserName) }

        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Change Username",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Username") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isNotEmpty()) {
                            currentUser?.uid?.let { uid ->
                                db.reference.child("users").child(uid).child("name").setValue(trimmed)
                                currentUserName = trimmed
                            }
                        }
                        showNameDialog = false
                    }
                ) {
                    Text("Save", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showAboutDialog) {
        var newAbout by remember { mutableStateOf(currentUserAbout ?: "") }

        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "About",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newAbout,
                    onValueChange = { newAbout = it },
                    placeholder = { Text("Tell people something about you...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    label = { Text("About") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newAbout.trim()
                        currentUser?.uid?.let { uid ->
                            db.reference.child("users").child(uid).child("about").setValue(trimmed)
                            currentUserAbout = trimmed
                        }
                        showAboutDialog = false
                    }
                ) {
                    Text("Save", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showWorldChatInfoDialog) {
        WorldChatInfoDialog(
            onDismissRequest = { showWorldChatInfoDialog = false }
        )
    }

    if (selectedCustomGroupForInfo != null) {
        CustomGroupInfoDialog(
            channelId = selectedCustomGroupForInfo!!.id,
            channelName = selectedCustomGroupForInfo!!.name,
            onDismissRequest = { selectedCustomGroupForInfo = null }
        )
    }

    if (showThemeDialog) {
        val currentTheme by com.example.kchat.ui.theme.ThemeManager.currentTheme.collectAsState()
        com.example.kchat.ui.theme.ThemeSelectionDialog(
            currentTheme = currentTheme,
            onThemeSelected = { selectedTheme ->
                com.example.kchat.ui.theme.ThemeManager.setTheme(selectedTheme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}


@Composable
fun TabButton(title: String, selected: Boolean, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected)
                    MaterialTheme.colorScheme.primary
                else
                    if (isDark) Color.DarkGray else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = if (selected) Color.White
            else if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelItem(
    channel: HomeChannel,
    modifier: Modifier,
    shouldShowCallButtons: Boolean,
    imageUrl: String? = null,
    subtitle: String? = null,
    onAvatarClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    invitees: List<com.zegocloud.uikit.service.defines.ZegoUIKitUser> = emptyList(),
    totalMemberCount: Int? = null,
    trailingContent: @Composable (() -> Unit)? = null
){
    val context = LocalContext.current
    val currentUid = Firebase.auth.currentUser?.uid ?: ""
    val muteVersion by ConversationMuteManager.muteStateVersion.collectAsState()
    val isMuted = remember(currentUid, channel.id, muteVersion) {
        ConversationMuteManager.isMuted(context, currentUid, channel.id)
    }

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)

    val formattedTime =
        if (channel.lastTime != null && channel.lastTime != 0L)
            SimpleDateFormat("hh:mm a", Locale.getDefault())
                .format(Date(channel.lastTime))
        else ""

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isDark) colorResource(id = R.color.light_blue) else MaterialTheme.colorScheme.surface
            )
            .then(
                if (!isDark) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(10.dp)
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            val effectiveImageUrl = channel.imageUrl ?: imageUrl
            val isWorldChat = channel.id == HomeViewModel.WORLD_CHAT_ID
            val isSelfChat = channel.id.startsWith("self_chat_")

            // 🔹 Group Avatar
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(if (isDark) colorResource(id = R.color.dark_blue) else MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        if (onAvatarClick != null) {
                            onAvatarClick()
                        } else {
                            onClick()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isWorldChat) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_world_chat),
                        contentDescription = "World Chat",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else if (isSelfChat) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_saved_messages),
                        contentDescription = "Saved Messages",
                        tint = if (isDark) colorResource(id = R.color.light_blue) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                } else if (!effectiveImageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = effectiveImageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = channel.name.firstOrNull()?.uppercase() ?: "G",
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                // 🔹 Top Row (Name + Time + Mute)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = channel.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isMuted) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = "Muted",
                                tint = if (isDark) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (formattedTime.isNotEmpty()) {
                            Text(
                                text = formattedTime,
                                fontSize = 12.sp,
                                color = if (isDark) colorResource(id = R.color.dark_blue) else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 🔹 Bottom Row (Last Message + Badge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    val subtitleText = subtitle ?: (channel.lastMessage?.let {
                        "${channel.lastSenderName ?: ""}: $it"
                    } ?: "Start Chatting")

                    Text(
                        text = subtitleText,
                        fontSize = 14.sp,
                        color = if (subtitleText.equals("online", ignoreCase = true)) (if (isDark) Color.White else Color(0xFF16A34A)) else (if (isDark) Color.DarkGray else MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    if (channel.unreadCount > 0) {
                        Badge(
                            containerColor = if (isDark) colorResource(R.color.dark_blue) else MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = channel.unreadCount.toString(),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // 🔹 Trailing Content (e.g. selection action buttons in chat)
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(4.dp))
                trailingContent()
            }

            // 🔹 Call Buttons (Hidden for World Chat)
            val showCalls = shouldShowCallButtons && (channel.id != HomeViewModel.WORLD_CHAT_ID)
            if (showCalls) {
                val isDirect = !channel.id.startsWith("-") && channel.id != HomeViewModel.WORLD_CHAT_ID && channel.id.contains("_")
                val callId = if (isDirect) null else "call_group_${channel.id.replace("-", "_")}"
                Spacer(modifier = Modifier.width(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallButton(isVideoCall = true, invitees = invitees, callId = callId, totalMemberCount = totalMemberCount)
                    CallButton(isVideoCall = false, invitees = invitees, callId = callId, totalMemberCount = totalMemberCount)
                }
            }
        }
    }
}

@Composable
fun rememberGroupOnlineCount(channelId: String): Int {
    var onlineCount by remember(channelId) { mutableStateOf(0) }
    DisposableEffect(channelId) {
        val currentUid = Firebase.auth.currentUser?.uid
        val db = Firebase.database
        val membersRef = db.reference.child("channels").child(channelId).child("users")
        val creatorRef = db.reference.child("channel_creator").child(channelId)

        val memberUids = mutableSetOf<String>()
        val statusListeners = mutableMapOf<String, ValueEventListener>()
        val onlineMap = mutableMapOf<String, Boolean>()

        fun updateCount() {
            onlineCount = onlineMap.count { (uid, isOnline) ->
                uid != currentUid && isOnline
            }
        }

        fun attachListener(uid: String) {
            if (statusListeners.containsKey(uid)) return
            val statusRef = db.reference.child("status").child(uid).child("online")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onlineMap[uid] = snapshot.getValue(Boolean::class.java) ?: false
                    updateCount()
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            statusListeners[uid] = listener
            statusRef.addValueEventListener(listener)
        }

        val membersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val current = snapshot.children.mapNotNull { it.key }.toSet()
                memberUids.addAll(current)
                current.forEach { attachListener(it) }
                val removed = statusListeners.keys - memberUids
                removed.forEach { uid ->
                    statusListeners.remove(uid)?.let { l ->
                        db.reference.child("status").child(uid).child("online").removeEventListener(l)
                    }
                    onlineMap.remove(uid)
                }
                updateCount()
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        val creatorListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val creator = snapshot.getValue(String::class.java)
                if (!creator.isNullOrEmpty()) {
                    memberUids.add(creator)
                    attachListener(creator)
                    updateCount()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        membersRef.addValueEventListener(membersListener)
        creatorRef.addValueEventListener(creatorListener)

        onDispose {
            membersRef.removeEventListener(membersListener)
            creatorRef.removeEventListener(creatorListener)
            statusListeners.forEach { (uid, l) ->
                db.reference.child("status").child(uid).child("online").removeEventListener(l)
            }
            statusListeners.clear()
            onlineMap.clear()
        }
    }
    return onlineCount
}


@Composable
fun ProfileOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val isItemDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isItemDark) {
                colorResource(id = R.color.light_blue).copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (isItemDark) colorResource(id = R.color.light_blue).copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

