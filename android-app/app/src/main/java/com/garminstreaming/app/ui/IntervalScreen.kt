package com.garminstreaming.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garminstreaming.app.intervals.IntervalManager
import com.garminstreaming.app.intervals.IntervalPhase
import com.garminstreaming.app.intervals.IntervalWorkout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalScreen(
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val intervalManager = remember { IntervalManager.getInstance(context) }
    val state by intervalManager.state.collectAsState()
    val presets = remember { intervalManager.getPresetWorkouts() }

    // Timer tick
    LaunchedEffect(state.isRunning, state.isPaused) {
        if (state.isRunning && !state.isPaused) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                intervalManager.tick()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Interval Training") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (state.isRunning) {
            // Active workout view
            IntervalActiveView(
                state = state,
                onPause = { intervalManager.togglePause() },
                onStop = { intervalManager.stopWorkout() },
                modifier = Modifier.padding(padding)
            )
        } else {
            // Workout selection view
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Select Workout",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(presets) { workout ->
                    WorkoutCard(
                        workout = workout,
                        onClick = { intervalManager.startWorkout(workout) }
                    )
                }
            }
        }
    }
}

@Composable
fun WorkoutCard(
    workout: IntervalWorkout,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = workout.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Start",
                    tint = Color(0xFF4CAF50)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                WorkoutStat(
                    label = "Work",
                    value = "${workout.workSeconds}s"
                )
                WorkoutStat(
                    label = "Rest",
                    value = "${workout.restSeconds}s"
                )
                WorkoutStat(
                    label = "Reps",
                    value = "${workout.repetitions}"
                )
                WorkoutStat(
                    label = "Total",
                    value = workout.totalDurationFormatted
                )
            }
        }
    }
}

@Composable
fun WorkoutStat(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun IntervalActiveView(
    state: com.garminstreaming.app.intervals.IntervalState,
    onPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val phaseColor = when (state.currentPhase) {
        IntervalPhase.WARMUP -> Color(0xFF2196F3)
        IntervalPhase.WORK -> Color(0xFFF44336)
        IntervalPhase.REST -> Color(0xFF4CAF50)
        IntervalPhase.COOLDOWN -> Color(0xFF2196F3)
        IntervalPhase.COMPLETED -> Color(0xFF9C27B0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Progress
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = state.progressPercent / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = phaseColor,
                trackColor = phaseColor.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Interval ${state.completedIntervals + 1} of ${state.totalIntervals}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        // Main display
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Phase name
            Text(
                text = when (state.currentPhase) {
                    IntervalPhase.WARMUP -> "WARM UP"
                    IntervalPhase.WORK -> "WORK"
                    IntervalPhase.REST -> "REST"
                    IntervalPhase.COOLDOWN -> "COOL DOWN"
                    IntervalPhase.COMPLETED -> "COMPLETE"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = phaseColor
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Timer
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(phaseColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.remainingFormatted,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = phaseColor
                )
            }

            if (state.isPaused) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "PAUSED",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFFF9800)
                )
            }
        }

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Stop button
            IconButton(
                onClick = onStop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF44336))
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Pause/Resume button
            IconButton(
                onClick = onPause,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (state.isPaused) Color(0xFF4CAF50) else Color(0xFFFF9800))
            ) {
                Icon(
                    if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (state.isPaused) "Resume" else "Pause",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
