package com.garminstreaming.app.alerts

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.garminstreaming.app.HeartRateZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings for heart rate zone alerts
 */
data class ZoneAlertSettings(
    val enabled: Boolean = false,
    val targetZone: HeartRateZone = HeartRateZone.ZONE_3,
    val alertOnExit: Boolean = true,
    val alertOnEntry: Boolean = false,
    val vibrationDurationMs: Long = 500,
    val cooldownMs: Long = 10000, // Minimum time between alerts
    val maxHeartRate: Int = 190
)

/**
 * Alert state information
 */
data class ZoneAlertState(
    val isInTargetZone: Boolean = true,
    val currentZone: HeartRateZone = HeartRateZone.ZONE_1,
    val targetZone: HeartRateZone = HeartRateZone.ZONE_3,
    val lastAlertTime: Long = 0,
    val alertCount: Int = 0
)

/**
 * Manager for heart rate zone alerts with vibration feedback
 */
class ZoneAlertManager(private val context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val _settings = MutableStateFlow(ZoneAlertSettings())
    val settings: StateFlow<ZoneAlertSettings> = _settings.asStateFlow()

    private val _alertState = MutableStateFlow(ZoneAlertState())
    val alertState: StateFlow<ZoneAlertState> = _alertState.asStateFlow()

    private var previousZone: HeartRateZone? = null

    /**
     * Update alert settings
     */
    fun updateSettings(newSettings: ZoneAlertSettings) {
        _settings.value = newSettings
        // Reset state when target zone changes
        if (newSettings.targetZone != _alertState.value.targetZone) {
            _alertState.value = _alertState.value.copy(
                targetZone = newSettings.targetZone,
                isInTargetZone = true,
                alertCount = 0
            )
            previousZone = null
        }
    }

    /**
     * Enable or disable alerts
     */
    fun setEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(enabled = enabled)
    }

    /**
     * Set target zone
     */
    fun setTargetZone(zone: HeartRateZone) {
        updateSettings(_settings.value.copy(targetZone = zone))
    }

    /**
     * Check heart rate and trigger alerts if necessary
     */
    fun checkHeartRate(heartRate: Int) {
        val currentSettings = _settings.value
        if (!currentSettings.enabled || heartRate <= 0) return

        val currentZone = HeartRateZone.fromHeartRate(heartRate, currentSettings.maxHeartRate)
        val isInTarget = currentZone == currentSettings.targetZone
        val wasInTarget = _alertState.value.isInTargetZone
        val now = System.currentTimeMillis()

        // Update state
        _alertState.value = _alertState.value.copy(
            currentZone = currentZone,
            isInTargetZone = isInTarget
        )

        // Check if we should trigger an alert
        val timeSinceLastAlert = now - _alertState.value.lastAlertTime
        val canAlert = timeSinceLastAlert >= currentSettings.cooldownMs

        if (canAlert && previousZone != null) {
            var shouldAlert = false

            // Alert on exit from target zone
            if (currentSettings.alertOnExit && wasInTarget && !isInTarget) {
                shouldAlert = true
            }

            // Alert on entry to target zone
            if (currentSettings.alertOnEntry && !wasInTarget && isInTarget) {
                shouldAlert = true
            }

            if (shouldAlert) {
                triggerAlert()
                _alertState.value = _alertState.value.copy(
                    lastAlertTime = now,
                    alertCount = _alertState.value.alertCount + 1
                )
            }
        }

        previousZone = currentZone
    }

    /**
     * Trigger vibration alert
     */
    private fun triggerAlert() {
        val settings = _settings.value

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Pattern: short-pause-long for exit alert
            val pattern = if (_alertState.value.isInTargetZone) {
                // Entry: single vibration
                longArrayOf(0, settings.vibrationDurationMs)
            } else {
                // Exit: double vibration pattern
                longArrayOf(0, 200, 100, 300)
            }

            val effect = VibrationEffect.createWaveform(pattern, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(settings.vibrationDurationMs)
        }
    }

    /**
     * Test vibration (for settings preview)
     */
    fun testVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(
                _settings.value.vibrationDurationMs,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(_settings.value.vibrationDurationMs)
        }
    }

    /**
     * Reset alert state (e.g., when starting new session)
     */
    fun reset() {
        previousZone = null
        _alertState.value = ZoneAlertState(
            targetZone = _settings.value.targetZone
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: ZoneAlertManager? = null

        fun getInstance(context: Context): ZoneAlertManager {
            return INSTANCE ?: synchronized(this) {
                val instance = ZoneAlertManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
