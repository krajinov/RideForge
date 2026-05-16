package com.delminiusapps.rideforge.domain.trainer

import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
}

@Serializable
enum class TrainerCapability {
    POWER,
    CADENCE,
    SPEED,
    ERG,
    RESISTANCE,
    HEART_RATE,
}

@Serializable
enum class TrainerErrorType {
    BLUETOOTH_DISABLED,
    PERMISSION_DENIED,
    DEVICE_LOST,
    CONNECTION_TIMEOUT,
    UNSUPPORTED_TRAINER,
    CONNECTION_FAILED,
    ERG_COMMAND_FAILED,
}

@Serializable
data class TrainerError(
    val type: TrainerErrorType,
    val message: String,
)

@Serializable
data class SmartTrainerDevice(
    val id: String,
    val name: String,
    val rssi: Int,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val supportsErg: Boolean = false,
    val capabilities: Set<TrainerCapability> = emptySet(),
)

@Serializable
data class TrainerMetrics(
    val powerWatts: Int = 0,
    val cadence: Int = 0,
    val speedKmh: Double = 0.0,
    val heartRate: Int = 0,
    val resistance: Int = 0,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
)

@Serializable
data class TrainerControlState(
    val ergEnabled: Boolean = false,
    val targetPower: Int = 0,
    val currentResistance: Int = 0,
)
