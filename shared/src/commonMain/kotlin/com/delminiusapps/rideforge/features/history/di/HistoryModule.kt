package com.delminiusapps.rideforge.features.history.di

import com.delminiusapps.rideforge.features.history.presentation.HistoryDetailViewModel
import com.delminiusapps.rideforge.features.history.presentation.HistoryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

import com.delminiusapps.rideforge.domain.usecase.GetSessionAnalysisUseCase

val historyModule = module {
    viewModel { HistoryViewModel(get()) }
    viewModel { parameters ->
        HistoryDetailViewModel(
            getSessionSummaryUseCase = get(),
            getSessionMetricsUseCase = get(),
            getWorkoutUseCase = get(),
            getCurrentUserUseCase = get(),
            getRideHistoryUseCase = get(),
            syncPendingSessionsUseCase = get(),
            syncWorkoutToStravaUseCase = get(),
            getStravaSyncStatusUseCase = get(),
            getSessionAnalysisUseCase = get(),
            sessionId = parameters.get(),
        )
    }
}
