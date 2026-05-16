package com.delminiusapps.rideforge.features.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.domain.usecase.GetHomeDashboardUseCase
import com.delminiusapps.rideforge.domain.usecase.HomeDashboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getHomeDashboardUseCase: GetHomeDashboardUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        loadDashboard()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> loadDashboard()
        }
    }

    private fun loadDashboard() {
        _state.update { HomeUiState.Loading }
        viewModelScope.launch {
            runCatching {
                getHomeDashboardUseCase()
            }.onSuccess { dashboard ->
                _state.update { HomeUiState.Ready(dashboard) }
            }.onFailure {
                _state.update { HomeUiState.Error }
            }
        }
    }
}

sealed interface HomeAction {
    data object Refresh : HomeAction
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Error : HomeUiState
    data class Ready(val dashboard: HomeDashboard) : HomeUiState
}
