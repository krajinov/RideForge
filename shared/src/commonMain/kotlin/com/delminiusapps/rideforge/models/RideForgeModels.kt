package com.delminiusapps.rideforge.models

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

typealias ConnectionState = com.delminiusapps.rideforge.domain.trainer.ConnectionState
typealias SmartTrainerDevice = com.delminiusapps.rideforge.domain.trainer.SmartTrainerDevice
typealias TrainerCapability = com.delminiusapps.rideforge.domain.trainer.TrainerCapability
typealias TrainerConnectionStatus = com.delminiusapps.rideforge.domain.trainer.ConnectionState
typealias TrainerControlState = com.delminiusapps.rideforge.domain.trainer.TrainerControlState
typealias TrainerError = com.delminiusapps.rideforge.domain.trainer.TrainerError
typealias TrainerErrorType = com.delminiusapps.rideforge.domain.trainer.TrainerErrorType
typealias TrainerMetrics = com.delminiusapps.rideforge.domain.trainer.TrainerMetrics

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
)

data class AuthSession(
    val tokens: AuthTokens,
    val user: UserProfile,
)

@Serializable
data class UserProfile(
    val name: String,
    val ftpWatts: Int,
    val weightKg: Double,
    val units: String,
    val connectedDevice: String,
    val subscription: String,
    val powerZones: List<PowerZone>,
)

@Serializable
data class PowerZone(
    val id: String,
    val name: String,
    val rangeLabel: String,
    val colorHex: String,
)

@Serializable
data class TrainingPlan(
    val id: String,
    val name: String,
    val durationWeeks: Int,
    val difficulty: String,
    val workoutCount: Int,
    val description: String,
)

@Serializable
enum class WorkoutType {
    RECOVERY,
    ENDURANCE,
    TEMPO,
    SWEET_SPOT,
    THRESHOLD,
    VO2_MAX,
    OVER_UNDER,
    RACE_SIMULATION
}

@Serializable
data class Workout(
    val id: String,
    val name: String,
    val durationMinutes: Int,
    val difficulty: String,
    val description: String,
    val targetZones: List<String>,
    val intervals: List<WorkoutInterval>,
    val planId: String,
    val weekNumber: Int = 1,
    val dayNumber: Int = 1,
    val workoutType: WorkoutType = WorkoutType.ENDURANCE,
)

@Serializable
data class WorkoutInterval(
    val name: String,
    val durationSeconds: Int,
    val targetFtpPercent: Int,
    val zone: String,
) {
    fun targetPower(ftp: Int): Int = (ftp * targetFtpPercent / 100)
}

@Serializable
data class WorkoutSession(
    val workoutId: String,
    val elapsedSeconds: Int,
    val averagePowerWatts: Int,
    val normalizedPowerWatts: Int,
    val calories: Int,
    val tss: Int,
    val completionPercent: Int,
    val id: String = "",
    val workoutName: String = "",
    val completedAtEpochMillis: Long? = null,
    val hasRealTrainerData: Boolean = false,
    val averageSpeedKmh: Double? = null,
    val totalDistanceKm: Double? = null,
)

@Serializable
data class MetricSample(
    val elapsedSeconds: Int,
    val currentPowerWatts: Int,
    val targetPowerWatts: Int,
    val cadenceRpm: Int,
    val heartRateBpm: Int,
    val speedKmh: Double = 0.0,
)

@Serializable
enum class SyncStatus {
    Synced,
    Syncing,
    PendingSync,
    SyncFailed,
}

@Serializable
data class StravaConnectionStatus(
    val connected: Boolean,
    val athleteId: String? = null,
)

@Serializable
enum class StravaSyncState {
    NotSynced,
    Syncing,
    Synced,
    Failed,
}

@Serializable
data class StravaSyncInfo(
    val state: StravaSyncState,
    val activityId: String? = null,
    val activityUrl: String? = null,
    val error: String? = null,
    val canSync: Boolean = false,
    val connected: Boolean = false,
)

@Serializable
data class RideHistoryItem(
    val id: String,
    val workoutName: String,
    val date: LocalDate,
    val durationMinutes: Int,
    val averagePowerWatts: Int,
    val completionPercent: Int,
)

@Serializable
data class WeeklyProgress(
    val completedWorkouts: Int,
    val plannedWorkouts: Int,
)
