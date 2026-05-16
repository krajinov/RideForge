package com.delminiusapps.rideforge.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class PageDto<T>(
    val items: List<T>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val name: String,
    val ftp: Int = 240,
    val weightKg: Double = 78.0,
    val units: String = "metric",
)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val name: String,
    val ftp: Int,
    val weightKg: Double,
    val units: String,
    val createdAt: String,
    val enrolledPlanId: String? = null,
)

@Serializable
data class UpdateProfileRequestDto(
    val ftp: Int,
    val weightKg: Double,
    val units: String,
)

@Serializable
data class TrainingPlanDto(
    val id: String,
    val name: String,
    val description: String,
    val durationWeeks: Int,
    val difficulty: String,
    val workoutCount: Int,
)

@Serializable
data class WorkoutDto(
    val id: String,
    val planId: String,
    val name: String,
    val description: String,
    val durationMinutes: Int,
    val difficulty: String,
    val targetZones: List<String>,
    val intervals: List<WorkoutIntervalDto> = emptyList(),
    val weekNumber: Int? = null,
    val dayNumber: Int? = null,
    val workoutType: String? = null,
)

@Serializable
data class WorkoutIntervalDto(
    val id: String,
    val workoutId: String,
    val name: String,
    val durationSeconds: Int,
    val targetPowerWatts: Int,
    val targetFtpPercent: Int? = null,
    val type: String,
)

@Serializable
data class WorkoutSessionDto(
    val id: String,
    val userId: String,
    val workoutId: String,
    val status: String,
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
data class StartSessionRequestDto(val workoutId: String)

@Serializable
data class CompleteSessionRequestDto(val elapsedSeconds: Int? = null)

@Serializable
data class MetricSampleRequestDto(
    val timestamp: String? = null,
    val currentPower: Int,
    val targetPower: Int,
    val cadence: Int,
    val heartRate: Int,
    val speedKmh: Double,
)

@Serializable
data class MetricSampleDto(
    val sessionId: String,
    val timestamp: String,
    val currentPower: Int,
    val targetPower: Int,
    val cadence: Int,
    val heartRate: Int,
    val speedKmh: Double,
)

@Serializable
data class MetricsAcceptedResponseDto(
    val accepted: Int,
    val latest: MetricSampleDto,
)

@Serializable
data class SessionResponseDto(
    val session: WorkoutSessionDto,
    val workout: WorkoutDto
)
