package com.garminstreaming.app.intervals

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * Types of interval phases
 */
enum class IntervalPhase {
    WARMUP,
    WORK,
    REST,
    COOLDOWN,
    COMPLETED
}

/**
 * A single interval step
 */
data class IntervalStep(
    val phase: IntervalPhase,
    val durationSeconds: Int,
    val targetHrZone: Int? = null, // 1-5 or null for any
    val description: String = ""
)

/**
 * Complete interval workout definition
 */
data class IntervalWorkout(
    val name: String,
    val warmupSeconds: Int = 300, // 5 min warmup
    val workSeconds: Int = 60,
    val restSeconds: Int = 30,
    val repetitions: Int = 8,
    val cooldownSeconds: Int = 300, // 5 min cooldown
    val workTargetZone: Int? = 4, // Zone 4 for work
    val restTargetZone: Int? = 2  // Zone 2 for rest
) {
    val totalDurationSeconds: Int get() {
        return warmupSeconds + (workSeconds + restSeconds) * repetitions + cooldownSeconds
    }

    val totalDurationFormatted: String get() {
        val minutes = totalDurationSeconds / 60
        val seconds = totalDurationSeconds % 60
        return if (seconds > 0) "${minutes}m ${seconds}s" else "${minutes}m"
    }

    fun toSteps(): List<IntervalStep> {
        val steps = mutableListOf<IntervalStep>()

        // Warmup
        if (warmupSeconds > 0) {
            steps.add(IntervalStep(IntervalPhase.WARMUP, warmupSeconds, null, "Warm up"))
        }

        // Work/Rest intervals
        for (i in 1..repetitions) {
            steps.add(IntervalStep(IntervalPhase.WORK, workSeconds, workTargetZone, "Interval $i - Work"))
            if (i < repetitions || cooldownSeconds > 0) {
                steps.add(IntervalStep(IntervalPhase.REST, restSeconds, restTargetZone, "Interval $i - Rest"))
            }
        }

        // Cooldown
        if (cooldownSeconds > 0) {
            steps.add(IntervalStep(IntervalPhase.COOLDOWN, cooldownSeconds, null, "Cool down"))
        }

        return steps
    }
}

/**
 * Current state of interval workout
 */
