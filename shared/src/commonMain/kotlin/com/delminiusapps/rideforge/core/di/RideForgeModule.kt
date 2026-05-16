package com.delminiusapps.rideforge.core.di

import com.delminiusapps.rideforge.data.mock.MockAuthRepository
import com.delminiusapps.rideforge.data.mock.MockHistoryRepository
import com.delminiusapps.rideforge.data.mock.MockTrainingPlanRepository
import com.delminiusapps.rideforge.data.mock.MockWorkoutRepository
import com.delminiusapps.rideforge.core.network.ApiClient
import com.delminiusapps.rideforge.core.network.AppConfig
import com.delminiusapps.rideforge.core.network.DataSourceMonitor
import com.delminiusapps.rideforge.core.network.createRideForgeHttpClient
import com.delminiusapps.rideforge.data.auth.AuthManager
import com.delminiusapps.rideforge.data.auth.AuthSessionStore
import com.delminiusapps.rideforge.data.auth.TokenStore
import com.delminiusapps.rideforge.data.local.WorkoutLocalStorage
import com.delminiusapps.rideforge.data.local.createRideForgeKeyValueStore
import com.delminiusapps.rideforge.data.repository.local.LocalWorkoutSessionRepository
import com.delminiusapps.rideforge.data.repository.sync.LocalPendingSyncQueue
import com.delminiusapps.rideforge.data.repository.sync.MetricSampleBatchUploader
import com.delminiusapps.rideforge.data.repository.sync.SessionSyncManager
import com.delminiusapps.rideforge.data.remote.MockApiClient
import com.delminiusapps.rideforge.domain.repository.AuthRepository
import com.delminiusapps.rideforge.domain.repository.HistoryRepository
import com.delminiusapps.rideforge.domain.repository.SessionRepository
import com.delminiusapps.rideforge.domain.repository.TrainingPlanRepository
import com.delminiusapps.rideforge.domain.repository.WorkoutRepository
import com.delminiusapps.rideforge.data.trainer.DefaultTrainerConnectionRepository
import com.delminiusapps.rideforge.data.trainer.DefaultTrainerControlService
import com.delminiusapps.rideforge.data.trainer.createPlatformBluetoothTrainerClient
import com.delminiusapps.rideforge.domain.trainer.BluetoothTrainerClient
import com.delminiusapps.rideforge.domain.trainer.TrainerConnectionRepository
import com.delminiusapps.rideforge.domain.trainer.TrainerControlService
import com.delminiusapps.rideforge.data.repository.remote.RemoteHistoryRepository
import com.delminiusapps.rideforge.data.repository.remote.RemoteProfileRepository
import com.delminiusapps.rideforge.data.repository.remote.RemoteTrainingPlanRepository
import com.delminiusapps.rideforge.data.repository.remote.RemoteWorkoutSessionRepository
import com.delminiusapps.rideforge.data.repository.remote.RemoteWorkoutRepository
import com.delminiusapps.rideforge.domain.usecase.CompleteWorkoutSessionUseCase
import com.delminiusapps.rideforge.domain.usecase.GetHomeDashboardUseCase
import com.delminiusapps.rideforge.domain.usecase.GetCurrentUserUseCase
import com.delminiusapps.rideforge.domain.usecase.GetLatestWorkoutSummaryUseCase
import com.delminiusapps.rideforge.domain.usecase.GetRecommendedWorkoutUseCase
import com.delminiusapps.rideforge.domain.usecase.GetRideHistoryUseCase
import com.delminiusapps.rideforge.domain.usecase.GetSessionMetricsUseCase
import com.delminiusapps.rideforge.domain.usecase.GetTrainingPlansUseCase
import com.delminiusapps.rideforge.domain.usecase.LoginUseCase
import com.delminiusapps.rideforge.domain.usecase.LogoutUseCase
import com.delminiusapps.rideforge.domain.usecase.ObserveSessionSyncStatusUseCase
import com.delminiusapps.rideforge.domain.usecase.PauseWorkoutSessionUseCase
import com.delminiusapps.rideforge.domain.usecase.RegisterUseCase
import com.delminiusapps.rideforge.domain.usecase.RestoreAuthSessionUseCase
import com.delminiusapps.rideforge.domain.usecase.ResumeWorkoutSessionUseCase
import com.delminiusapps.rideforge.domain.usecase.StartWorkoutSessionUseCase
import com.delminiusapps.rideforge.domain.usecase.SyncPendingSessionsUseCase
import com.delminiusapps.rideforge.domain.usecase.UpdateProfileUseCase
import com.delminiusapps.rideforge.domain.usecase.UploadMetricBatchUseCase
import org.koin.dsl.module

val rideForgeModule = module {
    single<com.delminiusapps.rideforge.data.remote.ApiClient> { MockApiClient() }
    single<BluetoothTrainerClient> { createPlatformBluetoothTrainerClient() }
    single<TrainerConnectionRepository> { DefaultTrainerConnectionRepository(get()) }
    single<TrainerControlService> { DefaultTrainerControlService(get()) }
    single { AppConfig() }
    single { DataSourceMonitor() }
    single { createRideForgeKeyValueStore() }
    single { WorkoutLocalStorage(get()) }
    single { AuthSessionStore() }
    single { ApiClient(createRideForgeHttpClient(), get(), get(), get()) }
    single { MockAuthRepository() }
    single { MockWorkoutRepository() }
    single { MockTrainingPlanRepository() }
    single { MockHistoryRepository() }
    single<AuthRepository> { RemoteProfileRepository(get(), get<MockAuthRepository>(), get()) }
    single<WorkoutRepository> { RemoteWorkoutRepository(get(), get<MockWorkoutRepository>(), get()) }
    single<TrainingPlanRepository> { RemoteTrainingPlanRepository(get(), get<MockTrainingPlanRepository>(), get()) }
    single<HistoryRepository> { com.delminiusapps.rideforge.data.repository.remote.RemoteHistoryRepository(get(), get<MockHistoryRepository>(), get()) }
    single { LocalWorkoutSessionRepository(get()) }
    single { RemoteWorkoutSessionRepository(get(), get()) }
    single { LocalPendingSyncQueue(get()) }
    single<SessionRepository> {
        SessionSyncManager(
            get<RemoteWorkoutSessionRepository>(),
            get<LocalWorkoutSessionRepository>(),
            get(),
            get(),
        )
    }
    factory { GetHomeDashboardUseCase(get(), get(), get(), get()) }
    factory { GetRecommendedWorkoutUseCase(get()) }
    factory { GetTrainingPlansUseCase(get()) }
    factory { GetRideHistoryUseCase(get()) }
    factory { GetLatestWorkoutSummaryUseCase(get()) }
    factory { RegisterUseCase(get(), get()) }
    factory { LoginUseCase(get(), get()) }
    factory { RestoreAuthSessionUseCase(get(), get()) }
    factory { LogoutUseCase(get(), get()) }
    factory { GetCurrentUserUseCase(get()) }
    factory { UpdateProfileUseCase(get()) }
    single { AuthManager(get(), get(), get()) }
    factory { StartWorkoutSessionUseCase(get()) }
    factory { PauseWorkoutSessionUseCase(get()) }
    factory { ResumeWorkoutSessionUseCase(get()) }
    factory { CompleteWorkoutSessionUseCase(get()) }
    factory { UploadMetricBatchUseCase(get()) }
    factory { SyncPendingSessionsUseCase(get()) }
    factory { ObserveSessionSyncStatusUseCase(get()) }
    factory { GetSessionMetricsUseCase(get()) }
    factory { MetricSampleBatchUploader(get(), get()) }
}
