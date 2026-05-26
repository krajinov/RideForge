package com.delminiusapps.rideforge.features.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.domain.trainer.TrainerConnectionRepository
import com.delminiusapps.rideforge.domain.usecase.GetHomeDashboardUseCase
import com.delminiusapps.rideforge.domain.usecase.HomeDashboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getHomeDashboardUseCase: GetHomeDashboardUseCase,
    private val trainerConnectionRepository: TrainerConnectionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        observeTrainerConnection()
        loadDashboard()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> loadDashboard()
            HomeAction.ScreenVisible -> loadDashboard(showLoading = _state.value !is HomeUiState.Ready)
        }
    }

    private fun loadDashboard(showLoading: Boolean = true) {
        if (showLoading) {
            _state.update { HomeUiState.Loading }
        }
        viewModelScope.launch {
            runCatching {
                getHomeDashboardUseCase()
            }.onSuccess { dashboard ->
                _state.update { HomeUiState.Ready(dashboard.withCurrentTrainerState()) }
            }.onFailure {
                _state.update { HomeUiState.Error }
            }
        }
    }

    private fun observeTrainerConnection() {
        viewModelScope.launch {
            combine(
                trainerConnectionRepository.connectionState,
                trainerConnectionRepository.connectedDevice,
            ) { status, device -> status to device }
                .collect { (status, device) ->
                    _state.update { current ->
                        if (current is HomeUiState.Ready) {
                            current.copy(
                                dashboard = current.dashboard.copy(
                                    trainerStatus = status,
                                    trainerDevice = device,
                                ),
                            )
                        } else {
                            current
                        }
                    }
                }
        }
    }

    private fun HomeDashboard.withCurrentTrainerState(): HomeDashboard = copy(
        trainerStatus = trainerConnectionRepository.connectionState.value,
        trainerDevice = trainerConnectionRepository.connectedDevice.value,
    )
}

sealed interface HomeAction {
    data object Refresh : HomeAction
    data object ScreenVisible : HomeAction
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Error : HomeUiState
    data class Ready(val dashboard: HomeDashboard) : HomeUiState
}
