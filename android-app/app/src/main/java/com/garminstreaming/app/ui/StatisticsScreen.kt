package com.garminstreaming.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garminstreaming.app.data.ActivityTypeStats
import com.garminstreaming.app.data.AppDatabase
import com.garminstreaming.app.data.WeeklyStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var activityStats by remember { mutableStateOf<List<ActivityTypeStats>>(emptyList()) }
    var weeklyStats by remember { mutableStateOf<WeeklyStats?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val dao = database.activitySessionDao()

            activityStats = dao.getStatsByActivityType()

            // Calculate start of current week (Monday)
            val calendar = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            weeklyStats = dao.getWeeklyStats(calendar.timeInMillis)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Weekly summary
                item {
                    WeeklySummaryCard(weeklyStats = weeklyStats)
                }

                // Overall totals
                item {
                    OverallTotalsCard(activityStats = activityStats)
                }

                // Stats by activity type
                if (activityStats.isNotEmpty()) {
                    item {
                        Text(
                            text = "By Activity Type",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(activityStats) { stats ->
                        ActivityTypeCard(stats = stats)
                    }
                } else {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No activities recorded yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklySummaryCard(weeklyStats: WeeklyStats?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "This Week",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn(
                    value = weeklyStats?.sessionCount?.toString() ?: "0",
                    label = "Sessions",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                StatColumn(
                    value = "%.1f".format(weeklyStats?.totalDistanceKm ?: 0.0),
                    label = "km",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                StatColumn(
                    value = weeklyStats?.totalDurationFormatted ?: "0m",
                    label = "Time",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun OverallTotalsCard(activityStats: List<ActivityTypeStats>) {
    val totalSessions = activityStats.sumOf { it.sessionCount }
    val totalDistance = activityStats.sumOf { it.totalDistance }
    val totalDuration = activityStats.sumOf { it.totalDuration }

    val totalDurationFormatted = run {
        val totalSeconds = totalDuration / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        if (hours > 0) "%dh %dm".format(hours, minutes) else "%dm".format(minutes)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "All Time",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn(
                    value = totalSessions.toString(),
                    label = "Sessions",
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                StatColumn(
                    value = "%.1f".format(totalDistance / 1000.0),
                    label = "km",
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                StatColumn(
                    value = totalDurationFormatted,
                    label = "Time",
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun ActivityTypeCard(stats: ActivityTypeStats) {
    val activityIcon = when (stats.activityType.lowercase()) {
        "running" -> "🏃"
        "cycling" -> "🚴"
        "walking" -> "🚶"
        "hiking" -> "🥾"
        "swimming" -> "🏊"
        else -> "🏃"
    }

    val activityColor = when (stats.activityType.lowercase()) {
        "running" -> Color(0xFF4CAF50)
        "cycling" -> Color(0xFF2196F3)
        "walking" -> Color(0xFFFF9800)
        "hiking" -> Color(0xFF795548)
        "swimming" -> Color(0xFF00BCD4)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
            // Header with activity type
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activityIcon,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stats.activityType.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = activityColor
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${stats.sessionCount} sessions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatBox(
                    value = "%.1f".format(stats.totalDistanceKm),
                    unit = "km",
                    label = "Distance"
                )
                StatBox(
                    value = stats.totalDurationFormatted,
                    unit = "",
                    label = "Time"
                )
                StatBox(
                    value = "%.0f".format(stats.avgHeartRate),
                    unit = "bpm",
                    label = "Avg HR"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Show pace for running/walking/hiking, speed for cycling
                if (stats.activityType.lowercase() in listOf("running", "walking", "hiking")) {
                    StatBox(
                        value = stats.avgPaceFormatted,
                        unit = "/km",
                        label = "Avg Pace"
                    )
                } else {
                    StatBox(
                        value = "%.1f".format(stats.avgSpeedKmh),
                        unit = "km/h",
                        label = "Avg Speed"
                    )
                }

                if (stats.maxHeartRate > 0) {
                    StatBox(
                        value = stats.maxHeartRate.toString(),
                        unit = "bpm",
                        label = "Max HR"
                    )
                } else {
                    Spacer(modifier = Modifier.width(80.dp))
                }

                if (stats.totalElevationGain > 0) {
                    StatBox(
                        value = "%.0f".format(stats.totalElevationGain),
                        unit = "m",
                        label = "Elevation"
                    )
                } else if (stats.avgCadence > 0) {
                    StatBox(
                        value = "%.0f".format(stats.avgCadence),
                        unit = "spm",
                        label = "Cadence"
                    )
                } else {
                    Spacer(modifier = Modifier.width(80.dp))
                }
            }
        }
    }
}

@Composable
fun StatColumn(value: String, label: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun StatBox(value: String, unit: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
