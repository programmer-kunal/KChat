package com.example.kchat.feature.chat

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.kchat.R
import com.example.kchat.SupabaseStorageUtils
import com.example.kchat.feature.home.rememberGroupOnlineCount
import com.example.kchat.model.User
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun GroupMemberRow(
    member: User,
    isCreator: Boolean,
    canRemove: Boolean,
    onMemberClick: () -> Unit = {},
    onRemoveClicked: () -> Unit
) {
    var isOnline by remember { mutableStateOf(false) }
    DisposableEffect(member.uid) {
        val statusRef = Firebase.database.reference.child("status").child(member.uid).child("online")
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
            .clickable { onMemberClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp)) {
            if (!member.profileImage.isNullOrEmpty()) {
                AsyncImage(
                    model = member.profileImage,
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
                        text = member.name.firstOrNull()?.uppercase() ?: "U",
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isCreator) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Creator",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            val aboutText = if (!member.about.isNullOrBlank()) member.about else "Hey there! I am using KChat."
            Text(
                text = aboutText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (canRemove) {
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = onRemoveClicked,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Remove",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CustomGroupInfoDialog(
    channelId: String,
    channelName: String,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = Firebase.database
    val currentUid = Firebase.auth.currentUser?.uid ?: ""

    var currentGroupName by remember(channelId) { mutableStateOf(channelName) }
    var groupImageUrl by remember(channelId) { mutableStateOf<String?>(null) }
    var groupCreatorUid by remember(channelId) { mutableStateOf<String?>(null) }
    var groupMembersList by remember(channelId) { mutableStateOf<List<User>>(emptyList()) }
    var isLoadingMembers by remember(channelId) { mutableStateOf(true) }
    var memberToRemove by remember { mutableStateOf<User?>(null) }
    var selectedMemberForProfile by remember { mutableStateOf<User?>(null) }

    var showGroupPhotoChoiceDialog by remember { mutableStateOf(false) }
    var tempGroupCameraUri by remember { mutableStateOf<Uri?>(null) }

    val groupGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val storageUtils = SupabaseStorageUtils(context)
                val publicUrl = storageUtils.uploadImage(uri)
                if (!publicUrl.isNullOrEmpty()) {
                    db.reference.child("channel_image").child(channelId).setValue(publicUrl)
                        .addOnFailureListener { error ->
                            Toast.makeText(context, error.message ?: "Failed to update group image", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }
    }

    val groupCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempGroupCameraUri?.let { uri ->
                coroutineScope.launch {
                    val storageUtils = SupabaseStorageUtils(context)
                    val publicUrl = storageUtils.uploadImage(uri)
                    if (!publicUrl.isNullOrEmpty()) {
                        db.reference.child("channel_image").child(channelId).setValue(publicUrl)
                            .addOnFailureListener { error ->
                                Toast.makeText(context, error.message ?: "Failed to update group image", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }
        }
    }

    val groupCameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            tempGroupCameraUri?.let { uri ->
                groupCameraLauncher.launch(uri)
            }
        }
    }

    // Observe group image
    DisposableEffect(channelId) {
        val groupImgRef = db.reference.child("channel_image").child(channelId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                groupImageUrl = snapshot.getValue(String::class.java)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        groupImgRef.addValueEventListener(listener)
        onDispose { groupImgRef.removeEventListener(listener) }
    }

    // Observe group name
    DisposableEffect(channelId) {
        val channelRef = db.reference.child("channel").child(channelId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name = snapshot.getValue(String::class.java)
                if (!name.isNullOrBlank()) {
                    currentGroupName = name
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        channelRef.addValueEventListener(listener)
        onDispose { channelRef.removeEventListener(listener) }
    }

    // Fetch creator and members
    LaunchedEffect(channelId) {
        val membersRef = db.reference.child("channels").child(channelId).child("users")
        val creatorRef = db.reference.child("channel_creator").child(channelId)

        membersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val memberUids = snapshot.children.mapNotNull { it.key }.toMutableSet()
                creatorRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(creatorSnap: DataSnapshot) {
                        val creatorUid = creatorSnap.getValue(String::class.java)
                        if (!creatorUid.isNullOrBlank()) {
                            groupCreatorUid = creatorUid
                            memberUids.add(creatorUid)
                        }
                        if (memberUids.isEmpty()) {
                            groupMembersList = emptyList()
                            isLoadingMembers = false
                            return
                        }
                        val usersRef = db.reference.child("users")
                        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(usersSnapshot: DataSnapshot) {
                                val memberList = mutableListOf<User>()
                                for (uid in memberUids) {
                                    val userSnap = usersSnapshot.child(uid)
                                    val name = userSnap.child("name").getValue(String::class.java)
                                        ?: userSnap.child("email").getValue(String::class.java)
                                        ?: "User"
                                    val email = userSnap.child("email").getValue(String::class.java) ?: ""
                                    val imageUrl = userSnap.child("imageUrl").getValue(String::class.java)
                                    val about = userSnap.child("about").getValue(String::class.java)
                                    memberList.add(
                                        User(
                                            uid = uid,
                                            name = name,
                                            email = email,
                                            profileImage = imageUrl,
                                            about = about
                                        )
                                    )
                                }
                                groupMembersList = memberList
                                isLoadingMembers = false
                            }

                            override fun onCancelled(error: DatabaseError) {
                                isLoadingMembers = false
                            }
                        })
                    }

                    override fun onCancelled(error: DatabaseError) {
                        isLoadingMembers = false
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                isLoadingMembers = false
            }
        })
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Group Info",
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
                // Group Photo (Clickable to change for any group member)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { showGroupPhotoChoiceDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (!groupImageUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = groupImageUrl,
                            contentDescription = "Group Photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = currentGroupName.firstOrNull()?.uppercase() ?: "G",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Camera icon overlay badge at bottom-right
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change Group Photo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = currentGroupName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Online member count
                val onlineCount = rememberGroupOnlineCount(channelId)
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

                Spacer(modifier = Modifier.height(14.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.8.dp)

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Members",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isLoadingMembers) {
                        Text(
                            text = "${groupMembersList.size} members",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoadingMembers) {
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
                } else {
                    val isCurrentUserCreator = (currentUid == groupCreatorUid)
                    val groupListMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.35f).coerceIn(200f, 400f).dp
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = groupListMaxHeight)
                    ) {
                        items(groupMembersList, key = { it.uid }) { member ->
                            val isMemberCreator = (member.uid == groupCreatorUid)
                            GroupMemberRow(
                                member = member,
                                isCreator = isMemberCreator,
                                canRemove = isCurrentUserCreator && !isMemberCreator,
                                onMemberClick = {
                                    selectedMemberForProfile = member
                                },
                                onRemoveClicked = {
                                    memberToRemove = member
                                }
                            )
                        }
                    }
                }
            }
        }
    )

    if (selectedMemberForProfile != null) {
        UserProfileDialog(
            user = selectedMemberForProfile!!,
            onDismissRequest = { selectedMemberForProfile = null }
        )
    }

    // Member Removal Confirmation Dialog
    if (memberToRemove != null) {
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Remove Member",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove ${memberToRemove?.name} from this group?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uidToRemove = memberToRemove?.uid ?: return@Button
                        db.reference.child("channels").child(channelId).child("users").child(uidToRemove)
                            .removeValue()
                            .addOnSuccessListener {
                                groupMembersList = groupMembersList.filter { it.uid != uidToRemove }
                                memberToRemove = null
                            }
                            .addOnFailureListener { error ->
                                Toast.makeText(context, error.message ?: "Failed to remove member", Toast.LENGTH_SHORT).show()
                                memberToRemove = null
                            }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    // Dialog for changing custom group photo
    if (showGroupPhotoChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showGroupPhotoChoiceDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Change Group Photo",
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
                                groupGalleryLauncher.launch("image/*")
                                showGroupPhotoChoiceDialog = false
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
                                val file = File(context.cacheDir, "camera_grp_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                                )
                                tempGroupCameraUri = uri
                                groupCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                showGroupPhotoChoiceDialog = false
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
                TextButton(onClick = { showGroupPhotoChoiceDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}
