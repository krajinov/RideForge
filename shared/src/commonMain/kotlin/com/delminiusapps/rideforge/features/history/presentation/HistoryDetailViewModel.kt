package com.delminiusapps.rideforge.features.history.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.domain.usecase.GetCurrentUserUseCase
import com.delminiusapps.rideforge.domain.usecase.GetRideHistoryUseCase
import com.delminiusapps.rideforge.domain.usecase.GetSessionMetricsUseCase
import com.delminiusapps.rideforge.domain.usecase.GetSessionSummaryUseCase
import com.delminiusapps.rideforge.domain.usecase.GetWorkoutUseCase
import com.delminiusapps.rideforge.models.RideHistoryItem
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryDetailViewModel(
    private val getSessionSummaryUseCase: GetSessionSummaryUseCase,
    private val getSessionMetricsUseCase: GetSessionMetricsUseCase,
    private val getWorkoutUseCase: GetWorkoutUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getRideHistoryUseCase: GetRideHistoryUseCase,
    private val sessionId: String,
) : ViewModel() {
    private val _state = MutableStateFlow<HistoryDetailUiState>(HistoryDetailUiState.Loading)
    val state: StateFlow<HistoryDetailUiState> = _state.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        viewModelScope.launch {
            runCatching {
                val summary = getSessionSummaryUseCase(sessionId)
                val metrics = runCatching { getSessionMetricsUseCase(sessionId) }.getOrDefault(emptyList())
                val workout = runCatching { getWorkoutUseCase(summary.workoutId) }.getOrNull()
                val userFtp = runCatching { getCurrentUserUseCase().ftpWatts }.getOrDefault(240)
                val history = runCatching { getRideHistoryUseCase() }.getOrDefault(emptyList())
                val historyItem = history.firstOrNull { it.id == sessionId }
                val analysis = buildWorkoutAnalysis(
                    summary = summary,
                    workout = workout,
                    metrics = metrics,
                    userFtp = userFtp,
                    history = history,
                )
                HistoryDetailUiState.Ready(summary, workout, historyItem, userFtp, analysis)
            }.onSuccess { ready ->
                _state.update { ready }
            }.onFailure {
                _state.update { HistoryDetailUiState.Error }
            }
        }
    }
}

sealed interface HistoryDetailUiState {
    data object Loading : HistoryDetailUiState
    data object Error : HistoryDetailUiState
    data class Ready(
        val summary: WorkoutSession,
        val workout: Workout?,
        val historyItem: RideHistoryItem?,
        val userFtp: Int,
        val analysis: WorkoutAnalysis,
    ) : HistoryDetailUiState
}
