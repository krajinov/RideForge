package com.delminiusapps.rideforge.presentation.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.domain.trainer.ConnectionState
import com.delminiusapps.rideforge.domain.trainer.SmartTrainerDevice
import com.delminiusapps.rideforge.domain.trainer.TrainerConnectionRepository
import com.delminiusapps.rideforge.domain.trainer.TrainerControlService
import com.delminiusapps.rideforge.domain.trainer.TrainerControlState
import com.delminiusapps.rideforge.domain.trainer.TrainerError
import com.delminiusapps.rideforge.domain.trainer.TrainerMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TrainerViewModel(
    private val repository: TrainerConnectionRepository,
    private val controlService: TrainerControlService,
) : ViewModel() {
    private val selectedTargetPower = MutableStateFlow(180)
    private var targetPowerJob: Job? = null

    val state: StateFlow<TrainerUiState> = combine(
        repository.devices,
        repository.connectionState,
        repository.connectedDevice,
        repository.metrics,
        repository.controlState,
        repository.error,
        selectedTargetPower,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        TrainerUiState(
            devices = values[0] as List<SmartTrainerDevice>,
            connectionState = values[1] as ConnectionState,
            connectedDevice = values[2] as SmartTrainerDevice?,
            metrics = values[3] as TrainerMetrics,
            controlState = values[4] as TrainerControlState,
            error = values[5] as TrainerError?,
            selectedTargetPower = values[6] as Int,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainerUiState())

    init {
        onAction(TrainerAction.Scan)
    }

    fun onAction(action: TrainerAction) {
        when (action) {
            TrainerAction.Scan -> scan()
            is TrainerAction.Connect -> connect(action.deviceId)
            TrainerAction.Disconnect -> disconnect()
            TrainerAction.EnableErg -> enableErg()
            TrainerAction.DisableErg -> disableErg()
            is TrainerAction.SetTargetPower -> setTargetPower(action.watts)
            is TrainerAction.SetResistance -> setResistance(action.resistance)
        }
    }

    private fun scan() {
        viewModelScope.launch {
            repository.scan()
        }
    }

    private fun connect(deviceId: String) {
        viewModelScope.launch {
            repository.connect(deviceId)
        }
    }

    private fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
        }
    }

    private fun enableErg() {
        targetPowerJob?.cancel()
        viewModelScope.launch {
            runCatching {
                controlService.setTargetPower(selectedTargetPower.value)
            }
        }
    }

    private fun disableErg() {
        targetPowerJob?.cancel()
        viewModelScope.launch {
            runCatching { controlService.disableErgMode() }
        }
    }

    private fun setTargetPower(watts: Int) {
        selectedTargetPower.value = watts
        targetPowerJob?.cancel()
        targetPowerJob = viewModelScope.launch {
            delay(TargetPowerDebounceMillis)
            if (repository.connectionState.value == ConnectionState.CONNECTED && repository.controlState.value.ergEnabled) {
                runCatching { controlService.setTargetPower(selectedTargetPower.value) }
            }
        }
    }

    private fun setResistance(resistance: Int) {
        targetPowerJob?.cancel()
        viewModelScope.launch {
            runCatching { controlService.setResistance(resistance) }
        }
    }

    private companion object {
        const val TargetPowerDebounceMillis = 350L
    }
}

data class TrainerUiState(
    val devices: List<SmartTrainerDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectedDevice: SmartTrainerDevice? = null,
    val metrics: TrainerMetrics = TrainerMetrics(),
    val controlState: TrainerControlState = TrainerControlState(),
    val error: TrainerError? = null,
    val selectedTargetPower: Int = 180,
)

sealed interface TrainerAction {
    data object Scan : TrainerAction
    data class Connect(val deviceId: String) : TrainerAction
    data object Disconnect : TrainerAction
    data object EnableErg : TrainerAction
    data object DisableErg : TrainerAction
    data class SetTargetPower(val watts: Int) : TrainerAction
    data class SetResistance(val resistance: Int) : TrainerAction
}
