package com.garminstreaming.app.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.garminstreaming.app.data.ActivitySession
import com.garminstreaming.app.data.SessionManager
import com.garminstreaming.app.export.GpxExporter
import com.garminstreaming.app.getHeartRateZoneColor
import com.garminstreaming.app.HeartRateZone
import com.garminstreaming.app.ZoneTimeData
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SessionManager.getInstance() }
    var session by remember { mutableStateOf<ActivitySession?>(null) }

    LaunchedEffect(sessionId) {
        session = repository.getSessionById(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    session?.let { s ->
                        IconButton(
                            onClick = {
                                exportSessionToGpx(context, s)
                            }
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Export GPX",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        session?.let { s ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Date header
                Text(
                    text = formatFullDate(s.startTime),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Main metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DetailMetricCard(
                        value = "%.2f".format(s.distanceKm),
                        unit = "km",
                        label = "Distance",
                        color = Color.Green,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DetailMetricCard(
                        value = s.durationFormatted,
                        unit = "",
                        label = "Duration",
                        color = Color.Cyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DetailMetricCard(
                        value = s.avgPaceFormatted,
                        unit = "/km",
                        label = "Avg Pace",
                        color = Color.Yellow,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DetailMetricCard(
                        value = "%.1f".format(s.avgSpeedKmh),
                        unit = "km/h",
                        label = "Avg Speed",
                        color = Color.Magenta,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Heart rate section
                if (s.avgHeartRate > 0) {
                    Text(
                        text = "Heart Rate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        HrStatItem(value = s.minHeartRate, label = "Min", color = Color(0xFF64B5F6))
                        HrStatItem(value = s.avgHeartRate, label = "Avg", color = Color(0xFF4CAF50))
                        HrStatItem(value = s.maxHeartRate, label = "Max", color = Color(0xFFF44336))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // HR Graph
                    val hrData = s.heartRateData
                    if (hrData.isNotEmpty()) {
                        SessionHrGraph(
                            heartRateData = hrData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Zone time breakdown
                    val zoneData = s.zoneTimeData
                    if (zoneData.totalMs > 0) {
                        Text(
                            text = "Zone Distribution",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        ZoneTimeBreakdown(
                            zoneTimeData = zoneData,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Map section
                val trackPoints = s.trackPoints
                if (trackPoints.isNotEmpty()) {
                    Text(
                        text = "Route",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        SessionMapView(
                            trackPoints = trackPoints.map {
                                com.garminstreaming.app.TrackPoint(
                                    latitude = it.latitude,
                                    longitude = it.longitude,
                                    timestamp = it.timestamp,
                                    heartRate = it.heartRate
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun DetailMetricCard(
    value: String,
    unit: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = color.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HrStatItem(value: Int, label: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value.toString(),
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
fun SessionHrGraph(
    heartRateData: List<Pair<Long, Int>>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            if (heartRateData.size < 2) return@Canvas

            val width = size.width
            val height = size.height
            val padding = 4.dp.toPx()

            val hrMin = (heartRateData.minOfOrNull { it.second } ?: 60) - 10
            val hrMax = (heartRateData.maxOfOrNull { it.second } ?: 180) + 10
            val hrRange = (hrMax - hrMin).coerceAtLeast(1)

            val pointSpacing = (width - 2 * padding) / (heartRateData.size - 1).coerceAtLeast(1)

            // Draw path
            val path = Path()
            heartRateData.forEachIndexed { index, (_, hr) ->
                val x = padding + index * pointSpacing
                val y = height - padding - ((hr - hrMin).toFloat() / hrRange) * (height - 2 * padding)

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = Color(0xFFF44336),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@Composable
fun SessionMapView(
    trackPoints: List<com.garminstreaming.app.TrackPoint>,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
            }
        },
        update = { map ->
            if (trackPoints.isNotEmpty()) {
                map.overlays.clear()

                val polyline = Polyline().apply {
                    trackPoints.forEach { point ->
                        addPoint(GeoPoint(point.latitude, point.longitude))
                    }
                    outlinePaint.color = android.graphics.Color.BLUE
                    outlinePaint.strokeWidth = 5f
                }

                map.overlays.add(polyline)

                // Calculate bounds and zoom
                val lats = trackPoints.map { it.latitude }
                val lons = trackPoints.map { it.longitude }
                val centerLat = (lats.minOrNull()!! + lats.maxOrNull()!!) / 2
                val centerLon = (lons.minOrNull()!! + lons.maxOrNull()!!) / 2

                map.controller.setCenter(GeoPoint(centerLat, centerLon))
                map.controller.setZoom(15.0)

                map.invalidate()
            }
        }
    )
}

private fun formatFullDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun ZoneTimeBreakdown(
    zoneTimeData: ZoneTimeData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            HeartRateZone.entries.forEach { zone ->
                val percent = zoneTimeData.getZonePercent(zone)
                val timeFormatted = zoneTimeData.formatZoneTime(zone)
                val zoneColor = Color(zone.color)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zone label
                    Text(
                        text = zone.zoneName,
                        style = MaterialTheme.typography.labelSmall,
                        color = zoneColor,
                        modifier = Modifier.width(50.dp)
                    )

                    // Progress bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .padding(horizontal = 8.dp)
                    ) {
                        // Background
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    color = zoneColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )
                        // Fill
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (percent / 100f).coerceIn(0f, 1f))
                                .background(
                                    color = zoneColor,
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )
                    }

                    // Time and percent
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.width(60.dp)
                    ) {
                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "%.0f%%".format(percent),
                            style = MaterialTheme.typography.labelSmall,
                            color = zoneColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun exportSessionToGpx(context: android.content.Context, session: ActivitySession) {
    try {
        // Generate GPX content
        val gpxContent = GpxExporter.export(session)
        val filename = GpxExporter.generateFilename(session)

        // Write to cache directory
        val cacheDir = File(context.cacheDir, "exports")
        cacheDir.mkdirs()
        val file = File(cacheDir, filename)
        file.writeText(gpxContent)

        // Create share intent using FileProvider
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Activity Export - ${session.activityType}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Export GPX"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
