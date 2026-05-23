package com.delminiusapps.rideforge.data.repository.remote

import com.delminiusapps.rideforge.core.network.ApiClient
import com.delminiusapps.rideforge.core.network.ApiClientException
import com.delminiusapps.rideforge.core.network.DataSourceMonitor
import com.delminiusapps.rideforge.data.auth.AuthSession
import com.delminiusapps.rideforge.data.dto.PageDto
import com.delminiusapps.rideforge.data.dto.StravaConnectUrlDto
import com.delminiusapps.rideforge.data.dto.StravaStatusDto
import com.delminiusapps.rideforge.data.dto.StravaSyncStatusDto
import com.delminiusapps.rideforge.data.dto.TrainingPlanDto
import com.delminiusapps.rideforge.data.dto.UpdateProfileRequestDto
import com.delminiusapps.rideforge.data.dto.UserDto
import com.delminiusapps.rideforge.data.dto.WorkoutDto
import com.delminiusapps.rideforge.data.dto.WorkoutIntervalDto
import com.delminiusapps.rideforge.data.dto.WorkoutSessionDto
import com.delminiusapps.rideforge.data.mapper.toDomain
import com.delminiusapps.rideforge.data.mapper.toDomainProfile
import com.delminiusapps.rideforge.data.mapper.toDomainSummary
import com.delminiusapps.rideforge.data.mapper.toHistoryItem
import com.delminiusapps.rideforge.data.repository.AuthRepository
import com.delminiusapps.rideforge.data.repository.HistoryRepository
import com.delminiusapps.rideforge.data.repository.StravaRepository
import com.delminiusapps.rideforge.data.repository.TrainingPlanRepository
import com.delminiusapps.rideforge.data.repository.WorkoutRepository
import com.delminiusapps.rideforge.models.RideHistoryItem
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.StravaConnectionStatus
import com.delminiusapps.rideforge.models.StravaSyncInfo
import com.delminiusapps.rideforge.models.SyncStatus
import com.delminiusapps.rideforge.models.TrainingPlan
import com.delminiusapps.rideforge.models.UserProfile
import com.delminiusapps.rideforge.models.WeeklyProgress
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RemoteProfileRepository(
    private val api: ApiClient,
    private val fallback: AuthRepository,
    private val monitor: DataSourceMonitor,
) : AuthRepository {
    override suspend fun register(name: String, email: String, password: String): AuthSession = runCatching {
        val auth = api.register(name = name, email = email, password = password)
        monitor.markRemote()
        AuthSession(
            tokens = com.delminiusapps.rideforge.data.auth.AuthTokens(auth.accessToken, auth.refreshToken),
            user = auth.user.toDomainProfile(),
        )
    }.getOrElse { error ->
        if (error is ApiClientException && error.statusCode < 500) throw error
        monitor.markFallback(error)
        fallback.register(name, email, password)
    }

    override suspend fun login(email: String, password: String): AuthSession = runCatching {
        val auth = api.login(email, password)
        monitor.markRemote()
        AuthSession(
            tokens = com.delminiusapps.rideforge.data.auth.AuthTokens(auth.accessToken, auth.refreshToken),
            user = auth.user.toDomainProfile(),
        )
    }.getOrElse { error ->
        if (error is ApiClientException && error.statusCode < 500) throw error
        monitor.markFallback(error)
        fallback.login(email, password)
    }

    override suspend fun restoreSession(): AuthSession? {
        val storedTokens = api.restoreTokens() ?: return null
        return runCatching {
            val user = api.get<UserDto>("/auth/me").toDomainProfile()
            val currentTokens = api.restoreTokens() ?: storedTokens
            monitor.markRemote()
            AuthSession(currentTokens, user)
        }.getOrElse { error ->
            if (error is ApiClientException && error.statusCode == 401) return null
            monitor.markFallback(error)
            AuthSession(storedTokens, fallback.currentUser())
        }
    }

    override suspend fun currentUser(): UserProfile = remoteOrFallback(
        monitor = monitor,
        fallback = { fallback.currentUser() },
    ) {
        api.get<UserDto>("/profile").toDomainProfile()
    }

    override suspend fun updateProfile(ftpWatts: Int, weightKg: Double, units: String): UserProfile = runCatching {
        api.put<UpdateProfileRequestDto, UserDto>(
            "/profile",
            UpdateProfileRequestDto(
                ftp = ftpWatts,
                weightKg = weightKg,
                units = units.lowercase(),
            ),
        ).toDomainProfile()
    }.onSuccess {
        monitor.markRemote()
    }.getOrElse { error ->
        if (error is ApiClientException && error.statusCode < 500) throw error
        monitor.markFallback(error)
        fallback.updateProfile(ftpWatts, weightKg, units)
    }

    override suspend fun logout() {
        api.logout()
    }
}

