package com.example.kchat.feature.home

import android.Manifest
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.kchat.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import java.io.File

data class FriendSelectionItem(
    val uid: String,
    val name: String,
    val imageUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreateGroup: (groupName: String, selectedFriendUids: List<String>, imageUri: Uri?) -> Unit
) {
    // ── State ──────────────────────────────────────────────────────────────────
    var groupName by remember { mutableStateOf("") }
    val selectedFriendUids = remember { mutableStateListOf<String>() }
    var friends by remember { mutableStateOf<List<FriendSelectionItem>>(emptyList()) }
    var isLoadingFriends by remember { mutableStateOf(true) }
    var friendSearchQuery by remember { mutableStateOf("") }

    // Case-insensitive substring search — runs locally on already-loaded list
    val filteredFriends = remember(friends, friendSearchQuery) {
        val q = friendSearchQuery.trim().lowercase()
        if (q.isEmpty()) friends else friends.filter { it.name.lowercase().contains(q) }
    }

    val context = LocalContext.current
    var selectedGroupImageUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoPickerChoice by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // ── Launchers ──────────────────────────────────────────────────────────────
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) selectedGroupImageUri = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) tempCameraUri?.let { selectedGroupImageUri = it }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) tempCameraUri?.let { cameraLauncher.launch(it) }
    }

    // ── Firebase friend load (runs once) ────────────────────────────────────────
    val currentUid = Firebase.auth.currentUser?.uid
    val db = Firebase.database

    LaunchedEffect(currentUid) {
        if (currentUid == null) {
            isLoadingFriends = false
            return@LaunchedEffect
        }
        db.reference.child("friends").child(currentUid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val friendUids = snapshot.children.mapNotNull { it.key }
                    if (friendUids.isEmpty()) {
                        friends = emptyList()
                        isLoadingFriends = false
                        return
                    }
                    val loadedFriends = mutableListOf<FriendSelectionItem>()
                    var pendingCount = friendUids.size
                    friendUids.forEach { friendUid ->
                        db.reference.child("users").child(friendUid)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(userSnap: DataSnapshot) {
                                    val name = userSnap.child("name").getValue(String::class.java)
                                        ?: userSnap.child("email").getValue(String::class.java)
                                        ?: "User"
                                    val imageUrl = userSnap.child("imageUrl").getValue(String::class.java)
                                    loadedFriends.add(FriendSelectionItem(uid = friendUid, name = name, imageUrl = imageUrl))
                                    pendingCount--
                                    if (pendingCount <= 0) {
                                        friends = loadedFriends.sortedBy { it.name.lowercase() }
                                        isLoadingFriends = false
                                    }
                                }
                                override fun onCancelled(error: DatabaseError) {
                                    pendingCount--
                                    if (pendingCount <= 0) {
                                        friends = loadedFriends.sortedBy { it.name.lowercase() }
                                        isLoadingFriends = false
                                    }
                                }
                            })
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    isLoadingFriends = false
                }
            })
    }

    // ── Full-screen Scaffold ───────────────────────────────────────────────────
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Create Group",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 0.dp,
                color = MaterialTheme.colorScheme.background
            ) {
                Button(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            onCreateGroup(groupName.trim(), selectedFriendUids.toList(), selectedGroupImageUri)
                        }
                    },
                    enabled = groupName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorResource(id = R.color.light_blue),
                        contentColor = Color.White,
                        disabledContainerColor = colorResource(id = R.color.light_blue).copy(alpha = 0.38f)
                    )
                ) {
                    val countText = if (selectedFriendUids.isNotEmpty()) "${selectedFriendUids.size} " else ""
                    Text(
                        text = "Create Group ( ${countText}members)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {

            // ── Group image + name row ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8EEF5))
                        .clickable { showPhotoPickerChoice = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedGroupImageUri != null) {
                        AsyncImage(
                            model = selectedGroupImageUri,
                            contentDescription = "Group Photo",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Add Group Photo",
                            tint = Color(0xFF4A5568),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                TextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    placeholder = {
                        Text(
                            text = "Group Name",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Select Friends header ──────────────────────────────────────────
            val countHeading = if (selectedFriendUids.isNotEmpty()) selectedFriendUids.size.toString() else ""
            Text(
                text = "Select Friends ($countHeading)",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Search field (shown only after friends load) ───────────────────
            if (!isLoadingFriends && friends.isNotEmpty()) {
                TextField(
                    value = friendSearchQuery,
                    onValueChange = { friendSearchQuery = it },
                    placeholder = {
                        Text("Search friends...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (friendSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { friendSearchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ── Friend list ────────────────────────────────────────────────────
            when {
                isLoadingFriends -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                friends.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No friends yet. Add friends first to invite them to a group.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                filteredFriends.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No friends match \"$friendSearchQuery\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                else -> {
                    // Full-screen gives ample space — use weight(1f) to fill available area
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(filteredFriends, key = { it.uid }) { friend ->
                            val isChecked = friend.uid in selectedFriendUids
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (isChecked) selectedFriendUids.remove(friend.uid)
                                        else selectedFriendUids.add(friend.uid)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!friend.imageUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = friend.imageUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = friend.name.firstOrNull()?.uppercase() ?: "U",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Text(
                                    text = friend.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )

                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedFriendUids.add(friend.uid)
                                        else selectedFriendUids.remove(friend.uid)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                                        uncheckedColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Photo picker dialog ────────────────────────────────────────────────────
    if (showPhotoPickerChoice) {
        AlertDialog(
            onDismissRequest = { showPhotoPickerChoice = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Group Photo",
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
                                showPhotoPickerChoice = false
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
                                tempCameraUri = uri
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                showPhotoPickerChoice = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Take Photo", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                    }
                    if (selectedGroupImageUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedGroupImageUri = null
                                    showPhotoPickerChoice = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Remove Photo", color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoPickerChoice = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}
