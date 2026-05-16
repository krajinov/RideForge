package com.delminiusapps.rideforge.data.trainer

import com.delminiusapps.rideforge.data.local.RideForgeKeyValueStore
import com.delminiusapps.rideforge.domain.trainer.BluetoothTrainerClient
import com.delminiusapps.rideforge.domain.trainer.ConnectionState
import com.delminiusapps.rideforge.domain.trainer.SmartTrainerDevice
import com.delminiusapps.rideforge.domain.trainer.TrainerConnectionRepository
import com.delminiusapps.rideforge.domain.trainer.TrainerControlService
import com.delminiusapps.rideforge.domain.trainer.TrainerControlState
import com.delminiusapps.rideforge.domain.trainer.TrainerError
import com.delminiusapps.rideforge.domain.trainer.TrainerMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DefaultTrainerConnectionRepository(
    private val client: BluetoothTrainerClient,
    private val keyValueStore: RideForgeKeyValueStore,
) : TrainerConnectionRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val devices: StateFlow<List<SmartTrainerDevice>> = client.devices
    override val connectionState: StateFlow<ConnectionState> = client.connectionState
    override val connectedDevice: StateFlow<SmartTrainerDevice?> = client.connectedDevice
    override val metrics: StateFlow<TrainerMetrics> = client.metrics
    override val controlState: StateFlow<TrainerControlState> = client.controlState
    override val error: StateFlow<TrainerError?> = client.error

    init {
        rememberSuccessfulConnections()
        autoConnectLastTrainer()
    }

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
        val deviceId = connectedDevice.value?.id ?: keyValueStore.readString(LastTrainerDeviceIdKey)
        deviceId?.let { connect(it) }
    }

    private fun rememberSuccessfulConnections() {
        scope.launch {
            connectedDevice.collect { device ->
                if (device != null) {
                    runCatching { keyValueStore.writeString(LastTrainerDeviceIdKey, device.id) }
                }
            }
        }
    }

    private fun autoConnectLastTrainer() {
        scope.launch {
            val deviceId = keyValueStore.readString(LastTrainerDeviceIdKey) ?: return@launch
            if (connectionState.value == ConnectionState.CONNECTED || connectionState.value == ConnectionState.CONNECTING) return@launch
            runCatching { connect(deviceId) }
        }
    }

    private companion object {
        const val ScanWindowMillis = 3_500L
        const val LastTrainerDeviceIdKey = "trainer.last_device_id"
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
