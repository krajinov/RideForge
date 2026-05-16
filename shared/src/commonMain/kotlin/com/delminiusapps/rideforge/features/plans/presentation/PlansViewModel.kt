package com.delminiusapps.rideforge.features.plans.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.domain.usecase.GetTrainingPlansUseCase
import com.delminiusapps.rideforge.models.TrainingPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlansViewModel(
    private val getTrainingPlansUseCase: GetTrainingPlansUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<PlansUiState>(PlansUiState.Loading)
    val state: StateFlow<PlansUiState> = _state.asStateFlow()

    init {
        loadPlans()
    }

    private fun loadPlans() {
        viewModelScope.launch {
            runCatching {
                getTrainingPlansUseCase()
            }.onSuccess { plans ->
                _state.update { PlansUiState.Ready(plans) }
            }.onFailure {
                // handle error
                _state.update { PlansUiState.Ready(emptyList()) }
            }
        }
    }
}

sealed interface PlansUiState {
    data object Loading : PlansUiState
    data class Ready(val plans: List<TrainingPlan>) : PlansUiState
}
