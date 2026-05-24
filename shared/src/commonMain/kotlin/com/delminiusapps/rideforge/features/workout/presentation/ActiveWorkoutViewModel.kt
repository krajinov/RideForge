package com.delminiusapps.rideforge.features.workout.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.data.local.StoredActiveWorkout
import com.delminiusapps.rideforge.data.local.WorkoutControlMode
import com.delminiusapps.rideforge.data.local.WorkoutLocalStorage
import com.delminiusapps.rideforge.data.mock.ActiveWorkoutState
import com.delminiusapps.rideforge.data.mock.MockWorkoutEngine
import com.delminiusapps.rideforge.data.repository.sync.MetricSampleBatchUploader
import com.delminiusapps.rideforge.domain.trainer.ConnectionState
import com.delminiusapps.rideforge.domain.trainer.SmartTrainerDevice
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
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.SyncStatus
import com.delminiusapps.rideforge.models.WorkoutSession
import com.delminiusapps.rideforge.utils.RideMetricCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class ActiveWorkoutViewModel(
    private val getWorkoutUseCase: GetWorkoutUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val startWorkoutSessionUseCase: StartWorkoutSessionUseCase,
    private val pauseWorkoutSessionUseCase: PauseWorkoutSessionUseCase,
    private val resumeWorkoutSessionUseCase: ResumeWorkoutSessionUseCase,
    private val completeWorkoutSessionUseCase: CompleteWorkoutSessionUseCase,
    private val observeSessionSyncStatusUseCase: ObserveSessionSyncStatusUseCase,
    private val syncPendingSessionsUseCase: SyncPendingSessionsUseCase,
    private val metricSampleBatchUploader: MetricSampleBatchUploader,
    private val trainerConnectionRepository: TrainerConnectionRepository,
    private val trainerControlService: TrainerControlService,
    private val workoutLocalStorage: WorkoutLocalStorage,
    private val workoutId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ActiveWorkoutUiState>(ActiveWorkoutUiState.Loading)
    val uiState: StateFlow<ActiveWorkoutUiState> = _uiState.asStateFlow()

    private var engine: MockWorkoutEngine? = null
    private var session: WorkoutSession? = null
    private var uploadJob: Job? = null
    private var countdownJob: Job? = null
    private var targetResendJob: Job? = null
    private var reconnectJob: Job? = null
    private var stoppedPedalingJob: Job? = null
    private var resumePedalingJob: Job? = null
    private var latestSyncStatus = SyncStatus.Synced
    private var latestTrainerConnectionState = ConnectionState.DISCONNECTED
    private var latestTrainerMetrics = TrainerMetrics()
    private var latestTrainerControlState = TrainerControlState()
    private var latestConnectedDevice: SmartTrainerDevice? = null
    private var latestTrainerError: TrainerError? = null
    private var lastKnownTrainerDeviceId: String? = null
    private var lastSentTargetPower: Int? = null
    private var liveSamples: List<MetricSample> = emptyList()
    private var riderWeightKg = RideMetricCalculator.DefaultRiderWeightKg
    private var totalDistanceKm = 0.0
    private var phase = ActiveWorkoutPhase.PRE_WORKOUT
    private var selectedControlMode = WorkoutControlMode.SIMULATION
    private var controlModeSelectedByUser = false
    private var countdownSeconds: Int? = null
    private var isCheckingDevice = false
    private var isReconnectingTrainer = false
    private var ergControlEnabled = false
    private var ergCommandFailed = false
    private var autoPausedForStoppedPedaling = false
    private var recordedRealTrainerData = false
    private var didFinalizeCompletion = false
    private var completionSessionId: String? = null
    private var resumedFromStorage = false
    private val activeWorkoutPersistenceMutex = Mutex()
    private var lastQueuedActiveWorkoutKey: ActiveWorkoutPersistenceKey? = null
    private var lastSavedActiveWorkoutKey: ActiveWorkoutPersistenceKey? = null
    private var activeWorkoutPersistenceGeneration = 0L

    init {
        observeSyncStatus()
        observeTrainer()
        loadWorkout()
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            observeSessionSyncStatusUseCase().collect { status ->
                latestSyncStatus = status
                updateReadyState()
            }
        }
    }

    private fun observeTrainer() {
        viewModelScope.launch {
            combine(
                trainerConnectionRepository.connectionState,
                trainerConnectionRepository.metrics,
                trainerConnectionRepository.controlState,
                trainerConnectionRepository.connectedDevice,
                trainerConnectionRepository.error,
            ) { connectionState, metrics, controlState, connectedDevice, error ->
                TrainerSnapshot(connectionState, metrics, controlState, connectedDevice, error)
            }.collect { snapshot ->
                latestTrainerConnectionState = snapshot.connectionState
                latestTrainerMetrics = snapshot.metrics
                latestTrainerControlState = snapshot.controlState
                latestConnectedDevice = snapshot.connectedDevice
                latestTrainerError = snapshot.error
                if (snapshot.connectedDevice != null) {
                    lastKnownTrainerDeviceId = snapshot.connectedDevice.id
                }
                maybeDefaultToTrainerMode(snapshot)
                if (snapshot.error?.type == TrainerErrorType.PERMISSION_DENIED) {
                    selectedControlMode = WorkoutControlMode.SIMULATION
                    ergControlEnabled = false
                    autoPausedForStoppedPedaling = false
                }
                maybeReconnectAfterDisconnect()
                evaluatePedalingPauseState()
                updateReadyState()
            }
        }
    }

    private fun loadWorkout() {
        viewModelScope.launch {
            runCatching {
                val storedWorkout = workoutLocalStorage.getActiveWorkout()
                val requestedWorkoutId = workoutId.ifBlank { storedWorkout?.workoutId.orEmpty() }
                val workout = getWorkoutUseCase(requestedWorkoutId)
                val matchingStoredWorkout = storedWorkout?.takeIf { it.workoutId == workout.id }
                val user = runCatching { getCurrentUserUseCase() }.getOrNull()
                val ftp = matchingStoredWorkout?.ftpWatts ?: user?.ftpWatts ?: 240
                riderWeightKg = matchingStoredWorkout?.riderWeightKg
                    ?: user?.weightKg
                    ?: RideMetricCalculator.DefaultRiderWeightKg
                val restoredElapsedSeconds = matchingStoredWorkout
                    ?.let { restoredElapsedSeconds(it, workout.intervals.sumOf { interval -> interval.durationSeconds }) }
                    ?: 0

                resumedFromStorage = matchingStoredWorkout != null
                controlModeSelectedByUser = matchingStoredWorkout != null
                selectedControlMode = matchingStoredWorkout?.controlMode
                    ?: if (latestTrainerConnectionState == ConnectionState.CONNECTED) WorkoutControlMode.TRAINER else WorkoutControlMode.SIMULATION
                ergControlEnabled = matchingStoredWorkout?.ergEnabled ?: (selectedControlMode == WorkoutControlMode.TRAINER)
                liveSamples = matchingStoredWorkout?.samples.orEmpty()
                totalDistanceKm = matchingStoredWorkout?.distanceKm
                    ?: RideMetricCalculator.distanceKm(liveSamples)
                    ?: 0.0
                recordedRealTrainerData = matchingStoredWorkout?.controlMode == WorkoutControlMode.TRAINER &&
                    liveSamples.any { it.hasTrainerSignal() }
                phase = if (matchingStoredWorkout != null) ActiveWorkoutPhase.ACTIVE else ActiveWorkoutPhase.PRE_WORKOUT
                session = matchingStoredWorkout?.let {
                    WorkoutSession(
                        id = it.sessionId,
                        workoutId = it.workoutId,
                        workoutName = workout.name,
                        elapsedSeconds = restoredElapsedSeconds,
                        averagePowerWatts = 0,
                        normalizedPowerWatts = 0,
                        calories = 0,
                        tss = 0,
                        completionPercent = 0,
                    )
                }

                val newEngine = MockWorkoutEngine(
                    workout = workout,
                    ftpWatts = ftp,
                    scope = viewModelScope,
                    initialElapsedSeconds = restoredElapsedSeconds,
                    initialSamples = matchingStoredWorkout?.samples.orEmpty(),
                    initiallyPaused = matchingStoredWorkout?.isPaused ?: true,
                )
                engine = newEngine
                didFinalizeCompletion = false
                completionSessionId = null
                lastSentTargetPower = null

                session?.id?.let(::startMetricUploadLoop)
                if (matchingStoredWorkout == null) {
                    runPreWorkoutDeviceCheck()
                } else if (!matchingStoredWorkout.isPaused && !newEngine.state.value.isComplete) {
                    newEngine.start()
                }

                viewModelScope.launch {
                    newEngine.state.collect { engineState ->
                        onEngineState(engineState)
                    }
                }
                updateReadyState(newEngine.state.value)
            }.onFailure {
                _uiState.value = ActiveWorkoutUiState.Error
            }
        }
    }

    fun onAction(action: ActiveWorkoutAction) {
        when (action) {
            ActiveWorkoutAction.RetryDeviceCheck -> runPreWorkoutDeviceCheck()
            is ActiveWorkoutAction.SelectControlMode -> selectControlMode(action.mode)
            ActiveWorkoutAction.Start -> startWorkout()
            ActiveWorkoutAction.Pause -> pauseWorkout()
            ActiveWorkoutAction.Resume -> resumeWorkout()
            ActiveWorkoutAction.SkipInterval -> engine?.skipInterval()
            ActiveWorkoutAction.ToggleErg -> toggleErg()
            ActiveWorkoutAction.End -> endWorkout()
            ActiveWorkoutAction.FlushActiveSnapshot -> flushActiveWorkoutSnapshot()
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        uploadJob?.cancel()
        reconnectJob?.cancel()
        stoppedPedalingJob?.cancel()
        resumePedalingJob?.cancel()
        engine?.stop()
        viewModelScope.launch {
            runCatching { trainerControlService.disableErgMode() }
        }
        super.onCleared()
    }

    private fun selectControlMode(mode: WorkoutControlMode) {
        controlModeSelectedByUser = true
        selectedControlMode = mode
        ergControlEnabled = mode == WorkoutControlMode.TRAINER
        ergCommandFailed = false
        autoPausedForStoppedPedaling = false
        stoppedPedalingJob?.cancel()
        resumePedalingJob?.cancel()
        latestTrainerError = null // Clear any stale trainer errors when switching modes
        updateReadyState()
    }

    private fun runPreWorkoutDeviceCheck() {
        if (isCheckingDevice) return
        isCheckingDevice = true
        updateReadyState()
        viewModelScope.launch {
            runCatching {
                if (latestTrainerConnectionState != ConnectionState.CONNECTED) {
                    trainerConnectionRepository.scan()
                }
            }.onFailure { error ->
                if (latestTrainerError == null) {
                    latestTrainerError = TrainerError(TrainerErrorType.CONNECTION_FAILED, error.message ?: "Trainer check failed.")
                }
            }
            isCheckingDevice = false
            if (latestTrainerConnectionState != ConnectionState.CONNECTED) {
                selectedControlMode = WorkoutControlMode.SIMULATION
                ergControlEnabled = false
            } else {
                maybeDefaultToTrainerMode(
                    TrainerSnapshot(
                        connectionState = latestTrainerConnectionState,
                        metrics = latestTrainerMetrics,
                        controlState = latestTrainerControlState,
                        connectedDevice = latestConnectedDevice,
                        error = latestTrainerError,
                    ),
                )
            }
            updateReadyState()
        }
    }

    private fun maybeDefaultToTrainerMode(snapshot: TrainerSnapshot) {
        if (
            phase == ActiveWorkoutPhase.PRE_WORKOUT &&
            !resumedFromStorage &&
            !controlModeSelectedByUser &&
            snapshot.connectionState == ConnectionState.CONNECTED &&
            snapshot.connectedDevice != null &&
            snapshot.error?.type != TrainerErrorType.PERMISSION_DENIED
        ) {
            selectedControlMode = WorkoutControlMode.TRAINER
            ergControlEnabled = true
            ergCommandFailed = false
        }
    }

    private fun startWorkout() {
        val currentEngine = engine ?: return
        if (phase != ActiveWorkoutPhase.PRE_WORKOUT || countdownJob?.isActive == true) return

        if (selectedControlMode == WorkoutControlMode.TRAINER && !isTrainerErgReady()) {
            ergCommandFailed = true
            updateReadyState()
            return
        }

        countdownJob = viewModelScope.launch {
            val startedSession = ensureSessionStarted() ?: return@launch
            startMetricUploadLoop(startedSession.id)
            if (selectedControlMode == WorkoutControlMode.TRAINER) {
                val ergReady = prepareErg(currentEngine.state.value.sample.targetPowerWatts)
                if (!ergReady) {
                    phase = ActiveWorkoutPhase.PRE_WORKOUT
                    countdownSeconds = null
                    updateReadyState()
                    return@launch
                }
            }

            phase = ActiveWorkoutPhase.COUNTDOWN
            for (second in 3 downTo 1) {
                countdownSeconds = second
                updateReadyState()
                delay(1_000)
            }
            countdownSeconds = null
            phase = ActiveWorkoutPhase.ACTIVE
            autoPausedForStoppedPedaling = false
            currentEngine.resume()
            currentEngine.start()
            persistActiveWorkout(currentEngine.state.value, force = true)
            updateReadyState()
        }
    }

    private fun pauseWorkout(autoPaused: Boolean = false) {
        val currentEngine = engine ?: return
        if (phase != ActiveWorkoutPhase.ACTIVE) return
        autoPausedForStoppedPedaling = autoPaused
        stoppedPedalingJob?.cancel()
        stoppedPedalingJob = null
        resumePedalingJob?.cancel()
        resumePedalingJob = null
        currentEngine.pause()
        persistActiveWorkout(currentEngine.state.value, force = true)
        session?.id?.let { sessionId ->
            viewModelScope.launch {
                metricSampleBatchUploader.flush(sessionId)
                runCatching { pauseWorkoutSessionUseCase(sessionId) }
                runCatching { syncPendingSessionsUseCase() }
            }
        }
    }

    private fun resumeWorkout() {
        val currentEngine = engine ?: return
        if (phase != ActiveWorkoutPhase.ACTIVE) return
        autoPausedForStoppedPedaling = false
        stoppedPedalingJob?.cancel()
        stoppedPedalingJob = null
        resumePedalingJob?.cancel()
        resumePedalingJob = null
        currentEngine.start()
        currentEngine.resume()
        persistActiveWorkout(currentEngine.state.value, force = true)
        syncTrainerTarget(currentEngine.state.value, force = true)
        session?.id?.let { sessionId ->
            viewModelScope.launch {
                runCatching { resumeWorkoutSessionUseCase(sessionId) }
                runCatching { syncPendingSessionsUseCase() }
            }
        }
    }

    private fun toggleErg() {
        if (selectedControlMode != WorkoutControlMode.TRAINER) return
        val currentEngine = engine ?: return
        if (latestTrainerConnectionState != ConnectionState.CONNECTED) {
            ergCommandFailed = true
            updateReadyState()
            return
        }

        ergControlEnabled = !ergControlEnabled
        updateReadyState()
        viewModelScope.launch {
            val result = if (ergControlEnabled) {
                runCatching {
                    trainerControlService.setTargetPower(currentEngine.state.value.sample.targetPowerWatts)
                }
            } else {
                runCatching { trainerControlService.disableErgMode() }
            }
            result.onFailure {
                ergCommandFailed = true
                updateReadyState()
            }.onSuccess {
                ergCommandFailed = false
                lastSentTargetPower = if (ergControlEnabled) currentEngine.state.value.sample.targetPowerWatts else null
                persistActiveWorkout(currentEngine.state.value, force = true)
                updateReadyState()
            }
        }
    }

    private fun endWorkout() {
        val currentEngine = engine ?: return
        autoPausedForStoppedPedaling = false
        stoppedPedalingJob?.cancel()
        resumePedalingJob?.cancel()
        currentEngine.end()
    }

    private fun onEngineState(engineState: ActiveWorkoutState) {
        val displaySample = sampleFor(engineState)
        appendLiveSample(displaySample)
        if (
            phase == ActiveWorkoutPhase.ACTIVE &&
            selectedControlMode == WorkoutControlMode.TRAINER &&
            latestTrainerConnectionState == ConnectionState.CONNECTED &&
            displaySample.hasTrainerSignal()
        ) {
            recordedRealTrainerData = true
        }
        evaluatePedalingPauseState(engineState)
        if (phase == ActiveWorkoutPhase.ACTIVE && !engineState.isPaused && !engineState.isComplete) {
            session?.id?.let { sessionId ->
                viewModelScope.launch { metricSampleBatchUploader.record(sessionId, displaySample) }
            }
            persistActiveWorkout(engineState)
            syncTrainerTarget(engineState)
        }
        if (engineState.isComplete && !didFinalizeCompletion) {
            finalizeCompletion(engineState)
        }
        updateReadyState(engineState)
    }

    private suspend fun ensureSessionStarted(): WorkoutSession? {
        session?.let { return it }
        return runCatching {
            startWorkoutSessionUseCase(engine?.state?.value?.workout?.id ?: workoutId)
        }.onSuccess {
            session = it
            latestSyncStatus = SyncStatus.PendingSync
        }.getOrElse {
            _uiState.value = ActiveWorkoutUiState.Error
            null
        }
    }

    private fun startMetricUploadLoop(sessionId: String) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            while (true) {
                delay(MetricUploadIntervalMillis)
                metricSampleBatchUploader.flush(sessionId)
                runCatching { syncPendingSessionsUseCase() }
            }
        }
    }

    private fun syncTrainerTarget(engineState: ActiveWorkoutState, force: Boolean = false) {
        if (
            phase != ActiveWorkoutPhase.ACTIVE ||
            selectedControlMode != WorkoutControlMode.TRAINER ||
            !ergControlEnabled ||
            latestTrainerConnectionState != ConnectionState.CONNECTED ||
            engineState.isPaused ||
            engineState.isComplete ||
            latestConnectedDevice?.supportsErg == false
        ) {
            return
        }
        val targetPower = engineState.sample.targetPowerWatts
        if (!force && targetPower == lastSentTargetPower) return

        lastSentTargetPower = targetPower
        viewModelScope.launch {
            runCatching {
                // Removed redundant enableErgMode() call that was causing command racing
                trainerControlService.setTargetPower(targetPower)
            }.onFailure {
                ergCommandFailed = true
                updateReadyState()
            }.onSuccess {
                ergCommandFailed = false
                updateReadyState()
            }
        }
    }

    private suspend fun prepareErg(targetPower: Int): Boolean {
        if (selectedControlMode != WorkoutControlMode.TRAINER) return true
        val result = runCatching {
            // Removed redundant enableErgMode() here as setTargetPower handles control point initiation
            trainerControlService.setTargetPower(targetPower)
        }
        return result.fold(
            onSuccess = {
                ergCommandFailed = false
                ergControlEnabled = true
                lastSentTargetPower = targetPower
                true
            },
            onFailure = {
                ergCommandFailed = true
                false
            },
        )
    }

    private fun maybeReconnectAfterDisconnect() {
        if (
            phase != ActiveWorkoutPhase.ACTIVE ||
            selectedControlMode != WorkoutControlMode.TRAINER ||
            latestTrainerConnectionState != ConnectionState.DISCONNECTED ||
            reconnectJob?.isActive == true
        ) {
            return
        }

        reconnectJob = viewModelScope.launch {
            isReconnectingTrainer = true
            updateReadyState()
            repeat(ReconnectAttempts) {
                val deviceId = lastKnownTrainerDeviceId
                runCatching {
                    if (deviceId != null) {
                        trainerConnectionRepository.connect(deviceId)
                    } else {
                        trainerConnectionRepository.reconnect()
                    }
                }
                delay(ReconnectWindowMillis)
                if (latestTrainerConnectionState == ConnectionState.CONNECTED) {
                    isReconnectingTrainer = false
                    syncTrainerTarget(engine?.state?.value ?: return@launch, force = true)
                    updateReadyState()
                    return@launch
                }
            }
            isReconnectingTrainer = false
            selectedControlMode = WorkoutControlMode.SIMULATION
            ergControlEnabled = false
            runCatching { trainerControlService.disableErgMode() }
            persistActiveWorkout(engine?.state?.value ?: return@launch, force = true)
            updateReadyState()
        }
    }

    private fun finalizeCompletion(engineState: ActiveWorkoutState) {
        val sessionId = session?.id ?: return
        didFinalizeCompletion = true
        targetResendJob?.cancel()
        uploadJob?.cancel()
        viewModelScope.launch {
            metricSampleBatchUploader.flush(sessionId)
            val completedSession = runCatching {
                completeWorkoutSessionUseCase(sessionId, engineState.elapsedSeconds, recordedRealTrainerData)
            }.getOrElse {
                session ?: return@launch
            }
            completionSessionId = completedSession.id
            runCatching { syncPendingSessionsUseCase() }
            runCatching { trainerControlService.disableErgMode() }
            clearPersistedActiveWorkout()
            updateReadyState(engineState)
        }
    }

    private fun flushActiveWorkoutSnapshot() {
        engine?.state?.value?.let { engineState ->
            persistActiveWorkout(engineState, force = true)
        }
    }

    private fun persistActiveWorkout(engineState: ActiveWorkoutState, force: Boolean = false) {
        val currentSession = session ?: return
        if (engineState.isComplete || didFinalizeCompletion) return
        val samples = resolvedSamples(engineState).takeLast(96)
        val workout = StoredActiveWorkout(
            workoutId = engineState.workout.id,
            sessionId = currentSession.id,
            ftpWatts = engineState.ftpWatts,
            elapsedSeconds = engineState.elapsedSeconds,
            samples = samples,
            controlMode = selectedControlMode,
            isPaused = engineState.isPaused,
            ergEnabled = ergControlEnabled,
            updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            riderWeightKg = riderWeightKg,
            distanceKm = totalDistanceKm,
        )
        val key = workout.persistenceKey()
        if (!force && key == lastQueuedActiveWorkoutKey) return

        lastQueuedActiveWorkoutKey = key
        val generation = activeWorkoutPersistenceGeneration
        viewModelScope.launch {
            runCatching {
                activeWorkoutPersistenceMutex.withLock {
                    if (generation != activeWorkoutPersistenceGeneration || didFinalizeCompletion) return@withLock
                    if (key == lastSavedActiveWorkoutKey) return@withLock
                    workoutLocalStorage.saveActiveWorkout(workout)
                    lastSavedActiveWorkoutKey = key
                }
            }.onFailure {
                if (lastQueuedActiveWorkoutKey == key) {
                    lastQueuedActiveWorkoutKey = lastSavedActiveWorkoutKey
                }
            }
        }
    }

    private suspend fun clearPersistedActiveWorkout() {
        activeWorkoutPersistenceGeneration += 1
        lastQueuedActiveWorkoutKey = null
        lastSavedActiveWorkoutKey = null
        activeWorkoutPersistenceMutex.withLock {
            runCatching { workoutLocalStorage.clearActiveWorkout() }
        }
    }

    private fun updateReadyState(engineState: ActiveWorkoutState? = engine?.state?.value) {
        val currentEngineState = engineState ?: return
        val displaySample = sampleFor(currentEngineState)
        _uiState.update { state ->
            when (state) {
                ActiveWorkoutUiState.Loading,
                ActiveWorkoutUiState.Error,
                is ActiveWorkoutUiState.Ready,
                -> ActiveWorkoutUiState.Ready(
                    engineState = currentEngineState,
                    sessionId = session?.id.orEmpty(),
                    syncStatus = latestSyncStatus,
                    displaySample = displaySample,
                    displaySamples = resolvedSamples(currentEngineState),
                    trainerConnectionState = latestTrainerConnectionState,
                    trainerControlState = latestTrainerControlState,
                    connectedDevice = latestConnectedDevice,
                    trainerError = latestTrainerError,
                    usingSimulatedMetrics = selectedControlMode == WorkoutControlMode.SIMULATION ||
                        latestTrainerConnectionState != ConnectionState.CONNECTED,
                    phase = phase,
                    controlMode = selectedControlMode,
                    countdownSeconds = countdownSeconds,
                    isCheckingDevice = isCheckingDevice,
                    isReconnectingTrainer = isReconnectingTrainer,
                    ergControlEnabled = ergControlEnabled,
                    autoPausedForStoppedPedaling = autoPausedForStoppedPedaling,
                    banners = currentBanners(),
                    completionSessionId = completionSessionId,
                    resumedFromStorage = resumedFromStorage,
                    distanceKm = totalDistanceKm,
                )
            }
        }
    }

    private fun sampleFor(engineState: ActiveWorkoutState): MetricSample {
        val trainerMetrics = latestTrainerMetrics
        if (
            selectedControlMode == WorkoutControlMode.SIMULATION ||
            latestTrainerConnectionState != ConnectionState.CONNECTED
        ) {
            return engineState.sample.withCalculatedSpeed()
        }

        return MetricSample(
            elapsedSeconds = engineState.elapsedSeconds,
            currentPowerWatts = trainerMetrics.powerWatts,
            targetPowerWatts = engineState.sample.targetPowerWatts,
            cadenceRpm = trainerMetrics.cadence,
            heartRateBpm = trainerMetrics.heartRate,
            speedKmh = RideMetricCalculator.speedKmh(trainerMetrics.powerWatts, riderWeightKg),
        )
    }

    private fun appendLiveSample(sample: MetricSample) {
        if (sample.elapsedSeconds <= 0) return
        if (liveSamples.lastOrNull()?.elapsedSeconds == sample.elapsedSeconds) {
            liveSamples = liveSamples.dropLast(1) + sample
        } else {
            totalDistanceKm += distanceDeltaForNextSample(sample)
            liveSamples = liveSamples + sample
        }
        liveSamples = liveSamples.takeLast(96)
    }

    private fun resolvedSamples(engineState: ActiveWorkoutState): List<MetricSample> {
        return liveSamples.takeIf { it.isNotEmpty() }
            ?: engineState.samples.map { it.withCalculatedSpeed() }
    }

    private fun MetricSample.withCalculatedSpeed(): MetricSample =
        copy(speedKmh = RideMetricCalculator.speedKmh(currentPowerWatts, riderWeightKg))

    private fun distanceDeltaForNextSample(sample: MetricSample): Double {
        val previous = liveSamples.lastOrNull() ?: MetricSample(
            elapsedSeconds = 0,
            currentPowerWatts = 0,
            targetPowerWatts = sample.targetPowerWatts,
            cadenceRpm = 0,
            heartRateBpm = 0,
            speedKmh = sample.speedKmh,
        )
        return RideMetricCalculator.distanceDeltaKm(previous, sample)
    }

    private fun currentBanners(): List<ActiveWorkoutBanner> = buildList {
        if (latestTrainerError?.type == TrainerErrorType.PERMISSION_DENIED) {
            add(ActiveWorkoutBanner.BluetoothPermissionMissing)
        }
        if (
            phase == ActiveWorkoutPhase.ACTIVE &&
            selectedControlMode == WorkoutControlMode.TRAINER &&
            latestTrainerConnectionState != ConnectionState.CONNECTED
        ) {
            add(ActiveWorkoutBanner.TrainerDisconnected)
        }
        if (ergCommandFailed || latestTrainerError?.type == TrainerErrorType.ERG_COMMAND_FAILED) {
            add(ActiveWorkoutBanner.ErgCommandFailed)
        }
        if (latestSyncStatus == SyncStatus.SyncFailed) {
            add(ActiveWorkoutBanner.BackendSyncPending)
        }
    }.distinct()

    private fun evaluatePedalingPauseState(engineState: ActiveWorkoutState? = engine?.state?.value) {
        val currentEngineState = engineState ?: return
        if (shouldAutoResumeFromPedaling(currentEngineState)) {
            stoppedPedalingJob?.cancel()
            stoppedPedalingJob = null
            if (resumePedalingJob?.isActive != true) {
                resumePedalingJob = viewModelScope.launch {
                    delay(PedalingResumeDelayMillis)
                    if (shouldAutoResumeFromPedaling(engine?.state?.value ?: return@launch)) {
                        resumeWorkout()
                    }
                }
            }
            return
        } else if (autoPausedForStoppedPedaling) {
            resumePedalingJob?.cancel()
            resumePedalingJob = null
            return
        }

        if (shouldAutoPauseForStoppedPedaling(currentEngineState)) {
            if (stoppedPedalingJob?.isActive != true) {
                stoppedPedalingJob = viewModelScope.launch {
                    delay(StoppedPedalingPauseDelayMillis)
                    if (shouldAutoPauseForStoppedPedaling(engine?.state?.value ?: return@launch)) {
                        pauseWorkout(autoPaused = true)
                    }
                }
            }
        } else {
            stoppedPedalingJob?.cancel()
            stoppedPedalingJob = null
        }
    }

    private fun shouldAutoPauseForStoppedPedaling(engineState: ActiveWorkoutState): Boolean {
        return phase == ActiveWorkoutPhase.ACTIVE &&
            selectedControlMode == WorkoutControlMode.TRAINER &&
            latestTrainerConnectionState == ConnectionState.CONNECTED &&
            !engineState.isPaused &&
            !engineState.isComplete &&
            latestTrainerMetrics.cadence <= StoppedCadenceRpm &&
            latestTrainerMetrics.powerWatts <= StoppedPowerWatts
    }

    private fun shouldAutoResumeFromPedaling(engineState: ActiveWorkoutState): Boolean {
        return autoPausedForStoppedPedaling &&
            phase == ActiveWorkoutPhase.ACTIVE &&
            selectedControlMode == WorkoutControlMode.TRAINER &&
            latestTrainerConnectionState == ConnectionState.CONNECTED &&
            engineState.isPaused &&
            !engineState.isComplete &&
            (latestTrainerMetrics.cadence >= ResumeCadenceRpm || latestTrainerMetrics.powerWatts >= ResumePowerWatts)
    }

    private fun restoredElapsedSeconds(
        storedWorkout: StoredActiveWorkout,
        totalSeconds: Int,
    ): Int {
        if (storedWorkout.isPaused) {
            return storedWorkout.elapsedSeconds.coerceIn(0, totalSeconds)
        }
        val backgroundSeconds = ((Clock.System.now().toEpochMilliseconds() - storedWorkout.updatedAtEpochMillis) / 1_000L)
            .coerceAtLeast(0L)
            .toInt()
        return (storedWorkout.elapsedSeconds + backgroundSeconds).coerceIn(0, totalSeconds)
    }

    private fun isTrainerErgReady(): Boolean {
        return latestTrainerConnectionState == ConnectionState.CONNECTED &&
            latestConnectedDevice?.supportsErg != false &&
            latestTrainerError?.type != TrainerErrorType.PERMISSION_DENIED
    }

    private companion object {
        const val MetricUploadIntervalMillis = 5_000L
        const val TargetResendIntervalMillis = 4_000L
        const val ReconnectAttempts = 3
        const val ReconnectWindowMillis = 3_500L
        const val StoppedPedalingPauseDelayMillis = 6_000L
        const val PedalingResumeDelayMillis = 2_000L
        const val StoppedCadenceRpm = 0
        const val StoppedPowerWatts = 10
        const val ResumeCadenceRpm = 15
        const val ResumePowerWatts = 25
    }
}

