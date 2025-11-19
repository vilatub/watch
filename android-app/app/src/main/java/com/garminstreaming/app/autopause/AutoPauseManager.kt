package com.garminstreaming.app.autopause

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings for auto-pause functionality
 */
data class AutoPauseSettings(
    val enabled: Boolean = false,
    val speedThresholdMs: Double = 0.5, // m/s - below this is considered "stopped"
    val pauseDelayMs: Long = 3000, // Wait this long before pausing
    val resumeDelayMs: Long = 1000 // Wait this long before resuming
)

/**
 * State of auto-pause
 */
data class AutoPauseState(
    val isPaused: Boolean = false,
    val pausedDurationMs: Long = 0,
    val lastPauseTime: Long = 0,
    val lastResumeTime: Long = 0,
    val pauseCount: Int = 0,
    val isWaitingToPause: Boolean = false,
    val isWaitingToResume: Boolean = false
) {
    val pausedDurationFormatted: String get() {
        val totalSeconds = pausedDurationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Manager for automatic pause/resume based on speed
 */
class AutoPauseManager {

    private val _settings = MutableStateFlow(AutoPauseSettings())
    val settings: StateFlow<AutoPauseSettings> = _settings.asStateFlow()

    private val _state = MutableStateFlow(AutoPauseState())
    val state: StateFlow<AutoPauseState> = _state.asStateFlow()

    private var stopDetectedTime: Long = 0
    private var moveDetectedTime: Long = 0
    private var accumulatedPausedTime: Long = 0

    /**
     * Update settings
     */
    fun updateSettings(newSettings: AutoPauseSettings) {
        _settings.value = newSettings
    }

    /**
     * Enable or disable auto-pause
     */
    fun setEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(enabled = enabled)
        if (!enabled) {
            // Resume if currently paused when disabling
            if (_state.value.isPaused) {
                resume()
            }
        }
    }

    /**
     * Set speed threshold
     */
    fun setSpeedThreshold(speedMs: Double) {
        _settings.value = _settings.value.copy(speedThresholdMs = speedMs)
    }

    /**
     * Check speed and handle auto-pause logic
     * @param speedMs current speed in m/s
     */
    fun checkSpeed(speedMs: Double) {
        val currentSettings = _settings.value
        if (!currentSettings.enabled) return

        val now = System.currentTimeMillis()
        val isStopped = speedMs < currentSettings.speedThresholdMs
        val currentState = _state.value

        if (isStopped && !currentState.isPaused) {
            // User has stopped - start waiting to pause
            if (stopDetectedTime == 0L) {
                stopDetectedTime = now
                _state.value = currentState.copy(isWaitingToPause = true, isWaitingToResume = false)
            } else if (now - stopDetectedTime >= currentSettings.pauseDelayMs) {
                // Delay elapsed - pause
                pause()
            }
            moveDetectedTime = 0
        } else if (!isStopped && currentState.isPaused) {
            // User is moving - start waiting to resume
            if (moveDetectedTime == 0L) {
                moveDetectedTime = now
                _state.value = currentState.copy(isWaitingToResume = true, isWaitingToPause = false)
            } else if (now - moveDetectedTime >= currentSettings.resumeDelayMs) {
                // Delay elapsed - resume
                resume()
            }
            stopDetectedTime = 0
        } else if (!isStopped && !currentState.isPaused) {
            // User is moving and not paused - reset stop detection
            stopDetectedTime = 0
            if (currentState.isWaitingToPause) {
                _state.value = currentState.copy(isWaitingToPause = false)
            }
        } else if (isStopped && currentState.isPaused) {
            // User is stopped and paused - update paused duration
            moveDetectedTime = 0
            if (currentState.isWaitingToResume) {
                _state.value = currentState.copy(isWaitingToResume = false)
            }
            updatePausedDuration()
        }
    }

    /**
     * Pause recording
     */
    private fun pause() {
        val now = System.currentTimeMillis()
        _state.value = _state.value.copy(
            isPaused = true,
            lastPauseTime = now,
            pauseCount = _state.value.pauseCount + 1,
            isWaitingToPause = false,
            isWaitingToResume = false
        )
        stopDetectedTime = 0
    }

    /**
     * Resume recording
     */
    private fun resume() {
        val now = System.currentTimeMillis()
        val currentState = _state.value

        // Add time spent paused to accumulated total
        if (currentState.lastPauseTime > 0) {
            accumulatedPausedTime += now - currentState.lastPauseTime
        }

        _state.value = currentState.copy(
            isPaused = false,
            lastResumeTime = now,
            pausedDurationMs = accumulatedPausedTime,
            isWaitingToPause = false,
            isWaitingToResume = false
        )
        moveDetectedTime = 0
    }

    /**
     * Update the displayed paused duration
     */
    private fun updatePausedDuration() {
        val currentState = _state.value
        if (currentState.isPaused && currentState.lastPauseTime > 0) {
            val currentPauseDuration = System.currentTimeMillis() - currentState.lastPauseTime
            _state.value = currentState.copy(
                pausedDurationMs = accumulatedPausedTime + currentPauseDuration
            )
        }
    }

    /**
     * Get actual elapsed time (total time minus paused time)
     */
    fun getActiveTime(totalElapsedMs: Long): Long {
        val currentState = _state.value
        var pausedTime = accumulatedPausedTime

        // Add current pause duration if currently paused
        if (currentState.isPaused && currentState.lastPauseTime > 0) {
            pausedTime += System.currentTimeMillis() - currentState.lastPauseTime
        }

        return (totalElapsedMs - pausedTime).coerceAtLeast(0)
    }

    /**
     * Reset state (e.g., when starting new session)
     */
    fun reset() {
        stopDetectedTime = 0
        moveDetectedTime = 0
        accumulatedPausedTime = 0
        _state.value = AutoPauseState()
    }

    /**
     * Manual pause toggle
     */
    fun togglePause() {
        if (_state.value.isPaused) {
            resume()
        } else {
            pause()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AutoPauseManager? = null

        fun getInstance(): AutoPauseManager {
            return INSTANCE ?: synchronized(this) {
                val instance = AutoPauseManager()
                INSTANCE = instance
                instance
            }
        }
    }
}
