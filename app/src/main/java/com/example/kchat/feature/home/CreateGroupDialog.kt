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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
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
import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreateGroup: (groupName: String, selectedFriendUids: List<String>, imageUri: Uri?) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val selectedFriendUids = remember { mutableStateListOf<String>() }
    var friends by remember { mutableStateOf<List<FriendSelectionItem>>(emptyList()) }
    var isLoadingFriends by remember { mutableStateOf(true) }

    val context = LocalContext.current
    var selectedGroupImageUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoPickerChoice by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedGroupImageUri = uri
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempCameraUri?.let { uri ->
                selectedGroupImageUri = uri
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            tempCameraUri?.let { uri ->
                cameraLauncher.launch(uri)
            }
        }
    }

    var isTextFieldFocused by remember { mutableStateOf(false) }
    var isKeyboardDismissedByBack by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val view = LocalView.current
    var isKeyboardVisible by remember { mutableStateOf(false) }

    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val insets = ViewCompat.getRootWindowInsets(view)
            val imeVisibleFromInsets = insets?.isVisible(WindowInsetsCompat.Type.ime()) ?: false

            val rect = Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            val imeVisibleFromHeight = keypadHeight > screenHeight * 0.15

            val currentlyVisible = imeVisibleFromInsets || imeVisibleFromHeight
            isKeyboardVisible = currentlyVisible
            if (currentlyVisible) {
                isKeyboardDismissedByBack = false
            }
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    val isKeyboardOpen = isKeyboardVisible || isTextFieldFocused || WindowInsets.isImeVisible

    BackHandler(enabled = true) {
        if (!isKeyboardDismissedByBack && isKeyboardOpen) {
            isKeyboardDismissedByBack = true
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        } else {
            onDismiss()
        }
    }

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            text = "Create Group",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showPhotoPickerChoice = true },
                contentAlignment = Alignment.Center
            ) {
                if (selectedGroupImageUri != null) {
                    AsyncImage(
                        model = selectedGroupImageUri,
                        contentDescription = "Group Photo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Add Group Photo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            TextField(
                value = groupName,
                onValueChange = { groupName = it },
                placeholder = { Text("Enter group name...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .onFocusChanged { focusState ->
                        isTextFieldFocused = focusState.isFocused
                        if (focusState.isFocused) {
                            isKeyboardDismissedByBack = false
                        }
                    },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Select Friends",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (selectedFriendUids.isNotEmpty()) {
                Text(
                    text = "${selectedFriendUids.size} selected",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoadingFriends) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else if (friends.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No friends in list. Add friends first to invite them.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                items(friends, key = { it.uid }) { friend ->
                    val isChecked = friend.uid in selectedFriendUids
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (isChecked) {
                                    selectedFriendUids.remove(friend.uid)
                                } else {
                                    selectedFriendUids.add(friend.uid)
                                }
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!friend.imageUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = friend.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = friend.name.firstOrNull()?.uppercase() ?: "U",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

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
                                if (checked) {
                                    selectedFriendUids.add(friend.uid)
                                } else {
                                    selectedFriendUids.remove(friend.uid)
                                }
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

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (groupName.isNotBlank()) {
                        onCreateGroup(groupName.trim(), selectedFriendUids.toList(), selectedGroupImageUri)
                    }
                },
                enabled = groupName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create Group", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }

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
