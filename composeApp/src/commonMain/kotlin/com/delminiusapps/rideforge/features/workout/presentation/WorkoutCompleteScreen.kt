package com.delminiusapps.rideforge.features.workout.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.StravaSyncInfo
import com.delminiusapps.rideforge.models.StravaSyncState
import com.delminiusapps.rideforge.models.WorkoutSession
import com.delminiusapps.rideforge.navigation.AppRoute
import com.delminiusapps.rideforge.presentation.components.AppCard
import com.delminiusapps.rideforge.presentation.components.ErrorState
import com.delminiusapps.rideforge.presentation.components.LoadingState
import com.delminiusapps.rideforge.presentation.components.MetricCard
import com.delminiusapps.rideforge.presentation.components.PrimaryButton
import com.delminiusapps.rideforge.presentation.components.ProgressGraph
import com.delminiusapps.rideforge.presentation.components.ScreenHeader
import com.delminiusapps.rideforge.presentation.components.ScreenLazyColumn
import com.delminiusapps.rideforge.presentation.components.SecondaryButton
import com.delminiusapps.rideforge.theme.ForgeGreen
import com.delminiusapps.rideforge.theme.ForgeMuted
import com.delminiusapps.rideforge.theme.ForgeOrange
import com.delminiusapps.rideforge.theme.ForgeStrava
import com.delminiusapps.rideforge.utils.formatDuration
import org.jetbrains.compose.resources.stringResource
import rideforge.composeapp.generated.resources.Res
import rideforge.composeapp.generated.resources.workout_complete_average_power
import rideforge.composeapp.generated.resources.workout_complete_calories
import rideforge.composeapp.generated.resources.workout_complete_completion
import rideforge.composeapp.generated.resources.workout_complete_duration
import rideforge.composeapp.generated.resources.workout_complete_load_error
import rideforge.composeapp.generated.resources.workout_complete_loading_summary
import rideforge.composeapp.generated.resources.workout_complete_loading_workout
import rideforge.composeapp.generated.resources.workout_complete_normalized_power
import rideforge.composeapp.generated.resources.workout_complete_power_curve
import rideforge.composeapp.generated.resources.workout_complete_return_home
import rideforge.composeapp.generated.resources.workout_complete_save
import rideforge.composeapp.generated.resources.workout_complete_save_failed
import rideforge.composeapp.generated.resources.workout_complete_saving
import rideforge.composeapp.generated.resources.workout_complete_saving_message
import rideforge.composeapp.generated.resources.workout_complete_share
import rideforge.composeapp.generated.resources.workout_complete_summary_format
import rideforge.composeapp.generated.resources.workout_complete_title
import rideforge.composeapp.generated.resources.workout_complete_tss