data class IntervalState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentStepIndex: Int = 0,
    val currentPhase: IntervalPhase = IntervalPhase.WARMUP,
    val remainingSeconds: Int = 0,
    val elapsedSeconds: Int = 0,
    val completedIntervals: Int = 0,
    val totalIntervals: Int = 0,
    val workout: IntervalWorkout? = null
) {
    val remainingFormatted: String get() {
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    val progressPercent: Float get() {
        val workout = workout ?: return 0f
        return (elapsedSeconds.toFloat() / workout.totalDurationSeconds) * 100
    }
}

/**
 * Manager for interval training workouts
 */
class IntervalManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsInitialized = false

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val _state = MutableStateFlow(IntervalState())
    val state: StateFlow<IntervalState> = _state.asStateFlow()

    private var steps: List<IntervalStep> = emptyList()
    private var currentStepStartTime: Long = 0

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsInitialized = true
            }
        }
    }

    /**
     * Preset interval workouts
     */
    fun getPresetWorkouts(): List<IntervalWorkout> = listOf(
        IntervalWorkout(
            name = "Beginner HIIT",
            warmupSeconds = 300,
            workSeconds = 30,
            restSeconds = 60,
            repetitions = 6,
            cooldownSeconds = 300
        ),
        IntervalWorkout(
            name = "Classic 4x4",
            warmupSeconds = 600,
            workSeconds = 240, // 4 min
            restSeconds = 180, // 3 min
            repetitions = 4,
            cooldownSeconds = 300
        ),
        IntervalWorkout(
            name = "Tabata",
            warmupSeconds = 300,
            workSeconds = 20,
            restSeconds = 10,
            repetitions = 8,
            cooldownSeconds = 120
        ),
        IntervalWorkout(
            name = "Pyramid",
            warmupSeconds = 300,
            workSeconds = 60,
            restSeconds = 30,
            repetitions = 10,
            cooldownSeconds = 300
        ),
        IntervalWorkout(
            name = "Sprint Intervals",
            warmupSeconds = 600,
            workSeconds = 30,
            restSeconds = 90,
            repetitions = 8,
            cooldownSeconds = 300
        )
    )

    /**
     * Start an interval workout
     */
    fun startWorkout(workout: IntervalWorkout) {
        steps = workout.toSteps()
        if (steps.isEmpty()) return

        val firstStep = steps[0]
        currentStepStartTime = System.currentTimeMillis()

        _state.value = IntervalState(
            isRunning = true,
            isPaused = false,
            currentStepIndex = 0,
            currentPhase = firstStep.phase,
            remainingSeconds = firstStep.durationSeconds,
            elapsedSeconds = 0,
            completedIntervals = 0,
            totalIntervals = workout.repetitions,
            workout = workout
        )

        announcePhase(firstStep)
    }

    /**
     * Update timer (call every second)
     */
    fun tick() {
        val currentState = _state.value
        if (!currentState.isRunning || currentState.isPaused) return

        val newRemaining = currentState.remainingSeconds - 1
        val newElapsed = currentState.elapsedSeconds + 1

        if (newRemaining <= 0) {
            // Move to next step
            nextStep()
        } else {
            // Countdown warnings
            if (newRemaining == 10 || newRemaining == 5 || newRemaining == 3) {
                speak("$newRemaining")
            } else if (newRemaining <= 3) {
                shortVibrate()
            }

            _state.value = currentState.copy(
                remainingSeconds = newRemaining,
                elapsedSeconds = newElapsed
            )
        }
    }

    /**
     * Move to next interval step
     */
    private fun nextStep() {
        val currentState = _state.value
        val nextIndex = currentState.currentStepIndex + 1

        if (nextIndex >= steps.size) {
            // Workout complete
            _state.value = currentState.copy(
                isRunning = false,
                currentPhase = IntervalPhase.COMPLETED,
                remainingSeconds = 0
            )
            announceComplete()
            return
        }

        val nextStep = steps[nextIndex]
        val completedIntervals = if (nextStep.phase == IntervalPhase.REST || nextStep.phase == IntervalPhase.COOLDOWN) {
            currentState.completedIntervals + (if (currentState.currentPhase == IntervalPhase.WORK) 1 else 0)
        } else {
            currentState.completedIntervals
        }

        _state.value = currentState.copy(
            currentStepIndex = nextIndex,
            currentPhase = nextStep.phase,
            remainingSeconds = nextStep.durationSeconds,
            completedIntervals = completedIntervals
        )

        announcePhase(nextStep)
    }

    /**
     * Pause/Resume workout
     */
    fun togglePause() {
        val currentState = _state.value
        if (!currentState.isRunning) return

        _state.value = currentState.copy(isPaused = !currentState.isPaused)

        if (currentState.isPaused) {
            speak("Resumed")
        } else {
            speak("Paused")
        }
    }

    /**
     * Stop workout
     */
    fun stopWorkout() {
        _state.value = IntervalState()
        speak("Workout stopped")
    }

    /**
     * Announce phase change
     */
    private fun announcePhase(step: IntervalStep) {
        longVibrate()

        val message = when (step.phase) {
            IntervalPhase.WARMUP -> "Warm up. ${step.durationSeconds / 60} minutes"
            IntervalPhase.WORK -> "Go! Work interval"
            IntervalPhase.REST -> "Rest"
            IntervalPhase.COOLDOWN -> "Cool down. ${step.durationSeconds / 60} minutes"
            IntervalPhase.COMPLETED -> "Workout complete"
        }
        speak(message)
    }

    /**
     * Announce workout complete
     */
    private fun announceComplete() {
        longVibrate()
        speak("Excellent! Workout complete. Great job!")
    }

    private fun speak(text: String) {
        if (ttsInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "interval_${System.currentTimeMillis()}")
        }
    }

    private fun shortVibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }

    private fun longVibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }

    companion object {
        @Volatile
        private var INSTANCE: IntervalManager? = null

        fun getInstance(context: Context): IntervalManager {
            return INSTANCE ?: synchronized(this) {
                val instance = IntervalManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