class RemoteTrainingPlanRepository(
    private val api: ApiClient,
    private val fallback: TrainingPlanRepository,
    private val monitor: DataSourceMonitor,
) : TrainingPlanRepository {
    override suspend fun getPlans(): List<TrainingPlan> = remoteOrFallback(
        monitor = monitor,
        fallback = { fallback.getPlans() },
    ) {
        api.get<PageDto<TrainingPlanDto>>("/plans").items.map { it.toDomain() }
    }
}

class RemoteWorkoutRepository(
    private val api: ApiClient,
    private val fallback: WorkoutRepository,
    private val monitor: DataSourceMonitor,
) : WorkoutRepository {
    override suspend fun getWorkouts(): List<Workout> = remoteOrFallback(
        monitor = monitor,
        fallback = { fallback.getWorkouts() },
    ) {
        api.get<PageDto<WorkoutDto>>("/workouts").items.map { it.toDomain() }
    }

    override suspend fun getWorkoutsForPlan(planId: String): List<Workout> = remoteOrFallback(
        monitor = monitor,
        fallback = { fallback.getWorkoutsForPlan(planId) },
    ) {
        val workouts = api.get<List<WorkoutDto>>("/plans/$planId/workouts")
        workouts.map { workout ->
            val intervals = if (workout.intervals.isNotEmpty()) {
                workout.intervals.map { it.toDomain() }
            } else {
                api.get<List<WorkoutIntervalDto>>("/workouts/${workout.id}/intervals").map { it.toDomain() }
            }
            workout.toDomain(intervals)
        }.sortedWith(compareBy<Workout> { it.weekNumber }.thenBy { it.dayNumber })
    }

    override suspend fun getRecommendedWorkout(): Workout = remoteOrFallback(
        monitor = monitor,
        fallback = { fallback.getRecommendedWorkout() },
    ) {
        val workout = api.get<WorkoutDto>("/workouts/recommended")
        workout.toDomain(api.get<List<WorkoutIntervalDto>>("/workouts/${workout.id}/intervals").map { it.toDomain() })
    }

    override suspend fun getWorkout(id: String): Workout = remoteOrFallback(
        monitor = monitor,
        fallback = { fallback.getWorkout(id) },
    ) {
        val workout = api.get<WorkoutDto>("/workouts/$id")
        workout.toDomain(api.get<List<WorkoutIntervalDto>>("/workouts/${workout.id}/intervals").map { it.toDomain() })
    }
}

class RemoteHistoryRepository(
    private val api: ApiClient,
    private val fallback: HistoryRepository,
    private val monitor: DataSourceMonitor,
) : HistoryRepository {
    override suspend fun getHistory(): List<RideHistoryItem> = remoteOrFallback(
        monitor = monitor,
        fallback = { fallback.getHistory() },
    ) {
        api.get<PageDto<WorkoutSessionDto>>("/history").items.map { session ->
            val workoutName = runCatching { api.get<WorkoutDto>("/workouts/${session.workoutId}").name }
                .getOrDefault(session.workoutId)
            session.toHistoryItem(workoutName)
        }
    }

    override suspend fun getWeeklyProgress(): WeeklyProgress = remoteOrFallback(
        monitor = monitor,
        fallback = { fallback.getWeeklyProgress() },
    ) {
        val completed = api.get<PageDto<WorkoutSessionDto>>("/history?limit=5").items.size.coerceAtMost(5)
        WeeklyProgress(completedWorkouts = completed, plannedWorkouts = 5)
    }

    override suspend fun getLatestWorkoutSummary(): WorkoutSession = remoteOrFallback(
        monitor = monitor,
        fallback = { fallback.getLatestWorkoutSummary() },
    ) {
        val session = api.get<PageDto<WorkoutSessionDto>>("/history?limit=1").items.firstOrNull()
            ?: error("No completed workouts")
        val workoutName = runCatching { api.get<WorkoutDto>("/workouts/${session.workoutId}").name }
            .getOrDefault(session.workoutId)
        session.toDomainSummary(workoutName)
    }
}

