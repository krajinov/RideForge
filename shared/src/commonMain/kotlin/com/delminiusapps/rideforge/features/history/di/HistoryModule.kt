package com.delminiusapps.rideforge.features.history.di

import com.delminiusapps.rideforge.features.history.presentation.HistoryDetailViewModel
import com.delminiusapps.rideforge.features.history.presentation.HistoryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val historyModule = module {
    viewModel { HistoryViewModel(get()) }
    viewModel { parameters ->
        HistoryDetailViewModel(
            getSessionSummaryUseCase = get(),
            getSessionMetricsUseCase = get(),
            getWorkoutUseCase = get(),
            getCurrentUserUseCase = get(),
            getRideHistoryUseCase = get(),
            sessionId = parameters.get(),
        )
    }
}
