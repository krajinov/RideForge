package com.delminiusapps.rideforge.features.workout.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.delminiusapps.rideforge.platform.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.delminiusapps.rideforge.data.local.WorkoutControlMode
import com.delminiusapps.rideforge.domain.trainer.ConnectionState
import com.delminiusapps.rideforge.models.SyncStatus
import com.delminiusapps.rideforge.navigation.AppRoute
import com.delminiusapps.rideforge.presentation.components.AppCard
import com.delminiusapps.rideforge.presentation.components.ErgBadge
import com.delminiusapps.rideforge.presentation.components.ErrorState
import com.delminiusapps.rideforge.presentation.components.IntervalTimeline
import com.delminiusapps.rideforge.presentation.components.LoadingState
import com.delminiusapps.rideforge.presentation.components.PrimaryButton
import com.delminiusapps.rideforge.presentation.components.ProgressGraph
import com.delminiusapps.rideforge.presentation.components.ScreenLazyColumn
import com.delminiusapps.rideforge.presentation.components.SecondaryButton
import com.delminiusapps.rideforge.theme.ForgeBlue
import com.delminiusapps.rideforge.theme.ForgeGreen
import com.delminiusapps.rideforge.theme.ForgeMuted
import com.delminiusapps.rideforge.theme.ForgeOrange
import com.delminiusapps.rideforge.theme.ForgeRed
import com.delminiusapps.rideforge.theme.ForgeSurfaceHigh
import com.delminiusapps.rideforge.utils.formatDuration
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import rideforge.composeapp.generated.resources.Res
import rideforge.composeapp.generated.resources.workout_cadence
import rideforge.composeapp.generated.resources.workout_current_interval_progress
import rideforge.composeapp.generated.resources.workout_current_power
import rideforge.composeapp.generated.resources.workout_end
import rideforge.composeapp.generated.resources.workout_finish
import rideforge.composeapp.generated.resources.workout_heart_rate
import rideforge.composeapp.generated.resources.workout_load_error
import rideforge.composeapp.generated.resources.workout_loading_engine
import rideforge.composeapp.generated.resources.workout_next
import rideforge.composeapp.generated.resources.workout_pause
import rideforge.composeapp.generated.resources.workout_remaining
import rideforge.composeapp.generated.resources.workout_resume
import rideforge.composeapp.generated.resources.workout_skip_interval
import rideforge.composeapp.generated.resources.workout_sync_failed
import rideforge.composeapp.generated.resources.workout_sync_pending
import rideforge.composeapp.generated.resources.workout_sync_synced
import rideforge.composeapp.generated.resources.workout_sync_syncing
import rideforge.composeapp.generated.resources.workout_target
import rideforge.composeapp.generated.resources.workout_total_progress
import rideforge.composeapp.generated.resources.workout_watts