private data class TrainerSnapshot(
    val connectionState: ConnectionState,
    val metrics: TrainerMetrics,
    val controlState: TrainerControlState,
    val connectedDevice: SmartTrainerDevice?,
    val error: TrainerError?,
)

private data class ActiveWorkoutPersistenceKey(
    val workoutId: String,
    val sessionId: String,
    val ftpWatts: Int,
    val elapsedSeconds: Int,
    val samples: List<MetricSample>,
    val controlMode: WorkoutControlMode,
    val isPaused: Boolean,
    val ergEnabled: Boolean,
    val riderWeightKg: Double,
    val distanceKm: Double,
)

private fun StoredActiveWorkout.persistenceKey(): ActiveWorkoutPersistenceKey {
    return ActiveWorkoutPersistenceKey(
        workoutId = workoutId,
        sessionId = sessionId,
        ftpWatts = ftpWatts,
        elapsedSeconds = elapsedSeconds,
        samples = samples,
        controlMode = controlMode,
        isPaused = isPaused,
        ergEnabled = ergEnabled,
        riderWeightKg = riderWeightKg,
        distanceKm = distanceKm,
    )
}

private fun MetricSample.hasTrainerSignal(): Boolean =
    currentPowerWatts > 0 || cadenceRpm > 0 || heartRateBpm > 0 || speedKmh > 0.0

