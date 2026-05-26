package com.delminiusapps.rideforge.features.history.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.domain.usecase.GetCurrentUserUseCase
import com.delminiusapps.rideforge.domain.usecase.GetRideHistoryUseCase
import com.delminiusapps.rideforge.domain.usecase.GetSessionMetricsUseCase
import com.delminiusapps.rideforge.domain.usecase.GetSessionSummaryUseCase
import com.delminiusapps.rideforge.domain.usecase.GetStravaSyncStatusUseCase
import com.delminiusapps.rideforge.domain.usecase.GetWorkoutUseCase
import com.delminiusapps.rideforge.domain.usecase.SyncPendingSessionsUseCase
import com.delminiusapps.rideforge.domain.usecase.SyncWorkoutToStravaUseCase
import com.delminiusapps.rideforge.domain.usecase.GetSessionAnalysisUseCase
import com.delminiusapps.rideforge.models.RideHistoryItem
import com.delminiusapps.rideforge.models.StravaSyncInfo
import com.delminiusapps.rideforge.models.StravaSyncState
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val syncPendingSessionsUseCase: SyncPendingSessionsUseCase,
    private val syncWorkoutToStravaUseCase: SyncWorkoutToStravaUseCase,
    private val getStravaSyncStatusUseCase: GetStravaSyncStatusUseCase,
    private val getSessionAnalysisUseCase: GetSessionAnalysisUseCase,
    private val sessionId: String,
) : ViewModel() {
    private val _state = MutableStateFlow<HistoryDetailUiState>(HistoryDetailUiState.Loading)
    val state: StateFlow<HistoryDetailUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<HistoryDetailEvent>()
    val events: SharedFlow<HistoryDetailEvent> = _events.asSharedFlow()

    private var stravaStatusPollingJob: Job? = null

    init {
        loadDetails()
    }

    fun onAction(action: HistoryDetailAction) {
        when (action) {
            HistoryDetailAction.SyncToStrava -> syncToStrava()
            HistoryDetailAction.ViewOnStrava -> viewOnStrava()
        }
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
                val stravaSessionId = summary.id.ifBlank { sessionId }
                val strava = runCatching { getStravaSyncStatusUseCase(stravaSessionId) }.getOrNull()
                val serverAnalysis = runCatching { getSessionAnalysisUseCase(sessionId) }.getOrNull()
                val analysis = buildWorkoutAnalysis(
                    summary = summary,
                    workout = workout,
                    metrics = metrics,
                    userFtp = userFtp,
                    history = history,
                )
                HistoryDetailUiState.Ready(
                    summary = summary,
                    workout = workout,
                    historyItem = historyItem,
                    userFtp = userFtp,
                    analysis = analysis,
                    stravaSync = strava,
                    serverAnalysis = serverAnalysis
                )
            }.onSuccess { ready ->
                _state.update { ready }
                maybePollStravaStatus(ready.summary.id.ifBlank { sessionId }, ready.stravaSync)
            }.onFailure {
                _state.update { HistoryDetailUiState.Error }
            }
        }
    }

    private fun syncToStrava() {
        val ready = _state.value as? HistoryDetailUiState.Ready ?: return
        if (ready.isStravaSyncing || ready.stravaSync?.state == StravaSyncState.Synced) return
        _state.update { state ->
            when (state) {
                is HistoryDetailUiState.Ready -> state.copy(isStravaSyncing = true)
                else -> state
            }
        }
        viewModelScope.launch {
            runCatching {
                syncPendingSessionsUseCase()
                val summary = getSessionSummaryUseCase(sessionId)
                val stravaSessionId = summary.id.ifBlank { sessionId }
                StravaSyncResult(summary, stravaSessionId, syncWorkoutToStravaUseCase(stravaSessionId))
            }.onSuccess { result ->
                _state.update { state ->
                    when (state) {
                        is HistoryDetailUiState.Ready -> state.copy(
                            summary = result.summary,
                            stravaSync = result.sync,
                            isStravaSyncing = false,
                        )
                        else -> state
                    }
                }
                maybePollStravaStatus(result.sessionId, result.sync)
            }.onFailure {
                _state.update { state ->
                    when (state) {
                        is HistoryDetailUiState.Ready -> state.copy(
                            stravaSync = (state.stravaSync ?: StravaSyncInfo(StravaSyncState.Failed)).copy(
                                state = StravaSyncState.Failed,
                                error = "Could not sync this workout to Strava.",
                            ),
                            isStravaSyncing = false,
                        )
                        else -> state
                    }
                }
            }
        }
    }

    private fun viewOnStrava() {
        val url = (_state.value as? HistoryDetailUiState.Ready)?.stravaSync?.activityUrl ?: return
        viewModelScope.launch {
            _events.emit(HistoryDetailEvent.OpenUrl(url))
        }
    }

    private fun maybePollStravaStatus(sessionId: String, sync: StravaSyncInfo?) {
        stravaStatusPollingJob?.cancel()
        stravaStatusPollingJob = null
        if (sync?.state != StravaSyncState.Syncing) return
        stravaStatusPollingJob = viewModelScope.launch {
            while (true) {
                delay(StravaStatusPollIntervalMillis)
                val latest = runCatching { getStravaSyncStatusUseCase(sessionId) }.getOrNull() ?: continue
                _state.update { state ->
                    when (state) {
                        is HistoryDetailUiState.Ready -> state.copy(
                            stravaSync = latest,
                            isStravaSyncing = false,
                        )
                        else -> state
                    }
                }
                if (latest.state != StravaSyncState.Syncing) {
                    stravaStatusPollingJob = null
                    return@launch
                }
            }
        }
    }

    private companion object {
        const val StravaStatusPollIntervalMillis = 5_000L
    }
}

private data class StravaSyncResult(
    val summary: WorkoutSession,
    val sessionId: String,
    val sync: StravaSyncInfo,
)

sealed interface HistoryDetailUiState {
    data object Loading : HistoryDetailUiState
    data object Error : HistoryDetailUiState
    data class Ready(
        val summary: WorkoutSession,
        val workout: Workout?,
        val historyItem: RideHistoryItem?,
        val userFtp: Int,
        val analysis: WorkoutAnalysis,
        val stravaSync: StravaSyncInfo? = null,
        val isStravaSyncing: Boolean = false,
        val serverAnalysis: com.delminiusapps.rideforge.models.WorkoutAnalysis? = null,
    ) : HistoryDetailUiState
}

sealed interface HistoryDetailAction {
    data object SyncToStrava : HistoryDetailAction
    data object ViewOnStrava : HistoryDetailAction
}

sealed interface HistoryDetailEvent {
    data class OpenUrl(val url: String) : HistoryDetailEvent
}
