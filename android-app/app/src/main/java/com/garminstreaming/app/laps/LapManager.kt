package com.garminstreaming.app.laps

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data for a single lap
 */
data class LapData(
    val lapNumber: Int,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val distanceMeters: Double,
    val avgHeartRate: Int,
    val maxHeartRate: Int,
    val avgSpeed: Double // m/s
) {
    val distanceKm: Double get() = distanceMeters / 1000.0

    val durationFormatted: String get() {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    val paceFormatted: String get() {
        if (avgSpeed <= 0 || distanceMeters <= 0) return "--:--"
        val paceSeconds = (1000.0 / avgSpeed).toInt()
        val minutes = paceSeconds / 60
        val seconds = paceSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    val avgSpeedKmh: Double get() = avgSpeed * 3.6
}

/**
 * Current lap state
 */
data class LapState(
    val currentLapNumber: Int = 1,
    val currentLapStartTime: Long = 0,
    val currentLapStartDistance: Double = 0.0,
    val laps: List<LapData> = emptyList(),
    val hrSamples: List<Int> = emptyList()
) {
    val totalLaps: Int get() = laps.size

    val lastLap: LapData? get() = laps.lastOrNull()

    val currentLapDuration: Long get() {
        return if (currentLapStartTime > 0) {
            System.currentTimeMillis() - currentLapStartTime
        } else 0
    }

    fun getCurrentLapDistance(totalDistance: Double): Double {
        return totalDistance - currentLapStartDistance
    }
}

/**
 * Manager for lap tracking during workouts
 */
class LapManager {

    private val _state = MutableStateFlow(LapState())
    val state: StateFlow<LapState> = _state.asStateFlow()

    private var speedSamples = mutableListOf<Double>()

    /**
     * Start tracking (called when workout starts)
     */
    fun startTracking() {
        val now = System.currentTimeMillis()
        _state.value = LapState(
            currentLapNumber = 1,
            currentLapStartTime = now,
            currentLapStartDistance = 0.0,
            laps = emptyList(),
            hrSamples = emptyList()
        )
        speedSamples.clear()
    }

    /**
     * Add HR sample for current lap averaging
     */
    fun addHeartRateSample(hr: Int) {
        if (hr > 0) {
            _state.value = _state.value.copy(
                hrSamples = _state.value.hrSamples + hr
            )
        }
    }

    /**
     * Add speed sample for current lap averaging
     */
    fun addSpeedSample(speed: Double) {
        if (speed >= 0) {
            speedSamples.add(speed)
        }
    }

    /**
     * Mark a new lap
     */
    fun markLap(currentDistance: Double, currentHeartRate: Int): LapData? {
        val currentState = _state.value
        val now = System.currentTimeMillis()

        if (currentState.currentLapStartTime == 0L) return null

        // Calculate lap metrics
        val lapDuration = now - currentState.currentLapStartTime
        val lapDistance = currentDistance - currentState.currentLapStartDistance

        val hrSamples = currentState.hrSamples
        val avgHr = if (hrSamples.isNotEmpty()) hrSamples.average().toInt() else currentHeartRate
        val maxHr = hrSamples.maxOrNull() ?: currentHeartRate

        val avgSpeed = if (speedSamples.isNotEmpty()) {
            speedSamples.average()
        } else if (lapDuration > 0) {
            lapDistance / (lapDuration / 1000.0)
        } else 0.0

        // Create lap data
        val lap = LapData(
            lapNumber = currentState.currentLapNumber,
            startTime = currentState.currentLapStartTime,
            endTime = now,
            durationMs = lapDuration,
            distanceMeters = lapDistance,
            avgHeartRate = avgHr,
            maxHeartRate = maxHr,
            avgSpeed = avgSpeed
        )

        // Update state for next lap
        _state.value = currentState.copy(
            currentLapNumber = currentState.currentLapNumber + 1,
            currentLapStartTime = now,
            currentLapStartDistance = currentDistance,
            laps = currentState.laps + lap,
            hrSamples = emptyList()
        )
        speedSamples.clear()

        return lap
    }

    /**
     * Get all laps
     */
    fun getLaps(): List<LapData> = _state.value.laps

    /**
     * Get laps as JSON for storage
     */
    fun getLapsJson(): String {
        val laps = _state.value.laps
        if (laps.isEmpty()) return "[]"

        val jsonLaps = laps.map { lap ->
            """{"n":${lap.lapNumber},"st":${lap.startTime},"et":${lap.endTime},"d":${lap.durationMs},"dist":${lap.distanceMeters},"hr":${lap.avgHeartRate},"mhr":${lap.maxHeartRate},"spd":${lap.avgSpeed}}"""
        }
        return "[${jsonLaps.joinToString(",")}]"
    }

    /**
     * Reset (called when workout ends or new workout starts)
     */
    fun reset() {
        _state.value = LapState()
        speedSamples.clear()
    }

    companion object {
        @Volatile
        private var INSTANCE: LapManager? = null

        fun getInstance(): LapManager {
            return INSTANCE ?: synchronized(this) {
                val instance = LapManager()
                INSTANCE = instance
                instance
            }
        }
    }
}