class RemoteWorkoutSessionRepository(
    private val api: ApiClient,
    private val monitor: DataSourceMonitor,
) : com.delminiusapps.rideforge.data.repository.SessionRepository {
    private val _syncStatus = MutableStateFlow(SyncStatus.Synced)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus

    override suspend fun startSession(workoutId: String): WorkoutSession = remoteCall {
        val response = api.post<com.delminiusapps.rideforge.data.dto.StartSessionRequestDto, com.delminiusapps.rideforge.data.dto.SessionResponseDto>(
            "/sessions/start",
            com.delminiusapps.rideforge.data.dto.StartSessionRequestDto(workoutId)
        )
        val workoutName = runCatching { api.get<WorkoutDto>("/workouts/${response.session.workoutId}").name }
            .getOrDefault(response.session.workoutId)
        response.session.toDomainSummary(workoutName)
    }

    override suspend fun pauseSession(sessionId: String) {
        remoteCall<Unit> {
            api.put<Unit, WorkoutSessionDto>("/sessions/$sessionId/pause", Unit)
            Unit
        }
    }

    override suspend fun resumeSession(sessionId: String) {
        remoteCall<Unit> {
            api.put<Unit, WorkoutSessionDto>("/sessions/$sessionId/resume", Unit)
            Unit
        }
    }

    override suspend fun addMetric(sessionId: String, sample: MetricSample) {
        addMetrics(sessionId, listOf(sample))
    }

    override suspend fun addMetrics(sessionId: String, samples: List<MetricSample>) {
        if (samples.isEmpty()) return
        _syncStatus.value = SyncStatus.Syncing
        val failed = mutableListOf<MetricSample>()
        val failures = mutableListOf<Throwable>()
        samples.forEach { sample ->
            runCatching { uploadMetric(sessionId, sample) }
                .onFailure {
                    failed += sample
                    failures += it
                }
        }
        if (failed.isEmpty()) {
            _syncStatus.value = SyncStatus.Synced
            monitor.markRemote()
        } else {
            // Some samples could not be delivered; the SessionSyncManager will
            // re-queue them via its pending-event mechanism.
            _syncStatus.value = SyncStatus.SyncFailed
            monitor.markFallback(Exception("${failed.size} metric sample(s) failed to upload for session $sessionId"))
            if (failed.size == samples.size) {
                // All samples failed — propagate so callers can handle full failures.
                throw failures.firstOrNull()
                    ?: IllegalStateException("All ${samples.size} metric samples failed to upload for session $sessionId")
            }
        }
    }

    private suspend fun uploadMetric(sessionId: String, sample: MetricSample) {
        api.post<com.delminiusapps.rideforge.data.dto.MetricSampleRequestDto, com.delminiusapps.rideforge.data.dto.MetricsAcceptedResponseDto>(
            "/sessions/$sessionId/metrics",
            com.delminiusapps.rideforge.data.dto.MetricSampleRequestDto(
                elapsedSeconds = sample.elapsedSeconds,
                currentPower = sample.currentPowerWatts,
                targetPower = sample.targetPowerWatts,
                cadence = sample.cadenceRpm,
                heartRate = sample.heartRateBpm,
                speedKmh = sample.speedKmh,
            ),
        )
    }

    override suspend fun completeSession(
        sessionId: String,
        elapsedSeconds: Int?,
        hasRealTrainerData: Boolean,
    ): WorkoutSession = remoteCall {
        val response = api.put<com.delminiusapps.rideforge.data.dto.CompleteSessionRequestDto, WorkoutSessionDto>(
            "/sessions/$sessionId/complete",
            com.delminiusapps.rideforge.data.dto.CompleteSessionRequestDto(
                elapsedSeconds = elapsedSeconds,
                hasRealTrainerData = hasRealTrainerData,
            )
        )
        val workoutName = runCatching { api.get<WorkoutDto>("/workouts/${response.workoutId}").name }
            .getOrDefault(response.workoutId)
        response.toDomainSummary(workoutName)
    }

    override suspend fun getSessionMetrics(sessionId: String): List<MetricSample> = remoteCall {
        val response = api.get<List<com.delminiusapps.rideforge.data.dto.MetricSampleDto>>("/sessions/$sessionId/metrics")
        response.mapIndexed { index, sample ->
            MetricSample(
                elapsedSeconds = sample.elapsedSeconds ?: index,
                currentPowerWatts = sample.currentPower,
                targetPowerWatts = sample.targetPower,
                cadenceRpm = sample.cadence,
                heartRateBpm = sample.heartRate,
                speedKmh = sample.speedKmh,
            )
        }
    }

    override suspend fun getSessionSummary(sessionId: String): WorkoutSession = remoteCall {
        val session = api.get<WorkoutSessionDto>("/history/$sessionId")
        val workoutName = runCatching { api.get<WorkoutDto>("/workouts/${session.workoutId}").name }
            .getOrDefault(session.workoutId)
        session.toDomainSummary(workoutName)
    }

    override suspend fun syncPending() = Unit

    private suspend fun <T> remoteCall(block: suspend () -> T): T {
        _syncStatus.value = SyncStatus.Syncing
        return runCatching { block() }
            .onSuccess {
                _syncStatus.value = SyncStatus.Synced
                monitor.markRemote()
            }
            .onFailure {
                _syncStatus.value = SyncStatus.SyncFailed
                monitor.markFallback(it)
            }
            .getOrThrow()
    }
}