@Composable
fun ActiveWorkoutScreen(
    workoutId: String,
    onNavigate: (AppRoute) -> Unit,
    viewModel: ActiveWorkoutViewModel = koinViewModel { parametersOf(workoutId) },
) {
    val uiState by viewModel.uiState.collectAsState()
    var showEndConfirmation by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                -> viewModel.onAction(ActiveWorkoutAction.FlushActiveSnapshot)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.onAction(ActiveWorkoutAction.FlushActiveSnapshot)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = uiState is ActiveWorkoutUiState.Ready && (uiState as ActiveWorkoutUiState.Ready).phase == ActiveWorkoutPhase.ACTIVE) {
        showEndConfirmation = true
    }

    if (uiState is ActiveWorkoutUiState.Error) {
        ScreenLazyColumn {
            item { ErrorState(message = stringResource(Res.string.workout_load_error), title = "Unable to start workout") }
        }
        return
    }

    com.delminiusapps.rideforge.ui.utils.KeepScreenOn()

    val readyState = uiState as? ActiveWorkoutUiState.Ready
    if (readyState == null) {
        ScreenLazyColumn {
            item { LoadingState(stringResource(Res.string.workout_loading_engine)) }
        }
        return
    }

    LaunchedEffect(readyState.completionSessionId) {
        readyState.completionSessionId?.let { onNavigate(AppRoute.WorkoutComplete(id = it)) }
    }

    if (showEndConfirmation) {
        AlertDialog(
            onDismissRequest = { showEndConfirmation = false },
            title = { Text("End workout?") },
            text = { Text("Your local recording will be saved and backend sync will continue in the background.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEndConfirmation = false
                        viewModel.onAction(ActiveWorkoutAction.End)
                    },
                ) {
                    Text("End workout", color = ForgeRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirmation = false }) {
                    Text("Keep riding")
                }
            },
        )
    }

    val state = readyState.engineState
    val sample = readyState.displaySample

    Box(Modifier.fillMaxSize()) {
        ScreenLazyColumn {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.workout.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text(state.currentInterval.name, color = ForgeMuted)
                    }
                    SyncStatusChip(readyState.syncStatus)
                    TrainerConnectionChip(
                        connectionState = readyState.trainerConnectionState,
                        usingSimulatedMetrics = readyState.usingSimulatedMetrics,
                        controlMode = readyState.controlMode,
                    )
                    if (readyState.ergControlEnabled && readyState.controlMode == WorkoutControlMode.TRAINER) {
                        ErgBadge()
                    }
                }
            }

            readyState.banners.forEach { banner ->
                item { WorkoutErrorBanner(banner, readyState.isReconnectingTrainer) }
            }

            if (readyState.phase != ActiveWorkoutPhase.ACTIVE) {
                item {
                    PreWorkoutDeviceCheck(
                        readyState = readyState,
                        onRetry = { viewModel.onAction(ActiveWorkoutAction.RetryDeviceCheck) },
                    )
                }
                item {
                    ControlModeSelector(
                        selectedMode = readyState.controlMode,
                        trainerEnabled = readyState.trainerConnectionState == ConnectionState.CONNECTED,
                        onModeSelected = { viewModel.onAction(ActiveWorkoutAction.SelectControlMode(it)) },
                    )
                }
                item { ErgReadinessCard(readyState) }
                if (readyState.phase == ActiveWorkoutPhase.COUNTDOWN) {
                    item { CountdownCard(readyState.countdownSeconds ?: 3) }
                } else {
                    item {
                        PrimaryButton(
                            "Start Workout",
                            { viewModel.onAction(ActiveWorkoutAction.Start) },
                            Modifier.fillMaxWidth(),
                            enabled = readyState.controlMode == WorkoutControlMode.SIMULATION ||
                                readyState.trainerConnectionState == ConnectionState.CONNECTED,
                        )
                    }
                }
                return@ScreenLazyColumn
            }

            if (readyState.resumedFromStorage && state.isPaused) {
                item {
                    AppCard {
                        Text("Unfinished workout restored. Resume when you are ready.", color = ForgeOrange, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier.fillMaxWidth().background(ForgeSurfaceHigh, RoundedCornerShape(28.dp)).padding(22.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.workout_current_power), color = ForgeMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${sample.currentPowerWatts}", fontSize = 86.sp, lineHeight = 88.sp, fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(Res.string.workout_watts), color = ForgeMuted, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BigMetric(stringResource(Res.string.workout_target), "${sample.targetPowerWatts} W", ForgeBlue, Modifier.weight(1f))
                    BigMetric(stringResource(Res.string.workout_cadence), "${sample.cadenceRpm} rpm", ForgeGreen, Modifier.weight(1f))
                    BigMetric(stringResource(Res.string.workout_heart_rate), "${sample.heartRateBpm}", ForgeRed, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BigMetric(stringResource(Res.string.workout_remaining), formatDuration((state.totalSeconds - state.elapsedSeconds).coerceAtLeast(0)), ForgeBlue, Modifier.weight(1f))
                    BigMetric(stringResource(Res.string.workout_next), state.nextInterval?.name ?: stringResource(Res.string.workout_finish), ForgeMuted, Modifier.weight(1f))
                }
            }
            item { ProgressGraph(readyState.displaySamples) }
            item { IntervalTimeline(state.workout.intervals, progress = state.totalProgress) }
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProgressLabel(stringResource(Res.string.workout_current_interval_progress), state.intervalProgress)
                        ProgressLabel(stringResource(Res.string.workout_total_progress), state.totalProgress)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(if (state.isPaused) stringResource(Res.string.workout_resume) else stringResource(Res.string.workout_pause), {
                        viewModel.onAction(if (state.isPaused) ActiveWorkoutAction.Resume else ActiveWorkoutAction.Pause)
                    }, Modifier.weight(1f))
                    SecondaryButton(stringResource(Res.string.workout_skip_interval), { viewModel.onAction(ActiveWorkoutAction.SkipInterval) }, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(
                        if (readyState.ergControlEnabled) "ERG Off" else "ERG On",
                        { viewModel.onAction(ActiveWorkoutAction.ToggleErg) },
                        Modifier.weight(1f),
                        enabled = readyState.controlMode == WorkoutControlMode.TRAINER &&
                            readyState.trainerConnectionState == ConnectionState.CONNECTED,
                    )
                    PrimaryButton(stringResource(Res.string.workout_end), { showEndConfirmation = true }, Modifier.weight(1f))
                }
            }
        }

        if (readyState.autoPausedForStoppedPedaling) {
            StoppedPedalingOverlay(
                readyState = readyState,
                onResume = { viewModel.onAction(ActiveWorkoutAction.Resume) },
                onEnd = { showEndConfirmation = true },
            )
        }
    }
}

