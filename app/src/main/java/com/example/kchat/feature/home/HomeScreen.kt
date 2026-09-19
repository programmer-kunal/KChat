package com.example.kchat.feature.home

import android.annotation.SuppressLint
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import com.example.kchat.feature.chat.DirectChatItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable


@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val showDeletedGroups = remember { mutableStateOf(false) }

    val addChannel = remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // ✅ DIRECT OPEN BY DEFAULT
    val selectedTab = rememberSaveable { mutableStateOf(1) }
    var searchQuery by remember { mutableStateOf("") }
    val plainContext = LocalContext.current
    val scope = rememberCoroutineScope()

    var showProfileOptions by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var selectedUserForSelection by remember { mutableStateOf<DirectChatItem?>(null) }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val db = Firebase.database
    val currentUser = Firebase.auth.currentUser

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
        floatingActionButton = {
            if (selectedTab.value == 0) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colorResource(id = R.color.light_blue))
                            .clickable { showDeletedGroups.value = true }
                    ) {
                        Text(
                            text = "Removed Groups",
                            modifier = Modifier.padding(16.dp),
                            color = Color.White
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
                            color = Color.White
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
                .background(colorResource(id = R.color.dark_blue))
        ) {

            // 🔹 Back Button Interception when Selection is active
            if (selectedUserForSelection != null) {
                BackHandler {
                    selectedUserForSelection = null
                }
            }

            // 🔹 Top Bar
            if (selectedUserForSelection != null) {
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
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Exit Selection",
                                tint = colorResource(id = R.color.light_blue)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedUserForSelection?.name ?: "",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    var showDropdown by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showDropdown = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu Options",
                                tint = colorResource(id = R.color.light_blue)
                            )
                        }

                        DropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false },
                            modifier = Modifier.background(colorResource(id = R.color.dark_blue))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear Chat", color = Color.White) },
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
                                text = { Text("Unfriend", color = Color.White) },
                                onClick = {
                                    showDropdown = false
                                    selectedUserForSelection?.let { item ->
                                        currentUser?.uid?.let { uid ->
                                            db.reference.child("friends").child(uid).child(item.uid).removeValue()
                                            db.reference.child("friends").child(item.uid).child(uid).removeValue()
                                        }
                                    }
                                    selectedUserForSelection = null
                                }
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    val currentUser = Firebase.auth.currentUser
                    var profileImageUrl by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        currentUser?.uid?.let { uid ->
                            Firebase.database.reference
                                .child("users")
                                .child(uid)
                                .child("imageUrl")
                                .addValueEventListener(object : ValueEventListener {
                                    override fun onDataChange(snapshot: DataSnapshot) {
                                        profileImageUrl =
                                            snapshot.getValue(String::class.java)
                                    }

                                    override fun onCancelled(error: DatabaseError) {}
                                })
                        }
                    }
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
                                text = currentUser?.email?.first()?.uppercase() ?: "O",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }


                    Text(
                        text = "KChat",
                        color = colorResource(id = R.color.light_blue),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Logout",
                        color = colorResource(id = R.color.light_blue),
                        modifier = Modifier.clickable {
                            Firebase.auth.signOut()
                            navController.navigate("login") {
                                popUpTo("home") { inclusive = true }
                            }
                        }
                    )
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
                }

                TabButton("Groups", selectedTab.value == 0) {
                    selectedTab.value = 0
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 🔹 Search
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(30.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colorResource(id = R.color.light_blue),
                    unfocusedContainerColor = colorResource(id = R.color.light_blue),
                    focusedTextColor = colorResource(id = R.color.dark_blue),
                    unfocusedTextColor = colorResource(id = R.color.dark_blue),
                    focusedPlaceholderColor = colorResource(id = R.color.dark_blue),
                    unfocusedPlaceholderColor = colorResource(id = R.color.dark_blue)
                ),
                trailingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 🔹 Content
            if (selectedTab.value == 0) {

                LazyColumn {
                    items(
                        channels.value.filter {
                            it.name.contains(searchQuery, ignoreCase = true) && it.id !in deletedChannelIds.value
                        }
                    ) { channel ->
                        var showMenu by remember { mutableStateOf(false) }
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        var showLeaveConfirm by remember { mutableStateOf(false) }
                        val currentUid = Firebase.auth.currentUser?.uid ?: ""
                        val isOwner = channel.creatorUid == currentUid

                        Box {
                            ChannelItem(
                                channel = channel,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                shouldShowCallButtons = false,
                                onClick = {
                                    navController.navigate("chat/${channel.id}&${channel.name}")
                                },
                                onLongClick = {
                                    showMenu = true
                                },
                                invitees = emptyList()
                            )

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(colorResource(id = R.color.dark_blue))
                            ) {
                                if (isOwner) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Group", color = Color.Red) },
                                        onClick = {
                                            showMenu = false
                                            showDeleteConfirm = true
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Remove Group", color = Color.White) },
                                        onClick = {
                                            showMenu = false
                                            showLeaveConfirm = true
                                        }
                                    )
                                }
                            }
                        }

                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                title = { Text("Delete Group", color = Color.White) },
                                text = { Text("Are you sure you want to permanently delete this group?", color = Color.White) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.deleteGroup(channel.id)
                                        showDeleteConfirm = false
                                    }) {
                                        Text("Delete", color = Color.Red)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteConfirm = false }) {
                                        Text("Cancel", color = Color.White)
                                    }
                                },
                                containerColor = colorResource(id = R.color.dark_blue)
                            )
                        }

                        if (showLeaveConfirm) {
                            AlertDialog(
                                onDismissRequest = { showLeaveConfirm = false },
                                title = { Text("Remove Group", color = Color.White) },
                                text = { Text("Are you sure you want to remove this group?", color = Color.White) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.leaveGroup(channel.id)
                                        showLeaveConfirm = false
                                    }) {
                                        Text("Remove", color = Color.Red)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showLeaveConfirm = false }) {
                                        Text("Cancel", color = Color.White)
                                    }
                                },
                                containerColor = colorResource(id = R.color.dark_blue)
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
            containerColor = colorResource(id = R.color.dark_blue)
        ) {
            AddChannelDialog {
                viewModel.addChannel(it)
                addChannel.value = false
            }
        }

    }

    if (showDeletedGroups.value) {
        ModalBottomSheet(
            onDismissRequest = { showDeletedGroups.value = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = colorResource(id = R.color.dark_blue)
        ) {
            DeletedGroupsScreen(
                viewModel = viewModel,
                deletedChannelIds = deletedChannelIds.value,
                onDismiss = { showDeletedGroups.value = false }
            )
        }
    }

    if (showProfileOptions) {
        ModalBottomSheet(
            onDismissRequest = { showProfileOptions = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = colorResource(id = R.color.dark_blue),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Text(
                    text = "Profile Options",
                    color = colorResource(id = R.color.light_blue),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                ProfileOptionItem(
                    icon = Icons.Default.Image,
                    title = "Choose from Gallery",
                    subtitle = "Pick an existing profile picture from device storage",
                    onClick = {
                        galleryLauncher.launch("image/*")
                        showProfileOptions = false
                    }
                )

                ProfileOptionItem(
                    icon = Icons.Default.CameraAlt,
                    title = "Take Photo",
                    subtitle = "Capture a fresh image directly using your camera",
                    onClick = {
                        val file = File(
                            plainContext.cacheDir,
                            "camera_image_${System.currentTimeMillis()}.jpg"
                        )

                        val uri = FileProvider.getUriForFile(
                            plainContext,
                            "${plainContext.packageName}.provider",
                            file
                        )

                        tempCameraUri = uri
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        showProfileOptions = false
                    }
                )

                ProfileOptionItem(
                    icon = Icons.Default.Edit,
                    title = "Change Username",
                    subtitle = "Update your custom display handle shown to contacts",
                    onClick = {
                        showNameDialog = true
                        showProfileOptions = false
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
    if (showNameDialog) {

        var newName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        currentUser?.uid?.let { uid ->
                            db.reference.child("users")
                                .child(uid)
                                .child("name")
                                .setValue(newName)
                        }
                        showNameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = colorResource(id = R.color.dark_blue),
            title = {
                Text(
                    "Change Username",
                    color = colorResource(id = R.color.light_blue)
                )
            },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true
                )
            }
        )
    }
}


@Composable
fun TabButton(title: String, selected: Boolean, onClick: () -> Unit) {

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected)
                    colorResource(id = R.color.light_blue)
                else
                    Color.DarkGray
            )
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = if (selected) Color.White
            else colorResource(id = R.color.light_blue),
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
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    invitees: List<com.zegocloud.uikit.service.defines.ZegoUIKitUser>
){

    val formattedTime =
        if (channel.lastTime != null && channel.lastTime != 0L)
            SimpleDateFormat("hh:mm a", Locale.getDefault())
                .format(Date(channel.lastTime))
        else ""

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(id = R.color.light_blue))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(10.dp)
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            // 🔹 Group Avatar
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(colorResource(id = R.color.dark_blue))
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                if (!imageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = channel.name.first().uppercase(),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                // 🔹 Top Row (Name + Time)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Text(
                        text = channel.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )

                    if (formattedTime.isNotEmpty()) {
                        Text(
                            text = formattedTime,
                            fontSize = 12.sp,
                            color =colorResource(id = R.color.dark_blue)
                        )
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
                        color = if (subtitleText.equals("online", ignoreCase = true)) Color.White else Color.DarkGray,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    if (channel.unreadCount > 0) {
                        Badge(
                            containerColor =colorResource(R.color.dark_blue)
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
        }

        // 🔹 Call Buttons (unchanged)
        if (shouldShowCallButtons) {
            Row(
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                CallButton(isVideoCall = true, invitees = invitees)
                CallButton(isVideoCall = false, invitees = invitees)
            }
        }
    }
}

@Composable
fun AddChannelDialog(onAddChannel: (String) -> Unit) {

    val channelName = remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Write Your Group Name Below",
            color = colorResource(id = R.color.light_blue),
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        TextField(
            value = channelName.value,
            onValueChange = { channelName.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.DarkGray,
                unfocusedContainerColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onAddChannel(channelName.value) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add")
        }
    }
}

@Composable
fun ProfileOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(id = R.color.light_blue).copy(alpha = 0.15f)
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
                    .background(colorResource(id = R.color.light_blue).copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorResource(id = R.color.light_blue),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = subtitle,
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun DeletedGroupsScreen(
    viewModel: HomeViewModel,
    deletedChannelIds: Set<String>,
    onDismiss: () -> Unit
) {
    val channels = viewModel.channels.collectAsState()
    val deletedChannels = channels.value.filter { it.id in deletedChannelIds }
    var selectedChannelForRejoin by remember { mutableStateOf<HomeChannel?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Removed Groups",
            color = colorResource(id = R.color.light_blue),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (deletedChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No deleted groups",
                    color = Color.LightGray,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(deletedChannels) { channel ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedChannelForRejoin = channel },
                        colors = CardDefaults.cardColors(
                            containerColor = colorResource(id = R.color.light_blue).copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colorResource(id = R.color.dark_blue)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = channel.name.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = channel.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedChannelForRejoin != null) {
            AlertDialog(
                onDismissRequest = { selectedChannelForRejoin = null },
                title = { Text("Rejoin Group?", color = Color.White) },
                text = { Text("Do you want to rejoin '${selectedChannelForRejoin?.name}' and add it back to your active list?", color = Color.White) },
                confirmButton = {
                    TextButton(onClick = {
                        selectedChannelForRejoin?.let {
                            viewModel.rejoinGroup(it.id)
                        }
                        selectedChannelForRejoin = null
                        onDismiss()
                    }) {
                        Text("Join", color = colorResource(id = R.color.light_blue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedChannelForRejoin = null }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = colorResource(id = R.color.dark_blue)
            )
        }
    }
}
