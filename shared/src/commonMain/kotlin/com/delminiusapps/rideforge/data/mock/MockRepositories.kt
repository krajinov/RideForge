package com.delminiusapps.rideforge.data.mock

import com.delminiusapps.rideforge.data.repository.AuthRepository
import com.delminiusapps.rideforge.data.auth.AuthSession
import com.delminiusapps.rideforge.data.auth.AuthTokens
import com.delminiusapps.rideforge.data.mapper.powerZonesForFtp
import com.delminiusapps.rideforge.data.repository.HistoryRepository
import com.delminiusapps.rideforge.data.repository.TrainerConnectionRepository
import com.delminiusapps.rideforge.data.repository.TrainingPlanRepository
import com.delminiusapps.rideforge.data.repository.WorkoutRepository
import com.delminiusapps.rideforge.domain.trainer.ConnectionState
import com.delminiusapps.rideforge.domain.trainer.TrainerControlState
import com.delminiusapps.rideforge.domain.trainer.TrainerError
import com.delminiusapps.rideforge.models.RideHistoryItem
import com.delminiusapps.rideforge.models.SmartTrainerDevice
import com.delminiusapps.rideforge.models.SyncStatus
import com.delminiusapps.rideforge.models.TrainerMetrics
import com.delminiusapps.rideforge.models.TrainingPlan
import com.delminiusapps.rideforge.models.UserProfile
import com.delminiusapps.rideforge.models.WeeklyProgress
import com.delminiusapps.rideforge.models.Workout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MockAuthRepository : AuthRepository {
    private var profile = MockData.userProfile

    override suspend fun register(name: String, email: String, password: String): AuthSession = AuthSession(
        tokens = AuthTokens(accessToken = "mock-access-token", refreshToken = "mock-refresh-token"),
        user = profile.copy(name = name.ifBlank { profile.name }),
    )

    override suspend fun login(email: String, password: String): AuthSession = AuthSession(
        tokens = AuthTokens(accessToken = "mock-access-token", refreshToken = "mock-refresh-token"),
        user = profile,
    )

    override suspend fun restoreSession(): AuthSession? = null

    override suspend fun currentUser(): UserProfile = profile

    override suspend fun updateProfile(ftpWatts: Int, weightKg: Double, units: String): UserProfile {
        profile = profile.copy(
            ftpWatts = ftpWatts,
            weightKg = weightKg,
            units = units.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            powerZones = powerZonesForFtp(ftpWatts),
        )
        return profile
    }

    override suspend fun logout() = Unit
}

class MockWorkoutRepository : WorkoutRepository {
    override suspend fun getWorkouts(): List<Workout> = MockData.workouts
    override suspend fun getWorkoutsForPlan(planId: String): List<Workout> = MockData.workouts
        .filter { it.planId == planId }
        .sortedWith(compareBy<Workout> { it.weekNumber }.thenBy { it.dayNumber })

    override suspend fun getRecommendedWorkout(): Workout = MockData.vo2Workout
    override suspend fun getWorkout(id: String): Workout = MockData.workouts.firstOrNull { it.id == id } ?: MockData.vo2Workout
}

class MockTrainingPlanRepository : TrainingPlanRepository {
    override suspend fun getPlans(): List<TrainingPlan> = MockData.trainingPlans
}

class MockHistoryRepository : HistoryRepository {
    override suspend fun getHistory(): List<RideHistoryItem> = MockData.history
    override suspend fun getWeeklyProgress(): WeeklyProgress = MockData.weeklyProgress
    override suspend fun getLatestWorkoutSummary() = MockData.latestWorkoutSummary
}