@Composable
private fun StoppedPedalingOverlay(
    readyState: ActiveWorkoutUiState.Ready,
    onResume: () -> Unit,
    onEnd: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Workout paused", fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Text("Start pedaling to resume", color = ForgeBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Text(
                    "No power or cadence detected. Timing is paused and your ride is still saved locally.",
                    color = ForgeMuted,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton("Resume anyway", onResume, Modifier.weight(1f))
                    PrimaryButton(stringResource(Res.string.workout_end), onEnd, Modifier.weight(1f))
                }
                Text(
                    "${readyState.engineState.currentInterval.name} - ${formatDuration(readyState.engineState.elapsedSeconds)}",
                    color = ForgeMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PreWorkoutDeviceCheck(
    readyState: ActiveWorkoutUiState.Ready,
    onRetry: () -> Unit,
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Pre-workout device check", fontWeight = FontWeight.Bold)
            Text(deviceCheckMessage(readyState), color = ForgeMuted, lineHeight = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(
                    if (readyState.isCheckingDevice) "Checking..." else "Check again",
                    onRetry,
                    Modifier.weight(1f),
                    enabled = !readyState.isCheckingDevice,
                )
                SecondaryButton(
                    readyState.connectedDevice?.name ?: "No trainer",
                    {},
                    Modifier.weight(1f),
                    enabled = false,
                )
            }
        }
    }
}

@Composable
private fun ControlModeSelector(
    selectedMode: WorkoutControlMode,
    trainerEnabled: Boolean,
    onModeSelected: (WorkoutControlMode) -> Unit,
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Workout mode", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ModeButton(
                    label = "Trainer",
                    selected = selectedMode == WorkoutControlMode.TRAINER,
                    enabled = trainerEnabled,
                    onClick = { onModeSelected(WorkoutControlMode.TRAINER) },
                    modifier = Modifier.weight(1f),
                )
                ModeButton(
                    label = "Simulation",
                    selected = selectedMode == WorkoutControlMode.SIMULATION,
                    enabled = true,
                    onClick = { onModeSelected(WorkoutControlMode.SIMULATION) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        PrimaryButton(label, onClick, modifier, enabled = enabled)
    } else {
        SecondaryButton(label, onClick, modifier, enabled = enabled)
    }
}

