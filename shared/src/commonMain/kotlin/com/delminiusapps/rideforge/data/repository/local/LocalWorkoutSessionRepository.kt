package com.delminiusapps.rideforge.data.repository.local

import com.delminiusapps.rideforge.data.local.WorkoutLocalStorage
import com.delminiusapps.rideforge.domain.repository.SessionRepository
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.SyncStatus
import com.delminiusapps.rideforge.models.WorkoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import kotlin.time.Clock

class LocalWorkoutSessionRepository(
    private val storage: WorkoutLocalStorage,
) : SessionRepository {
    private val mutex = Mutex()
    private val _syncStatus = MutableStateFlow(SyncStatus.Synced)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus

    override suspend fun startSession(workoutId: String): WorkoutSession = mutex.withLock {
        val session = WorkoutSession(
            id = localSessionId(workoutId),
            workoutId = workoutId,
            workoutName = workoutId,
            elapsedSeconds = 0,
            averagePowerWatts = 0,
            normalizedPowerWatts = 0,
            calories = 0,
            tss = 0,
            completionPercent = 0,
        )
        storage.upsertLocalSession(session)
        storage.replaceLocalSessionMetrics(session.id, emptyList())
        session
    }

    override suspend fun pauseSession(sessionId: String) = Unit

    override suspend fun resumeSession(sessionId: String) = Unit

    override suspend fun addMetric(sessionId: String, sample: MetricSample) {
        addMetrics(sessionId, listOf(sample))
    }

    override suspend fun addMetrics(sessionId: String, samples: List<MetricSample>) {
        if (samples.isEmpty()) return
        mutex.withLock {
            storage.appendLocalSessionMetrics(sessionId, samples)
        }
    }

    override suspend fun completeSession(sessionId: String, elapsedSeconds: Int?): WorkoutSession = mutex.withLock {
        val metrics = storage.getLocalSessionMetrics(sessionId)
        val existing = storage.getLocalSession(sessionId)
        val elapsed = elapsedSeconds
            ?: metrics.maxOfOrNull { it.elapsedSeconds }
            ?: existing?.elapsedSeconds
            ?: 0
        val average = metrics.map { it.currentPowerWatts }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.roundToInt()
            ?: existing?.averagePowerWatts
            ?: 0
        val normalized = if (metrics.isEmpty()) {
            existing?.normalizedPowerWatts ?: average
        } else {
            (average * 1.08f).roundToInt().coerceAtLeast(average)
        }
        // Use the target power from actual metrics as a proxy for FTP-relative intensity;
        // fall back to a sensible default if no target data is available.
        val impliedFtp = metrics.mapNotNull { it.targetPowerWatts.takeIf { w -> w > 0 } }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.roundToInt()
            ?: 240
        val summary = (existing ?: WorkoutSession(
            id = sessionId,
            workoutId = sessionId.removePrefix("local-").substringBeforeLast("-"),
            workoutName = sessionId,
            elapsedSeconds = 0,
            averagePowerWatts = 0,
            normalizedPowerWatts = 0,
            calories = 0,
            tss = 0,
            completionPercent = 0,
        )).copy(
            elapsedSeconds = elapsed,
            averagePowerWatts = average,
            normalizedPowerWatts = normalized,
            calories = caloriesFor(average, elapsed),
            tss = tssFor(normalized, elapsed, impliedFtp),
            completionPercent = if (elapsed > 0) 100 else 0,
            completedAtEpochMillis = existing?.completedAtEpochMillis ?: Clock.System.now().toEpochMilliseconds(),
        )
        storage.upsertLocalSession(summary)
        summary
    }

    override suspend fun getSessionMetrics(sessionId: String): List<MetricSample> = mutex.withLock {
        storage.getLocalSessionMetrics(sessionId)
    }

    override suspend fun getSessionSummary(sessionId: String): WorkoutSession = mutex.withLock {
        storage.getLocalSession(sessionId)
            ?: summarizeFromMetrics(sessionId, storage.getLocalSessionMetrics(sessionId))
    }

    override suspend fun syncPending() = Unit

    private fun summarizeFromMetrics(
        sessionId: String,
        metrics: List<MetricSample>,
    ): WorkoutSession {
        val elapsed = metrics.maxOfOrNull { it.elapsedSeconds } ?: 0
        val average = metrics.map { it.currentPowerWatts }.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0
        val normalized = (average * 1.08f).roundToInt().coerceAtLeast(average)
        val impliedFtp = metrics.mapNotNull { it.targetPowerWatts.takeIf { w -> w > 0 } }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.roundToInt()
            ?: 240
        return WorkoutSession(
            id = sessionId,
            workoutId = sessionId.removePrefix("local-").substringBeforeLast("-"),
            workoutName = sessionId,
            elapsedSeconds = elapsed,
            averagePowerWatts = average,
            normalizedPowerWatts = normalized,
            calories = caloriesFor(average, elapsed),
            tss = tssFor(normalized, elapsed, impliedFtp),
            completionPercent = if (elapsed > 0) 100 else 0,
        )
    }

    private fun localSessionId(workoutId: String): String {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        return "local-$workoutId-$timestamp"
    }

    private fun caloriesFor(averagePower: Int, elapsedSeconds: Int): Int {
        return ((averagePower * elapsedSeconds) / 1000.0 * 3.6).roundToInt().coerceAtLeast(0)
    }

    private fun tssFor(normalizedPower: Int, elapsedSeconds: Int, ftpWatts: Int): Int {
        if (ftpWatts <= 0) return 0
        val hours = elapsedSeconds / 3600.0
        val intensityFactor = normalizedPower / ftpWatts.toDouble()
        return (hours * intensityFactor * intensityFactor * 100.0).roundToInt().coerceAtLeast(0)
    }
}