@Composable
fun WorkoutCompleteScreen(
    sessionId: String,
    onNavigate: (AppRoute) -> Unit,
    viewModel: WorkoutCompleteViewModel = koinViewModel(key = sessionId) { parametersOf(sessionId) },
) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WorkoutCompleteEvent.NavigateHome -> onNavigate(AppRoute.Home)
                is WorkoutCompleteEvent.OpenUrl -> runCatching { uriHandler.openUri(event.url) }
            }
        }
    }

    ScreenLazyColumn {
        when (val uiState = state) {
            is WorkoutCompleteUiState.Loading -> {
                item {
                    ScreenHeader(
                        stringResource(Res.string.workout_complete_title),
                        stringResource(Res.string.workout_complete_loading_summary),
                    )
                }
                item { LoadingState(stringResource(Res.string.workout_complete_loading_workout)) }
            }
            is WorkoutCompleteUiState.Error -> {
                item { ScreenHeader(stringResource(Res.string.workout_complete_title)) }
                item { ErrorState(message = stringResource(Res.string.workout_complete_load_error), title = "Unable to load summary") }
                item { PrimaryButton(stringResource(Res.string.workout_complete_return_home), { onNavigate(AppRoute.Home) }, Modifier.fillMaxWidth()) }
            }
            is WorkoutCompleteUiState.Ready -> {
                val loadedSummary = uiState.summary
                val workoutName = loadedSummary.workoutName.ifBlank { loadedSummary.workoutId }
                val curve = completedPowerCurve(loadedSummary)

                item {
                    ScreenHeader(
                        stringResource(Res.string.workout_complete_title),
                        stringResource(Res.string.workout_complete_summary_format, workoutName),
                    )
                }
                if (uiState.isSaving) {
                    item {
                        AppCard {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(stringResource(Res.string.workout_complete_saving_message), color = ForgeMuted, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                if (uiState.saveFailed) {
                    item {
                        AppCard {
                            Text(stringResource(Res.string.workout_complete_save_failed), color = ForgeOrange, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricCard(stringResource(Res.string.workout_complete_duration), formatDuration(loadedSummary.elapsedSeconds), modifier = Modifier.weight(1f))
                        MetricCard(stringResource(Res.string.workout_complete_average_power), "${loadedSummary.averagePowerWatts} W", ForgeGreen, Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricCard(stringResource(Res.string.workout_complete_normalized_power), "${loadedSummary.normalizedPowerWatts} W", ForgeOrange, Modifier.weight(1f))
                        MetricCard(stringResource(Res.string.workout_complete_calories), "${loadedSummary.calories}", modifier = Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricCard(stringResource(Res.string.workout_complete_tss), "${loadedSummary.tss}", modifier = Modifier.weight(1f))
                        MetricCard(stringResource(Res.string.workout_complete_completion), "${loadedSummary.completionPercent}%", ForgeGreen, Modifier.weight(1f))
                    }
                }
                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(Res.string.workout_complete_power_curve), fontWeight = FontWeight.Bold)
                            ProgressGraph(curve)
                        }
                    }
                }
                item {
                    StravaSyncCard(
                        sync = uiState.stravaSync,
                        hasRealTrainerData = loadedSummary.hasRealTrainerData,
                        isSyncing = uiState.isStravaSyncing,
                        onSync = { viewModel.onAction(WorkoutCompleteAction.SyncToStrava) },
                        onView = { viewModel.onAction(WorkoutCompleteAction.ViewOnStrava) },
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton(
                            text = if (uiState.isSaving) {
                                stringResource(Res.string.workout_complete_saving)
                            } else {
                                stringResource(Res.string.workout_complete_save)
                            },
                            onClick = { viewModel.onAction(WorkoutCompleteAction.Save) },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isSaving,
                        )
                        SecondaryButton(
                            stringResource(Res.string.workout_complete_share),
                            {},
                            Modifier.weight(1f),
                            enabled = !uiState.isSaving,
                        )
                    }
                }
                item {
                    PrimaryButton(
                        stringResource(Res.string.workout_complete_return_home),
                        { onNavigate(AppRoute.Home) },
                        Modifier.fillMaxWidth(),
                        enabled = !uiState.isSaving,
                    )
                }
            }
        }
    }
}

@Composable
private fun StravaSyncCard(
    sync: StravaSyncInfo?,
    hasRealTrainerData: Boolean,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onView: () -> Unit,
) {
    val state = sync?.state ?: StravaSyncState.NotSynced
    val connected = sync?.connected == true
    val canSync = sync?.canSync ?: hasRealTrainerData
    val isProcessing = isSyncing || state == StravaSyncState.Syncing
    val statusText = when {
        state == StravaSyncState.Synced -> "Synced to Strava"
        isProcessing -> "Strava is processing this upload."
        state == StravaSyncState.Failed -> sync?.error ?: "Strava sync failed."
        !hasRealTrainerData -> "Only workouts recorded from a real trainer can be uploaded."
        !connected -> "Connect Strava in Profile before syncing."
        else -> "Ready to upload as an indoor virtual ride."
    }
    val buttonText = when {
        state == StravaSyncState.Synced && sync?.activityUrl != null -> "View on Strava"
        isProcessing -> "Syncing..."
        !hasRealTrainerData -> "Trainer data required"
        state == StravaSyncState.Failed -> "Retry Strava Sync"
        else -> "Sync to Strava"
    }
    val enabled = when {
        state == StravaSyncState.Synced -> sync?.activityUrl != null
        isProcessing -> false
        else -> connected && canSync
    }

    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Strava", fontWeight = FontWeight.Bold, color = ForgeStrava)
            Text(statusText, color = ForgeMuted)
            if (state == StravaSyncState.Synced && sync?.activityUrl != null) {
                PrimaryButton(buttonText, onView, Modifier.fillMaxWidth(), enabled = enabled)
            } else {
                SecondaryButton(buttonText, onSync, Modifier.fillMaxWidth(), enabled = enabled)
            }
        }
    }
}

private fun completedPowerCurve(summary: WorkoutSession): List<MetricSample> {
    val average = summary.averagePowerWatts.coerceAtLeast(90)
    val normalized = summary.normalizedPowerWatts.coerceAtLeast(average)
    val durationStep = (summary.elapsedSeconds / 13).coerceAtLeast(1)
    val pattern = listOf(0.58f, 0.62f, 0.68f, 1.30f, 1.34f, 0.56f, 0.60f, 1.32f, 1.28f, 0.57f, 0.63f, 1.31f, 1.26f, 0.50f)
    return pattern.mapIndexed { index, multiplier ->
        val power = (average * multiplier).toInt().coerceAtLeast(80)
        val target = if (power >= normalized) normalized + 54 else (average * 0.56f).toInt().coerceAtLeast(90)
        MetricSample(
            elapsedSeconds = index * durationStep,
            currentPowerWatts = power,
            targetPowerWatts = target,
            cadenceRpm = 86 + index % 8,
            heartRateBpm = 118 + (index * 3),
        )
    }
}
