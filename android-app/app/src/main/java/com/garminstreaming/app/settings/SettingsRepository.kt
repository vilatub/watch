package com.garminstreaming.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.garminstreaming.app.HeartRateZone
import com.garminstreaming.app.alerts.ZoneAlertSettings
import com.garminstreaming.app.autopause.AutoPauseSettings
import com.garminstreaming.app.voice.VoiceFeedbackSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * User profile settings
 */
data class UserSettings(
    val maxHeartRate: Int = 190,
    val restingHeartRate: Int = 60,
    val weight: Double = 70.0, // kg
    val height: Int = 175, // cm
    val age: Int = 30
)

/**
 * Repository for persisting app settings using DataStore
 */
class SettingsRepository(private val context: Context) {

    // Keys for Zone Alert Settings
    private object ZoneAlertKeys {
        val ENABLED = booleanPreferencesKey("zone_alert_enabled")
        val TARGET_ZONE = intPreferencesKey("zone_alert_target_zone")
        val ALERT_ON_EXIT = booleanPreferencesKey("zone_alert_on_exit")
        val ALERT_ON_ENTRY = booleanPreferencesKey("zone_alert_on_entry")
        val VIBRATION_DURATION = longPreferencesKey("zone_alert_vibration_duration")
        val COOLDOWN = longPreferencesKey("zone_alert_cooldown")
    }

    // Keys for Auto-Pause Settings
    private object AutoPauseKeys {
        val ENABLED = booleanPreferencesKey("auto_pause_enabled")
        val SPEED_THRESHOLD = doublePreferencesKey("auto_pause_speed_threshold")
        val PAUSE_DELAY = longPreferencesKey("auto_pause_delay")
        val RESUME_DELAY = longPreferencesKey("auto_pause_resume_delay")
    }

    // Keys for Voice Feedback Settings
    private object VoiceKeys {
        val ENABLED = booleanPreferencesKey("voice_enabled")
        val ANNOUNCE_DISTANCE = booleanPreferencesKey("voice_announce_distance")
        val ANNOUNCE_PACE = booleanPreferencesKey("voice_announce_pace")
        val ANNOUNCE_HR = booleanPreferencesKey("voice_announce_hr")
        val ANNOUNCE_TIME = booleanPreferencesKey("voice_announce_time")
        val DISTANCE_INTERVAL = doublePreferencesKey("voice_distance_interval")
        val TIME_INTERVAL = intPreferencesKey("voice_time_interval")
    }

    // Keys for User Settings
    private object UserKeys {
        val MAX_HR = intPreferencesKey("user_max_hr")
        val RESTING_HR = intPreferencesKey("user_resting_hr")
        val WEIGHT = doublePreferencesKey("user_weight")
        val HEIGHT = intPreferencesKey("user_height")
        val AGE = intPreferencesKey("user_age")
    }

    // Zone Alert Settings
    val zoneAlertSettings: Flow<ZoneAlertSettings> = context.dataStore.data.map { prefs ->
        ZoneAlertSettings(
            enabled = prefs[ZoneAlertKeys.ENABLED] ?: false,
            targetZone = HeartRateZone.entries.getOrElse(prefs[ZoneAlertKeys.TARGET_ZONE] ?: 2) { HeartRateZone.ZONE_3 },
            alertOnExit = prefs[ZoneAlertKeys.ALERT_ON_EXIT] ?: true,
            alertOnEntry = prefs[ZoneAlertKeys.ALERT_ON_ENTRY] ?: false,
            vibrationDurationMs = prefs[ZoneAlertKeys.VIBRATION_DURATION] ?: 500,
            cooldownMs = prefs[ZoneAlertKeys.COOLDOWN] ?: 10000,
            maxHeartRate = prefs[UserKeys.MAX_HR] ?: 190
        )
    }

