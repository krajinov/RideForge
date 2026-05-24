package com.delminiusapps.rideforge.features.workout.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.domain.usecase.GetSessionSummaryUseCase
import com.delminiusapps.rideforge.domain.usecase.GetStravaSyncStatusUseCase
import com.delminiusapps.rideforge.domain.usecase.ObserveSessionSyncStatusUseCase
import com.delminiusapps.rideforge.domain.usecase.SyncPendingSessionsUseCase
import com.delminiusapps.rideforge.domain.usecase.SyncWorkoutToStravaUseCase
import com.delminiusapps.rideforge.models.StravaSyncInfo
import com.delminiusapps.rideforge.models.StravaSyncState
import com.delminiusapps.rideforge.models.SyncStatus
import com.delminiusapps.rideforge.models.WorkoutSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkoutCompleteViewModel(
    private val getSessionSummaryUseCase: GetSessionSummaryUseCase,
    private val syncPendingSessionsUseCase: SyncPendingSessionsUseCase,
    private val observeSessionSyncStatusUseCase: ObserveSessionSyncStatusUseCase,
    private val syncWorkoutToStravaUseCase: SyncWorkoutToStravaUseCase,
    private val getStravaSyncStatusUseCase: GetStravaSyncStatusUseCase,
    private val sessionId: String,
) : ViewModel() {
    private val _state = MutableStateFlow<WorkoutCompleteUiState>(WorkoutCompleteUiState.Loading)
    val state: StateFlow<WorkoutCompleteUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WorkoutCompleteEvent>()
    val events: SharedFlow<WorkoutCompleteEvent> = _events.asSharedFlow()

    private var latestSyncStatus = SyncStatus.Synced
    private var stravaStatusPollingJob: Job? = null

    init {
        observeSyncStatus()
        loadSummary()
    }

    fun onAction(action: WorkoutCompleteAction) {
        when (action) {
            WorkoutCompleteAction.Save -> saveWorkout()
            WorkoutCompleteAction.SyncToStrava -> syncToStrava()
            WorkoutCompleteAction.ViewOnStrava -> viewOnStrava()
        }
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            observeSessionSyncStatusUseCase().collect { status ->
                latestSyncStatus = status
                _state.update { state ->
                    when (state) {
                        is WorkoutCompleteUiState.Ready -> state.copy(syncStatus = status)
                        else -> state
                    }
                }
            }
        }
    }

    private fun loadSummary() {
        viewModelScope.launch {
            runCatching {
                val summary = getSessionSummaryUseCase(sessionId)
                val stravaSessionId = summary.id.ifBlank { sessionId }
                val strava = runCatching { getStravaSyncStatusUseCase(stravaSessionId) }.getOrNull()
                summary to strava
            }.onSuccess { (summary, strava) ->
                _state.update {
                    WorkoutCompleteUiState.Ready(
                        summary = summary,
                        syncStatus = latestSyncStatus,
                        stravaSync = strava,
                    )
                }
                maybePollStravaStatus(summary.id.ifBlank { sessionId }, strava)
            }.onFailure {
                _state.update { WorkoutCompleteUiState.Error }
            }
        }
    }

    private fun saveWorkout() {
        val ready = _state.value as? WorkoutCompleteUiState.Ready ?: return
        if (ready.isSaving) return

        _state.update { state ->
            when (state) {
                is WorkoutCompleteUiState.Ready -> state.copy(isSaving = true, saveFailed = false)
                else -> state
            }
        }

        viewModelScope.launch {
            runCatching {
                syncPendingSessionsUseCase()
                getSessionSummaryUseCase(sessionId)
            }.onSuccess { summary ->
                if (latestSyncStatus == SyncStatus.Synced) {
                    _state.update { state ->
                        when (state) {
                            is WorkoutCompleteUiState.Ready -> state.copy(
                                summary = summary,
                                syncStatus = latestSyncStatus,
                                isSaving = false,
                            )
                            else -> state
                        }
                    }
                    _events.emit(WorkoutCompleteEvent.NavigateHome)
                } else {
                    _state.update { state ->
                        when (state) {
                            is WorkoutCompleteUiState.Ready -> state.copy(
                                summary = summary,
                                syncStatus = latestSyncStatus,
                                isSaving = false,
                                saveFailed = true,
                            )
                            else -> state
                        }
                    }
                }
            }.onFailure {
                _state.update { state ->
                    when (state) {
                        is WorkoutCompleteUiState.Ready -> state.copy(
                            syncStatus = latestSyncStatus,
                            isSaving = false,
                            saveFailed = true,
                        )
                        else -> state
                    }
                }
            }
        }
    }

    private fun syncToStrava() {
        val ready = _state.value as? WorkoutCompleteUiState.Ready ?: return
        if (ready.isStravaSyncing || ready.stravaSync?.state == StravaSyncState.Synced) return

        _state.update { state ->
            when (state) {
                is WorkoutCompleteUiState.Ready -> state.copy(isStravaSyncing = true)
                else -> state
            }
        }
        viewModelScope.launch {
            runCatching {
                syncPendingSessionsUseCase()
                val summary = getSessionSummaryUseCase(sessionId)
                val stravaSessionId = summary.id.ifBlank { sessionId }
                WorkoutStravaSyncResult(summary, stravaSessionId, syncWorkoutToStravaUseCase(stravaSessionId))
            }.onSuccess { result ->
                _state.update { state ->
                    when (state) {
                        is WorkoutCompleteUiState.Ready -> state.copy(
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
                        is WorkoutCompleteUiState.Ready -> state.copy(
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
        val url = (_state.value as? WorkoutCompleteUiState.Ready)?.stravaSync?.activityUrl ?: return
        viewModelScope.launch {
            _events.emit(WorkoutCompleteEvent.OpenUrl(url))
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
                        is WorkoutCompleteUiState.Ready -> state.copy(
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

private data class WorkoutStravaSyncResult(
    val summary: WorkoutSession,
    val sessionId: String,
    val sync: StravaSyncInfo,
)

sealed interface WorkoutCompleteUiState {
    data object Loading : WorkoutCompleteUiState
    data object Error : WorkoutCompleteUiState
    data class Ready(
        val summary: WorkoutSession,
        val syncStatus: SyncStatus,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
        val stravaSync: StravaSyncInfo? = null,
        val isStravaSyncing: Boolean = false,
    ) : WorkoutCompleteUiState
}

sealed interface WorkoutCompleteAction {
    data object Save : WorkoutCompleteAction
    data object SyncToStrava : WorkoutCompleteAction
    data object ViewOnStrava : WorkoutCompleteAction
}

sealed interface WorkoutCompleteEvent {
    data object NavigateHome : WorkoutCompleteEvent
    data class OpenUrl(val url: String) : WorkoutCompleteEvent
}