@Composable
private fun ErgReadinessCard(readyState: ActiveWorkoutUiState.Ready) {
    val trainerError = readyState.trainerError
    val (message, color) = when {
        readyState.controlMode == WorkoutControlMode.SIMULATION -> "ERG commands disabled in simulation mode." to ForgeMuted
        readyState.trainerConnectionState != ConnectionState.CONNECTED -> "Connect a trainer or choose simulation before starting." to ForgeOrange
        readyState.connectedDevice?.supportsErg == false -> "Connected trainer does not report ERG support." to ForgeRed
        trainerError != null -> trainerError.message to ForgeOrange
        else -> "Trainer is connected and ERG is ready for the first target." to ForgeGreen
    }
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text("ERG readiness", fontWeight = FontWeight.Bold)
            Text(message, color = color, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun CountdownCard(seconds: Int) {
    AppCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Starting in", color = ForgeMuted, fontWeight = FontWeight.Bold)
            Text("$seconds", color = ForgeBlue, fontSize = 68.sp, lineHeight = 72.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun WorkoutErrorBanner(
    banner: ActiveWorkoutBanner,
    isReconnecting: Boolean,
) {
    val (message, color) = when (banner) {
        ActiveWorkoutBanner.TrainerDisconnected -> {
            val suffix = if (isReconnecting) " Attempting reconnect." else " Using simulation if reconnect fails."
            "Trainer disconnected.$suffix" to ForgeOrange
        }
        ActiveWorkoutBanner.ErgCommandFailed -> "ERG command failed. Ride timing continues." to ForgeRed
        ActiveWorkoutBanner.BackendSyncPending -> "Backend sync pending. Workout is saved locally." to ForgeOrange
        ActiveWorkoutBanner.BluetoothPermissionMissing -> "Bluetooth permission missing. Simulation mode is available." to ForgeRed
    }
    AppCard {
        Text(message, color = color, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
    }
}

private fun deviceCheckMessage(readyState: ActiveWorkoutUiState.Ready): String {
    if (readyState.isCheckingDevice) return "Checking Bluetooth trainer status..."
    if (readyState.trainerError?.type?.name == "PERMISSION_DENIED") {
        return "Bluetooth permission is missing. You can still run this workout in simulation mode."
    }
    return when (readyState.trainerConnectionState) {
        ConnectionState.CONNECTED -> "Trainer connected. ERG will be checked before countdown."
        ConnectionState.CONNECTING -> "Trainer is connecting. Wait for connection or switch to simulation."
        ConnectionState.SCANNING -> "Scanning for a trainer. Simulation remains available."
        ConnectionState.DISCONNECTED -> "No trainer connected. Simulation mode will keep the workout available."
    }
}

@Composable
private fun TrainerConnectionChip(
    connectionState: ConnectionState,
    usingSimulatedMetrics: Boolean,
    controlMode: WorkoutControlMode,
) {
    val (label, color) = when {
        controlMode == WorkoutControlMode.SIMULATION -> "Simulation" to ForgeOrange
        usingSimulatedMetrics -> "Simulated" to ForgeOrange
        connectionState == ConnectionState.CONNECTED -> "Connected" to ForgeGreen
        else -> "Disconnected" to ForgeRed
    }
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SyncStatusChip(status: SyncStatus) {
    val (label, color) = when (status) {
        SyncStatus.Synced -> stringResource(Res.string.workout_sync_synced) to ForgeGreen
        SyncStatus.Syncing -> stringResource(Res.string.workout_sync_syncing) to ForgeBlue
        SyncStatus.PendingSync -> stringResource(Res.string.workout_sync_pending) to ForgeOrange
        SyncStatus.SyncFailed -> stringResource(Res.string.workout_sync_failed) to ForgeRed
    }
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun BigMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(label.uppercase(), color = ForgeMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(value, color = color, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun ProgressLabel(label: String, value: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text(label, Modifier.weight(1f), color = ForgeMuted)
            Text("${(value * 100).toInt()}%", fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier.fillMaxWidth(),
            color = ForgeGreen,
            trackColor = ForgeSurfaceHigh,
        )
    }
}
