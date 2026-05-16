package com.delminiusapps.rideforge.domain.trainer

import kotlinx.coroutines.flow.StateFlow

interface TrainerConnectionRepository {
    val devices: StateFlow<List<SmartTrainerDevice>>
    val connectionState: StateFlow<ConnectionState>
    val connectedDevice: StateFlow<SmartTrainerDevice?>
    val metrics: StateFlow<TrainerMetrics>
    val controlState: StateFlow<TrainerControlState>
    val error: StateFlow<TrainerError?>

    suspend fun scan(): List<SmartTrainerDevice>
    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connect(deviceId: String)
    suspend fun disconnect()
    suspend fun reconnect()
}

interface BluetoothTrainerClient {
    val devices: StateFlow<List<SmartTrainerDevice>>
    val connectionState: StateFlow<ConnectionState>
    val connectedDevice: StateFlow<SmartTrainerDevice?>
    val metrics: StateFlow<TrainerMetrics>
    val controlState: StateFlow<TrainerControlState>
    val error: StateFlow<TrainerError?>

    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connect(deviceId: String)
    suspend fun disconnect()
    suspend fun enableErgMode()
    suspend fun setTargetPower(watts: Int)
    suspend fun disableErgMode()
    suspend fun setResistance(resistance: Int)
}

interface TrainerControlService {
    val controlState: StateFlow<TrainerControlState>

    suspend fun enableErgMode()
    suspend fun setTargetPower(watts: Int)
    suspend fun disableErgMode()
    suspend fun setResistance(resistance: Int)
}
