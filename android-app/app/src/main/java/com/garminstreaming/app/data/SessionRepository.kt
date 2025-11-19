package com.garminstreaming.app.data

import android.content.Context
import com.garminstreaming.app.ActivityRepository
import com.garminstreaming.app.HeartRateZone
import com.garminstreaming.app.TrackPoint
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Repository for managing activity sessions
 */
class SessionRepository(context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val sessionDao = database.activitySessionDao()
    private val gson = Gson()

    private var currentSessionId: Long? = null
    private var sessionStartTime: Long = 0

    /**
     * Get all sessions as Flow
     */
    fun getAllSessions(): Flow<List<ActivitySession>> {
        return sessionDao.getAllSessions()
    }

    /**
     * Get session by ID
     */
    suspend fun getSessionById(sessionId: Long): ActivitySession? {
        return sessionDao.getSessionById(sessionId)
    }

    /**
     * Start a new recording session
     */
    suspend fun startSession(): Long {
        sessionStartTime = System.currentTimeMillis()
        ActivityRepository.clearSession()

        val session = ActivitySession(
            startTime = sessionStartTime,
            activityType = "running"
        )

        currentSessionId = sessionDao.insertSession(session)
        return currentSessionId!!
    }

    /**
     * Stop current session and save final data
     */
    suspend fun stopSession(): ActivitySession? {
        val sessionId = currentSessionId ?: return null
        val endTime = System.currentTimeMillis()

        // Get data from ActivityRepository
        val trackPoints = ActivityRepository.trackPoints.value
        val heartRateHistory = ActivityRepository.heartRateHistory.value
        val currentData = ActivityRepository.currentData.value

        // Calculate statistics
        val hrValues = heartRateHistory.map { it.second }.filter { it > 0 }
        val avgHr = if (hrValues.isNotEmpty()) hrValues.average().toInt() else 0
        val maxHr = hrValues.maxOrNull() ?: 0
        val minHr = hrValues.minOrNull() ?: 0

        // Calculate zone times (using 190 as default max HR)
        val userMaxHr = 190
        var zone1Time = 0L
        var zone2Time = 0L
        var zone3Time = 0L
        var zone4Time = 0L
        var zone5Time = 0L

        if (heartRateHistory.size >= 2) {
            for (i in 0 until heartRateHistory.size - 1) {
                val (timestamp, hr) = heartRateHistory[i]
                val nextTimestamp = heartRateHistory[i + 1].first
                val duration = nextTimestamp - timestamp

                if (duration > 0 && hr > 0) {
                    val zone = HeartRateZone.fromHeartRate(hr, userMaxHr)
                    when (zone) {
                        HeartRateZone.ZONE_1 -> zone1Time += duration
                        HeartRateZone.ZONE_2 -> zone2Time += duration
                        HeartRateZone.ZONE_3 -> zone3Time += duration
                        HeartRateZone.ZONE_4 -> zone4Time += duration
                        HeartRateZone.ZONE_5 -> zone5Time += duration
                    }
                }
            }
        }

        val speeds = trackPoints.map {
            // Calculate speed from track points if not stored
            0.0 // Will be improved with actual speed data
        }

        // Convert track points to session format
        val sessionTrackPoints = trackPoints.map { tp ->
            SessionTrackPoint(
                latitude = tp.latitude,
                longitude = tp.longitude,
                timestamp = tp.timestamp,
                heartRate = tp.heartRate
            )
        }

        // For now, use current values as averages (TODO: track history for proper averaging)
        val avgCadence = currentData.cadence
        val maxCadence = currentData.cadence
        val avgPower = currentData.power
        val maxPower = currentData.power

        val session = ActivitySession(
            id = sessionId,
            startTime = sessionStartTime,
            endTime = endTime,
            durationMs = endTime - sessionStartTime,
            distanceMeters = currentData.distance,
            avgHeartRate = avgHr,
            maxHeartRate = maxHr,
            minHeartRate = minHr,
            avgSpeed = if (currentData.distance > 0 && (endTime - sessionStartTime) > 0) {
                currentData.distance / ((endTime - sessionStartTime) / 1000.0)
            } else 0.0,
            maxSpeed = currentData.speed,
            avgCadence = avgCadence,
            maxCadence = maxCadence,
            avgPower = avgPower,
            maxPower = maxPower,
            trackPointsJson = gson.toJson(sessionTrackPoints),
            heartRateDataJson = gson.toJson(heartRateHistory),
            activityType = currentData.activityType.name.lowercase(),
            zone1TimeMs = zone1Time,
            zone2TimeMs = zone2Time,
            zone3TimeMs = zone3Time,
            zone4TimeMs = zone4Time,
            zone5TimeMs = zone5Time
        )

        sessionDao.updateSession(session)
        currentSessionId = null

        return session
    }

    /**
     * Delete a session
     */
    suspend fun deleteSession(sessionId: Long) {
        sessionDao.deleteSessionById(sessionId)
    }

    /**
     * Get statistics
     */
    suspend fun getStats(): SessionStats {
        return SessionStats(
            totalSessions = sessionDao.getSessionCount(),
            totalDistance = sessionDao.getTotalDistance() ?: 0.0,
            totalDuration = sessionDao.getTotalDuration() ?: 0L
        )
    }

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = currentSessionId != null

    /**
     * Get current session ID
     */
    fun getCurrentSessionId(): Long? = currentSessionId
}

/**
 * Statistics data class
 */
data class SessionStats(
    val totalSessions: Int,
    val totalDistance: Double,
    val totalDuration: Long
) {
    val totalDistanceKm: Double get() = totalDistance / 1000.0

    val totalDurationFormatted: String get() {
        val totalSeconds = totalDuration / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return "%dh %dm".format(hours, minutes)
    }
}

/**
 * Singleton instance for global access
 */
object SessionManager {
    private var repository: SessionRepository? = null

    fun initialize(context: Context) {
        if (repository == null) {
            repository = SessionRepository(context.applicationContext)
        }
    }

    fun getInstance(): SessionRepository {
        return repository ?: throw IllegalStateException("SessionManager not initialized")
    }
}
