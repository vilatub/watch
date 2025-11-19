package com.garminstreaming.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garminstreaming.app.data.ActivitySession
import com.garminstreaming.app.data.SessionManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SessionManager.getInstance() }
    var sessions by remember { mutableStateOf<List<ActivitySession>>(emptyList()) }
    var selectedPeriod by remember { mutableStateOf(AnalyticsPeriod.WEEK) }

    LaunchedEffect(Unit) {
        repository.getAllSessions().collect { allSessions ->
            sessions = allSessions
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Period selector
            PeriodSelector(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { selectedPeriod = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Filter sessions by period
            val filteredSessions = filterSessionsByPeriod(sessions, selectedPeriod)

            if (filteredSessions.isEmpty()) {
                EmptyAnalyticsMessage()
            } else {
                // Summary stats
                SummaryStatsCard(filteredSessions)

                Spacer(modifier = Modifier.height(16.dp))

                // Distance chart
                Text(
                    text = "Distance Over Time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                DistanceChart(
                    sessions = filteredSessions,
                    period = selectedPeriod,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Pace trend
                Text(
                    text = "Pace Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                PaceTrendChart(
                    sessions = filteredSessions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Heart rate zones distribution
                Text(
                    text = "HR Zone Distribution",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                ZoneDistributionChart(
                    sessions = filteredSessions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Personal records
                PersonalRecordsCard(sessions)

                Spacer(modifier = Modifier.height(16.dp))

                // Weekly heatmap
                if (selectedPeriod == AnalyticsPeriod.MONTH) {
                    Text(
                        text = "Training Heatmap",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TrainingHeatmap(
                        sessions = filteredSessions,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

enum class AnalyticsPeriod(val label: String, val days: Int) {
    WEEK("Week", 7),
    MONTH("Month", 30),
    YEAR("Year", 365)
}

@Composable
fun PeriodSelector(
    selectedPeriod: AnalyticsPeriod,
    onPeriodSelected: (AnalyticsPeriod) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        AnalyticsPeriod.entries.forEach { period ->
            FilterChip(
                selected = selectedPeriod == period,
                onClick = { onPeriodSelected(period) },
                label = { Text(period.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
fun SummaryStatsCard(sessions: List<ActivitySession>) {
    val totalDistance = sessions.sumOf { it.distanceMeters } / 1000.0
    val totalDuration = sessions.sumOf { it.durationMs }
    val avgHeartRate = sessions.filter { it.avgHeartRate > 0 }
        .takeIf { it.isNotEmpty() }
        ?.map { it.avgHeartRate }
        ?.average()?.toInt() ?: 0

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
                text = "Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryStatItem(
                    value = sessions.size.toString(),
                    label = "Workouts"
                )
                SummaryStatItem(
                    value = "%.1f".format(totalDistance),
                    label = "km"
                )
                SummaryStatItem(
                    value = formatTotalDuration(totalDuration),
                    label = "Time"
                )
                if (avgHeartRate > 0) {
                    SummaryStatItem(
                        value = avgHeartRate.toString(),
                        label = "Avg HR"
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryStatItem(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun DistanceChart(
    sessions: List<ActivitySession>,
    period: AnalyticsPeriod,
    modifier: Modifier = Modifier
) {
    val groupedData = groupSessionsByDay(sessions, period)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (groupedData.isEmpty()) return@Canvas

            val maxDistance = groupedData.maxOfOrNull { it.second } ?: 1.0
            val barWidth = (size.width - 32.dp.toPx()) / groupedData.size
            val chartHeight = size.height - 20.dp.toPx()

            groupedData.forEachIndexed { index, (_, distance) ->
                val barHeight = (distance / maxDistance * chartHeight).toFloat()
                val x = 16.dp.toPx() + index * barWidth

                drawRect(
                    color = Color(0xFF4CAF50),
                    topLeft = Offset(x, chartHeight - barHeight),
                    size = Size(barWidth * 0.8f, barHeight)
                )
            }
        }
    }
}

@Composable
fun PaceTrendChart(
    sessions: List<ActivitySession>,
    modifier: Modifier = Modifier
) {
    val paceData = sessions
        .filter { it.avgSpeed > 0 && it.distanceMeters > 0 }
        .sortedBy { it.startTime }
        .map { session ->
            val paceSeconds = (1000.0 / session.avgSpeed).toInt()
            session.startTime to paceSeconds
        }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (paceData.size < 2) return@Canvas

            val minPace = paceData.minOfOrNull { it.second } ?: 180
            val maxPace = paceData.maxOfOrNull { it.second } ?: 600
            val paceRange = (maxPace - minPace).coerceAtLeast(1)

            val pointSpacing = size.width / (paceData.size - 1).coerceAtLeast(1)

            val path = Path()
            paceData.forEachIndexed { index, (_, pace) ->
                val x = index * pointSpacing
                // Lower pace (faster) should be higher on chart
                val y = ((pace - minPace).toFloat() / paceRange) * size.height

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = Color(0xFFFF9800),
                style = Stroke(width = 3.dp.toPx())
            )

            // Draw points
            paceData.forEachIndexed { index, (_, pace) ->
                val x = index * pointSpacing
                val y = ((pace - minPace).toFloat() / paceRange) * size.height

                drawCircle(
                    color = Color(0xFFFF9800),
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
fun ZoneDistributionChart(
    sessions: List<ActivitySession>,
    modifier: Modifier = Modifier
) {
    val zoneTimes = listOf(
        "Z1" to sessions.sumOf { it.zone1TimeMs },
        "Z2" to sessions.sumOf { it.zone2TimeMs },
        "Z3" to sessions.sumOf { it.zone3TimeMs },
        "Z4" to sessions.sumOf { it.zone4TimeMs },
        "Z5" to sessions.sumOf { it.zone5TimeMs }
    )
    val totalTime = zoneTimes.sumOf { it.second }.coerceAtLeast(1)

    val zoneColors = listOf(
        Color(0xFF64B5F6),  // Z1 - Blue
        Color(0xFF81C784),  // Z2 - Green
        Color(0xFFFFD54F),  // Z3 - Yellow
        Color(0xFFFFB74D),  // Z4 - Orange
        Color(0xFFE57373)   // Z5 - Red
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bar chart
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val barHeight = size.height / 5 - 8.dp.toPx()

                zoneTimes.forEachIndexed { index, (_, time) ->
                    val barWidth = (time.toFloat() / totalTime) * size.width
                    val y = index * (barHeight + 8.dp.toPx())

                    drawRect(
                        color = zoneColors[index],
                        topLeft = Offset(0f, y),
                        size = Size(barWidth, barHeight)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Legend
            Column {
                zoneTimes.forEachIndexed { index, (label, time) ->
                    val percent = (time.toFloat() / totalTime * 100).toInt()
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(zoneColors[index], RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$label $percent%",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PersonalRecordsCard(sessions: List<ActivitySession>) {
    val longestRun = sessions.maxByOrNull { it.distanceMeters }
    val fastestPace = sessions
        .filter { it.avgSpeed > 0 && it.distanceMeters >= 1000 }
        .maxByOrNull { it.avgSpeed }
    val longestDuration = sessions.maxByOrNull { it.durationMs }
    val maxHr = sessions.maxByOrNull { it.maxHeartRate }

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
            Text(
                text = "Personal Records",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            longestRun?.let {
                RecordItem(
                    label = "Longest Run",
                    value = "%.2f km".format(it.distanceKm),
                    date = formatShortDate(it.startTime)
                )
            }

            fastestPace?.let {
                RecordItem(
                    label = "Fastest Pace",
                    value = "${it.avgPaceFormatted} /km",
                    date = formatShortDate(it.startTime)
                )
            }

            longestDuration?.let {
                RecordItem(
                    label = "Longest Duration",
                    value = it.durationFormatted,
                    date = formatShortDate(it.startTime)
                )
            }

            maxHr?.takeIf { it.maxHeartRate > 0 }?.let {
                RecordItem(
                    label = "Max Heart Rate",
                    value = "${it.maxHeartRate} bpm",
                    date = formatShortDate(it.startTime)
                )
            }
        }
    }
}

@Composable
fun RecordItem(label: String, value: String, date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun TrainingHeatmap(
    sessions: List<ActivitySession>,
    modifier: Modifier = Modifier
) {
    val calendar = Calendar.getInstance()
    val dayOfWeekCounts = mutableMapOf<Int, Int>()

    sessions.forEach { session ->
        calendar.timeInMillis = session.startTime
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        dayOfWeekCounts[dayOfWeek] = (dayOfWeekCounts[dayOfWeek] ?: 0) + 1
    }

    val maxCount = dayOfWeekCounts.values.maxOrNull() ?: 1
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            days.forEachIndexed { index, day ->
                val count = dayOfWeekCounts[index + 1] ?: 0
                val intensity = count.toFloat() / maxCount

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = Color(0xFF4CAF50).copy(alpha = 0.2f + intensity * 0.8f),
                                shape = RoundedCornerShape(4.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyAnalyticsMessage() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No workouts recorded yet.\nStart tracking to see analytics!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun filterSessionsByPeriod(
    sessions: List<ActivitySession>,
    period: AnalyticsPeriod
): List<ActivitySession> {
    val cutoffTime = System.currentTimeMillis() - period.days * 24 * 60 * 60 * 1000L
    return sessions.filter { it.startTime >= cutoffTime }
}

private fun groupSessionsByDay(
    sessions: List<ActivitySession>,
    period: AnalyticsPeriod
): List<Pair<Long, Double>> {
    val calendar = Calendar.getInstance()
    val grouped = mutableMapOf<Long, Double>()

    sessions.forEach { session ->
        calendar.timeInMillis = session.startTime
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val dayStart = calendar.timeInMillis
        grouped[dayStart] = (grouped[dayStart] ?: 0.0) + session.distanceMeters / 1000.0
    }

    return grouped.entries
        .sortedBy { it.key }
        .map { it.key to it.value }
}

private fun formatTotalDuration(ms: Long): String {
    val hours = ms / (1000 * 60 * 60)
    val minutes = (ms / (1000 * 60)) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatShortDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
