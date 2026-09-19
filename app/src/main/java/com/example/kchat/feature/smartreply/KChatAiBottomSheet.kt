package com.example.kchat.feature.smartreply

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kchat.R

/**
 * Compact entry-point button for KChat AI inside the conversation composer.
 * Designed with a ~38dp touch target and subtle accent styling so it never
 * overlaps the attachment, message input, or send button.
 */
@Composable
fun KChatAiEntryPoint(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(38.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(colorResource(id = R.color.light_blue).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "KChat AI",
                tint = colorResource(id = R.color.light_blue),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Material 3 ModalBottomSheet displaying the KChat AI intelligence suite.
 * Houses a 2x2 responsive grid for the four AI capabilities:
 * 1. Smart Reply (Active)
 * 2. Smart Thread Summarization (Active)
 * 3. Context-Aware Conversation Search (Active)
 * 4. AI Scam & Phishing Guard (Active)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KChatAiBottomSheet(
    onDismiss: () -> Unit,
    onSmartReplyClicked: () -> Unit,
    onThreadSummaryClicked: () -> Unit,
    onContextSearchClicked: () -> Unit,
    onScamGuardClicked: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorResource(id = R.color.dark_blue),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Gray.copy(alpha = 0.5f))
            )
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header: Sparkle + Title + Subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colorResource(id = R.color.light_blue).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "KChat AI",
                        tint = colorResource(id = R.color.light_blue),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "KChat AI",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Contextual Conversational Intelligence",
                        color = colorResource(id = R.color.light_blue).copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2x2 Feature Grid - Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AiFeatureCard(
                    title = "Smart Reply",
                    subtitle = "Context-aware reply suggestions",
                    icon = Icons.Default.AutoAwesome,
                    statusBadge = "Ready",
                    isActive = true,
                    onClick = {
                        onDismiss()
                        onSmartReplyClicked()
                    },
                    modifier = Modifier.weight(1f)
                )
                AiFeatureCard(
                    title = "Thread Summary",
                    subtitle = "Key conversation highlights",
                    icon = Icons.Default.Summarize,
                    statusBadge = "Ready",
                    isActive = true,
                    onClick = {
                        onDismiss()
                        onThreadSummaryClicked()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2x2 Feature Grid - Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AiFeatureCard(
                    title = "Context Search",
                    subtitle = "Search by topic & intent",
                    icon = Icons.Default.Search,
                    statusBadge = "Ready",
                    isActive = true,
                    onClick = {
                        onDismiss()
                        onContextSearchClicked()
                    },
                    modifier = Modifier.weight(1f)
                )
                AiFeatureCard(
                    title = "Scam Guard",
                    subtitle = "Detect suspicious requests",
                    icon = Icons.Default.Security,
                    statusBadge = "Ready",
                    isActive = true,
                    onClick = {
                        onDismiss()
                        onScamGuardClicked()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Reusable card composable for each capability in the 2x2 KChat AI grid.
 */
@Composable
private fun AiFeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    statusBadge: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) colorResource(id = R.color.light_blue).copy(alpha = 0.12f)
                else Color.White.copy(alpha = 0.05f)
            )
            .border(
                width = 1.dp,
                color = if (isActive) colorResource(id = R.color.light_blue).copy(alpha = 0.45f)
                else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top Row: Icon + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) colorResource(id = R.color.light_blue).copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.08f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isActive) colorResource(id = R.color.light_blue) else Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Status Badge ("Ready" or "Soon")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isActive) colorResource(id = R.color.light_blue).copy(alpha = 0.22f)
                            else Color.White.copy(alpha = 0.1f)
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusBadge,
                        color = if (isActive) colorResource(id = R.color.light_blue) else Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = subtitle,
                color = Color.LightGray.copy(alpha = 0.85f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2
            )
        }
    }
}
