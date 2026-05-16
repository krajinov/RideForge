package com.delminiusapps.rideforge.data.trainer

import com.delminiusapps.rideforge.domain.trainer.BluetoothTrainerClient
import com.delminiusapps.rideforge.domain.trainer.ConnectionState
import com.delminiusapps.rideforge.domain.trainer.SmartTrainerDevice
import com.delminiusapps.rideforge.domain.trainer.TrainerConnectionRepository
import com.delminiusapps.rideforge.domain.trainer.TrainerControlService
import com.delminiusapps.rideforge.domain.trainer.TrainerControlState
import com.delminiusapps.rideforge.domain.trainer.TrainerError
import com.delminiusapps.rideforge.domain.trainer.TrainerMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

class DefaultTrainerConnectionRepository(
    private val client: BluetoothTrainerClient,
) : TrainerConnectionRepository {
    override val devices: StateFlow<List<SmartTrainerDevice>> = client.devices
    override val connectionState: StateFlow<ConnectionState> = client.connectionState
    override val connectedDevice: StateFlow<SmartTrainerDevice?> = client.connectedDevice
    override val metrics: StateFlow<TrainerMetrics> = client.metrics
    override val controlState: StateFlow<TrainerControlState> = client.controlState
    override val error: StateFlow<TrainerError?> = client.error

    override suspend fun scan(): List<SmartTrainerDevice> {
        startScan()
        delay(ScanWindowMillis)
        stopScan()
        return devices.value
    }

    override suspend fun startScan() = client.startScan()
    override suspend fun stopScan() = client.stopScan()
    override suspend fun connect(deviceId: String) = client.connect(deviceId)
    override suspend fun disconnect() = client.disconnect()

    override suspend fun reconnect() {
        connectedDevice.value?.id?.let { connect(it) }
    }

    private companion object {
        const val ScanWindowMillis = 3_500L
    }
}

class DefaultTrainerControlService(
    private val client: BluetoothTrainerClient,
) : TrainerControlService {
    override val controlState: StateFlow<TrainerControlState> = client.controlState

    override suspend fun enableErgMode() = client.enableErgMode()
    override suspend fun setTargetPower(watts: Int) = client.setTargetPower(watts)
    override suspend fun disableErgMode() = client.disableErgMode()
    override suspend fun setResistance(resistance: Int) = client.setResistance(resistance)
}
