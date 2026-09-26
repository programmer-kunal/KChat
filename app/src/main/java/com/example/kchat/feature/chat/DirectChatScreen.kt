package com.example.kchat.feature.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.kchat.R
import com.example.kchat.model.User
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DirectChatScreen(
    navController: NavController,
    searchQuery: String,
    selectedUserForSelection: DirectChatItem?,
    onUserSelectedForSelection: (DirectChatItem?) -> Unit,
    viewModel: DirectChatViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {

    val currentUid = Firebase.auth.currentUser?.uid ?: return
    val db = Firebase.database

    val users by viewModel.users.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val sentRequests by viewModel.sentRequests.collectAsState()
    val friendIds by viewModel.friendIds.collectAsState()
    val hiddenChats by viewModel.hiddenChats.collectAsState()
    val incomingRequests by viewModel.incomingRequests.collectAsState()

    var showUserDialog by remember { mutableStateOf(false) }
    var showRequestDialog by remember { mutableStateOf(false) }
    var selectedUserForProfile by remember { mutableStateOf<User?>(null) }

    val context = LocalContext.current
    val muteVersion by ConversationMuteManager.muteStateVersion.collectAsState()

    LaunchedEffect(currentUid) {
        viewModel.startListeners()
    }


    val onlineColor = Color(
        red = 0f,
        green = 0.55f,
        blue = 0.65f,
        alpha = 1f
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // KChat Extension Entry Point FAB
                androidx.compose.material3.FloatingActionButton(
                    onClick = { navController.navigate("extension") },
                    modifier = Modifier
                        .size(46.dp)
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), CircleShape),
                    shape = CircleShape,
                    containerColor = Color(0xFF0F172A),
                    contentColor = Color(0xFF38BDF8),
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_kchat_extension),
                        contentDescription = "KChat Extension",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box {
                    androidx.compose.material3.FloatingActionButton(
                        onClick = {
                            if (incomingRequests.isNotEmpty()) {
                                showRequestDialog = true
                            } else {
                                showUserDialog = true
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text("+", color = MaterialTheme.colorScheme.onPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }

                    if (incomingRequests.isNotEmpty()) {
                        Badge(
                            modifier = Modifier.align(Alignment.TopEnd),
                            containerColor = Color.Red
                        ) {
                            Text(
                                text = incomingRequests.size.toString(),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = selectedUserForSelection != null
                ) {
                    onUserSelectedForSelection(null)
                }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {

                items(
                    users.filter {
                        val chatId = if (currentUid < it.uid) "${currentUid}_${it.uid}" else "${it.uid}_$currentUid"
                        val isHidden = hiddenChats[chatId] ?: false
                        it.uid in friendIds && !isHidden && it.name.contains(searchQuery, ignoreCase = true)
                    }
                ) { user ->

                    val chatId = if (currentUid < user.uid) "${currentUid}_${user.uid}" else "${user.uid}_$currentUid"
                    val isMuted = remember(currentUid, chatId, muteVersion) {
                        ConversationMuteManager.isMuted(context, currentUid, chatId)
                    }

                    var isOnline by remember { mutableStateOf(false) }

                DisposableEffect(user.uid) {
                    val statusRef = db.reference.child("status").child(user.uid)
                    val statusListener = object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            isOnline =
                                snapshot.child("online")
                                    .getValue(Boolean::class.java) ?: false
                        }

                        override fun onCancelled(error: DatabaseError) {}
                    }
                    statusRef.addValueEventListener(statusListener)

                    onDispose {
                        statusRef.removeEventListener(statusListener)
                    }
                }

                val formattedTime = DateTimeUtils.formatConversationTime(user.lastTime)

                val isSelected = selectedUserForSelection?.uid == user.uid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) colorResource(id = R.color.light_blue).copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .combinedClickable(
                            onLongClick = {
                                onUserSelectedForSelection(user)
                            },
                            onClick = {
                                if (selectedUserForSelection != null) {
                                    if (isSelected) {
                                        onUserSelectedForSelection(null)
                                    } else {
                                        onUserSelectedForSelection(user)
                                    }
                                } else {
                                    val chatId =
                                        if (currentUid < user.uid)
                                            "${currentUid}_${user.uid}"
                                        else
                                            "${user.uid}_$currentUid"

                                    db.reference.child("messages")
                                        .child(chatId)
                                        .get()
                                        .addOnSuccessListener { snapshot ->
                                            snapshot.children.forEach { msg ->
                                                msg.ref.child("deliveredBy")
                                                    .child(currentUid)
                                                    .setValue(true)
                                                msg.ref.child("readBy")
                                                    .child(currentUid)
                                                    .setValue(true)
                                            }
                                        }

                                    navController.navigate("chat/$chatId&${user.name}")
                                }
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (selectedUserForSelection != null) {
                                    if (isSelected) {
                                        onUserSelectedForSelection(null)
                                    } else {
                                        onUserSelectedForSelection(user)
                                    }
                                } else {
                                    val fullUser = allUsers.find { it.uid == user.uid }
                                        ?: User(uid = user.uid, name = user.name, profileImage = user.imageUrl)
                                    selectedUserForProfile = fullUser
                                }
                            }
                    ) {

                        if (!user.imageUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = user.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.name.first().uppercase(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 🔥 Animated Online Indicator
                        if (isOnline) {

                            val infiniteTransition = rememberInfiniteTransition()

                            val scale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.6f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1400),
                                    repeatMode = RepeatMode.Restart
                                )
                            )

                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.5f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1400),
                                    repeatMode = RepeatMode.Restart
                                )
                            )

                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .align(Alignment.BottomEnd),
                                contentAlignment = Alignment.Center
                            ) {

                                // Glow Ring (Dark Teal Pulse)
                                Box(
                                    modifier = Modifier
                                        .size((12 * scale).dp)
                                        .clip(CircleShape)
                                        .background(
                                            onlineColor.copy(alpha = alpha)
                                        )
                                )

                                // Solid Dot (Dark Teal Core)
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(onlineColor)
                                )
                            }
                        }

                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        val isDirectDark = MaterialTheme.colorScheme.background == Color(0xFF162542)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = user.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isDirectDark) colorResource(id = R.color.light_blue) else MaterialTheme.colorScheme.onSurface,
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
                                        tint = if (isDirectDark) colorResource(id = R.color.light_blue).copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                if (formattedTime.isNotEmpty()) {
                                    Text(
                                        text = formattedTime,
                                        fontSize = 12.sp,
                                        color = if (isDirectDark) colorResource(id = R.color.light_blue) else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = if (user.lastMessage.isNullOrBlank()) "Start Chatting" else user.lastMessage,
                                fontSize = 14.sp,
                                color = if (isDirectDark) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )

                            if (user.unreadCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = user.unreadCount.toString(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
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
                            enabled = selectedUserForSelection != null
                        ) {
                            onUserSelectedForSelection(null)
                        }
                )
            }
        }
    }
}
    if (showUserDialog) {
        var searchQuery by remember { mutableStateOf("") }
        var displayLimit by remember { mutableStateOf(15) }
        val screenHeight = LocalConfiguration.current.screenHeightDp
        val listMaxHeight = (screenHeight * 0.40f).coerceIn(220f, 420f).dp

        val candidateUsers = remember(allUsers, friendIds, incomingRequests) {
            allUsers.filter { user ->
                user.uid != currentUid && user.uid !in friendIds && user.uid !in incomingRequests.keys
            }
        }

        val isSearching = searchQuery.isNotBlank()
        val searchResults = remember(candidateUsers, searchQuery) {
            if (isSearching) {
                candidateUsers.filter { com.example.kchat.model.matchesUserSearch(it.name, searchQuery) }
            } else {
                emptyList()
            }
        }

        val displayedUsers = remember(candidateUsers, searchResults, isSearching, displayLimit) {
            if (isSearching) {
                searchResults
            } else {
                candidateUsers.take(displayLimit)
            }
        }

        val isDiscoverDark = MaterialTheme.colorScheme.background == Color(0xFF162542)

        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showUserDialog = false
                searchQuery = ""
            },
            confirmButton = {
                TextButton(onClick = {
                    showUserDialog = false
                    searchQuery = ""
                }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = {
                Column {
                    Text(
                        text = "Discover People",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Find friends or connect with suggested people",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Search bar
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = if (isDiscoverDark) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = if (isDiscoverDark) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Section title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSearching) "Search Results (${searchResults.size})" else "Suggested for You",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!isSearching && candidateUsers.isNotEmpty()) {
                            Text(
                                text = "${minOf(displayLimit, candidateUsers.size)} of ${candidateUsers.size}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (displayedUsers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSearching) "No users matching \"$searchQuery\"" else "No more suggestions right now.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = listMaxHeight)
                        ) {
                            items(
                                items = displayedUsers,
                                key = { user -> user.uid }
                            ) { user ->
                                var localSent by remember { mutableStateOf(false) }
                                val isSent = user.uid in sentRequests || localSent

                                AddFriendUserRow(
                                    user = user,
                                    isSent = isSent,
                                    onSendRequest = {
                                        viewModel.sendFriendRequest(
                                            user.uid,
                                            user.name
                                        )
                                        localSent = true
                                    }
                                )
                            }

                            if (!isSearching && candidateUsers.size > displayLimit) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        TextButton(onClick = { displayLimit += 15 }) {
                                            Text(
                                                text = "View More (${candidateUsers.size - displayLimit} remaining)",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }
    if (showRequestDialog) {

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRequestDialog = false },
            confirmButton = {},
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Friend Requests",
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {

                LazyColumn {

                    items(incomingRequests.toList()) { request ->

                        val senderUid = request.first
                        val senderName = request.second

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = senderName,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row {

                                Text(
                                    text = "Accept",
                                    color = Color.Green,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .clickable {

                                            viewModel.acceptFriendRequest(senderUid)
                                            showRequestDialog = false
                                        }
                                )

                                Text(
                                    text = "Reject",
                                    color = Color.Red,
                                    modifier = Modifier.clickable {

                                        viewModel.rejectFriendRequest(senderUid)
                                        showRequestDialog = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        )
    }
    if (selectedUserForProfile != null) {
        UserProfileDialog(
            user = selectedUserForProfile!!,
            onDismissRequest = { selectedUserForProfile = null }
        )
    }
}

@Composable
fun AddFriendUserRow(
    user: User,
    isSent: Boolean,
    onSendRequest: () -> Unit
) {
    var isOnline by remember { mutableStateOf(false) }
    DisposableEffect(user.uid) {
        val statusRef = Firebase.database.reference.child("status").child(user.uid).child("online")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isOnline = snapshot.getValue(Boolean::class.java) ?: false
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        statusRef.addValueEventListener(listener)
        onDispose {
            statusRef.removeEventListener(listener)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp)) {
            if (!user.profileImage.isNullOrEmpty()) {
                AsyncImage(
                    model = user.profileImage,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.name.firstOrNull()?.uppercase() ?: "U",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isOnline) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E))
                        .align(Alignment.BottomEnd)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val aboutText = if (!user.about.isNullOrBlank()) user.about else "Hey there! I am using KChat."
            Text(
                text = aboutText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isSent) {
            Text(
                text = "Request Sent",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        } else {
            TextButton(
                onClick = onSendRequest,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Add Friend",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
