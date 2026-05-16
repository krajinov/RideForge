@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.delminiusapps.rideforge.data.trainer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import com.delminiusapps.rideforge.domain.trainer.BluetoothTrainerClient
import com.delminiusapps.rideforge.domain.trainer.ConnectionState
import com.delminiusapps.rideforge.domain.trainer.SmartTrainerDevice
import com.delminiusapps.rideforge.domain.trainer.TrainerCapability
import com.delminiusapps.rideforge.domain.trainer.TrainerControlState
import com.delminiusapps.rideforge.domain.trainer.TrainerError
import com.delminiusapps.rideforge.domain.trainer.TrainerErrorType
import com.delminiusapps.rideforge.domain.trainer.TrainerMetrics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

private var bluetoothContext: Context? = null

fun configureTrainerBluetoothContext(context: Context) {
    bluetoothContext = context.applicationContext
}

actual fun createPlatformBluetoothTrainerClient(): BluetoothTrainerClient {
    val context = bluetoothContext ?: return SimulatedBluetoothTrainerClient(platformLabel = "Android BLE unavailable")
    return AndroidFtmsBluetoothTrainerClient(context)
}

@SuppressLint("MissingPermission")
private class AndroidFtmsBluetoothTrainerClient(
    private val context: Context,
) : BluetoothTrainerClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val scannedBluetoothDevices = mutableMapOf<String, BluetoothDevice>()
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var controlPoint: BluetoothGattCharacteristic? = null
    private var controlPointResponse: PendingControlPointResponse? = null
    private var writeCompletionDeferred: CompletableDeferred<Boolean>? = null
    private var descriptorWriteDeferred: CompletableDeferred<Unit>? = null
    private var lastControlPointResultCode: Int? = null
    private var reconnectDeviceId: String? = null
    private var manualDisconnect = false
    private var connectionTimeoutJob: Job? = null
    private val controlCommandMutex = Mutex()
    private var hasControl = false
    private var lastBikeMetricsUpdateTime = 0L
    private var metricsTimeoutJob: Job? = null

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
        if (!ensureBluetoothReady(requireScanPermission = true)) return

        stopScan()
        scannedBluetoothDevices.clear()
        _error.value = null
        _connectionState.value = ConnectionState.SCANNING

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::handleScanResult)
            }

            override fun onScanFailed(errorCode: Int) {
                _connectionState.value = ConnectionState.DISCONNECTED
                _error.value = TrainerError(
                    TrainerErrorType.CONNECTION_FAILED,
                    "BLE scan failed with code $errorCode.",
                )
            }
        }
        scanCallback = callback
        scanner?.startScan(
            null,
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback,
        )
    }

    override suspend fun stopScan() {
        scanCallback?.let { callback ->
            if (hasScanPermission()) {
                runCatching { scanner?.stopScan(callback) }
            }
        }
        scanCallback = null
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = if (_connectedDevice.value != null) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
        }
    }

    override suspend fun connect(deviceId: String) {
        if (!ensureBluetoothReady(requireConnectPermission = true)) return
        stopScan()

        val device = scannedBluetoothDevices[deviceId] ?: runCatching {
            bluetoothAdapter?.getRemoteDevice(deviceId)
        }.getOrNull()

        if (device == null) {
            _error.value = TrainerError(TrainerErrorType.CONNECTION_FAILED, "Trainer not found.")
            return
        }

        manualDisconnect = false
        reconnectDeviceId = deviceId
        _error.value = null
        _connectionState.value = ConnectionState.CONNECTING
        updateDeviceConnectionState(deviceId, ConnectionState.CONNECTING)
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = scope.launch {
            delay(ConnectionTimeoutMillis)
            if (_connectionState.value == ConnectionState.CONNECTING) {
                _error.value = TrainerError(TrainerErrorType.CONNECTION_TIMEOUT, "Trainer connection timed out.")
                disconnect()
            }
        }

        gatt?.close()
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    override suspend fun disconnect() {
        manualDisconnect = true
        connectionTimeoutJob?.cancel()
        reconnectDeviceId = null
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        controlPoint = null
        _connectedDevice.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _controlState.value = TrainerControlState()
        updateAllDevicesDisconnected()
    }

    override suspend fun enableErgMode() {
        controlCommandMutex.withLock {
            _error.value = null
            if (!ensureControlSession()) throwControlCommandFailure()
            delay(CommandDelayMillis)
        }
    }

    override suspend fun setTargetPower(watts: Int) {
        controlCommandMutex.withLock {
            val target = watts.coerceIn(50, 800)
            _error.value = null
            val wasAlreadyControlled = hasControl
            if (!ensureControlSession()) throwControlCommandFailure()
            if (!wasAlreadyControlled) delay(CommandDelayMillis)
            if (!writeTargetPower(target)) throwControlCommandFailure()
            _controlState.value = _controlState.value.copy(ergEnabled = true, targetPower = target)
        }
    }

    override suspend fun disableErgMode() {
        controlCommandMutex.withLock {
            _error.value = null
            if (!ensureControlSession()) throwControlCommandFailure()
            if (!writeControlPoint(byteArrayOf(FtmsOpSetTargetResistanceLevel, 0x00, 0x00))) throwControlCommandFailure()
            _controlState.value = _controlState.value.copy(ergEnabled = false, targetPower = 0)
        }
    }

    override suspend fun setResistance(resistance: Int) {
        controlCommandMutex.withLock {
            val targetResistance = resistance.coerceIn(0, 100)
            _error.value = null
            val wasAlreadyControlled = hasControl
            if (!ensureControlSession()) throwControlCommandFailure()
            if (!wasAlreadyControlled) delay(CommandDelayMillis)
            if (!writeControlPoint(byteArrayOf(FtmsOpSetTargetResistanceLevel, targetResistance.toByte(), 0x00))) {
                throwControlCommandFailure()
            }
            _controlState.value = _controlState.value.copy(
                ergEnabled = false,
                targetPower = 0,
                currentResistance = targetResistance,
            )
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.CONNECTING
                    if (hasConnectPermission()) {
                        gatt.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> handleDisconnected(status)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            connectionTimeoutJob?.cancel()
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _error.value = TrainerError(TrainerErrorType.CONNECTION_FAILED, "Unable to discover trainer services.")
                handleDisconnected(status)
                return
            }

            val ftmsService = gatt.getService(FtmsServiceUuid)
            if (ftmsService == null) {
                _error.value = TrainerError(TrainerErrorType.UNSUPPORTED_TRAINER, "This device does not expose the FTMS service.")
                scope.launch { disconnect() }
                return
            }

            controlPoint = ftmsService.getCharacteristic(FtmsControlPointUuid)
            val indoorBikeData = ftmsService.getCharacteristic(IndoorBikeDataUuid)

            val connected = (_devices.value.firstOrNull { it.id == gatt.device.address }
                ?: SmartTrainerDevice(gatt.device.address, gatt.device.name ?: "FTMS Trainer", 0))
                .copy(
                    connectionState = ConnectionState.CONNECTED,
                    supportsErg = controlPoint != null,
                    capabilities = capabilitiesFromServices(gatt),
                )

            scope.launch {
                startMetricsTimeoutLoop()
                // Subscribe to control point indications first, then data notifications.
                // On API < 33 each descriptor write must be awaited via onDescriptorWrite
                // before the next one is issued — the GATT queue rejects overlapping ops.
                controlPoint?.let { awaitDescriptorWrite(gatt, it, enableControlPointDescriptorValue(it)) }
                indoorBikeData?.let { awaitDescriptorWrite(gatt, it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) }
                gatt.getService(HeartRateServiceUuid)
                    ?.getCharacteristic(HeartRateMeasurementUuid)
                    ?.let { awaitDescriptorWrite(gatt, it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) }

                // Only announce CONNECTED after the control point indication is subscribed so
                // that the first ERG command can be sent immediately without losing responses.
                _connectedDevice.value = connected
                _connectionState.value = ConnectionState.CONNECTED
                upsertDevice(connected)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val pending = writeCompletionDeferred
            writeCompletionDeferred = null
            pending?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            val pending = descriptorWriteDeferred
            descriptorWriteDeferred = null
            pending?.complete(Unit)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            characteristic.value?.let { handleCharacteristicChanged(characteristic.uuid, it) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleCharacteristicChanged(characteristic.uuid, value)
        }
    }

    private fun handleDisconnected(status: Int) {
        hasControl = false
        connectionTimeoutJob?.cancel()
        metricsTimeoutJob?.cancel()
        controlPoint = null
        writeCompletionDeferred?.cancel()
        writeCompletionDeferred = null
        descriptorWriteDeferred?.cancel()
        descriptorWriteDeferred = null
        controlPointResponse?.deferred?.cancel()
        controlPointResponse = null
        val lostDeviceId = reconnectDeviceId
        _connectedDevice.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
        updateAllDevicesDisconnected()

        if (!manualDisconnect && lostDeviceId != null) {
            _error.value = TrainerError(TrainerErrorType.DEVICE_LOST, "Trainer connection lost. Reconnecting...")
            scope.launch {
                delay(ReconnectDelayMillis)
                connect(lostDeviceId)
            }
        } else if (status != BluetoothGatt.GATT_SUCCESS && !manualDisconnect) {
            _error.value = TrainerError(TrainerErrorType.CONNECTION_FAILED, "Trainer disconnected with status $status.")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val name = result.scanRecord?.deviceName ?: result.device.name ?: return
        val serviceUuids = result.scanRecord?.serviceUuids.orEmpty()
        val exposesFtms = serviceUuids.contains(ParcelUuid(FtmsServiceUuid))
        val recognizedTrainer = recognizedTrainerNames.any { name.contains(it, ignoreCase = true) }
        if (!exposesFtms && !recognizedTrainer) return

        scannedBluetoothDevices[result.device.address] = result.device
        upsertDevice(
            SmartTrainerDevice(
                id = result.device.address,
                name = name,
                rssi = result.rssi,
                supportsErg = exposesFtms,
                capabilities = buildSet {
                    add(TrainerCapability.POWER)
                    add(TrainerCapability.CADENCE)
                    add(TrainerCapability.SPEED)
                    if (exposesFtms) {
                        add(TrainerCapability.ERG)
                        add(TrainerCapability.RESISTANCE)
                    }
                },
            ),
        )
    }

    private fun handleCharacteristicChanged(uuid: UUID, value: ByteArray) {
        when (uuid) {
            IndoorBikeDataUuid -> {
                lastBikeMetricsUpdateTime = System.currentTimeMillis()
                _metrics.value = parseIndoorBikeData(value, _metrics.value)
            }
            HeartRateMeasurementUuid -> _metrics.value = _metrics.value.copy(heartRate = parseHeartRate(value))
            FtmsControlPointUuid -> handleControlPointResponse(value)
        }
    }

    private fun handleControlPointResponse(value: ByteArray) {
        if (value.size < 3 || value[0] != FtmsOpResponseCode) return
        val requestOpCode = value[1]
        val resultCode = value[2].toInt() and 0xFF
        lastControlPointResultCode = resultCode
        val pending = controlPointResponse ?: return
        if (pending.requestOpCode == requestOpCode && !pending.deferred.isCompleted) {
            pending.deferred.complete(ControlPointResult(requestOpCode, resultCode))
            controlPointResponse = null
        }
    }

    private fun parseIndoorBikeData(value: ByteArray, previous: TrainerMetrics): TrainerMetrics {
        if (value.size < 2) return previous
        val flags = unsignedShort(value, 0)
        var offset = 2
        var speed = previous.speedKmh
        var cadence = previous.cadence
        var resistance = previous.resistance
        var power = previous.powerWatts
        var heartRate = previous.heartRate

        if ((flags and 0x0001) == 0 && value.hasBytes(offset, 2)) {
            speed = unsignedShort(value, offset) / 100.0
            offset += 2
        }
        if ((flags and 0x0002) != 0) offset += 2
        if ((flags and 0x0004) != 0 && value.hasBytes(offset, 2)) {
            cadence = unsignedShort(value, offset) / 2
            offset += 2
        }
        if ((flags and 0x0008) != 0) offset += 2
        if ((flags and 0x0010) != 0) offset += 3
        if ((flags and 0x0020) != 0 && value.hasBytes(offset, 2)) {
            resistance = signedShort(value, offset)
            offset += 2
        }
        if ((flags and 0x0040) != 0 && value.hasBytes(offset, 2)) {
            power = signedShort(value, offset)
            offset += 2
        }
        if ((flags and 0x0080) != 0) offset += 2
        if ((flags and 0x0100) != 0) offset += 5
        if ((flags and 0x0200) != 0 && value.hasBytes(offset, 1)) {
            heartRate = value[offset].toInt() and 0xFF
        }

        return TrainerMetrics(
            powerWatts = power.coerceAtLeast(0),
            cadence = cadence.coerceAtLeast(0),
            speedKmh = speed.coerceAtLeast(0.0),
            heartRate = heartRate.coerceAtLeast(0),
            resistance = resistance,
        )
    }

    private fun startMetricsTimeoutLoop() {
        metricsTimeoutJob?.cancel()
        metricsTimeoutJob = scope.launch {
            while (true) {
                delay(1000)
                if (System.currentTimeMillis() - lastBikeMetricsUpdateTime > 3000) {
                    _metrics.value = _metrics.value.copy(
                        powerWatts = 0,
                        cadence = 0,
                        speedKmh = 0.0,
                        resistance = 0,
                    )
                }
            }
        }
    }
    private fun parseHeartRate(value: ByteArray): Int {
        if (value.size < 2) return _metrics.value.heartRate
        val isUInt16 = value[0].toInt() and 0x01 == 1
        return if (isUInt16 && value.hasBytes(1, 2)) unsignedShort(value, 1) else value[1].toInt() and 0xFF
    }

    // Returns true only when the trainer confirms control was granted.
    private suspend fun requestControl(): Boolean = writeControlPoint(byteArrayOf(FtmsOpRequestControl))

    private suspend fun ensureControlSession(): Boolean {
        if (hasControl) return true
        if (!requestControl()) return false
        delay(CommandDelayMillis)
        
        // Some trainers (e.g. JetBlack, ThinkRider) require Start/Resume to activate the resistance engine.
        // We ignore failures here as some trainers don't support this opcode.
        writeControlPoint(byteArrayOf(FtmsOpStartOrResume))
        delay(CommandDelayMillis)
        
        hasControl = true
        return true
    }

    private suspend fun writeTargetPower(watts: Int): Boolean {
        return writeControlPoint(
            byteArrayOf(
                FtmsOpSetTargetPower,
                (watts and 0xFF).toByte(),
                ((watts shr 8) and 0xFF).toByte(),
            ),
        )
    }

    // Returns true only after the trainer responds with FTMS success.
    private suspend fun writeControlPoint(payload: ByteArray): Boolean {
        val currentGatt = gatt ?: run {
            failErgCommand("Trainer is not connected.")
            return false
        }
        val characteristic = controlPoint ?: run {
            _error.value = TrainerError(TrainerErrorType.UNSUPPORTED_TRAINER, "Trainer does not expose FTMS control point.")
            return false
        }
        if (!hasConnectPermission()) {
            _error.value = TrainerError(TrainerErrorType.PERMISSION_DENIED, "Bluetooth connect permission is required.")
            return false
        }

        val requestedOpCode = payload.firstOrNull() ?: return false
        val response = CompletableDeferred<ControlPointResult>()
        controlPointResponse?.deferred?.cancel()
        controlPointResponse = PendingControlPointResponse(requestedOpCode, response)
        lastControlPointResultCode = null

        for (attempt in 0 until ControlPointWriteAttempts) {
            val writeCompletion = CompletableDeferred<Boolean>().also { writeCompletionDeferred = it }

            val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentGatt.writeCharacteristic(
                    characteristic,
                    payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = payload
                currentGatt.writeCharacteristic(characteristic)
            }

            if (!accepted) {
                writeCompletionDeferred = null
                writeCompletion.cancel()
                if (attempt < ControlPointWriteAttempts - 1) {
                    delay(ControlPointRetryDelayMillis)
                }
                continue
            }

            // Wait for the GATT write ACK before awaiting the FTMS indication.
            val writeOk = withTimeoutOrNull(ControlPointResponseTimeoutMillis) { writeCompletion.await() } ?: false
            if (!writeOk) {
                controlPointResponse = null
                failErgCommand("Trainer did not ACK the GATT write.")
                return false
            }

            val result = withTimeoutOrNull(ControlPointResponseTimeoutMillis) { response.await() }
            if (result == null) {
                controlPointResponse = null
                failErgCommand("Trainer did not confirm the FTMS command.")
                return false
            }
            if (result.resultCode == FtmsResultSuccess) {
                return true
            }
            if (result.resultCode == FtmsResultControlNotPermitted) {
                hasControl = false
            }
            failErgCommand("Trainer rejected FTMS command: ${ftmsResultLabel(result.resultCode)}.")
            return false
        }
        controlPointResponse = null
        failErgCommand("Trainer rejected the ERG command.")
        return false
    }

    private fun failErgCommand(message: String) {
        _error.value = TrainerError(TrainerErrorType.ERG_COMMAND_FAILED, message)
    }

    private fun throwControlCommandFailure(): Nothing {
        throw TrainerControlCommandException(_error.value?.message ?: "Trainer command failed.")
    }

    private fun enableControlPointDescriptorValue(characteristic: BluetoothGattCharacteristic): ByteArray {
        return if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
    }

    /**
     * Writes a CCCD descriptor and — on API < 33 — suspends until [onDescriptorWrite] confirms
     * the write completed. This prevents back-to-back descriptor writes from colliding in the
     * GATT operation queue, which would silently drop the second write.
     */
    private suspend fun awaitDescriptorWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        descriptorValue: ByteArray,
    ) {
        if (!hasConnectPermission()) return
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(ClientCharacteristicConfigUuid) ?: return
        val completion = CompletableDeferred<Unit>().also { descriptorWriteDeferred = it }
        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, descriptorValue) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = descriptorValue
            gatt.writeDescriptor(descriptor)
        }

        if (success) {
            withTimeoutOrNull(DescriptorWriteTimeoutMillis) { completion.await() }
        } else {
            descriptorWriteDeferred = null
            completion.cancel()
            delay(DescriptorWriteDelayMillis)
        }
    }

    private fun capabilitiesFromServices(gatt: BluetoothGatt): Set<TrainerCapability> = buildSet {
        add(TrainerCapability.POWER)
        add(TrainerCapability.CADENCE)
        add(TrainerCapability.SPEED)
        if (controlPoint != null) {
            add(TrainerCapability.ERG)
            add(TrainerCapability.RESISTANCE)
        }
        if (gatt.getService(HeartRateServiceUuid) != null) {
            add(TrainerCapability.HEART_RATE)
        }
    }

    private fun ensureBluetoothReady(
        requireScanPermission: Boolean = false,
        requireConnectPermission: Boolean = false,
    ): Boolean {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = ConnectionState.DISCONNECTED
            _error.value = TrainerError(TrainerErrorType.BLUETOOTH_DISABLED, "Bluetooth is disabled.")
            return false
        }
        if (requireScanPermission && !hasScanPermission()) {
            _connectionState.value = ConnectionState.DISCONNECTED
            _error.value = TrainerError(TrainerErrorType.PERMISSION_DENIED, "Bluetooth scan permission is required.")
            return false
        }
        if (requireConnectPermission && !hasConnectPermission()) {
            _connectionState.value = ConnectionState.DISCONNECTED
            _error.value = TrainerError(TrainerErrorType.PERMISSION_DENIED, "Bluetooth connect permission is required.")
            return false
        }
        return true
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun upsertDevice(device: SmartTrainerDevice) {
        _devices.value = (_devices.value.filterNot { it.id == device.id } + device)
            .sortedByDescending { it.rssi }
    }

    private fun updateDeviceConnectionState(deviceId: String, state: ConnectionState) {
        _devices.value = _devices.value.map {
            if (it.id == deviceId) it.copy(connectionState = state) else it
        }
    }

    private fun updateAllDevicesDisconnected() {
        _devices.value = _devices.value.map { it.copy(connectionState = ConnectionState.DISCONNECTED) }
    }

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun signedShort(bytes: ByteArray, offset: Int): Int {
        val unsigned = unsignedShort(bytes, offset)
        return if (unsigned and 0x8000 != 0) unsigned - 0x10000 else unsigned
    }

    private fun ByteArray.hasBytes(offset: Int, count: Int): Boolean = offset + count <= size

    private fun ftmsResultLabel(resultCode: Int): String {
        return when (resultCode) {
            FtmsResultSuccess -> "success"
            FtmsResultOpCodeNotSupported -> "operation not supported"
            FtmsResultInvalidParameter -> "invalid parameter"
            FtmsResultOperationFailed -> "operation failed"
            FtmsResultControlNotPermitted -> "control not permitted"
            else -> "code $resultCode"
        }
    }

    private data class PendingControlPointResponse(
        val requestOpCode: Byte,
        val deferred: CompletableDeferred<ControlPointResult>,
    )

    private data class ControlPointResult(
        val requestOpCode: Byte,
        val resultCode: Int,
    )

    private class TrainerControlCommandException(message: String) : IllegalStateException(message)

    private companion object {
        val FtmsServiceUuid: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
        val IndoorBikeDataUuid: UUID = UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb")
        val FtmsControlPointUuid: UUID = UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")
        val HeartRateServiceUuid: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HeartRateMeasurementUuid: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val ClientCharacteristicConfigUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        const val FtmsOpRequestControl: Byte = 0x00
        const val FtmsOpSetTargetResistanceLevel: Byte = 0x04
        const val FtmsOpSetTargetPower: Byte = 0x05
        const val FtmsOpStartOrResume: Byte = 0x07
        const val FtmsOpResponseCode: Byte = 0x80.toByte()
        const val FtmsResultSuccess = 0x01
        const val FtmsResultOpCodeNotSupported = 0x02
        const val FtmsResultInvalidParameter = 0x03
        const val FtmsResultOperationFailed = 0x04
        const val FtmsResultControlNotPermitted = 0x05
        const val ConnectionTimeoutMillis = 12_000L
        const val ReconnectDelayMillis = 2_000L
        const val CommandDelayMillis = 500L
        const val ControlPointRetryDelayMillis = 150L
        const val ControlPointResponseTimeoutMillis = 2_500L
        const val ControlPointWriteAttempts = 3
        const val DescriptorWriteDelayMillis = 200L
        const val DescriptorWriteTimeoutMillis = 3_000L

        val recognizedTrainerNames = listOf(
            "wahoo",
            "kickr",
            "tacx",
            "elite",
            "zwift hub",
            "jetblack",
        )
    }
}
