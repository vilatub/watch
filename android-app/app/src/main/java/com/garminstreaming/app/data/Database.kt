package com.garminstreaming.app.data

import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

/**
 * Entity representing a recorded activity session
 */
@Entity(tableName = "activity_sessions")
data class ActivitySession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "start_time")
    val startTime: Long,

    @ColumnInfo(name = "end_time")
    val endTime: Long = 0,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0,

    @ColumnInfo(name = "distance_meters")
    val distanceMeters: Double = 0.0,

    @ColumnInfo(name = "avg_heart_rate")
    val avgHeartRate: Int = 0,

    @ColumnInfo(name = "max_heart_rate")
    val maxHeartRate: Int = 0,

    @ColumnInfo(name = "min_heart_rate")
    val minHeartRate: Int = 0,

    @ColumnInfo(name = "avg_speed")
    val avgSpeed: Double = 0.0,

    @ColumnInfo(name = "max_speed")
    val maxSpeed: Double = 0.0,

    @ColumnInfo(name = "elevation_gain")
    val elevationGain: Double = 0.0,

    @ColumnInfo(name = "avg_cadence")
    val avgCadence: Int = 0,

    @ColumnInfo(name = "max_cadence")
    val maxCadence: Int = 0,

    @ColumnInfo(name = "avg_power")
    val avgPower: Int = 0,

    @ColumnInfo(name = "max_power")
    val maxPower: Int = 0,

    @ColumnInfo(name = "track_points")
    val trackPointsJson: String = "[]",

    @ColumnInfo(name = "heart_rate_data")
    val heartRateDataJson: String = "[]",

    @ColumnInfo(name = "activity_type")
    val activityType: String = "running"
) {
    // Helper properties for serialization
    val trackPoints: List<SessionTrackPoint>
        get() {
            val type = object : TypeToken<List<SessionTrackPoint>>() {}.type
            return Gson().fromJson(trackPointsJson, type) ?: emptyList()
        }

    val heartRateData: List<Pair<Long, Int>>
        get() {
            val type = object : TypeToken<List<Pair<Long, Int>>>() {}.type
            return Gson().fromJson(heartRateDataJson, type) ?: emptyList()
        }

    // Formatted properties
    val distanceKm: Double get() = distanceMeters / 1000.0

    val durationFormatted: String get() {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    val avgPaceFormatted: String get() {
        if (avgSpeed <= 0 || distanceMeters <= 0) return "--:--"
        val paceSeconds = (1000.0 / avgSpeed).toInt()
        val minutes = paceSeconds / 60
        val seconds = paceSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    val avgSpeedKmh: Double get() = avgSpeed * 3.6
}

/**
 * Data class for track points (serialized to JSON)
 */
data class SessionTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val heartRate: Int,
    val altitude: Double = 0.0,
    val speed: Double = 0.0,
    val cadence: Int = 0,
    val power: Int = 0
)

/**
 * DAO for activity session operations
 */
@Dao
interface ActivitySessionDao {
    @Query("SELECT * FROM activity_sessions ORDER BY start_time DESC")
    fun getAllSessions(): Flow<List<ActivitySession>>

    @Query("SELECT * FROM activity_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): ActivitySession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ActivitySession): Long

    @Update
    suspend fun updateSession(session: ActivitySession)

    @Delete
    suspend fun deleteSession(session: ActivitySession)

    @Query("DELETE FROM activity_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("SELECT COUNT(*) FROM activity_sessions")
    suspend fun getSessionCount(): Int

    @Query("SELECT SUM(distance_meters) FROM activity_sessions")
    suspend fun getTotalDistance(): Double?

    @Query("SELECT SUM(duration_ms) FROM activity_sessions")
    suspend fun getTotalDuration(): Long?
}

/**
 * Room Database
 */
@Database(
    entities = [ActivitySession::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activitySessionDao(): ActivitySessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "garmin_streaming_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
