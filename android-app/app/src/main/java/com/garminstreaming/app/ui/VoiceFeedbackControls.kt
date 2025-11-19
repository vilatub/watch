package com.garminstreaming.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garminstreaming.app.voice.VoiceFeedbackManager

/**
 * Compact voice feedback toggle for main screen
 */
@Composable
fun VoiceFeedbackToggle(
    voiceManager: VoiceFeedbackManager,
    modifier: Modifier = Modifier,
    onExpandSettings: () -> Unit = {}
) {
    val settings by voiceManager.settings.collectAsState()
    val state by voiceManager.state.collectAsState()

    val backgroundColor by animateColorAsState(
        targetValue = when {
            !settings.enabled -> MaterialTheme.colorScheme.surfaceVariant
            state.isSpeaking -> Color(0xFF2196F3).copy(alpha = 0.5f)
            else -> Color(0xFF2196F3).copy(alpha = 0.3f)
        },
        label = "voiceBackground"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            !settings.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            else -> Color(0xFF2196F3)
        },
        label = "voiceIcon"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable { onExpandSettings() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (settings.enabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
            contentDescription = "Voice Feedback",
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Expandable voice feedback settings panel
 */
@Composable
fun VoiceFeedbackSettingsPanel(
    voiceManager: VoiceFeedbackManager,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val settings by voiceManager.settings.collectAsState()
    val state by voiceManager.state.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with enable toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Voice Feedback",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { voiceManager.setEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF2196F3),
                        checkedTrackColor = Color(0xFF2196F3).copy(alpha = 0.5f)
                    )
                )
            }

            if (settings.enabled) {
                Spacer(modifier = Modifier.height(16.dp))

                // Announcement options
                Text(
                    text = "Announcements",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Distance announcements
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Every kilometer",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Checkbox(
                        checked = settings.announceDistance,
                        onCheckedChange = {
                            voiceManager.updateSettings(settings.copy(announceDistance = it))
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF2196F3)
                        )
                    )
                }

                // Time announcements
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Every ${settings.timeIntervalMinutes} minutes",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Checkbox(
                        checked = settings.announceTime,
                        onCheckedChange = {
                            voiceManager.updateSettings(settings.copy(announceTime = it))
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF2196F3)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Include in announcements
                Text(
                    text = "Include",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Pace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pace",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Checkbox(
                        checked = settings.announcePace,
                        onCheckedChange = {
                            voiceManager.updateSettings(settings.copy(announcePace = it))
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF2196F3)
                        )
                    )
                }

                // Heart rate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Heart Rate",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Checkbox(
                        checked = settings.announceHeartRate,
                        onCheckedChange = {
                            voiceManager.updateSettings(settings.copy(announceHeartRate = it))
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF2196F3)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Test button
                OutlinedButton(
                    onClick = { voiceManager.testVoice() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF2196F3)
                    )
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Voice")
                }

                // Stats
                if (state.announcementCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Announcements: ${state.announcementCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
