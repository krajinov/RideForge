package com.delminiusapps.rideforge.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String,
    val passwordHash: String,
    val name: String,
    val ftp: Int,
    val weightKg: Double,
    val units: String,
    val createdAt: String,
    val enrolledPlanId: String? = null,
)

@Serializable
data class TrainingPlan(
    val id: String,
    val name: String,
    val description: String,
    val durationWeeks: Int,
    val difficulty: String,
    val workoutCount: Int,
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
    val planId: String,
    val name: String,
    val description: String,
    val durationMinutes: Int,
    val difficulty: String,
    val targetZones: List<String>,
    val intervals: List<WorkoutInterval> = emptyList(),
    val weekNumber: Int,
    val dayNumber: Int,
    val workoutType: WorkoutType = WorkoutType.ENDURANCE,
)

@Serializable
data class WorkoutInterval(
    val id: String,
    val workoutId: String,
    val name: String,
    val durationSeconds: Int,
    val targetPowerWatts: Int?,
    val targetFtpPercent: Int?,
    val type: IntervalType,
)

@Serializable
enum class IntervalType {
    warmup,
    work,
    recovery,
    cooldown,
}

@Serializable
data class WorkoutSession(
    val id: String,
    val userId: String,
    val workoutId: String,
    val status: SessionStatus,
    val startedAt: String,
    val completedAt: String? = null,
    val elapsedSeconds: Int = 0,
    val averagePower: Int? = null,
    val normalizedPower: Int? = null,
    val calories: Int? = null,
    val tss: Int? = null,
    val completionPercent: Int? = null,
)

@Serializable
enum class SessionStatus {
    active,
    paused,
    completed,
    abandoned,
}

@Serializable
data class MetricSample(
    val sessionId: String,
    val timestamp: String,
    val currentPower: Int,
    val targetPower: Int,
    val cadence: Int,
    val heartRate: Int,
    val speedKmh: Double,
)

@Serializable
data class Device(
    val id: String,
    val userId: String,
    val name: String,
    val type: String,
    val connectionStatus: String,
    val supportsErg: Boolean,
    val lastConnectedAt: String? = null,
)

data class RefreshTokenRecord(
    val tokenHash: String,
    val userId: String,
    val createdAt: String,
    val revokedAt: String? = null,
)
