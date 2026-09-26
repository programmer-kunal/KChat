package com.example.kchat.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Clean Material 3 theme selection dialog for KChat.
 * Allows switching between System Default, Light, and Dark mode with radio selection.
 */
@Composable
fun ThemeSelectionDialog(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Change Theme",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThemeOptionRow(
                    theme = AppTheme.SYSTEM,
                    icon = Icons.Default.BrightnessAuto,
                    isSelected = currentTheme == AppTheme.SYSTEM,
                    onClick = { onThemeSelected(AppTheme.SYSTEM) }
                )
                ThemeOptionRow(
                    theme = AppTheme.LIGHT,
                    icon = Icons.Default.LightMode,
                    isSelected = currentTheme == AppTheme.LIGHT,
                    onClick = { onThemeSelected(AppTheme.LIGHT) }
                )
                ThemeOptionRow(
                    theme = AppTheme.DARK,
                    icon = Icons.Default.DarkMode,
                    isSelected = currentTheme == AppTheme.DARK,
                    onClick = { onThemeSelected(AppTheme.DARK) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Done",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
private fun ThemeOptionRow(
    theme: AppTheme,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = theme.displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        RadioButton(
            selected = isSelected,
            onClick = null, // handled by Row selectable
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