    suspend fun saveZoneAlertSettings(settings: ZoneAlertSettings) {
        context.dataStore.edit { prefs ->
            prefs[ZoneAlertKeys.ENABLED] = settings.enabled
            prefs[ZoneAlertKeys.TARGET_ZONE] = settings.targetZone.ordinal
            prefs[ZoneAlertKeys.ALERT_ON_EXIT] = settings.alertOnExit
            prefs[ZoneAlertKeys.ALERT_ON_ENTRY] = settings.alertOnEntry
            prefs[ZoneAlertKeys.VIBRATION_DURATION] = settings.vibrationDurationMs
            prefs[ZoneAlertKeys.COOLDOWN] = settings.cooldownMs
        }
    }

    // Auto-Pause Settings
    val autoPauseSettings: Flow<AutoPauseSettings> = context.dataStore.data.map { prefs ->
        AutoPauseSettings(
            enabled = prefs[AutoPauseKeys.ENABLED] ?: false,
            speedThresholdMs = prefs[AutoPauseKeys.SPEED_THRESHOLD] ?: 0.5,
            pauseDelayMs = prefs[AutoPauseKeys.PAUSE_DELAY] ?: 3000,
            resumeDelayMs = prefs[AutoPauseKeys.RESUME_DELAY] ?: 1000
        )
    }

    suspend fun saveAutoPauseSettings(settings: AutoPauseSettings) {
        context.dataStore.edit { prefs ->
            prefs[AutoPauseKeys.ENABLED] = settings.enabled
            prefs[AutoPauseKeys.SPEED_THRESHOLD] = settings.speedThresholdMs
            prefs[AutoPauseKeys.PAUSE_DELAY] = settings.pauseDelayMs
            prefs[AutoPauseKeys.RESUME_DELAY] = settings.resumeDelayMs
        }
    }

    // Voice Feedback Settings
    val voiceFeedbackSettings: Flow<VoiceFeedbackSettings> = context.dataStore.data.map { prefs ->
        VoiceFeedbackSettings(
            enabled = prefs[VoiceKeys.ENABLED] ?: false,
            announceDistance = prefs[VoiceKeys.ANNOUNCE_DISTANCE] ?: true,
            announcePace = prefs[VoiceKeys.ANNOUNCE_PACE] ?: true,
            announceHeartRate = prefs[VoiceKeys.ANNOUNCE_HR] ?: true,
            announceTime = prefs[VoiceKeys.ANNOUNCE_TIME] ?: false,
            distanceIntervalKm = prefs[VoiceKeys.DISTANCE_INTERVAL] ?: 1.0,
            timeIntervalMinutes = prefs[VoiceKeys.TIME_INTERVAL] ?: 5
        )
    }

    suspend fun saveVoiceFeedbackSettings(settings: VoiceFeedbackSettings) {
        context.dataStore.edit { prefs ->
            prefs[VoiceKeys.ENABLED] = settings.enabled
            prefs[VoiceKeys.ANNOUNCE_DISTANCE] = settings.announceDistance
            prefs[VoiceKeys.ANNOUNCE_PACE] = settings.announcePace
            prefs[VoiceKeys.ANNOUNCE_HR] = settings.announceHeartRate
            prefs[VoiceKeys.ANNOUNCE_TIME] = settings.announceTime
            prefs[VoiceKeys.DISTANCE_INTERVAL] = settings.distanceIntervalKm
            prefs[VoiceKeys.TIME_INTERVAL] = settings.timeIntervalMinutes
        }
    }

    // User Settings
    val userSettings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            maxHeartRate = prefs[UserKeys.MAX_HR] ?: 190,
            restingHeartRate = prefs[UserKeys.RESTING_HR] ?: 60,
            weight = prefs[UserKeys.WEIGHT] ?: 70.0,
            height = prefs[UserKeys.HEIGHT] ?: 175,
            age = prefs[UserKeys.AGE] ?: 30
        )
    }

    suspend fun saveUserSettings(settings: UserSettings) {
        context.dataStore.edit { prefs ->
            prefs[UserKeys.MAX_HR] = settings.maxHeartRate
            prefs[UserKeys.RESTING_HR] = settings.restingHeartRate
            prefs[UserKeys.WEIGHT] = settings.weight
            prefs[UserKeys.HEIGHT] = settings.height
            prefs[UserKeys.AGE] = settings.age
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = SettingsRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
