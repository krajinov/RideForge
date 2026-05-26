package com.delminiusapps.rideforge.domain.repository

import com.delminiusapps.rideforge.models.AuthSession
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.RideHistoryItem
import com.delminiusapps.rideforge.models.StravaConnectionStatus
import com.delminiusapps.rideforge.models.StravaSyncInfo
import com.delminiusapps.rideforge.models.SyncStatus
import com.delminiusapps.rideforge.models.TrainingPlan
import com.delminiusapps.rideforge.models.UserProfile
import com.delminiusapps.rideforge.models.WeeklyProgress
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutSession
import kotlinx.coroutines.flow.StateFlow

typealias TrainerConnectionRepository = com.delminiusapps.rideforge.domain.trainer.TrainerConnectionRepository

interface AuthRepository {
    suspend fun register(name: String, email: String, password: String): AuthSession
    suspend fun login(email: String, password: String): AuthSession
    suspend fun restoreSession(): AuthSession?
    suspend fun currentUser(): UserProfile
    suspend fun updateProfile(ftpWatts: Int, weightKg: Double, units: String): UserProfile
    suspend fun logout()
}

interface WorkoutRepository {
    suspend fun getWorkouts(): List<Workout>
    suspend fun getWorkoutsForPlan(planId: String): List<Workout>
    suspend fun getRecommendedWorkout(): Workout
    suspend fun getWorkout(id: String): Workout
    suspend fun getWorkoutById(workoutId: String): Workout = getWorkout(workoutId)
}

interface TrainingPlanRepository {
    suspend fun getPlans(): List<TrainingPlan>
}

interface HistoryRepository {
    suspend fun getHistory(): List<RideHistoryItem>
    suspend fun getWeeklyProgress(): WeeklyProgress
    suspend fun getLatestWorkoutSummary(): WorkoutSession
}

interface WorkoutSessionRepository {
    val syncStatus: StateFlow<SyncStatus>

    suspend fun startSession(workoutId: String): WorkoutSession
    suspend fun pauseSession(sessionId: String)
    suspend fun resumeSession(sessionId: String)
    suspend fun addMetric(sessionId: String, sample: MetricSample)
    suspend fun addMetrics(sessionId: String, samples: List<MetricSample>)
    suspend fun completeSession(sessionId: String, elapsedSeconds: Int?, hasRealTrainerData: Boolean = false): WorkoutSession
    suspend fun getSessionMetrics(sessionId: String): List<MetricSample>
    suspend fun getSessionSummary(sessionId: String): WorkoutSession
    suspend fun syncPending()
}

typealias SessionRepository = WorkoutSessionRepository

interface StravaRepository {
    suspend fun getStatus(): StravaConnectionStatus
    suspend fun getConnectUrl(): String
    suspend fun disconnect(): StravaConnectionStatus
    suspend fun syncWorkout(sessionId: String): StravaSyncInfo
    suspend fun getSyncStatus(sessionId: String): StravaSyncInfo
}

interface AdaptiveRepository {
    suspend fun getDashboard(): com.delminiusapps.rideforge.models.AdaptiveDashboard
    suspend fun getTrends(): Pair<List<com.delminiusapps.rideforge.models.DailyFatigue>, List<com.delminiusapps.rideforge.models.FtpHistoryRecord>>
    suspend fun approveFtpEstimate(id: String): Int
    suspend fun dismissFtpEstimate(id: String)
    suspend fun getSessionAnalysis(sessionId: String): com.delminiusapps.rideforge.models.WorkoutAnalysis
}