class MockTrainerConnectionRepository : TrainerConnectionRepository {
    private val _devices = MutableStateFlow(MockData.trainerDevices)
    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTED)
    private val _connectedDevice = MutableStateFlow<SmartTrainerDevice?>(MockData.trainerDevices.first().copy(connectionState = ConnectionState.CONNECTED))
    private val _metrics = MutableStateFlow(TrainerMetrics(powerWatts = 185, cadence = 88, speedKmh = 32.4, heartRate = 132))
    private val _controlState = MutableStateFlow(TrainerControlState())
    private val _error = MutableStateFlow<TrainerError?>(null)

    override val devices: StateFlow<List<SmartTrainerDevice>> = _devices
    override val connectionState: StateFlow<ConnectionState> = _connectionState
    override val connectedDevice: StateFlow<SmartTrainerDevice?> = _connectedDevice
    override val metrics: StateFlow<TrainerMetrics> = _metrics
    override val controlState: StateFlow<TrainerControlState> = _controlState
    override val error: StateFlow<TrainerError?> = _error

    override suspend fun scan(): List<SmartTrainerDevice> {
        _connectionState.value = ConnectionState.SCANNING
        delay(450)
        _connectionState.value = if (_connectedDevice.value != null) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
        return MockData.trainerDevices
    }

    override suspend fun startScan() {
        scan()
    }

    override suspend fun stopScan() {
        _connectionState.value = if (_connectedDevice.value != null) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
    }

    override suspend fun connect(deviceId: String) {
        val device = MockData.trainerDevices.firstOrNull { it.id == deviceId } ?: MockData.trainerDevices.first()
        _connectedDevice.value = device.copy(connectionState = ConnectionState.CONNECTED)
        _connectionState.value = ConnectionState.CONNECTED
        _metrics.value = TrainerMetrics(powerWatts = 185, cadence = 88, speedKmh = 32.4, heartRate = 132)
    }

    override suspend fun disconnect() {
        _connectedDevice.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun reconnect() {
        MockData.trainerDevices.firstOrNull()?.let { connect(it.id) }
    }
}

class MockSessionRepository : com.delminiusapps.rideforge.data.repository.SessionRepository {
    private val metrics = mutableListOf<com.delminiusapps.rideforge.models.MetricSample>()
    private val _syncStatus = MutableStateFlow(SyncStatus.Synced)

    override val syncStatus: StateFlow<SyncStatus> = _syncStatus

    override suspend fun startSession(workoutId: String): com.delminiusapps.rideforge.models.WorkoutSession {
        metrics.clear()
        return MockData.latestWorkoutSummary.copy(
            id = "local-$workoutId",
            workoutId = workoutId,
            elapsedSeconds = 0,
            averagePowerWatts = 0,
            normalizedPowerWatts = 0,
            calories = 0,
            tss = 0,
            completionPercent = 0,
        )
    }

    override suspend fun pauseSession(sessionId: String) {
        _syncStatus.value = SyncStatus.PendingSync
    }

    override suspend fun resumeSession(sessionId: String) {
        _syncStatus.value = SyncStatus.PendingSync
    }

    override suspend fun addMetric(sessionId: String, sample: com.delminiusapps.rideforge.models.MetricSample) {
        metrics.add(sample)
    }

    override suspend fun addMetrics(sessionId: String, samples: List<com.delminiusapps.rideforge.models.MetricSample>) {
        metrics.addAll(samples)
    }

    override suspend fun completeSession(sessionId: String, elapsedSeconds: Int?): com.delminiusapps.rideforge.models.WorkoutSession {
        val resolvedElapsed = elapsedSeconds ?: metrics.maxOfOrNull { it.elapsedSeconds } ?: 0
        val average = metrics.map { it.currentPowerWatts }.takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 214
        return MockData.latestWorkoutSummary.copy(
            id = sessionId,
            elapsedSeconds = resolvedElapsed,
            averagePowerWatts = average,
            normalizedPowerWatts = (average * 1.10).toInt().coerceAtLeast(average),
            calories = ((average * resolvedElapsed) / 1000.0 * 3.6).toInt().coerceAtLeast(120),
            tss = MockData.latestWorkoutSummary.tss,
            completionPercent = MockData.latestWorkoutSummary.completionPercent,
        )
    }

    override suspend fun getSessionMetrics(sessionId: String): List<com.delminiusapps.rideforge.models.MetricSample> {
        return metrics
    }

    override suspend fun getSessionSummary(sessionId: String): com.delminiusapps.rideforge.models.WorkoutSession {
        return MockData.historySummaries[sessionId] ?: MockData.latestWorkoutSummary.copy(id = sessionId)
    }

    override suspend fun syncPending() {
        _syncStatus.value = SyncStatus.Synced
    }
}