enum class ActiveWorkoutPhase {
    PRE_WORKOUT,
    COUNTDOWN,
    ACTIVE,
}

enum class ActiveWorkoutBanner {
    TrainerDisconnected,
    ErgCommandFailed,
    BackendSyncPending,
    BluetoothPermissionMissing,
}

sealed interface ActiveWorkoutUiState {
    data object Loading : ActiveWorkoutUiState
    data class Ready(
        val engineState: ActiveWorkoutState,
        val sessionId: String,
        val syncStatus: SyncStatus,
        val displaySample: MetricSample,
        val displaySamples: List<MetricSample>,
        val trainerConnectionState: ConnectionState,
        val trainerControlState: TrainerControlState,
        val connectedDevice: SmartTrainerDevice?,
        val trainerError: TrainerError?,
        val usingSimulatedMetrics: Boolean,
        val phase: ActiveWorkoutPhase,
        val controlMode: WorkoutControlMode,
        val countdownSeconds: Int?,
        val isCheckingDevice: Boolean,
        val isReconnectingTrainer: Boolean,
        val ergControlEnabled: Boolean,
        val autoPausedForStoppedPedaling: Boolean,
        val banners: List<ActiveWorkoutBanner>,
        val completionSessionId: String?,
        val resumedFromStorage: Boolean,
        val distanceKm: Double,
    ) : ActiveWorkoutUiState
    data object Error : ActiveWorkoutUiState
}

sealed interface ActiveWorkoutAction {
    data object RetryDeviceCheck : ActiveWorkoutAction
    data class SelectControlMode(val mode: WorkoutControlMode) : ActiveWorkoutAction
    data object Start : ActiveWorkoutAction
    data object Pause : ActiveWorkoutAction
    data object Resume : ActiveWorkoutAction
    data object SkipInterval : ActiveWorkoutAction
    data object ToggleErg : ActiveWorkoutAction
    data object End : ActiveWorkoutAction
    data object FlushActiveSnapshot : ActiveWorkoutAction
}