class RemoteStravaRepository(
    private val api: ApiClient,
    private val fallback: StravaRepository,
    private val monitor: DataSourceMonitor,
) : StravaRepository {
    override suspend fun getStatus(): StravaConnectionStatus = remoteOrFallback(
        monitor = monitor,
        fallback = { fallback.getStatus() },
    ) {
        api.get<StravaStatusDto>("/integrations/strava/status").toDomain()
    }

    override suspend fun getConnectUrl(): String = remoteRequired {
        api.get<StravaConnectUrlDto>("/integrations/strava/connect-url").url
    }

    override suspend fun disconnect(): StravaConnectionStatus = remoteRequired {
        api.post<Unit, StravaStatusDto>("/integrations/strava/disconnect", Unit).toDomain()
    }

    override suspend fun syncWorkout(sessionId: String): StravaSyncInfo = remoteRequired {
        api.post<Unit, StravaSyncStatusDto>("/history/$sessionId/sync/strava", Unit).toDomain()
    }

    override suspend fun getSyncStatus(sessionId: String): StravaSyncInfo = remoteOrFallback(
        monitor = monitor,
        fallback = { fallback.getSyncStatus(sessionId) },
    ) {
        api.get<StravaSyncStatusDto>("/history/$sessionId/sync-status").toDomain()
    }

    private suspend fun <T> remoteRequired(block: suspend () -> T): T {
        return runCatching { block() }
            .onSuccess { monitor.markRemote() }
            .onFailure { monitor.markFallback(it) }
            .getOrThrow()
    }
}

private suspend fun <T> remoteOrFallback(
    monitor: DataSourceMonitor,
    fallback: suspend () -> T,
    remote: suspend () -> T,
): T {
    return runCatching { remote() }
        .onSuccess { monitor.markRemote() }
        .getOrElse { error ->
            monitor.markFallback(error)
            fallback()
        }
}
