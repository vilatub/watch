package com.garminstreaming.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.garminstreaming.app.alerts.ZoneAlertManager
import com.garminstreaming.app.autopause.AutoPauseManager
import com.garminstreaming.app.data.SessionManager
import com.garminstreaming.app.laps.LapManager
import com.garminstreaming.app.settings.SettingsRepository
import com.garminstreaming.app.ui.SessionDetailScreen
import com.garminstreaming.app.ui.SessionHistoryScreen
import com.garminstreaming.app.ui.StatisticsScreen
import com.garminstreaming.app.ui.ZoneAlertToggle
import com.garminstreaming.app.ui.ZoneAlertSettingsPanel
import com.garminstreaming.app.ui.OutOfZoneWarning
import com.garminstreaming.app.ui.AutoPauseToggle
import com.garminstreaming.app.ui.AutoPauseSettingsPanel
import com.garminstreaming.app.ui.PausedIndicator
import com.garminstreaming.app.ui.VoiceFeedbackToggle
import com.garminstreaming.app.ui.VoiceFeedbackSettingsPanel
import com.garminstreaming.app.ui.LapButton
import com.garminstreaming.app.ui.CurrentLapIndicator
import com.garminstreaming.app.ui.LastLapSummary
import com.garminstreaming.app.ui.LapHistoryPanel
import com.garminstreaming.app.voice.VoiceFeedbackManager
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startGarminService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OSMDroid
        Configuration.getInstance().userAgentValue = packageName

        // Initialize SessionManager
        SessionManager.initialize(this)

        checkPermissionsAndStart()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val requiredPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startGarminService()
        } else {
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    private fun startGarminService() {
        val serviceIntent = Intent(this, GarminListenerService::class.java)
        startService(serviceIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, GarminListenerService::class.java))
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            ActivityStreamingScreen(
                onNavigateToHistory = {
                    navController.navigate("history")
                },
                onNavigateToStatistics = {
                    navController.navigate("statistics")
                }
            )
        }
        composable("history") {
            SessionHistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSessionClick = { sessionId ->
                    navController.navigate("session/$sessionId")
                }
            )
        }
        composable("statistics") {
            StatisticsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = "session/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L
            SessionDetailScreen(
                sessionId = sessionId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
fun ActivityStreamingScreen(
    onNavigateToHistory: () -> Unit = {},
    onNavigateToStatistics: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activityData by ActivityRepository.currentData.collectAsState()
    val connectionStatus by ActivityRepository.connectionStatus.collectAsState()
    val trackPoints by ActivityRepository.trackPoints.collectAsState()
    val heartRateHistory by ActivityRepository.heartRateHistory.collectAsState()

    val scope = rememberCoroutineScope()
    val repository = remember { SessionManager.getInstance() }
    val settingsRepository = remember { SettingsRepository.getInstance(context) }
    val alertManager = remember { ZoneAlertManager.getInstance(context) }
    val autoPauseManager = remember { AutoPauseManager.getInstance() }
    val voiceManager = remember { VoiceFeedbackManager.getInstance(context) }
    val lapManager = remember { LapManager.getInstance() }
    var isRecording by remember { mutableStateOf(false) }
    var showAlertSettings by remember { mutableStateOf(false) }
    var showAutoPauseSettings by remember { mutableStateOf(false) }
    var showVoiceSettings by remember { mutableStateOf(false) }
    var sessionStartTime by remember { mutableStateOf(0L) }
    var showLastLap by remember { mutableStateOf(false) }

    val alertSettings by alertManager.settings.collectAsState()
    val alertState by alertManager.alertState.collectAsState()
    val autoPauseSettings by autoPauseManager.settings.collectAsState()
    val autoPauseState by autoPauseManager.state.collectAsState()
    val voiceSettings by voiceManager.settings.collectAsState()
    val lapState by lapManager.state.collectAsState()

    // Load saved settings on startup
    LaunchedEffect(Unit) {
        settingsRepository.zoneAlertSettings.collect { saved ->
            alertManager.updateSettings(saved)
        }
    }
    LaunchedEffect(Unit) {
        settingsRepository.autoPauseSettings.collect { saved ->
            autoPauseManager.updateSettings(saved)
        }
    }
    LaunchedEffect(Unit) {
        settingsRepository.voiceFeedbackSettings.collect { saved ->
            voiceManager.updateSettings(saved)
        }
    }

    // Save settings when they change
    LaunchedEffect(alertSettings) {
        settingsRepository.saveZoneAlertSettings(alertSettings)
    }
    LaunchedEffect(autoPauseSettings) {
        settingsRepository.saveAutoPauseSettings(autoPauseSettings)
    }
    LaunchedEffect(voiceSettings) {
        settingsRepository.saveVoiceFeedbackSettings(voiceSettings)
    }

    // Add samples for lap tracking
    LaunchedEffect(activityData.heartRate, activityData.speed) {
        if (isRecording) {
            lapManager.addHeartRateSample(activityData.heartRate)
            lapManager.addSpeedSample(activityData.speed)
        }
    }

    // Auto-hide last lap summary after 5 seconds
    LaunchedEffect(showLastLap) {
        if (showLastLap) {
            kotlinx.coroutines.delay(5000)
            showLastLap = false
        }
    }

    // Check heart rate for alerts when data updates
    LaunchedEffect(activityData.heartRate) {
        if (activityData.heartRate > 0) {
            alertManager.checkHeartRate(activityData.heartRate)
        }
    }

    // Check speed for auto-pause when data updates
    LaunchedEffect(activityData.speed) {
        if (isRecording) {
            autoPauseManager.checkSpeed(activityData.speed)
        }
    }

    // Check for voice announcements
    LaunchedEffect(activityData.distance) {
        if (isRecording && sessionStartTime > 0) {
            val elapsedMs = System.currentTimeMillis() - sessionStartTime
            voiceManager.checkAndAnnounce(
                distanceKm = activityData.distanceKm,
                paceFormatted = activityData.paceFormatted,
                heartRate = activityData.heartRate,
                elapsedMs = elapsedMs
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top bar with history button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Connection status
            ConnectionStatusBar(
                status = connectionStatus,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Auto-pause toggle
            AutoPauseToggle(
                autoPauseManager = autoPauseManager,
                onExpandSettings = { showAutoPauseSettings = !showAutoPauseSettings }
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Zone alert toggle
            ZoneAlertToggle(
                alertManager = alertManager,
                onExpandSettings = { showAlertSettings = !showAlertSettings }
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Voice feedback toggle
            VoiceFeedbackToggle(
                voiceManager = voiceManager,
                onExpandSettings = { showVoiceSettings = !showVoiceSettings }
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Statistics button
            IconButton(
                onClick = onNavigateToStatistics
            ) {
                Icon(
                    Icons.Default.Analytics,
                    contentDescription = "Statistics",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // History button
            IconButton(
                onClick = onNavigateToHistory
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = "History",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Auto-pause settings panel (expandable)
        if (showAutoPauseSettings) {
            Spacer(modifier = Modifier.height(8.dp))
            AutoPauseSettingsPanel(
                autoPauseManager = autoPauseManager,
                onDismiss = { showAutoPauseSettings = false }
            )
        }

        // Zone alert settings panel (expandable)
        if (showAlertSettings) {
            Spacer(modifier = Modifier.height(8.dp))
            ZoneAlertSettingsPanel(
                alertManager = alertManager,
                onDismiss = { showAlertSettings = false }
            )
        }

        // Voice feedback settings panel (expandable)
        if (showVoiceSettings) {
            Spacer(modifier = Modifier.height(8.dp))
            VoiceFeedbackSettingsPanel(
                voiceManager = voiceManager,
                onDismiss = { showVoiceSettings = false }
            )
        }

        // Paused indicator
        if (autoPauseSettings.enabled && autoPauseState.isPaused) {
            Spacer(modifier = Modifier.height(8.dp))
            PausedIndicator(state = autoPauseState)
        }

        // Out of zone warning
        if (alertSettings.enabled && !alertState.isInTargetZone && activityData.heartRate > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            OutOfZoneWarning(alertState = alertState)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Heart Rate - large display
            MetricCard(
                value = if (activityData.heartRate > 0) activityData.heartRate.toString() else "--",
                unit = "BPM",
                label = "Heart Rate",
                color = getHeartRateZoneColor(activityData.heartRate),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Speed
            MetricCard(
                value = "%.1f".format(activityData.speedKmh),
                unit = "km/h",
                label = "Speed",
                color = Color.Cyan,
                modifier = Modifier.weight(1f)
            )
        }

        // Current Zone Indicator
        if (activityData.heartRate > 0) {
            val currentZone = HeartRateZone.fromHeartRate(activityData.heartRate, 190)
            CurrentZoneIndicator(
                zone = currentZone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Secondary metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Distance
            MetricCard(
                value = "%.2f".format(activityData.distanceKm),
                unit = "km",
                label = "Distance",
                color = Color.Green,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Pace
            MetricCard(
                value = activityData.paceFormatted,
                unit = "/km",
                label = "Pace",
                color = Color.Yellow,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tertiary metrics (Cadence and Power)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Cadence
            MetricCard(
                value = if (activityData.cadence > 0) activityData.cadence.toString() else "--",
                unit = "spm",
                label = "Cadence",
                color = Color(0xFFFF9800), // Orange
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Power
            MetricCard(
                value = if (activityData.power > 0) activityData.power.toString() else "--",
                unit = "W",
                label = "Power",
                color = Color(0xFF9C27B0), // Purple
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Heart Rate Graph
        HeartRateGraph(
            heartRateHistory = heartRateHistory,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Map
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            MapViewComposable(
                trackPoints = trackPoints,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Altitude
        Text(
            text = "Altitude: %.0f m".format(activityData.altitude),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Current lap indicator
        if (isRecording && lapState.currentLapNumber >= 1) {
            CurrentLapIndicator(
                lapState = lapState,
                currentDistance = activityData.distance
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Last lap summary (shown briefly after marking a lap)
        if (showLastLap && lapState.lastLap != null) {
            LastLapSummary(lap = lapState.lastLap!!)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Lap history
        if (lapState.laps.isNotEmpty()) {
            LapHistoryPanel(laps = lapState.laps)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Record button
            Button(
                onClick = {
                    scope.launch {
                        if (isRecording) {
                            repository.stopSession()
                            lapManager.reset()
                            isRecording = false
                        } else {
                            repository.startSession()
                            alertManager.reset()
                            autoPauseManager.reset()
                            voiceManager.reset()
                            lapManager.startTracking()
                            voiceManager.announceStart()
                            sessionStartTime = System.currentTimeMillis()
                            isRecording = true
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color(0xFFF44336) else Color(0xFF4CAF50)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isRecording) "Stop" else "Start",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRecording) "Stop" else "Start",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Lap button
            LapButton(
                isRecording = isRecording,
                onLapClick = {
                    val lap = lapManager.markLap(
                        currentDistance = activityData.distance,
                        currentHeartRate = activityData.heartRate
                    )
                    if (lap != null) {
                        showLastLap = true
                    }
                },
                modifier = Modifier.width(100.dp)
            )
        }
    }
}

/**
 * Get color based on heart rate zone
 * Zones based on typical max HR of 190 (adjustable)
 */
fun getHeartRateZoneColor(hr: Int, maxHr: Int = 190): Color {
    if (hr <= 0) return Color.Gray
    val percentage = (hr.toFloat() / maxHr) * 100
    return when {
        percentage < 50 -> Color.Gray           // Rest
        percentage < 60 -> Color(0xFF64B5F6)    // Zone 1 - Light blue (Recovery)
        percentage < 70 -> Color(0xFF4CAF50)    // Zone 2 - Green (Aerobic)
        percentage < 80 -> Color(0xFFFFEB3B)    // Zone 3 - Yellow (Tempo)
        percentage < 90 -> Color(0xFFFF9800)    // Zone 4 - Orange (Threshold)
        else -> Color(0xFFF44336)               // Zone 5 - Red (VO2 Max)
    }
}

@Composable
fun HeartRateGraph(
    heartRateHistory: List<Pair<Long, Int>>,
    modifier: Modifier = Modifier,
    maxHr: Int = 190
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        if (heartRateHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Waiting for HR data...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                // Stats row
                val hrValues = heartRateHistory.map { it.second }
                val avgHr = hrValues.average().toInt()
                val minHr = hrValues.minOrNull() ?: 0
                val maxHrValue = hrValues.maxOrNull() ?: 0

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Min: $minHr",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64B5F6)
                    )
                    Text(
                        text = "Avg: $avgHr",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "Max: $maxHrValue",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF44336)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Graph
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (heartRateHistory.size < 2) return@Canvas

                    val width = size.width
                    val height = size.height
                    val padding = 4.dp.toPx()

                    // Take last 60 points (3 minutes of data at 3s intervals)
                    val displayData = heartRateHistory.takeLast(60)

                    // Find min/max for scaling
                    val hrMin = (displayData.minOfOrNull { it.second } ?: 60) - 10
                    val hrMax = (displayData.maxOfOrNull { it.second } ?: 180) + 10
                    val hrRange = (hrMax - hrMin).coerceAtLeast(1)

                    val pointSpacing = (width - 2 * padding) / (displayData.size - 1).coerceAtLeast(1)

                    // Draw grid lines
                    val gridColor = Color.Gray.copy(alpha = 0.2f)
                    for (i in 0..4) {
                        val y = padding + (height - 2 * padding) * i / 4
                        drawLine(
                            color = gridColor,
                            start = Offset(padding, y),
                            end = Offset(width - padding, y),
                            strokeWidth = 1f
                        )
                    }

                    // Draw HR line with zone colors
                    val path = Path()
                    displayData.forEachIndexed { index, (_, hr) ->
                        val x = padding + index * pointSpacing
                        val y = height - padding - ((hr - hrMin).toFloat() / hrRange) * (height - 2 * padding)

                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    // Draw the path
                    drawPath(
                        path = path,
                        color = Color(0xFFF44336),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Draw points with zone colors
                    displayData.forEachIndexed { index, (_, hr) ->
                        val x = padding + index * pointSpacing
                        val y = height - padding - ((hr - hrMin).toFloat() / hrRange) * (height - 2 * padding)
                        val zoneColor = getHeartRateZoneColor(hr, maxHr)

                        drawCircle(
                            color = zoneColor,
                            radius = 3.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusBar(
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val (statusText, statusColor) = when (status) {
        ConnectionStatus.DISCONNECTED -> "Disconnected" to Color.Gray
        ConnectionStatus.CONNECTING -> "Connecting..." to Color.Yellow
        ConnectionStatus.CONNECTED -> "Connected" to Color.Green
        ConnectionStatus.STREAMING -> "Streaming" to Color.Green
    }

    Row(
        modifier = modifier
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
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor
        )
    }
}

@Composable
fun MetricCard(
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
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = color.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
fun CurrentZoneIndicator(
    zone: HeartRateZone,
    modifier: Modifier = Modifier
) {
    val zoneColor = Color(zone.color)

    Row(
        modifier = modifier
            .background(
                color = zoneColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(zoneColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = zone.zoneName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = zoneColor
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = zone.description,
            style = MaterialTheme.typography.bodySmall,
            color = zoneColor.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun MapViewComposable(
    trackPoints: List<TrackPoint>,
    modifier: Modifier = Modifier
) {
    var mapView by remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.0)
                mapView = this
            }
        },
        update = { map ->
            // Update track on map
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

                // Center on last point
                val lastPoint = trackPoints.last()
                map.controller.setCenter(GeoPoint(lastPoint.latitude, lastPoint.longitude))

                map.invalidate()
            }
        }
    )
}
