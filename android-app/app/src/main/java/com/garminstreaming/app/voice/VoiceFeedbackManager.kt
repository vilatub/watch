package com.garminstreaming.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * Settings for voice feedback
 */
data class VoiceFeedbackSettings(
    val enabled: Boolean = false,
    val announceDistance: Boolean = true,      // Announce every km
    val announcePace: Boolean = true,          // Include pace in announcements
    val announceHeartRate: Boolean = true,     // Include HR in announcements
    val announceTime: Boolean = false,         // Announce time intervals
    val distanceIntervalKm: Double = 1.0,      // Distance between announcements
    val timeIntervalMinutes: Int = 5,          // Time between announcements
    val volume: Float = 1.0f                   // TTS volume (0.0 to 1.0)
)

/**
 * State of voice feedback
 */
data class VoiceFeedbackState(
    val isInitialized: Boolean = false,
    val isSpeaking: Boolean = false,
    val lastAnnouncedDistanceKm: Double = 0.0,
    val lastAnnouncedTime: Long = 0,
    val announcementCount: Int = 0
)

/**
 * Manager for voice feedback using Text-to-Speech
 */
class VoiceFeedbackManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null

    private val _settings = MutableStateFlow(VoiceFeedbackSettings())
    val settings: StateFlow<VoiceFeedbackSettings> = _settings.asStateFlow()

    private val _state = MutableStateFlow(VoiceFeedbackState())
    val state: StateFlow<VoiceFeedbackState> = _state.asStateFlow()

    private var sessionStartTime: Long = 0

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language to English (or device default)
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Try device default
                tts?.setLanguage(Locale.getDefault())
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _state.value = _state.value.copy(isSpeaking = true)
                }

                override fun onDone(utteranceId: String?) {
                    _state.value = _state.value.copy(isSpeaking = false)
                }

                override fun onError(utteranceId: String?) {
                    _state.value = _state.value.copy(isSpeaking = false)
                }
            })

            _state.value = _state.value.copy(isInitialized = true)
        }
    }

    /**
     * Update settings
     */
    fun updateSettings(newSettings: VoiceFeedbackSettings) {
        _settings.value = newSettings
    }

    /**
     * Enable or disable voice feedback
     */
    fun setEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(enabled = enabled)
    }

    /**
     * Check metrics and announce if needed
     */
    fun checkAndAnnounce(
        distanceKm: Double,
        paceFormatted: String,
        heartRate: Int,
        elapsedMs: Long
    ) {
        val currentSettings = _settings.value
        if (!currentSettings.enabled || !_state.value.isInitialized) return

        val currentState = _state.value

        // Check distance milestone
        if (currentSettings.announceDistance) {
            val nextMilestone = currentState.lastAnnouncedDistanceKm + currentSettings.distanceIntervalKm
            if (distanceKm >= nextMilestone) {
                announceDistance(distanceKm, paceFormatted, heartRate)
                _state.value = currentState.copy(
                    lastAnnouncedDistanceKm = distanceKm.toInt().toDouble(),
                    announcementCount = currentState.announcementCount + 1
                )
            }
        }

        // Check time interval
        if (currentSettings.announceTime) {
            val intervalMs = currentSettings.timeIntervalMinutes * 60 * 1000L
            val timeSinceLastAnnouncement = elapsedMs - currentState.lastAnnouncedTime
            if (currentState.lastAnnouncedTime == 0L || timeSinceLastAnnouncement >= intervalMs) {
                if (currentState.lastAnnouncedTime > 0) { // Don't announce at start
                    announceTime(elapsedMs, distanceKm, paceFormatted, heartRate)
                    _state.value = currentState.copy(
                        lastAnnouncedTime = elapsedMs,
                        announcementCount = currentState.announcementCount + 1
                    )
                } else {
                    _state.value = currentState.copy(lastAnnouncedTime = elapsedMs)
                }
            }
        }
    }

    /**
     * Announce distance milestone
     */
    private fun announceDistance(distanceKm: Double, paceFormatted: String, heartRate: Int) {
        val settings = _settings.value
        val parts = mutableListOf<String>()

        // Distance
        val km = distanceKm.toInt()
        parts.add("$km kilometer${if (km != 1) "s" else ""}")

        // Pace
        if (settings.announcePace && paceFormatted != "--:--") {
            val paceParts = paceFormatted.split(":")
            if (paceParts.size == 2) {
                val minutes = paceParts[0].toIntOrNull() ?: 0
                val seconds = paceParts[1].toIntOrNull() ?: 0
                parts.add("pace $minutes ${if (seconds > 0) "$seconds" else ""} per kilometer")
            }
        }

        // Heart rate
        if (settings.announceHeartRate && heartRate > 0) {
            parts.add("heart rate $heartRate")
        }

        speak(parts.joinToString(", "))
    }

    /**
     * Announce time interval
     */
    private fun announceTime(elapsedMs: Long, distanceKm: Double, paceFormatted: String, heartRate: Int) {
        val settings = _settings.value
        val parts = mutableListOf<String>()

        // Time
        val totalMinutes = (elapsedMs / 60000).toInt()
        parts.add("$totalMinutes minute${if (totalMinutes != 1) "s" else ""}")

        // Distance
        if (settings.announceDistance) {
            parts.add("%.1f kilometers".format(distanceKm))
        }

        // Pace
        if (settings.announcePace && paceFormatted != "--:--") {
            val paceParts = paceFormatted.split(":")
            if (paceParts.size == 2) {
                val minutes = paceParts[0].toIntOrNull() ?: 0
                val seconds = paceParts[1].toIntOrNull() ?: 0
                parts.add("pace $minutes ${if (seconds > 0) "$seconds" else ""}")
            }
        }

        // Heart rate
        if (settings.announceHeartRate && heartRate > 0) {
            parts.add("heart rate $heartRate")
        }

        speak(parts.joinToString(", "))
    }

    /**
     * Speak text using TTS
     */
    private fun speak(text: String) {
        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "voice_feedback_${System.currentTimeMillis()}"
        )
    }

    /**
     * Test voice feedback
     */
    fun testVoice() {
        speak("Voice feedback is working. Good luck with your workout!")
    }

    /**
     * Announce workout start
     */
    fun announceStart() {
        if (_settings.value.enabled && _state.value.isInitialized) {
            speak("Workout started")
        }
    }

    /**
     * Announce workout end
     */
    fun announceEnd(distanceKm: Double, durationFormatted: String) {
        if (_settings.value.enabled && _state.value.isInitialized) {
            speak("Workout complete. Total distance %.1f kilometers. Time $durationFormatted".format(distanceKm))
        }
    }

    /**
     * Reset state (e.g., when starting new session)
     */
    fun reset() {
        sessionStartTime = System.currentTimeMillis()
        _state.value = VoiceFeedbackState(isInitialized = _state.value.isInitialized)
    }

    /**
     * Clean up TTS resources
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        @Volatile
        private var INSTANCE: VoiceFeedbackManager? = null

        fun getInstance(context: Context): VoiceFeedbackManager {
            return INSTANCE ?: synchronized(this) {
                val instance = VoiceFeedbackManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
