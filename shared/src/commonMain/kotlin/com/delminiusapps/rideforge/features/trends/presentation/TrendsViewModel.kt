package com.delminiusapps.rideforge.features.trends.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.domain.usecase.GetAdaptiveDashboardUseCase
import com.delminiusapps.rideforge.domain.usecase.GetAdaptiveTrendsUseCase
import com.delminiusapps.rideforge.models.DailyFatigue
import com.delminiusapps.rideforge.models.FtpHistoryRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrendsViewModel(
    private val getAdaptiveTrendsUseCase: GetAdaptiveTrendsUseCase,
    private val getAdaptiveDashboardUseCase: GetAdaptiveDashboardUseCase
) : ViewModel() {
    private val _state = MutableStateFlow<TrendsUiState>(TrendsUiState.Loading)
    val state: StateFlow<TrendsUiState> = _state.asStateFlow()

    init {
        loadTrends()
    }

    fun refresh() {
        loadTrends()
    }

    private fun loadTrends() {
        _state.update { TrendsUiState.Loading }
        viewModelScope.launch {
            runCatching {
                val trends = getAdaptiveTrendsUseCase()
                val dashboard = runCatching { getAdaptiveDashboardUseCase() }.getOrNull()
                val levels = dashboard?.progressionLevels ?: emptyMap()
                val recommendation = dashboard?.recommendation
                val insights = dashboard?.insights ?: emptyList()
                TrendsData(trends.first, trends.second, levels, recommendation, insights)
            }.onSuccess { data ->
                _state.update {
                    TrendsUiState.Ready(
                        fatigueHistory = data.fatigue,
                        ftpHistory = data.ftp,
                        progressionLevels = data.levels,
                        recommendation = data.recommendation,
                        insights = data.insights
                    )
                }
            }.onFailure {
                _state.update { TrendsUiState.Error }
            }
        }
    }
}

private data class TrendsData(
    val fatigue: List<DailyFatigue>,
    val ftp: List<FtpHistoryRecord>,
    val levels: Map<String, Double>,
    val recommendation: com.delminiusapps.rideforge.models.AdaptiveRecommendation?,
    val insights: List<String>
)

sealed interface TrendsUiState {
    data object Loading : TrendsUiState
    data object Error : TrendsUiState
    data class Ready(
        val fatigueHistory: List<DailyFatigue>,
        val ftpHistory: List<FtpHistoryRecord>,
        val progressionLevels: Map<String, Double>,
        val recommendation: com.delminiusapps.rideforge.models.AdaptiveRecommendation? = null,
        val insights: List<String> = emptyList()
    ) : TrendsUiState
}
