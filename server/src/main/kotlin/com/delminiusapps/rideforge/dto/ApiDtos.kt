package com.delminiusapps.rideforge.dto

import com.delminiusapps.rideforge.models.Device
import com.delminiusapps.rideforge.models.IntervalType
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.SessionStatus
import com.delminiusapps.rideforge.models.TrainingPlan
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutInterval
import com.delminiusapps.rideforge.models.WorkoutSession
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
)

@Serializable
data class PageResponse<T>(
    val items: List<T>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    val ftp: Int = 240,
    val weightKg: Double = 78.0,
    val units: String = "metric",
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResponse,
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val name: String,
    val ftp: Int,
    val weightKg: Double,
    val units: String,
    val createdAt: String,
    val enrolledPlanId: String?,
)

@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    val ftp: Int? = null,
    val weightKg: Double? = null,
    val units: String? = null,
)

@Serializable
data class UpdateFtpRequest(val ftp: Int)

@Serializable
data class UpdateWeightRequest(val weightKg: Double)

@Serializable
data class EnrollResponse(val plan: TrainingPlan, val user: UserResponse)

@Serializable
data class StartSessionRequest(val workoutId: String)

@Serializable
data class CompleteSessionRequest(val elapsedSeconds: Int? = null)

@Serializable
data class SessionResponse(
    val session: WorkoutSession,
    val workout: Workout? = null,
)

@Serializable
data class MetricSampleRequest(
    val timestamp: String? = null,
    val currentPower: Int,
    val targetPower: Int,
    val cadence: Int,
    val heartRate: Int,
    val speedKmh: Double,
)

@Serializable
data class MetricsAcceptedResponse(val accepted: Int, val latest: MetricSample)

@Serializable
data class ConnectDeviceRequest(
    val deviceId: String? = null,
    val name: String,
    val type: String = "smart_trainer",
    val supportsErg: Boolean = true,
)

@Serializable
data class DeviceResponse(val device: Device?)

@Serializable
data class WorkoutIntervalResponse(
    val id: String,
    val workoutId: String,
    val name: String,
    val durationSeconds: Int,
    val targetPowerWatts: Int,
    val targetFtpPercent: Int?,
    val type: IntervalType,
)

fun com.delminiusapps.rideforge.models.User.toResponse(): UserResponse = UserResponse(
    id = id,
    email = email,
    name = name,
    ftp = ftp,
    weightKg = weightKg,
    units = units,
    createdAt = createdAt,
    enrolledPlanId = enrolledPlanId,
)

fun WorkoutInterval.toResponse(userFtp: Int): WorkoutIntervalResponse {
    val computedPower = targetPowerWatts ?: ((userFtp * (targetFtpPercent ?: 50)) / 100)
    return WorkoutIntervalResponse(
        id = id,
        workoutId = workoutId,
        name = name,
        durationSeconds = durationSeconds,
        targetPowerWatts = computedPower,
        targetFtpPercent = targetFtpPercent,
        type = type,
    )
}
