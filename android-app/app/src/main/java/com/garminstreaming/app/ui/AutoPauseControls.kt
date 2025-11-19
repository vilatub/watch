package com.garminstreaming.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garminstreaming.app.autopause.AutoPauseManager
import com.garminstreaming.app.autopause.AutoPauseSettings

/**
 * Compact auto-pause toggle for main screen
 */
@Composable
fun AutoPauseToggle(
    autoPauseManager: AutoPauseManager,
    modifier: Modifier = Modifier,
    onExpandSettings: () -> Unit = {}
) {
    val settings by autoPauseManager.settings.collectAsState()
    val state by autoPauseManager.state.collectAsState()

    val backgroundColor by animateColorAsState(
        targetValue = when {
            !settings.enabled -> MaterialTheme.colorScheme.surfaceVariant
            state.isPaused -> Color(0xFFFF9800).copy(alpha = 0.3f)
            state.isWaitingToPause -> Color(0xFFFFEB3B).copy(alpha = 0.3f)
            else -> Color(0xFF4CAF50).copy(alpha = 0.3f)
        },
        label = "autoPauseBackground"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            !settings.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            state.isPaused -> Color(0xFFFF9800)
            state.isWaitingToPause -> Color(0xFFFFEB3B)
            else -> Color(0xFF4CAF50)
        },
        label = "autoPauseIcon"
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
            imageVector = if (state.isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = "Auto-Pause",
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )

        if (settings.enabled && state.isPaused) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = state.pausedDurationFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = iconColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Expandable auto-pause settings panel
 */
@Composable
fun AutoPauseSettingsPanel(
    autoPauseManager: AutoPauseManager,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val settings by autoPauseManager.settings.collectAsState()
    val state by autoPauseManager.state.collectAsState()

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
                    text = "Auto-Pause",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { autoPauseManager.setEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                    )
                )
            }

            if (settings.enabled) {
                Spacer(modifier = Modifier.height(16.dp))

                // Speed threshold selector
                Text(
                    text = "Speed Threshold",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Speed threshold buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SpeedThresholdButton(
                        label = "0.3",
                        speedMs = 0.3,
                        isSelected = settings.speedThresholdMs == 0.3,
                        onClick = { autoPauseManager.setSpeedThreshold(0.3) }
                    )
                    SpeedThresholdButton(
                        label = "0.5",
                        speedMs = 0.5,
                        isSelected = settings.speedThresholdMs == 0.5,
                        onClick = { autoPauseManager.setSpeedThreshold(0.5) }
                    )
                    SpeedThresholdButton(
                        label = "1.0",
                        speedMs = 1.0,
                        isSelected = settings.speedThresholdMs == 1.0,
                        onClick = { autoPauseManager.setSpeedThreshold(1.0) }
                    )
                    SpeedThresholdButton(
                        label = "1.5",
                        speedMs = 1.5,
                        isSelected = settings.speedThresholdMs == 1.5,
                        onClick = { autoPauseManager.setSpeedThreshold(1.5) }
                    )
                }

                Text(
                    text = "Pause when speed drops below ${settings.speedThresholdMs} m/s (${String.format("%.1f", settings.speedThresholdMs * 3.6)} km/h)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Current status
                val statusColor = when {
                    state.isPaused -> Color(0xFFFF9800)
                    state.isWaitingToPause -> Color(0xFFFFEB3B)
                    state.isWaitingToResume -> Color(0xFF4CAF50)
                    else -> Color(0xFF4CAF50)
                }

                val statusText = when {
                    state.isPaused -> "Paused"
                    state.isWaitingToPause -> "Stopping..."
                    state.isWaitingToResume -> "Resuming..."
                    else -> "Active"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = statusColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Stats
                if (state.pauseCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Pauses: ${state.pauseCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Paused time: ${state.pausedDurationFormatted}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Speed threshold selection button
 */
@Composable
fun SpeedThresholdButton(
    label: String,
    speedMs: Double,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                color = if (isSelected) {
                    Color(0xFF4CAF50)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$label m/s",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Paused indicator overlay
 */
@Composable
fun PausedIndicator(
    state: com.garminstreaming.app.autopause.AutoPauseState,
    modifier: Modifier = Modifier
) {
    if (state.isPaused) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFFFF9800).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Pause,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Auto-Paused • ${state.pausedDurationFormatted}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFFF9800),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
