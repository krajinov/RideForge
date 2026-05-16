package com.delminiusapps.rideforge.presentation.trainer

import androidx.lifecycle.ViewModel
import com.delminiusapps.rideforge.domain.trainer.ConnectionState
import com.delminiusapps.rideforge.domain.trainer.SmartTrainerDevice
import com.delminiusapps.rideforge.domain.trainer.TrainerCapability
import com.delminiusapps.rideforge.domain.trainer.TrainerConnectionRepository
import com.delminiusapps.rideforge.domain.trainer.TrainerControlService
import com.delminiusapps.rideforge.domain.trainer.TrainerControlState
import com.delminiusapps.rideforge.domain.trainer.TrainerError
import com.delminiusapps.rideforge.domain.trainer.TrainerMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TrainerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun enablingErgSendsSelectedTargetPower() = runTest(dispatcher) {
        val repository = FakeTrainerRepository()
        val controlService = FakeTrainerControlService(repository)
        val viewModel = TrainerViewModel(repository, controlService)
        try {
            runCurrent()

            viewModel.onAction(TrainerAction.SetTargetPower(240))
            advanceTimeBy(400)
            runCurrent()
            viewModel.onAction(TrainerAction.EnableErg)
            runCurrent()

            assertEquals(listOf("target:240"), controlService.commands)
            assertEquals(240, repository.controlState.value.targetPower)
        } finally {
            viewModel.clearForTest()
        }
    }

    @Test
    fun targetPowerSliderDebouncesWhileErgIsEnabled() = runTest(dispatcher) {
        val repository = FakeTrainerRepository()
        val controlService = FakeTrainerControlService(repository)
        val viewModel = TrainerViewModel(repository, controlService)
        try {
            runCurrent()

            viewModel.onAction(TrainerAction.EnableErg)
            runCurrent()
            controlService.commands.clear()

            viewModel.onAction(TrainerAction.SetTargetPower(220))
            viewModel.onAction(TrainerAction.SetTargetPower(230))
            viewModel.onAction(TrainerAction.SetTargetPower(240))
            advanceTimeBy(349)
            runCurrent()

            assertTrue(controlService.commands.isEmpty())

            advanceTimeBy(1)
            runCurrent()

            assertEquals(listOf("target:240"), controlService.commands)
        } finally {
            viewModel.clearForTest()
        }
    }

    private fun ViewModel.clearForTest() {
        val method = ViewModel::class.java.getDeclaredMethod("clear\$lifecycle_viewmodel")
        method.isAccessible = true
        method.invoke(this)
    }
}

private class FakeTrainerRepository : TrainerConnectionRepository {
    private val trainer = SmartTrainerDevice(
        id = "trainer-1",
        name = "Think X222",
        rssi = -54,
        supportsErg = true,
        capabilities = setOf(TrainerCapability.POWER, TrainerCapability.CADENCE, TrainerCapability.ERG),
        connectionState = ConnectionState.CONNECTED,
    )

    override val devices: StateFlow<List<SmartTrainerDevice>> = MutableStateFlow(listOf(trainer))
    override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.CONNECTED)
    override val connectedDevice: StateFlow<SmartTrainerDevice?> = MutableStateFlow(trainer)
    override val metrics: StateFlow<TrainerMetrics> = MutableStateFlow(TrainerMetrics())
    override val controlState: MutableStateFlow<TrainerControlState> = MutableStateFlow(TrainerControlState())
    override val error: StateFlow<TrainerError?> = MutableStateFlow(null)

    override suspend fun scan(): List<SmartTrainerDevice> = devices.value
    override suspend fun startScan() = Unit
    override suspend fun stopScan() = Unit
    override suspend fun connect(deviceId: String) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun reconnect() = Unit
}

private class FakeTrainerControlService(
    private val repository: FakeTrainerRepository,
) : TrainerControlService {
    val commands = mutableListOf<String>()
    override val controlState: StateFlow<TrainerControlState> = repository.controlState

    override suspend fun enableErgMode() {
        commands += "enable"
        repository.controlState.value = repository.controlState.value.copy(ergEnabled = true)
    }

    override suspend fun setTargetPower(watts: Int) {
        commands += "target:$watts"
        repository.controlState.value = repository.controlState.value.copy(
            ergEnabled = true,
            targetPower = watts,
        )
    }

    override suspend fun disableErgMode() {
        commands += "disable"
        repository.controlState.value = repository.controlState.value.copy(ergEnabled = false, targetPower = 0)
    }

    override suspend fun setResistance(resistance: Int) {
        commands += "resistance:$resistance"
        repository.controlState.value = repository.controlState.value.copy(
            ergEnabled = false,
            currentResistance = resistance,
        )
    }
}
