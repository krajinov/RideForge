package com.delminiusapps.rideforge.features.workout.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import com.delminiusapps.rideforge.platform.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.delminiusapps.rideforge.navigation.AppRoute
import com.delminiusapps.rideforge.presentation.components.AppCard
import com.delminiusapps.rideforge.presentation.components.IntervalTimeline
import com.delminiusapps.rideforge.presentation.components.LoadingState
import com.delminiusapps.rideforge.presentation.components.PowerZoneChip
import com.delminiusapps.rideforge.presentation.components.PrimaryButton
import com.delminiusapps.rideforge.presentation.components.ScreenHeader
import com.delminiusapps.rideforge.presentation.components.ScreenLazyColumn
import com.delminiusapps.rideforge.presentation.components.SmallPill
import com.delminiusapps.rideforge.theme.ForgeMuted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutDetailScreen(
    workoutId: String,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
    viewModel: WorkoutDetailViewModel = koinViewModel(key = workoutId) { parametersOf(workoutId) },
) {
    val state by viewModel.state.collectAsState()

    ScreenLazyColumn {
        when (val uiState = state) {
            is WorkoutDetailUiState.Loading -> {
                item { ScreenHeader("Workout", "Loading workout...", onBack = onBack) }
                item { LoadingState("Loading intervals...") }
            }
            is WorkoutDetailUiState.Ready -> {
                val loadedWorkout = uiState.workout
                item {
                    ScreenHeader(
                        loadedWorkout.name,
                        "${loadedWorkout.durationMinutes} min • ${loadedWorkout.difficulty}",
                        onBack = onBack,
                    )
                }
                item { IntervalTimeline(loadedWorkout.intervals) }
                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Workout description", fontWeight = FontWeight.Bold)
                            Text(loadedWorkout.description, color = ForgeMuted, lineHeight = 22.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                loadedWorkout.targetZones.forEach { PowerZoneChip(it) }
                            }
                        }
                    }
                }
                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Intervals", fontWeight = FontWeight.Bold)
                            loadedWorkout.intervals.forEach { interval ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(interval.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    SmallPill("${interval.durationSeconds / 60} min")
                                    Text("${interval.targetFtpPercent}% (${interval.targetPower(uiState.userFtp)}W)", color = ForgeMuted)
                                }
                            }
                        }
                    }
                }
                item {
                    PrimaryButton("Start Workout", { onNavigate(AppRoute.ActiveWorkout(id = loadedWorkout.id)) }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
