package com.delminiusapps.rideforge.data.mock

import com.delminiusapps.rideforge.data.repository.AuthRepository
import com.delminiusapps.rideforge.data.auth.AuthSession
import com.delminiusapps.rideforge.data.auth.AuthTokens
import com.delminiusapps.rideforge.data.mapper.powerZonesForFtp
import com.delminiusapps.rideforge.data.repository.HistoryRepository
import com.delminiusapps.rideforge.data.repository.StravaRepository
import com.delminiusapps.rideforge.data.repository.TrainerConnectionRepository
import com.delminiusapps.rideforge.data.repository.TrainingPlanRepository
import com.delminiusapps.rideforge.data.repository.WorkoutRepository
import com.delminiusapps.rideforge.domain.trainer.ConnectionState
import com.delminiusapps.rideforge.domain.trainer.TrainerControlState
import com.delminiusapps.rideforge.domain.trainer.TrainerError
import com.delminiusapps.rideforge.models.RideHistoryItem
import com.delminiusapps.rideforge.models.SmartTrainerDevice
import com.delminiusapps.rideforge.models.StravaConnectionStatus
import com.delminiusapps.rideforge.models.StravaSyncInfo
import com.delminiusapps.rideforge.models.StravaSyncState
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

class MockStravaRepository : StravaRepository {
    private var connected = false
    private val syncs = mutableMapOf<String, StravaSyncInfo>()

    override suspend fun getStatus(): StravaConnectionStatus =
        StravaConnectionStatus(connected = connected, athleteId = if (connected) "mock-athlete" else null)

    override suspend fun getConnectUrl(): String {
        connected = true
        return "https://www.strava.com/oauth/authorize"
    }

    override suspend fun disconnect(): StravaConnectionStatus {
        connected = false
        return StravaConnectionStatus(connected = false)
    }

    override suspend fun syncWorkout(sessionId: String): StravaSyncInfo {
        val sync = StravaSyncInfo(
            state = StravaSyncState.Synced,
            activityId = "mock-$sessionId",
            activityUrl = "https://www.strava.com/activities/mock-$sessionId",
            canSync = true,
            connected = connected,
        )
        syncs[sessionId] = sync
        return sync
    }

    override suspend fun getSyncStatus(sessionId: String): StravaSyncInfo =
        syncs[sessionId] ?: StravaSyncInfo(
            state = StravaSyncState.NotSynced,
            canSync = true,
            connected = connected,
        )
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

    override suspend fun completeSession(
        sessionId: String,
        elapsedSeconds: Int?,
        hasRealTrainerData: Boolean,
    ): com.delminiusapps.rideforge.models.WorkoutSession {
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
            hasRealTrainerData = hasRealTrainerData,
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

class MockAdaptiveRepository : com.delminiusapps.rideforge.domain.repository.AdaptiveRepository {
    override suspend fun getDashboard(): com.delminiusapps.rideforge.models.AdaptiveDashboard {
        return com.delminiusapps.rideforge.models.AdaptiveDashboard(
            fatigue = com.delminiusapps.rideforge.models.FatigueState(ctl = 55.4, atl = 62.1, tsb = -6.7),
            progressionLevels = mapOf(
                "RECOVERY" to 1.0,
                "ENDURANCE" to 3.5,
                "TEMPO" to 2.5,
                "SWEET_SPOT" to 4.2,
                "THRESHOLD" to 3.0,
                "VO2_MAX" to 2.0,
                "OVER_UNDER" to 1.5,
                "RACE_SIMULATION" to 1.0
            ),
            pendingFtpEstimate = com.delminiusapps.rideforge.models.FtpEstimate(
                id = "mock-ftp-estimate",
                estimatedFtp = 248,
                previousFtp = 240,
                message = "Based on your recent sweet spot training, we estimate your FTP has increased from 240 W to 248 W (+3%).",
                createdAt = "2026-05-25T08:00:00Z"
            ),
            recommendation = com.delminiusapps.rideforge.models.AdaptiveRecommendation(
                type = "TRAINING",
                workoutId = "ftp-w1d1",
                title = "Sweet Spot Intro",
                description = "Two 10-minute efforts in your sweet spot zone.",
                reason = "Your fatigue balance is optimal. Time to stimulus your sweet spot progression."
            ),
            insights = listOf(
                "Fatigue is progressing nicely. Focus on maintaining steady cadence consistency.",
                "Your heart rate decoupling was 3.4% on your last ride, indicating good aerobic base support."
            )
        )
    }

    override suspend fun getTrends(): Pair<List<com.delminiusapps.rideforge.models.DailyFatigue>, List<com.delminiusapps.rideforge.models.FtpHistoryRecord>> {
        val fatigue = listOf(
            com.delminiusapps.rideforge.models.DailyFatigue("2026-05-19", 50.0, 52.0, -2.0, 45),
            com.delminiusapps.rideforge.models.DailyFatigue("2026-05-20", 51.2, 54.5, -3.3, 60),
            com.delminiusapps.rideforge.models.DailyFatigue("2026-05-21", 52.0, 50.1, 1.9, 0),
            com.delminiusapps.rideforge.models.DailyFatigue("2026-05-22", 53.4, 58.0, -4.6, 75),
            com.delminiusapps.rideforge.models.DailyFatigue("2026-05-23", 54.1, 62.4, -8.3, 80),
            com.delminiusapps.rideforge.models.DailyFatigue("2026-05-24", 54.8, 55.2, -0.4, 0),
            com.delminiusapps.rideforge.models.DailyFatigue("2026-05-25", 55.4, 62.1, -6.7, 90)
        )
        val ftp = listOf(
            com.delminiusapps.rideforge.models.FtpHistoryRecord("ftp-h1", 240, 235, "approved", "Approved by rider", "2026-05-01T08:00:00Z"),
            com.delminiusapps.rideforge.models.FtpHistoryRecord("ftp-h2", 245, 240, "approved", "Approved by rider", "2026-05-15T08:00:00Z")
        )
        return Pair(fatigue, ftp)
    }

    override suspend fun approveFtpEstimate(id: String): Int = 248

    override suspend fun dismissFtpEstimate(id: String) = Unit

    override suspend fun getSessionAnalysis(sessionId: String): com.delminiusapps.rideforge.models.WorkoutAnalysis {
        return com.delminiusapps.rideforge.models.WorkoutAnalysis(
            sessionId = sessionId,
            completionPercent = 98,
            intervalSuccessRate = 100,
            ergComplianceScore = 94,
            cadenceConsistencyScore = 89,
            powerFade = 1.2,
            hrDrift = 2.4,
            estimatedRpe = 6.0,
            classification = "Successful",
            coachNotesSummary = "Cadence control was a strength in this ride.",
            coachNotesRecommendation = "Progress to the next scheduled intensity workout.",
            coachNotesRecovery = "Moderate stress: keep the next 24 hours aerobic.",
            coachNotesNextWorkout = "Next planned workout",
            avgDeviationPower = 4.2,
            best5sPower = 480,
            best30sPower = 390,
            best1mPower = 340,
            best5mPower = 280,
            best20mPower = 250
        )
    }
}

