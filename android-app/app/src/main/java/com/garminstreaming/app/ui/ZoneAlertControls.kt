package com.garminstreaming.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garminstreaming.app.HeartRateZone
import com.garminstreaming.app.alerts.ZoneAlertManager
import com.garminstreaming.app.alerts.ZoneAlertSettings
import com.garminstreaming.app.alerts.ZoneAlertState

/**
 * Compact zone alert toggle for main screen
 */
@Composable
fun ZoneAlertToggle(
    alertManager: ZoneAlertManager,
    modifier: Modifier = Modifier,
    onExpandSettings: () -> Unit = {}
) {
    val settings by alertManager.settings.collectAsState()
    val alertState by alertManager.alertState.collectAsState()

    val backgroundColor by animateColorAsState(
        targetValue = when {
            !settings.enabled -> MaterialTheme.colorScheme.surfaceVariant
            alertState.isInTargetZone -> Color(0xFF4CAF50).copy(alpha = 0.3f)
            else -> Color(0xFFF44336).copy(alpha = 0.3f)
        },
        label = "alertBackground"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            !settings.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            alertState.isInTargetZone -> Color(0xFF4CAF50)
            else -> Color(0xFFF44336)
        },
        label = "alertIcon"
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
            imageVector = if (settings.enabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
            contentDescription = "Zone Alerts",
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )

        if (settings.enabled) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = settings.targetZone.zoneName,
                style = MaterialTheme.typography.labelSmall,
                color = iconColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Expandable zone alert settings panel
 */
@Composable
fun ZoneAlertSettingsPanel(
    alertManager: ZoneAlertManager,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val settings by alertManager.settings.collectAsState()
    val alertState by alertManager.alertState.collectAsState()

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
                    text = "Zone Alerts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { alertManager.setEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                    )
                )
            }

            if (settings.enabled) {
                Spacer(modifier = Modifier.height(16.dp))

                // Target zone selector
                Text(
                    text = "Target Zone",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HeartRateZone.entries.forEach { zone ->
                        ZoneButton(
                            zone = zone,
                            isSelected = settings.targetZone == zone,
                            isCurrent = alertState.currentZone == zone,
                            onClick = { alertManager.setTargetZone(zone) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Current status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (alertState.isInTargetZone) {
                                Color(0xFF4CAF50).copy(alpha = 0.2f)
                            } else {
                                Color(0xFFF44336).copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val statusColor = if (alertState.isInTargetZone) {
                        Color(0xFF4CAF50)
                    } else {
                        Color(0xFFF44336)
                    }

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (alertState.isInTargetZone) {
                            "In target zone"
                        } else {
                            "Outside target (${alertState.currentZone.zoneName})"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Test vibration button
                OutlinedButton(
                    onClick = { alertManager.testVibration() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.Vibration,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Vibration")
                }

                // Alert count
                if (alertState.alertCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Alerts triggered: ${alertState.alertCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Zone selection button
 */
@Composable
fun ZoneButton(
    zone: HeartRateZone,
    isSelected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val zoneColor = Color(zone.color)

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                color = if (isSelected) zoneColor else zoneColor.copy(alpha = 0.3f)
            )
            .border(
                width = if (isCurrent) 3.dp else 0.dp,
                color = if (isCurrent) Color.White else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = zone.ordinal.plus(1).toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else zoneColor
        )
    }
}

/**
 * Out-of-zone warning indicator
 */
@Composable
fun OutOfZoneWarning(
    alertState: ZoneAlertState,
    modifier: Modifier = Modifier
) {
    if (!alertState.isInTargetZone) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFFF44336).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = Color(0xFFF44336),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Outside ${alertState.targetZone.zoneName}! Currently in ${alertState.currentZone.zoneName}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFF44336),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
