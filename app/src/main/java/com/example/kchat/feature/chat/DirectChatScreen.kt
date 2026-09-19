package com.example.kchat.feature.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
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
        containerColor = colorResource(id = R.color.dark_blue),
        floatingActionButton = {

            Box {

                androidx.compose.material3.FloatingActionButton(
                    onClick = {   if (incomingRequests.isNotEmpty()) {
                        showRequestDialog = true
                    } else {
                        showUserDialog = true
                    } },
                    containerColor = colorResource(id = R.color.light_blue)
                ) {
                    Text("+", color = colorResource(id = R.color.dark_blue))
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
    ) { innerPadding ->

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

                    Box(modifier = Modifier.size(60.dp)) {

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
                                    .background(colorResource(id = R.color.light_blue)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.name.first().uppercase(),
                                    color = Color.White,
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = user.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = colorResource(id = R.color.light_blue)
                            )

                            if (formattedTime.isNotEmpty()) {
                                Text(
                                    text = formattedTime,
                                    fontSize = 12.sp,
                                    color = colorResource(id = R.color.light_blue)
                                )
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
                                color = Color.Gray,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )

                            if (user.unreadCount > 0) {
                                Badge(
                                    containerColor = colorResource(id = R.color.light_blue)
                                ) {
                                    Text(
                                        text = user.unreadCount.toString(),
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showUserDialog) {

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUserDialog = false },
            confirmButton = {},
            containerColor = colorResource(id = R.color.dark_blue),
            title = {
                Text(
                    text = "Add Friends",
                    color = colorResource(id = R.color.light_blue)
                )
            },
            text = {
                LazyColumn {

                    items(
                        items = allUsers.filter { user ->
                            user.uid !in friendIds &&
                                    user.uid !in incomingRequests.keys
                        },
                        key = { user -> user.uid }
                    ) { user ->

                        var localSent by remember { mutableStateOf(false) }
                        val isSent = user.uid in sentRequests || localSent

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = user.name,
                                color = Color.White,
                                fontSize = 14.sp
                            )

                            if (!isSent) {
                                Text(
                                    text = "Send Request",
                                    color = colorResource(id = R.color.light_blue),
                                    modifier = Modifier.clickable {

                                        viewModel.sendFriendRequest(
                                            user.uid,
                                            user.name
                                        )

                                        localSent = true
                                    }
                                )
                            } else {
                                Text(
                                    text = "Request Sent",
                                    color = Color.Gray
                                )
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
            containerColor = colorResource(id = R.color.dark_blue),
            title = {
                Text(
                    text = "Friend Requests",
                    color = colorResource(id = R.color.light_blue)
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
                                color = Color.White
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
}
