package com.delminiusapps.rideforge.features.workout.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.domain.repository.WorkoutRepository
import com.delminiusapps.rideforge.models.Workout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkoutListViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {
    private val _state = MutableStateFlow<WorkoutListUiState>(WorkoutListUiState.Loading)
    val state: StateFlow<WorkoutListUiState> = _state.asStateFlow()

    init {
        loadWorkouts()
    }

    fun loadWorkouts() {
        viewModelScope.launch {
            _state.update { WorkoutListUiState.Loading }
            runCatching {
                repository.getWorkouts()
            }.onSuccess { workouts ->
                _state.update { WorkoutListUiState.Ready(workouts) }
            }.onFailure { error ->
                _state.update { WorkoutListUiState.Error(error.message ?: "Unknown error") }
            }
        }
    }
}

sealed interface WorkoutListUiState {
    data object Loading : WorkoutListUiState
    data class Ready(val workouts: List<Workout>) : WorkoutListUiState
    data class Error(val message: String) : WorkoutListUiState
}
