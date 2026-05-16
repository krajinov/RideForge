package com.delminiusapps.rideforge.features.workout.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.domain.usecase.GetCurrentUserUseCase
import com.delminiusapps.rideforge.domain.usecase.GetWorkoutUseCase
import com.delminiusapps.rideforge.models.Workout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkoutDetailViewModel(
    private val getWorkoutUseCase: GetWorkoutUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val workoutId: String,
) : ViewModel() {
    private val _state = MutableStateFlow<WorkoutDetailUiState>(WorkoutDetailUiState.Loading)
    val state: StateFlow<WorkoutDetailUiState> = _state.asStateFlow()

    init {
        loadWorkout()
    }

    private fun loadWorkout() {
        viewModelScope.launch {
            runCatching {
                val workout = getWorkoutUseCase(workoutId)
                val user = runCatching { getCurrentUserUseCase() }.getOrNull()
                WorkoutDetailUiState.Ready(workout, user?.ftpWatts ?: 240)
            }.onSuccess { readyState ->
                _state.update { readyState }
            }.onFailure {
                // handle error
            }
        }
    }
}

sealed interface WorkoutDetailUiState {
    data object Loading : WorkoutDetailUiState
    data class Ready(val workout: Workout, val userFtp: Int) : WorkoutDetailUiState
}
