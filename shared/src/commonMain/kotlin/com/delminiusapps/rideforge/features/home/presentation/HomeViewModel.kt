package com.delminiusapps.rideforge.features.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.domain.trainer.TrainerConnectionRepository
import com.delminiusapps.rideforge.domain.usecase.GetHomeDashboardUseCase
import com.delminiusapps.rideforge.domain.usecase.GetAdaptiveDashboardUseCase
import com.delminiusapps.rideforge.domain.usecase.ApproveFtpEstimateUseCase
import com.delminiusapps.rideforge.domain.usecase.DismissFtpEstimateUseCase
import com.delminiusapps.rideforge.domain.usecase.HomeDashboard
import com.delminiusapps.rideforge.models.AdaptiveDashboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getHomeDashboardUseCase: GetHomeDashboardUseCase,
    private val trainerConnectionRepository: TrainerConnectionRepository,
    private val getAdaptiveDashboardUseCase: GetAdaptiveDashboardUseCase,
    private val approveFtpEstimateUseCase: ApproveFtpEstimateUseCase,
    private val dismissFtpEstimateUseCase: DismissFtpEstimateUseCase,
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
            is HomeAction.ApproveFtp -> approveFtp(action.id)
            is HomeAction.DismissFtp -> dismissFtp(action.id)
        }
    }

    private fun loadDashboard(showLoading: Boolean = true) {
        if (showLoading) {
            _state.update { HomeUiState.Loading }
        }
        viewModelScope.launch {
            runCatching {
                val dashboard = getHomeDashboardUseCase()
                val adaptive = runCatching { getAdaptiveDashboardUseCase() }.getOrNull()
                dashboard to adaptive
            }.onSuccess { (dashboard, adaptive) ->
                _state.update { HomeUiState.Ready(dashboard.withCurrentTrainerState(), adaptive) }
            }.onFailure {
                _state.update { HomeUiState.Error }
            }
        }
    }

    private fun approveFtp(id: String) {
        viewModelScope.launch {
            runCatching { approveFtpEstimateUseCase(id) }
                .onSuccess { loadDashboard(showLoading = false) }
        }
    }

    private fun dismissFtp(id: String) {
        viewModelScope.launch {
            runCatching { dismissFtpEstimateUseCase(id) }
                .onSuccess { loadDashboard(showLoading = false) }
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
    data class ApproveFtp(val id: String) : HomeAction
    data class DismissFtp(val id: String) : HomeAction
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Error : HomeUiState
    data class Ready(
        val dashboard: HomeDashboard,
        val adaptive: AdaptiveDashboard?
    ) : HomeUiState
}
