package com.delminiusapps.rideforge.features.workout.presentation

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.delminiusapps.rideforge.navigation.AppRoute
import com.delminiusapps.rideforge.presentation.components.EmptyState
import com.delminiusapps.rideforge.presentation.components.ErrorState
import com.delminiusapps.rideforge.presentation.components.LoadingState
import com.delminiusapps.rideforge.presentation.components.ScreenLazyColumn
import com.delminiusapps.rideforge.presentation.components.ScreenHeader
import com.delminiusapps.rideforge.presentation.components.WorkoutCard
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WorkoutListScreen(
    onNavigate: (AppRoute) -> Unit,
    viewModel: WorkoutListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    ScreenLazyColumn {
        item { ScreenHeader("Workouts", "Global structured library") }
        when (val uiState = state) {
            is WorkoutListUiState.Loading -> item { LoadingState("Loading workouts...") }
            is WorkoutListUiState.Ready -> {
                if (uiState.workouts.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No workouts available",
                            message = "The global workout library is empty.",
                        )
                    }
                }
                items(uiState.workouts, key = { it.id }) { workout ->
                    WorkoutCard(
                        workout = workout,
                        onClick = { onNavigate(AppRoute.Workout(workout.id)) },
                    )
                }
            }
            is WorkoutListUiState.Error -> item { ErrorState(message = uiState.message, title = "Unable to load workouts") }
        }
    }
}
