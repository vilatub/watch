package com.garminstreaming.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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

        checkPermissionsAndStart()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ActivityStreamingScreen()
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
fun ActivityStreamingScreen() {
    val activityData by ActivityRepository.currentData.collectAsState()
    val connectionStatus by ActivityRepository.connectionStatus.collectAsState()
    val trackPoints by ActivityRepository.trackPoints.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Connection status
        ConnectionStatusBar(connectionStatus)

        Spacer(modifier = Modifier.height(16.dp))

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
                color = Color.Red,
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

        Spacer(modifier = Modifier.height(16.dp))

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
    }
}

@Composable
fun ConnectionStatusBar(status: ConnectionStatus) {
    val (statusText, statusColor) = when (status) {
        ConnectionStatus.DISCONNECTED -> "Disconnected" to Color.Gray
        ConnectionStatus.CONNECTING -> "Connecting..." to Color.Yellow
        ConnectionStatus.CONNECTED -> "Connected" to Color.Green
        ConnectionStatus.STREAMING -> "Streaming" to Color.Green
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
