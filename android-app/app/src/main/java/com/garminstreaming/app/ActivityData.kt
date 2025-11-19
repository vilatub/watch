package com.garminstreaming.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data class representing activity metrics from the Garmin watch
 */
/**
 * Activity types supported
 */
enum class ActivityType(val displayName: String, val icon: String) {
    RUNNING("Running", "directions_run"),
    CYCLING("Cycling", "directions_bike"),
    WALKING("Walking", "directions_walk"),
    HIKING("Hiking", "hiking"),
    SWIMMING("Swimming", "pool"),
    OTHER("Other", "fitness_center");

    companion object {
        fun fromString(value: String): ActivityType {
            return entries.find { it.name.lowercase() == value.lowercase() } ?: OTHER
        }
    }
}

data class ActivityData(
    val timestamp: Long = 0,
    val heartRate: Int = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speed: Double = 0.0,      // m/s
    val altitude: Double = 0.0,   // meters
    val distance: Double = 0.0,   // meters
    val cadence: Int = 0,         // steps per minute (running) or rpm (cycling)
    val power: Int = 0,           // watts (if power meter available)
    val activityType: ActivityType = ActivityType.RUNNING
) {
    val speedKmh: Double get() = speed * 3.6
    val distanceKm: Double get() = distance / 1000.0

    // Pace in min/km (for running)
    val paceMinPerKm: Double get() = if (speed > 0) (1000.0 / speed) / 60.0 else 0.0

    val paceFormatted: String get() {
        if (speed <= 0) return "--:--"
        val totalSeconds = (1000.0 / speed).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Connection status for the Garmin watch
 */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    STREAMING
}

/**
 * GPS track point for map display
 */
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val heartRate: Int
)

/**
 * Heart rate training zones
 */
enum class HeartRateZone(
    val zoneName: String,
    val description: String,
    val minPercent: Int,
    val maxPercent: Int,
    val color: Long
) {
    ZONE_1("Zone 1", "Recovery", 50, 60, 0xFF90CAF9),      // Light Blue
    ZONE_2("Zone 2", "Endurance", 60, 70, 0xFF4CAF50),     // Green
    ZONE_3("Zone 3", "Tempo", 70, 80, 0xFFFFEB3B),         // Yellow
    ZONE_4("Zone 4", "Threshold", 80, 90, 0xFFFF9800),     // Orange
    ZONE_5("Zone 5", "VO2 Max", 90, 100, 0xFFF44336);      // Red

    companion object {
        fun fromHeartRate(hr: Int, maxHr: Int): HeartRateZone {
            if (hr <= 0 || maxHr <= 0) return ZONE_1
            val percent = (hr.toFloat() / maxHr * 100).toInt()
            return when {
                percent < 60 -> ZONE_1
                percent < 70 -> ZONE_2
                percent < 80 -> ZONE_3
                percent < 90 -> ZONE_4
                else -> ZONE_5
            }
        }
    }
}

/**
 * Zone time distribution data
 */
data class ZoneTimeData(
    val zone1Ms: Long = 0,
    val zone2Ms: Long = 0,
    val zone3Ms: Long = 0,
    val zone4Ms: Long = 0,
    val zone5Ms: Long = 0
) {
    val totalMs: Long get() = zone1Ms + zone2Ms + zone3Ms + zone4Ms + zone5Ms

    fun getZoneTime(zone: HeartRateZone): Long = when (zone) {
        HeartRateZone.ZONE_1 -> zone1Ms
        HeartRateZone.ZONE_2 -> zone2Ms
        HeartRateZone.ZONE_3 -> zone3Ms
        HeartRateZone.ZONE_4 -> zone4Ms
        HeartRateZone.ZONE_5 -> zone5Ms
    }

    fun getZonePercent(zone: HeartRateZone): Float {
        if (totalMs == 0L) return 0f
        return (getZoneTime(zone).toFloat() / totalMs) * 100
    }

    fun formatZoneTime(zone: HeartRateZone): String {
        val ms = getZoneTime(zone)
        val totalSeconds = (ms / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Repository for managing activity data state
 */
object ActivityRepository {
    private val _currentData = MutableStateFlow(ActivityData())
    val currentData: StateFlow<ActivityData> = _currentData.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _trackPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
    val trackPoints: StateFlow<List<TrackPoint>> = _trackPoints.asStateFlow()

    private val _heartRateHistory = MutableStateFlow<List<Pair<Long, Int>>>(emptyList())
    val heartRateHistory: StateFlow<List<Pair<Long, Int>>> = _heartRateHistory.asStateFlow()

    fun updateData(data: ActivityData) {
        _currentData.value = data

        // Add track point if we have valid GPS
        if (data.latitude != 0.0 && data.longitude != 0.0) {
            val newPoint = TrackPoint(
                latitude = data.latitude,
                longitude = data.longitude,
                timestamp = data.timestamp,
                heartRate = data.heartRate
            )
            _trackPoints.value = _trackPoints.value + newPoint
        }

        // Add heart rate to history
        if (data.heartRate > 0) {
            _heartRateHistory.value = _heartRateHistory.value + Pair(data.timestamp, data.heartRate)
        }
    }

    fun setConnectionStatus(status: ConnectionStatus) {
        _connectionStatus.value = status
    }

    fun clearSession() {
        _currentData.value = ActivityData()
        _trackPoints.value = emptyList()
        _heartRateHistory.value = emptyList()
    }
}
