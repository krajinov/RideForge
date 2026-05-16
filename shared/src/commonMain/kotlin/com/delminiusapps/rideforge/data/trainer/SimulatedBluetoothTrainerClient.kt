package com.delminiusapps.rideforge.data.trainer

import com.delminiusapps.rideforge.domain.trainer.BluetoothTrainerClient
import com.delminiusapps.rideforge.domain.trainer.ConnectionState
import com.delminiusapps.rideforge.domain.trainer.SmartTrainerDevice
import com.delminiusapps.rideforge.domain.trainer.TrainerCapability
import com.delminiusapps.rideforge.domain.trainer.TrainerControlState
import com.delminiusapps.rideforge.domain.trainer.TrainerError
import com.delminiusapps.rideforge.domain.trainer.TrainerErrorType
import com.delminiusapps.rideforge.domain.trainer.TrainerMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class SimulatedBluetoothTrainerClient(
    private val platformLabel: String = "Simulated BLE",
) : BluetoothTrainerClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val random = Random(26)
    private var metricsJob: Job? = null

    private val _devices = MutableStateFlow<List<SmartTrainerDevice>>(emptyList())
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _connectedDevice = MutableStateFlow<SmartTrainerDevice?>(null)
    private val _metrics = MutableStateFlow(TrainerMetrics())
    private val _controlState = MutableStateFlow(TrainerControlState())
    private val _error = MutableStateFlow<TrainerError?>(null)

    override val devices: StateFlow<List<SmartTrainerDevice>> = _devices
    override val connectionState: StateFlow<ConnectionState> = _connectionState
    override val connectedDevice: StateFlow<SmartTrainerDevice?> = _connectedDevice
    override val metrics: StateFlow<TrainerMetrics> = _metrics
    override val controlState: StateFlow<TrainerControlState> = _controlState
    override val error: StateFlow<TrainerError?> = _error

    override suspend fun startScan() {
        _error.value = null
        _connectionState.value = ConnectionState.SCANNING
        delay(650)
        _devices.value = simulatedDevices
    }

    override suspend fun stopScan() {
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = if (_connectedDevice.value != null) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
        }
    }

    override suspend fun connect(deviceId: String) {
        _connectionState.value = ConnectionState.CONNECTING
        delay(450)
        val device = simulatedDevices.firstOrNull { it.id == deviceId }
        if (device == null) {
            _connectionState.value = ConnectionState.DISCONNECTED
            _error.value = TrainerError(TrainerErrorType.CONNECTION_FAILED, "Trainer not found.")
            return
        }

        val connected = device.copy(connectionState = ConnectionState.CONNECTED)
        _connectedDevice.value = connected
        _devices.value = simulatedDevices.map {
            if (it.id == deviceId) connected else it.copy(connectionState = ConnectionState.DISCONNECTED)
        }
        _connectionState.value = ConnectionState.CONNECTED
        startMetricStream()
    }

    override suspend fun disconnect() {
        metricsJob?.cancel()
        metricsJob = null
        _connectedDevice.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _controlState.value = TrainerControlState()
        _metrics.value = TrainerMetrics()
        _devices.value = _devices.value.map { it.copy(connectionState = ConnectionState.DISCONNECTED) }
    }

    override suspend fun enableErgMode() {
        _controlState.value = _controlState.value.copy(ergEnabled = true)
    }

    override suspend fun setTargetPower(watts: Int) {
        _controlState.value = _controlState.value.copy(
            ergEnabled = true,
            targetPower = watts.coerceIn(50, 800),
        )
    }

    override suspend fun disableErgMode() {
        _controlState.value = _controlState.value.copy(ergEnabled = false, targetPower = 0)
    }

    override suspend fun setResistance(resistance: Int) {
        _controlState.value = _controlState.value.copy(
            ergEnabled = false,
            currentResistance = resistance.coerceIn(0, 100),
        )
    }

    private fun startMetricStream() {
        metricsJob?.cancel()
        metricsJob = scope.launch {
            var heartRate = 116
            while (true) {
                delay(1_000)
                val control = _controlState.value
                val target = when {
                    control.ergEnabled && control.targetPower > 0 -> control.targetPower
                    control.currentResistance > 0 -> 90 + control.currentResistance * 3
                    else -> 170
                }
                val power = max(65, target + random.nextInt(-8, 9))
                val cadence = if (target > 260) random.nextInt(88, 96) else random.nextInt(82, 92)
                heartRate = when {
                    power > 260 -> min(176, heartRate + random.nextInt(1, 4))
                    power < 130 -> max(104, heartRate - random.nextInt(1, 3))
                    else -> (heartRate + random.nextInt(-1, 2)).coerceIn(108, 162)
                }
                _metrics.value = TrainerMetrics(
                    powerWatts = power,
                    cadence = cadence,
                    speedKmh = 22.0 + power / 18.0,
                    heartRate = heartRate,
                    resistance = control.currentResistance,
                )
            }
        }
    }

    private val simulatedDevices: List<SmartTrainerDevice>
        get() = listOf(
            smartTrainer("wahoo-kickr", "Wahoo KICKR", -41),
            smartTrainer("tacx-neo", "Tacx NEO 2T", -55),
            smartTrainer("elite-suito", "Elite Suito", -63),
            smartTrainer("zwift-hub", "Zwift Hub", -59),
            smartTrainer("jetblack-volt", "JetBlack VOLT", -67),
        )

    private fun smartTrainer(id: String, name: String, rssi: Int): SmartTrainerDevice = SmartTrainerDevice(
        id = id,
        name = if (platformLabel == "Simulated BLE") name else "$name ($platformLabel)",
        rssi = rssi,
        supportsErg = true,
        capabilities = setOf(
            TrainerCapability.POWER,
            TrainerCapability.CADENCE,
            TrainerCapability.SPEED,
            TrainerCapability.ERG,
            TrainerCapability.RESISTANCE,
            TrainerCapability.HEART_RATE,
        ),
    )
}
