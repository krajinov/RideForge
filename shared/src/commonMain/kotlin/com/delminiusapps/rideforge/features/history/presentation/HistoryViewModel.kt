package com.delminiusapps.rideforge.features.history.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.domain.usecase.GetRideHistoryUseCase
import com.delminiusapps.rideforge.models.RideHistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val getRideHistoryUseCase: GetRideHistoryUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        loadHistory()
    }

    fun onAction(action: HistoryAction) {
        when (action) {
            HistoryAction.Refresh -> loadHistory()
        }
    }

    private fun loadHistory() {
        _state.update { HistoryUiState.Loading }
        viewModelScope.launch {
            runCatching {
                getRideHistoryUseCase()
            }.onSuccess { history ->
                _state.update { HistoryUiState.Ready(history) }
            }.onFailure {
                _state.update { HistoryUiState.Error }
            }
        }
    }
}

sealed interface HistoryAction {
    data object Refresh : HistoryAction
}

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data object Error : HistoryUiState
    data class Ready(val history: List<RideHistoryItem>) : HistoryUiState
}
