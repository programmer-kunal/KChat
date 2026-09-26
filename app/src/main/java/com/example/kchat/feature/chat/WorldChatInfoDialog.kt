package com.example.kchat.feature.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kchat.R
import com.example.kchat.feature.home.HomeViewModel
import com.example.kchat.feature.home.rememberGroupOnlineCount
import com.example.kchat.model.User
import com.example.kchat.model.matchesUserSearch
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database

@Composable
fun WorldChatUserRow(
    user: User,
    isCurrentUser: Boolean,
    isFriend: Boolean,
    isRequestSent: Boolean,
    onSendRequest: () -> Unit,
    onUserClick: (() -> Unit)? = null
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
            .then(
                if (isFriend && onUserClick != null) {
                    Modifier.clickable { onUserClick() }
                } else {
                    Modifier
                }
            )
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

        val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)

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

        if (isCurrentUser) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF334155) else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "You",
                    color = if (isDark) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (isFriend) {
            Text(
                text = "Friends",
                color = Color(0xFF22C55E),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else if (isRequestSent) {
            Text(
                text = "Request Sent",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
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

@Composable
fun WorldChatInfoDialog(
    onDismissRequest: () -> Unit
) {
    val db = Firebase.database
    val currentUid = Firebase.auth.currentUser?.uid ?: ""

    var allUsersList by remember { mutableStateOf<List<User>>(emptyList()) }
    var friendIdsSet by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sentRequestsSet by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoadingUsers by remember { mutableStateOf(true) }
    var userSearchQuery by remember { mutableStateOf("") }
    var displayLimit by remember { mutableStateOf(50) }
    var selectedUserForProfile by remember { mutableStateOf<User?>(null) }

    val screenHeight = LocalConfiguration.current.screenHeightDp
    val listMaxHeight = (screenHeight * 0.35f).coerceIn(200f, 400f).dp

    val isSearching = userSearchQuery.isNotBlank()
    val filteredUsers = remember(allUsersList, userSearchQuery) {
        if (isSearching) {
            allUsersList.filter { matchesUserSearch(it.name, userSearchQuery) }
        } else {
            allUsersList
        }
    }
    val displayedUsers = remember(filteredUsers, isSearching, displayLimit) {
        if (isSearching) {
            filteredUsers
        } else {
            filteredUsers.take(displayLimit)
        }
    }

    LaunchedEffect(Unit) {
        db.reference.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<User>()
                snapshot.children.forEach { child ->
                    val uid = child.key ?: return@forEach
                    val name = child.child("name").getValue(String::class.java)
                        ?: child.child("email").getValue(String::class.java)
                        ?: "User"
                    val email = child.child("email").getValue(String::class.java) ?: ""
                    val imageUrl = child.child("imageUrl").getValue(String::class.java)
                    val about = child.child("about").getValue(String::class.java)
                    list.add(
                        User(
                            uid = uid,
                            name = name,
                            email = email,
                            profileImage = imageUrl,
                            about = about
                        )
                    )
                }
                allUsersList = list

                if (currentUid.isNotEmpty()) {
                    db.reference.child("friends").child(currentUid).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(friendsSnap: DataSnapshot) {
                            val friendIds = friendsSnap.children.mapNotNull { it.key }.toSet()
                            friendIdsSet = friendIds

                            db.reference.child("friend_requests").addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(reqsSnap: DataSnapshot) {
                                    val sentUids = mutableSetOf<String>()
                                    reqsSnap.children.forEach { receiverSnap ->
                                        if (receiverSnap.hasChild(currentUid)) {
                                            receiverSnap.key?.let { sentUids.add(it) }
                                        }
                                    }
                                    sentRequestsSet = sentUids
                                    isLoadingUsers = false
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    isLoadingUsers = false
                                }
                            })
                        }

                        override fun onCancelled(error: DatabaseError) {
                            isLoadingUsers = false
                        }
                    })
                } else {
                    isLoadingUsers = false
                }
            }

            override fun onCancelled(error: DatabaseError) {
                isLoadingUsers = false
            }
        })
    }

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)

    AlertDialog(
        onDismissRequest = {
            onDismissRequest()
            userSearchQuery = ""
        },
        confirmButton = {
            TextButton(onClick = {
                onDismissRequest()
                userSearchQuery = ""
            }) {
                Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "World Chat Info",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Vector Emblem
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_world_chat),
                        contentDescription = "World Chat Emblem",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "World Chat",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Fixed Motto Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    )
                ) {
                    Text(
                        text = "करो कुछ ऐसा जो आज तक किसी ने ना किया हो -🚀कुनाल",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Online member count
                val onlineCount = rememberGroupOnlineCount(HomeViewModel.WORLD_CHAT_ID)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = DateTimeUtils.formatGroupOnlineCount(onlineCount),
                        color = Color(0xFF22C55E),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider(
                    color = if (isDark) Color(0xFF334155) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    thickness = 0.8.dp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Search Bar
                TextField(
                    value = userSearchQuery,
                    onValueChange = { userSearchQuery = it },
                    placeholder = { Text("Search members...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = if (isDark) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = if (isDark) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (userSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { userSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
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

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSearching) "Search Results (${filteredUsers.size})" else "Registered Members",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isLoadingUsers) {
                        Text(
                            text = if (isSearching) "${filteredUsers.size} found" else "${allUsersList.size} users",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoadingUsers) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else if (displayedUsers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isSearching) "No members matching \"$userSearchQuery\"" else "No registered members",
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
                        items(displayedUsers, key = { it.uid }) { user ->
                            val isFriend = (user.uid in friendIdsSet)
                            WorldChatUserRow(
                                user = user,
                                isCurrentUser = (user.uid == currentUid),
                                isFriend = isFriend,
                                isRequestSent = (user.uid in sentRequestsSet),
                                onSendRequest = {
                                    val currentUser = Firebase.auth.currentUser
                                    if (currentUser != null) {
                                        db.reference
                                            .child("friend_requests")
                                            .child(user.uid)
                                            .child(currentUser.uid)
                                            .setValue(currentUser.displayName ?: "")
                                        sentRequestsSet = sentRequestsSet + user.uid
                                    }
                                },
                                onUserClick = if (isFriend) {
                                    { selectedUserForProfile = user }
                                } else null
                            )
                        }

                        if (!isSearching && allUsersList.size > displayLimit) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TextButton(onClick = { displayLimit += 50 }) {
                                        Text(
                                            text = "Load More (${allUsersList.size - displayLimit} remaining)",
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

    if (selectedUserForProfile != null) {
        UserProfileDialog(
            user = selectedUserForProfile!!,
            onDismissRequest = { selectedUserForProfile = null }
        )
    }
}
