package com.garminstreaming.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data class representing activity metrics from the Garmin watch
 */
data class ActivityData(
    val timestamp: Long = 0,
    val heartRate: Int = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speed: Double = 0.0,      // m/s
    val altitude: Double = 0.0,   // meters
    val distance: Double = 0.0,   // meters
    val cadence: Int = 0,         // steps per minute (running) or rpm (cycling)
    val power: Int = 0            // watts (if power meter available)
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
