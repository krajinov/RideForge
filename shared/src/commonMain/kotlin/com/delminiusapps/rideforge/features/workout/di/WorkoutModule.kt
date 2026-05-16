package com.delminiusapps.rideforge.features.workout.di

import com.delminiusapps.rideforge.domain.usecase.GetSessionSummaryUseCase
import com.delminiusapps.rideforge.domain.usecase.GetWorkoutUseCase
import com.delminiusapps.rideforge.features.workout.presentation.ActiveWorkoutViewModel
import com.delminiusapps.rideforge.features.workout.presentation.WorkoutCompleteViewModel
import com.delminiusapps.rideforge.features.workout.presentation.WorkoutDetailViewModel
import com.delminiusapps.rideforge.features.workout.presentation.WorkoutListViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val workoutModule = module {
    factoryOf(::GetWorkoutUseCase)
    factoryOf(::GetSessionSummaryUseCase)

    viewModel { WorkoutListViewModel(get()) }
    viewModel { parameters ->
        WorkoutDetailViewModel(get(), get(), workoutId = parameters.get())
    }
    viewModel { parameters ->
        ActiveWorkoutViewModel(
            getWorkoutUseCase = get(),
            getCurrentUserUseCase = get(),
            startWorkoutSessionUseCase = get(),
            pauseWorkoutSessionUseCase = get(),
            resumeWorkoutSessionUseCase = get(),
            completeWorkoutSessionUseCase = get(),
            observeSessionSyncStatusUseCase = get(),
            syncPendingSessionsUseCase = get(),
            metricSampleBatchUploader = get(),
            trainerConnectionRepository = get(),
            trainerControlService = get(),
            workoutLocalStorage = get(),
            workoutId = parameters.get()
        )
    }
    viewModel { parameters ->
        WorkoutCompleteViewModel(get(), get(), get(), sessionId = parameters.get())
    }
}
