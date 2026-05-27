package com.delminiusapps.rideforge.features.plans.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.domain.repository.TrainingPlanRepository
import com.delminiusapps.rideforge.domain.usecase.GetPlanWorkoutsUseCase
import com.delminiusapps.rideforge.domain.usecase.GetTrainingPlansUseCase
import com.delminiusapps.rideforge.models.TrainingPlan
import com.delminiusapps.rideforge.models.Workout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlanWorkoutsViewModel(
    private val getPlanWorkoutsUseCase: GetPlanWorkoutsUseCase,
    private val getTrainingPlansUseCase: GetTrainingPlansUseCase,
    private val repository: TrainingPlanRepository,
    private val planId: String,
) : ViewModel() {
    private val _state = MutableStateFlow<PlanWorkoutsUiState>(PlanWorkoutsUiState.Loading)
    val state: StateFlow<PlanWorkoutsUiState> = _state.asStateFlow()

    init {
        loadWorkouts()
    }

    fun loadWorkouts() {
        viewModelScope.launch {
            runCatching {
                val plan = runCatching {
                    getTrainingPlansUseCase().firstOrNull { it.id == planId }
                }.getOrNull()
                val workouts = getPlanWorkoutsUseCase(planId)
                    .sortedWith(compareBy<Workout> { it.weekNumber }.thenBy { it.dayNumber })
                val isJoined = repository.getJoinedPlans().contains(planId)
                val completedWorkoutIds = repository.getPlanCompletedWorkoutIds(planId)
                PlanWorkoutsUiState.Ready(
                    plan = plan,
                    workoutsByWeek = workouts.groupBy { it.weekNumber },
                    isJoined = isJoined,
                    completedWorkoutIds = completedWorkoutIds,
                )
            }.onSuccess { ready ->
                _state.update { ready }
            }.onFailure {
                _state.update {
                    PlanWorkoutsUiState.Ready(
                        plan = null,
                        workoutsByWeek = emptyMap(),
                        isJoined = false,
                        completedWorkoutIds = emptyList(),
                    )
                }
            }
        }
    }

    fun joinPlan() {
        viewModelScope.launch {
            runCatching {
                repository.joinPlan(planId)
            }.onSuccess {
                loadWorkouts()
            }
        }
    }

    fun leavePlan() {
        viewModelScope.launch {
            runCatching {
                repository.leavePlan(planId)
            }.onSuccess {
                loadWorkouts()
            }
        }
    }
}

sealed interface PlanWorkoutsUiState {
    data object Loading : PlanWorkoutsUiState
    data class Ready(
        val plan: TrainingPlan?,
        val workoutsByWeek: Map<Int, List<Workout>>,
        val isJoined: Boolean,
        val completedWorkoutIds: List<String>,
    ) : PlanWorkoutsUiState
}
