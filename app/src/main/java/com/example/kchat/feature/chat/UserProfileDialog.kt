package com.example.kchat.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import coil.compose.AsyncImage
import com.example.kchat.R
import com.example.kchat.model.User
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database

@Composable
fun UserProfileDialog(
    user: User,
    onDismissRequest: () -> Unit
) {
    val db = Firebase.database
    var currentName by remember(user.uid) { mutableStateOf(user.name) }
    var currentEmail by remember(user.uid) { mutableStateOf(user.email) }
    var currentImage by remember(user.uid) { mutableStateOf(user.profileImage) }
    var currentAbout by remember(user.uid) { mutableStateOf(user.about) }
    var isOnline by remember(user.uid) { mutableStateOf(false) }
    var lastSeen by remember(user.uid) { mutableStateOf<Long?>(null) }

    DisposableEffect(user.uid) {
        val userRef = db.reference.child("users").child(user.uid)
        val userListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.child("name").getValue(String::class.java)?.let { if (it.isNotBlank()) currentName = it }
                snapshot.child("email").getValue(String::class.java)?.let { currentEmail = it }
                snapshot.child("imageUrl").getValue(String::class.java)?.let { currentImage = it }
                snapshot.child("about").getValue(String::class.java)?.let { currentAbout = it }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        userRef.addValueEventListener(userListener)

        val statusRef = db.reference.child("status").child(user.uid)
        val statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isOnline = snapshot.child("online").getValue(Boolean::class.java) ?: false
                lastSeen = snapshot.child("lastSeen").getValue(Long::class.java)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        statusRef.addValueEventListener(statusListener)

        onDispose {
            userRef.removeEventListener(userListener)
            statusRef.removeEventListener(statusListener)
        }
    }

        val isProfileDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
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
                    text = "User Profile",
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
                    if (!currentImage.isNullOrEmpty()) {
                        AsyncImage(
                            model = currentImage,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = currentName.firstOrNull()?.uppercase() ?: "U",
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = currentName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                if (!currentEmail.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentEmail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isOnline) Color(0f, 0.55f, 0.65f, 0.2f)
                            else if (isProfileDark) Color.DarkGray else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isOnline) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0f, 0.55f, 0.65f, 1f))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = if (isOnline) "Online" else DateTimeUtils.formatPresence(false, lastSeen),
                            color = if (isOnline) Color(0f, 0.55f, 0.65f, 1f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // About Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isProfileDark) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "About",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentAbout?.ifBlank { "Hey there! I am using KChat." }
                                ?: "Hey there! I am using KChat.",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    )
}
