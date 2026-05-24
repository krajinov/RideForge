package com.delminiusapps.rideforge.features.workout.presentation

import com.delminiusapps.rideforge.data.local.RideForgeKeyValueStore
import com.delminiusapps.rideforge.data.local.StoredActiveWorkout
import com.delminiusapps.rideforge.data.local.WorkoutControlMode
import com.delminiusapps.rideforge.data.local.WorkoutLocalStorage
import com.delminiusapps.rideforge.data.repository.sync.MetricSampleBatchUploader
import com.delminiusapps.rideforge.domain.repository.AuthRepository
import com.delminiusapps.rideforge.domain.repository.SessionRepository
import com.delminiusapps.rideforge.domain.repository.WorkoutRepository
import com.delminiusapps.rideforge.domain.trainer.ConnectionState
import com.delminiusapps.rideforge.domain.trainer.SmartTrainerDevice
import com.delminiusapps.rideforge.domain.trainer.TrainerCapability
import com.delminiusapps.rideforge.domain.trainer.TrainerConnectionRepository
import com.delminiusapps.rideforge.domain.trainer.TrainerControlService
import com.delminiusapps.rideforge.domain.trainer.TrainerControlState
import com.delminiusapps.rideforge.domain.trainer.TrainerError
import com.delminiusapps.rideforge.domain.trainer.TrainerErrorType
import com.delminiusapps.rideforge.domain.trainer.TrainerMetrics
import com.delminiusapps.rideforge.domain.usecase.CompleteWorkoutSessionUseCase
import com.delminiusapps.rideforge.domain.usecase.GetCurrentUserUseCase
import com.delminiusapps.rideforge.domain.usecase.GetWorkoutUseCase
import com.delminiusapps.rideforge.domain.usecase.ObserveSessionSyncStatusUseCase
import com.delminiusapps.rideforge.domain.usecase.PauseWorkoutSessionUseCase
import com.delminiusapps.rideforge.domain.usecase.ResumeWorkoutSessionUseCase
import com.delminiusapps.rideforge.domain.usecase.StartWorkoutSessionUseCase
import com.delminiusapps.rideforge.domain.usecase.SyncPendingSessionsUseCase
import com.delminiusapps.rideforge.domain.usecase.UploadMetricBatchUseCase
import com.delminiusapps.rideforge.models.AuthSession
import com.delminiusapps.rideforge.models.AuthTokens
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.PowerZone
import com.delminiusapps.rideforge.models.SyncStatus
import com.delminiusapps.rideforge.models.TrainingPlan
import com.delminiusapps.rideforge.models.UserProfile
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutInterval
import com.delminiusapps.rideforge.models.WorkoutSession
import com.delminiusapps.rideforge.models.WorkoutType
import com.delminiusapps.rideforge.utils.RideMetricCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.math.abs
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModelReliabilityTest {
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
    fun ergCommandFailureDoesNotStartWorkoutTiming() = runTest(dispatcher) {
        val environment = TestEnvironment(dispatcher)
        environment.trainer.setConnected()
        environment.trainerControl.failCommands = true
        val viewModel = environment.createViewModel()
        try {
            runCurrent()

            viewModel.onAction(ActiveWorkoutAction.SelectControlMode(WorkoutControlMode.TRAINER))
            viewModel.onAction(ActiveWorkoutAction.Start)
            runCurrent()
            advanceTimeBy(3_500)
            runCurrent()

            val ready = viewModel.readyState()
            assertEquals(ActiveWorkoutPhase.PRE_WORKOUT, ready.phase)
            assertEquals(0, ready.engineState.elapsedSeconds)
            assertTrue(ActiveWorkoutBanner.ErgCommandFailed in ready.banners)
            assertEquals(0, environment.sessionRepository.addedMetrics.size)
        } finally {
            viewModel.clearForTest()
        }
    }

    @Test
    fun trainerDisconnectFallsBackToSimulationWithoutStoppingEngine() = runTest(dispatcher) {
        val environment = TestEnvironment(dispatcher)
        environment.trainer.setConnected()
        val viewModel = environment.createViewModel()
        try {
            runCurrent()

            viewModel.onAction(ActiveWorkoutAction.SelectControlMode(WorkoutControlMode.TRAINER))
            viewModel.onAction(ActiveWorkoutAction.Start)
            advanceTimeBy(3_100)
            runCurrent()
            val activeBeforeDisconnect = viewModel.readyState()
            assertEquals(ActiveWorkoutPhase.ACTIVE, activeBeforeDisconnect.phase)
            assertEquals(WorkoutControlMode.TRAINER, activeBeforeDisconnect.controlMode)

            environment.trainer.failReconnect = true
            environment.trainer.setDisconnected()
            runCurrent()
            advanceTimeBy(11_000)
            runCurrent()
            val afterFallback = viewModel.readyState()

            assertEquals(ActiveWorkoutPhase.ACTIVE, afterFallback.phase)
            assertEquals(WorkoutControlMode.SIMULATION, afterFallback.controlMode)
            assertTrue(afterFallback.engineState.elapsedSeconds > activeBeforeDisconnect.engineState.elapsedSeconds)
        } finally {
            viewModel.clearForTest()
        }
    }

    @Test
    fun connectedTrainerZeroMetricsDoNotFallbackToSimulatedRiding() = runTest(dispatcher) {
        val environment = TestEnvironment(dispatcher)
        environment.trainer.setConnected()
        val viewModel = environment.createViewModel()
        try {
            runCurrent()

            viewModel.onAction(ActiveWorkoutAction.SelectControlMode(WorkoutControlMode.TRAINER))
            viewModel.onAction(ActiveWorkoutAction.Start)
            advanceTimeBy(3_100)
            runCurrent()

            environment.trainer.setMetrics(TrainerMetrics(powerWatts = 0, cadence = 0, speedKmh = 0.0, heartRate = 119))
            advanceTimeBy(1_100)
            runCurrent()

            val ready = viewModel.readyState()
            assertEquals(ActiveWorkoutPhase.ACTIVE, ready.phase)
            assertEquals(WorkoutControlMode.TRAINER, ready.controlMode)
            assertEquals(0, ready.displaySample.currentPowerWatts)
            assertEquals(0, ready.displaySample.cadenceRpm)
            assertEquals(0.0, ready.displaySample.speedKmh)
            assertEquals(119, ready.displaySample.heartRateBpm)
        } finally {
            viewModel.clearForTest()
        }
    }

    @Test
    fun trainerModeUsesCalculatedSpeedInsteadOfTrainerReportedSpeed() = runTest(dispatcher) {
        val environment = TestEnvironment(dispatcher)
        environment.trainer.setConnected()
        val viewModel = environment.createViewModel()
        try {
            runCurrent()

            viewModel.onAction(ActiveWorkoutAction.SelectControlMode(WorkoutControlMode.TRAINER))
            viewModel.onAction(ActiveWorkoutAction.Start)
            advanceTimeBy(3_100)
            runCurrent()

            environment.trainer.setMetrics(TrainerMetrics(powerWatts = 190, cadence = 88, speedKmh = 55.0, heartRate = 130))
            advanceTimeBy(1_100)
            runCurrent()

            val ready = viewModel.readyState()
            val expectedSpeed = RideMetricCalculator.speedKmh(190, riderWeightKg = 75.0)
            assertTrue(abs(ready.displaySample.speedKmh - expectedSpeed) < 0.0001)
            assertTrue(ready.distanceKm > 0.0)
            assertTrue(abs(ready.displaySample.speedKmh - 55.0) > 0.1)
        } finally {
            viewModel.clearForTest()
        }
    }

    @Test
    fun trainerConnectedAfterWorkoutLoadDefaultsToTrainerModeForCompletion() = runTest(dispatcher) {
        val environment = TestEnvironment(dispatcher)
        val viewModel = environment.createViewModel()
        try {
            runCurrent()
            assertEquals(WorkoutControlMode.SIMULATION, viewModel.readyState().controlMode)

            environment.trainer.setConnected()
            runCurrent()
            assertEquals(WorkoutControlMode.TRAINER, viewModel.readyState().controlMode)

            viewModel.onAction(ActiveWorkoutAction.Start)
            advanceTimeBy(3_100)
            runCurrent()

            environment.trainer.setMetrics(TrainerMetrics(powerWatts = 175, cadence = 86, heartRate = 0))
            advanceTimeBy(1_100)
            runCurrent()
            viewModel.onAction(ActiveWorkoutAction.End)
            runCurrent()

            assertEquals(true, environment.sessionRepository.completedHasRealTrainerData)
        } finally {
            viewModel.clearForTest()
        }
    }

    @Test
    fun stoppedPedalingAutoPausesConnectedTrainerWorkout() = runTest(dispatcher) {
        val environment = TestEnvironment(dispatcher)
        environment.trainer.setConnected()
        val viewModel = environment.createViewModel()
        try {
            runCurrent()

            viewModel.onAction(ActiveWorkoutAction.SelectControlMode(WorkoutControlMode.TRAINER))
            viewModel.onAction(ActiveWorkoutAction.Start)
            advanceTimeBy(3_100)
            runCurrent()

            environment.trainer.setMetrics(TrainerMetrics(powerWatts = 0, cadence = 0))
            advanceTimeBy(6_100)
            runCurrent()

            val paused = viewModel.readyState()
            assertEquals(ActiveWorkoutPhase.ACTIVE, paused.phase)
            assertTrue(paused.engineState.isPaused)
            assertTrue(paused.autoPausedForStoppedPedaling)
        } finally {
            viewModel.clearForTest()
        }
    }

    @Test
    fun autoPausedWorkoutResumesWhenPedalingReturns() = runTest(dispatcher) {
        val environment = TestEnvironment(dispatcher)
        environment.trainer.setConnected()
        val viewModel = environment.createViewModel()
        try {
            runCurrent()

            viewModel.onAction(ActiveWorkoutAction.SelectControlMode(WorkoutControlMode.TRAINER))
            viewModel.onAction(ActiveWorkoutAction.Start)
            advanceTimeBy(3_100)
            runCurrent()

            environment.trainer.setMetrics(TrainerMetrics(powerWatts = 0, cadence = 0))
            advanceTimeBy(6_100)
            runCurrent()
            assertTrue(viewModel.readyState().autoPausedForStoppedPedaling)

            environment.trainer.setMetrics(TrainerMetrics(powerWatts = 40, cadence = 18, heartRate = 120))
            advanceTimeBy(2_100)
            runCurrent()

            val resumed = viewModel.readyState()
            assertTrue(!resumed.engineState.isPaused)
            assertTrue(!resumed.autoPausedForStoppedPedaling)
        } finally {
            viewModel.clearForTest()
        }
    }

    @Test
    fun backendMetricUploadFailureDoesNotStopWorkoutTimingAndKeepsLocalBuffer() = runTest(dispatcher) {
        val environment = TestEnvironment(dispatcher)
        environment.sessionRepository.failMetricUploads = true
        val viewModel = environment.createViewModel()
        try {
            runCurrent()

            viewModel.onAction(ActiveWorkoutAction.Start)
            advanceTimeBy(9_000)
            runCurrent()
            val ready = viewModel.readyState()
            val sessionId = ready.sessionId

            assertEquals(ActiveWorkoutPhase.ACTIVE, ready.phase)
            assertTrue(ready.engineState.elapsedSeconds > 0)
            assertTrue(environment.storage.getMetricUploadBuffer().samplesBySessionId[sessionId].orEmpty().isNotEmpty())
        } finally {
            viewModel.clearForTest()
        }
    }

    @Test
    fun unfinishedWorkoutRestoresPausedAndCanResume() = runTest(dispatcher) {
        val keyValueStore = InMemoryRideForgeKeyValueStore()
        val storage = WorkoutLocalStorage(keyValueStore)
        storage.saveActiveWorkout(
            StoredActiveWorkout(
                workoutId = TestWorkoutId,
                sessionId = "local-restored",
                ftpWatts = 240,
                elapsedSeconds = 12,
                samples = listOf(MetricSample(12, 180, 190, 88, 130)),
                controlMode = WorkoutControlMode.SIMULATION,
                isPaused = true,
                ergEnabled = false,
                updatedAtEpochMillis = 10L,
            ),
        )
        val environment = TestEnvironment(dispatcher, storage = storage)

        val viewModel = environment.createViewModel()
        try {
            runCurrent()
            val restored = viewModel.readyState()

            assertEquals(ActiveWorkoutPhase.ACTIVE, restored.phase)
            assertEquals(12, restored.engineState.elapsedSeconds)
            assertTrue(restored.engineState.isPaused)
            assertTrue(restored.resumedFromStorage)

            viewModel.onAction(ActiveWorkoutAction.Resume)
            advanceTimeBy(1_100)
            runCurrent()

            assertTrue(viewModel.readyState().engineState.elapsedSeconds > 12)
        } finally {
            viewModel.clearForTest()
        }
    }

    @Test
    fun activeRestoredWorkoutAdvancesFromWallClockAndKeepsRunning() = runTest(dispatcher) {
        val keyValueStore = InMemoryRideForgeKeyValueStore()
        val storage = WorkoutLocalStorage(keyValueStore)
        storage.saveActiveWorkout(
            StoredActiveWorkout(
                workoutId = TestWorkoutId,
                sessionId = "local-restored-active",
                ftpWatts = 240,
                elapsedSeconds = 12,
                samples = listOf(MetricSample(12, 180, 190, 88, 130)),
                controlMode = WorkoutControlMode.SIMULATION,
                isPaused = false,
                ergEnabled = false,
                updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds() - 3_000L,
            ),
        )
        val environment = TestEnvironment(dispatcher, storage = storage)

        val viewModel = environment.createViewModel()
        try {
            runCurrent()
            val restored = viewModel.readyState()

            assertEquals(ActiveWorkoutPhase.ACTIVE, restored.phase)
            assertTrue(restored.engineState.elapsedSeconds >= 15)
            assertTrue(!restored.engineState.isPaused)

            advanceTimeBy(1_100)
            runCurrent()

            assertTrue(viewModel.readyState().engineState.elapsedSeconds > restored.engineState.elapsedSeconds)
        } finally {
            viewModel.clearForTest()
        }
    }

    @Test
    fun activeWorkoutPersistenceSkipsDuplicateStateAndStillPersistsProgress() = runTest(dispatcher) {
        val keyValueStore = CountingRideForgeKeyValueStore()
        val environment = TestEnvironment(dispatcher, storage = WorkoutLocalStorage(keyValueStore))
        val viewModel = environment.createViewModel()
        try {
            runCurrent()

            viewModel.onAction(ActiveWorkoutAction.Start)
            advanceTimeBy(3_100)
            runCurrent()
            val writesAfterStart = keyValueStore.writeCount("active_workout")

            advanceTimeBy(1_100)
            runCurrent()
            val restored = environment.storage.getActiveWorkout()

            assertEquals(1, writesAfterStart)
            assertEquals(2, keyValueStore.writeCount("active_workout"))
            assertNotNull(restored)
            assertTrue(restored.elapsedSeconds > 0)
        } finally {
            viewModel.clearForTest()
        }
    }

    private fun ActiveWorkoutViewModel.readyState(): ActiveWorkoutUiState.Ready {
        return uiState.value as ActiveWorkoutUiState.Ready
    }

    private fun ActiveWorkoutViewModel.clearForTest() {
        val method = androidx.lifecycle.ViewModel::class.java.getDeclaredMethod("clear\$lifecycle_viewmodel")
        method.isAccessible = true
        method.invoke(this)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class TestEnvironment(
    private val dispatcher: TestDispatcher,
    val storage: WorkoutLocalStorage = WorkoutLocalStorage(InMemoryRideForgeKeyValueStore()),
) {
    val workoutRepository = FakeWorkoutRepository()
    val authRepository = FakeAuthRepository()
    val sessionRepository = FakeSessionRepository()
    val trainer = FakeTrainerConnectionRepository()
    val trainerControl = FakeTrainerControlService(trainer)
    val metricUploader = MetricSampleBatchUploader(UploadMetricBatchUseCase(sessionRepository), storage)

    fun createViewModel(): ActiveWorkoutViewModel {
        return ActiveWorkoutViewModel(
            getWorkoutUseCase = GetWorkoutUseCase(workoutRepository),
            getCurrentUserUseCase = GetCurrentUserUseCase(authRepository),
            startWorkoutSessionUseCase = StartWorkoutSessionUseCase(sessionRepository),
            pauseWorkoutSessionUseCase = PauseWorkoutSessionUseCase(sessionRepository),
            resumeWorkoutSessionUseCase = ResumeWorkoutSessionUseCase(sessionRepository),
            completeWorkoutSessionUseCase = CompleteWorkoutSessionUseCase(sessionRepository),
            observeSessionSyncStatusUseCase = ObserveSessionSyncStatusUseCase(sessionRepository),
            syncPendingSessionsUseCase = SyncPendingSessionsUseCase(sessionRepository),
            metricSampleBatchUploader = metricUploader,
            trainerConnectionRepository = trainer,
            trainerControlService = trainerControl,
            workoutLocalStorage = storage,
            workoutId = TestWorkoutId,
        )
    }
}

private class InMemoryRideForgeKeyValueStore : RideForgeKeyValueStore {
    private val values = mutableMapOf<String, String>()

    override suspend fun readString(key: String): String? = values[key]

    override suspend fun writeString(key: String, value: String) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}

private class CountingRideForgeKeyValueStore : RideForgeKeyValueStore {
    private val values = mutableMapOf<String, String>()
    private val writeCounts = mutableMapOf<String, Int>()

    override suspend fun readString(key: String): String? = values[key]

    override suspend fun writeString(key: String, value: String) {
        values[key] = value
        writeCounts[key] = writeCounts.getOrElse(key) { 0 } + 1
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    fun writeCount(key: String): Int = writeCounts[key] ?: 0
}

private class FakeWorkoutRepository : WorkoutRepository {
    private val workout = Workout(
        id = TestWorkoutId,
        name = "Reliability Ride",
        durationMinutes = 1,
        difficulty = "Easy",
        description = "Test workout",
        targetZones = listOf("Z2"),
        intervals = listOf(
            WorkoutInterval("Warmup", 20, 55, "Z2"),
            WorkoutInterval("Steady", 40, 80, "Z3"),
        ),
        planId = "plan",
        workoutType = WorkoutType.ENDURANCE,
    )

    override suspend fun getWorkouts(): List<Workout> = listOf(workout)
    override suspend fun getWorkoutsForPlan(planId: String): List<Workout> = listOf(workout)
    override suspend fun getRecommendedWorkout(): Workout = workout
    override suspend fun getWorkout(id: String): Workout = workout
}

private class FakeAuthRepository : AuthRepository {
    override suspend fun register(name: String, email: String, password: String): AuthSession = session()
    override suspend fun login(email: String, password: String): AuthSession = session()
    override suspend fun restoreSession(): AuthSession? = session()
    override suspend fun currentUser(): UserProfile = session().user
    override suspend fun updateProfile(ftpWatts: Int, weightKg: Double, units: String): UserProfile = session().user
    override suspend fun logout() = Unit

    private fun session(): AuthSession {
        return AuthSession(
            tokens = AuthTokens("access", "refresh"),
            user = UserProfile(
                name = "Test Rider",
                ftpWatts = 240,
                weightKg = 75.0,
                units = "metric",
                connectedDevice = "",
                subscription = "free",
                powerZones = listOf(PowerZone("z2", "Z2", "56-75%", "#00AEEF")),
            ),
        )
    }
}

private class FakeSessionRepository : SessionRepository {
    private val _syncStatus = MutableStateFlow(SyncStatus.Synced)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus
    val addedMetrics = mutableListOf<MetricSample>()
    var failMetricUploads = false
    var completedHasRealTrainerData: Boolean? = null
    private var sessionCounter = 0

    override suspend fun startSession(workoutId: String): WorkoutSession {
        _syncStatus.value = SyncStatus.PendingSync
        sessionCounter += 1
        return emptySession("local-$workoutId-$sessionCounter", workoutId)
    }

    override suspend fun pauseSession(sessionId: String) {
        _syncStatus.value = SyncStatus.PendingSync
    }

    override suspend fun resumeSession(sessionId: String) {
        _syncStatus.value = SyncStatus.PendingSync
    }

    override suspend fun addMetric(sessionId: String, sample: MetricSample) {
        addMetrics(sessionId, listOf(sample))
    }

    override suspend fun addMetrics(sessionId: String, samples: List<MetricSample>) {
        if (failMetricUploads) error("Backend unavailable")
        addedMetrics += samples
    }

    override suspend fun completeSession(
        sessionId: String,
        elapsedSeconds: Int?,
        hasRealTrainerData: Boolean,
    ): WorkoutSession {
        _syncStatus.value = SyncStatus.PendingSync
        completedHasRealTrainerData = hasRealTrainerData
        return emptySession(sessionId, TestWorkoutId).copy(
            elapsedSeconds = elapsedSeconds ?: 0,
            hasRealTrainerData = hasRealTrainerData,
        )
    }

    override suspend fun getSessionMetrics(sessionId: String): List<MetricSample> = addedMetrics
    override suspend fun getSessionSummary(sessionId: String): WorkoutSession = emptySession(sessionId, TestWorkoutId)
    override suspend fun syncPending() {
        _syncStatus.value = if (failMetricUploads) SyncStatus.SyncFailed else SyncStatus.Synced
    }

    private fun emptySession(id: String, workoutId: String): WorkoutSession {
        return WorkoutSession(
            id = id,
            workoutId = workoutId,
            workoutName = workoutId,
            elapsedSeconds = 0,
            averagePowerWatts = 0,
            normalizedPowerWatts = 0,
            calories = 0,
            tss = 0,
            completionPercent = 0,
        )
    }
}

private class FakeTrainerConnectionRepository : TrainerConnectionRepository {
    private val trainer = SmartTrainerDevice(
        id = "trainer-1",
        name = "Test Trainer",
        rssi = -42,
        supportsErg = true,
        capabilities = setOf(TrainerCapability.POWER, TrainerCapability.CADENCE, TrainerCapability.ERG),
    )
    private val _devices = MutableStateFlow(listOf(trainer))
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _connectedDevice = MutableStateFlow<SmartTrainerDevice?>(null)
    private val _metrics = MutableStateFlow(TrainerMetrics(powerWatts = 190, cadence = 88, heartRate = 130))
    private val _controlState = MutableStateFlow(TrainerControlState())
    private val _error = MutableStateFlow<TrainerError?>(null)
    var failReconnect = false

    override val devices: StateFlow<List<SmartTrainerDevice>> = _devices
    override val connectionState: StateFlow<ConnectionState> = _connectionState
    override val connectedDevice: StateFlow<SmartTrainerDevice?> = _connectedDevice
    override val metrics: StateFlow<TrainerMetrics> = _metrics
    override val controlState: StateFlow<TrainerControlState> = _controlState
    override val error: StateFlow<TrainerError?> = _error

    override suspend fun scan(): List<SmartTrainerDevice> = _devices.value
    override suspend fun startScan() {
        _connectionState.value = ConnectionState.SCANNING
    }
    override suspend fun stopScan() {
        _connectionState.value = if (_connectedDevice.value == null) ConnectionState.DISCONNECTED else ConnectionState.CONNECTED
    }

    override suspend fun connect(deviceId: String) {
        if (failReconnect) {
            _error.value = TrainerError(TrainerErrorType.CONNECTION_FAILED, "Reconnect failed")
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }
        setConnected()
    }

    override suspend fun disconnect() {
        setDisconnected()
    }

    override suspend fun reconnect() {
        connect(trainer.id)
    }

    fun setConnected() {
        val connected = trainer.copy(connectionState = ConnectionState.CONNECTED)
        _connectedDevice.value = connected
        _devices.value = listOf(connected)
        _connectionState.value = ConnectionState.CONNECTED
        _error.value = null
    }

    fun setDisconnected() {
        _connectedDevice.value = null
        _devices.value = listOf(trainer.copy(connectionState = ConnectionState.DISCONNECTED))
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun setControlState(controlState: TrainerControlState) {
        _controlState.value = controlState
    }

    fun setMetrics(metrics: TrainerMetrics) {
        _metrics.value = metrics
    }

    fun setError(error: TrainerError?) {
        _error.value = error
    }
}

private class FakeTrainerControlService(
    private val trainer: FakeTrainerConnectionRepository,
) : TrainerControlService {
    override val controlState: StateFlow<TrainerControlState> = trainer.controlState
    var failCommands = false

    override suspend fun enableErgMode() {
        failIfNeeded()
        trainer.setControlState(controlState.value.copy(ergEnabled = true))
    }

    override suspend fun setTargetPower(watts: Int) {
        failIfNeeded()
        trainer.setControlState(controlState.value.copy(ergEnabled = true, targetPower = watts))
    }

    override suspend fun disableErgMode() {
        trainer.setControlState(controlState.value.copy(ergEnabled = false, targetPower = 0))
    }

    override suspend fun setResistance(resistance: Int) {
        failIfNeeded()
        trainer.setControlState(TrainerControlState(ergEnabled = false, currentResistance = resistance))
    }

    private fun failIfNeeded() {
        if (failCommands) {
            trainer.setError(TrainerError(TrainerErrorType.ERG_COMMAND_FAILED, "ERG command failed"))
            error("ERG command failed")
        }
    }
}

private const val TestWorkoutId = "workout-reliability"
