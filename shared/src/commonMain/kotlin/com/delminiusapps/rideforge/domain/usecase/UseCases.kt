package com.delminiusapps.rideforge.domain.usecase

import com.delminiusapps.rideforge.data.auth.AuthSessionStore
import com.delminiusapps.rideforge.domain.repository.AuthRepository
import com.delminiusapps.rideforge.domain.repository.HistoryRepository
import com.delminiusapps.rideforge.domain.repository.SessionRepository
import com.delminiusapps.rideforge.domain.repository.TrainerConnectionRepository
import com.delminiusapps.rideforge.domain.repository.TrainingPlanRepository
import com.delminiusapps.rideforge.domain.repository.WorkoutRepository
import com.delminiusapps.rideforge.models.MetricSample

class GetHomeDashboardUseCase(
    private val authRepository: AuthRepository,
    private val workoutRepository: WorkoutRepository,
    private val historyRepository: HistoryRepository,
    private val trainerConnectionRepository: TrainerConnectionRepository,
) {
    suspend operator fun invoke(): HomeDashboard {
        return HomeDashboard(
            user = authRepository.currentUser(),
            workout = workoutRepository.getRecommendedWorkout(),
            progress = historyRepository.getWeeklyProgress(),
            trainerStatus = trainerConnectionRepository.connectionState.value,
            trainerDevice = trainerConnectionRepository.connectedDevice.value,
        )
    }
}

class GetTrainingPlansUseCase(private val repository: TrainingPlanRepository) {
    suspend operator fun invoke() = repository.getPlans()
}

class GetPlanWorkoutsUseCase(private val repository: WorkoutRepository) {
    suspend operator fun invoke(planId: String) = repository.getWorkoutsForPlan(planId)
}

class GetRideHistoryUseCase(private val repository: HistoryRepository) {
    suspend operator fun invoke() = repository.getHistory()
}

class GetLatestWorkoutSummaryUseCase(private val repository: HistoryRepository) {
    suspend operator fun invoke() = repository.getLatestWorkoutSummary()
}

class GetRecommendedWorkoutUseCase(private val repository: WorkoutRepository) {
    suspend operator fun invoke() = repository.getRecommendedWorkout()
}

class GetWorkoutUseCase(private val repository: WorkoutRepository) {
    suspend operator fun invoke(id: String) = if (id.isBlank()) {
        repository.getRecommendedWorkout()
    } else {
        repository.getWorkoutById(id)
    }
}

class RegisterUseCase(
    private val repository: AuthRepository,
    private val authSessionStore: AuthSessionStore,
) {
    suspend operator fun invoke(name: String, email: String, password: String) {
        repository.register(name, email, password)
        authSessionStore.setAuthenticated(true)
    }
}

class LoginUseCase(
    private val repository: AuthRepository,
    private val authSessionStore: AuthSessionStore,
) {
    suspend operator fun invoke(email: String, password: String) {
        repository.login(email, password)
        authSessionStore.setAuthenticated(true)
    }
}

class RestoreAuthSessionUseCase(
    private val repository: AuthRepository,
    private val authSessionStore: AuthSessionStore,
) {
    suspend operator fun invoke(): Boolean {
        val session = repository.restoreSession()
        authSessionStore.setAuthenticated(session != null)
        return session != null
    }
}

class LogoutUseCase(
    private val repository: AuthRepository,
    private val authSessionStore: AuthSessionStore,
) {
    suspend operator fun invoke() {
        runCatching { repository.logout() }
        authSessionStore.setAuthenticated(false)
    }
}

class GetCurrentUserUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.currentUser()
}

class UpdateProfileUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(ftpWatts: Int, weightKg: Double, units: String) =
        repository.updateProfile(ftpWatts, weightKg, units)
}

class StartWorkoutSessionUseCase(private val repository: SessionRepository) {
    suspend operator fun invoke(workoutId: String) = repository.startSession(workoutId)
}

class PauseWorkoutSessionUseCase(private val repository: SessionRepository) {
    suspend operator fun invoke(sessionId: String) = repository.pauseSession(sessionId)
}

class ResumeWorkoutSessionUseCase(private val repository: SessionRepository) {
    suspend operator fun invoke(sessionId: String) = repository.resumeSession(sessionId)
}

class CompleteWorkoutSessionUseCase(private val repository: SessionRepository) {
    suspend operator fun invoke(sessionId: String, elapsedSeconds: Int?) = repository.completeSession(sessionId, elapsedSeconds)
}

class UploadMetricBatchUseCase(private val repository: SessionRepository) {
    suspend operator fun invoke(sessionId: String, samples: List<MetricSample>) = repository.addMetrics(sessionId, samples)
}

class SyncPendingSessionsUseCase(private val repository: SessionRepository) {
    suspend operator fun invoke() = repository.syncPending()
}

class ObserveSessionSyncStatusUseCase(private val repository: SessionRepository) {
    operator fun invoke() = repository.syncStatus
}

class GetSessionSummaryUseCase(private val repository: SessionRepository) {
    suspend operator fun invoke(id: String) = repository.getSessionSummary(id)
}

class GetSessionMetricsUseCase(private val repository: SessionRepository) {
    suspend operator fun invoke(id: String) = repository.getSessionMetrics(id)
}

data class HomeDashboard(
    val user: com.delminiusapps.rideforge.models.UserProfile,
    val workout: com.delminiusapps.rideforge.models.Workout,
    val progress: com.delminiusapps.rideforge.models.WeeklyProgress,
    val trainerStatus: com.delminiusapps.rideforge.models.ConnectionState,
    val trainerDevice: com.delminiusapps.rideforge.models.SmartTrainerDevice?,
)
