package com.delminiusapps.rideforge.data.repository.sync

import com.delminiusapps.rideforge.core.network.ApiClientException
import com.delminiusapps.rideforge.core.network.DataSourceMonitor
import com.delminiusapps.rideforge.data.local.RideForgeKeyValueStore
import com.delminiusapps.rideforge.data.local.StoredActiveWorkout
import com.delminiusapps.rideforge.data.local.WorkoutControlMode
import com.delminiusapps.rideforge.data.local.WorkoutLocalStorage
import com.delminiusapps.rideforge.data.repository.local.LocalWorkoutSessionRepository
import com.delminiusapps.rideforge.domain.repository.SessionRepository
import com.delminiusapps.rideforge.domain.usecase.UploadMetricBatchUseCase
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.SyncStatus
import com.delminiusapps.rideforge.models.WorkoutSession
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionSyncReliabilityTest {

    @Test
    fun sessionStartAndMetricsAreLocalFirstAndPersistUntilRemoteSyncSucceeds() = runTest {
        val storage = WorkoutLocalStorage(InMemoryRideForgeKeyValueStore())
        val local = LocalWorkoutSessionRepository(storage)
        val remote = FakeSessionRepository()
        val manager = SessionSyncManager(remote, local, LocalPendingSyncQueue(storage), DataSourceMonitor())

        val session = manager.startSession("workout-a")
        val sample = MetricSample(1, 205, 210, 88, 132)
        manager.addMetrics(session.id, listOf(sample))

        assertTrue(session.id.startsWith("local-workout-a-"))
        assertEquals(SyncStatus.PendingSync, manager.syncStatus.value)
        assertEquals(emptyList(), remote.startedWorkoutIds)
        assertEquals(listOf(sample), local.getSessionMetrics(session.id))

        val pendingBeforeSync = storage.getPendingSyncQueue().events
        assertEquals(2, pendingBeforeSync.size)
        assertTrue(pendingBeforeSync[0] is PendingSessionEvent.Start)
        assertTrue(pendingBeforeSync[1] is PendingSessionEvent.Metrics)

        remote.failWrites = true
        manager.syncPending()
        assertEquals(SyncStatus.SyncFailed, manager.syncStatus.value)
        assertEquals(2, storage.getPendingSyncQueue().events.size)

        remote.failWrites = false
        manager.syncPending()

        assertEquals(SyncStatus.Synced, manager.syncStatus.value)
        assertEquals(emptyList(), storage.getPendingSyncQueue().events)
        assertEquals(listOf("workout-a"), remote.startedWorkoutIds)
        assertEquals(listOf(sample), remote.metricsBySessionId.getValue("remote-1"))
    }

    @Test
    fun metricFlushRestoresBufferWhenUploadFailsAndRetriesLater() = runTest {
        val storage = WorkoutLocalStorage(InMemoryRideForgeKeyValueStore())
        val remote = FakeSessionRepository()
        val uploader = MetricSampleBatchUploader(UploadMetricBatchUseCase(remote), storage)
        val sample = MetricSample(7, 188, 190, 86, 128)

        uploader.record("local-session", sample)
        remote.failWrites = true
        uploader.flush("local-session")

        assertEquals(listOf(sample), storage.getMetricUploadBuffer().samplesBySessionId.getValue("local-session"))
        assertEquals(emptyList(), remote.metricsBySessionId["local-session"].orEmpty())

        remote.failWrites = false
        uploader.flush("local-session")

        assertEquals(emptyMap(), storage.getMetricUploadBuffer().samplesBySessionId)
        assertEquals(listOf(sample), remote.metricsBySessionId.getValue("local-session"))
    }

    @Test
    fun concurrentSyncPendingCallsProcessQueueOnce() = runTest {
        val storage = WorkoutLocalStorage(InMemoryRideForgeKeyValueStore())
        val local = LocalWorkoutSessionRepository(storage)
        val remote = FakeSessionRepository()
        val manager = SessionSyncManager(remote, local, LocalPendingSyncQueue(storage), DataSourceMonitor())
        val session = manager.startSession("workout-a")
        val sample = MetricSample(3, 215, 210, 90, 135)
        manager.addMetrics(session.id, listOf(sample))

        val first = async { manager.syncPending() }
        val second = async { manager.syncPending() }
        first.await()
        second.await()

        assertEquals(SyncStatus.Synced, manager.syncStatus.value)
        assertEquals(emptyList(), storage.getPendingSyncQueue().events)
        assertEquals(listOf("workout-a"), remote.startedWorkoutIds)
        assertEquals(listOf(sample), remote.metricsBySessionId.getValue("remote-1"))
    }

    @Test
    fun staleRemoteSessionBindingIsRecreatedAndMetricsContinueSyncing() = runTest {
        val storage = WorkoutLocalStorage(InMemoryRideForgeKeyValueStore())
        val local = LocalWorkoutSessionRepository(storage)
        val remote = FakeSessionRepository()
        val manager = SessionSyncManager(remote, local, LocalPendingSyncQueue(storage), DataSourceMonitor())
        val session = manager.startSession("workout-a")
        manager.syncPending()
        remote.missingSessionIds += "remote-1"

        val sample = MetricSample(5, 215, 220, 90, 136)
        manager.addMetrics(session.id, listOf(sample))
        manager.syncPending()

        assertEquals(SyncStatus.Synced, manager.syncStatus.value)
        assertEquals(emptyList(), storage.getPendingSyncQueue().events)
        assertEquals(listOf("workout-a", "workout-a"), remote.startedWorkoutIds)
        assertEquals(listOf(sample), remote.metricsBySessionId.getValue("remote-2"))
    }

    @Test
    fun staleRemoteOnlyMetricEventIsDrainedWhenServerForgotSession() = runTest {
        val storage = WorkoutLocalStorage(InMemoryRideForgeKeyValueStore())
        val local = LocalWorkoutSessionRepository(storage)
        val remote = FakeSessionRepository()
        val manager = SessionSyncManager(remote, local, LocalPendingSyncQueue(storage), DataSourceMonitor())
        val sample = MetricSample(5, 215, 220, 90, 136)
        remote.missingSessionIds += "session-stale"

        manager.addMetrics("session-stale", listOf(sample))
        manager.syncPending()

        assertEquals(SyncStatus.Synced, manager.syncStatus.value)
        assertEquals(emptyList(), storage.getPendingSyncQueue().events)
        assertEquals(emptyList(), remote.startedWorkoutIds)
        assertEquals(emptyMap(), remote.metricsBySessionId)
        assertEquals(listOf(sample), local.getSessionMetrics("session-stale"))
    }

    @Test
    fun activeWorkoutSnapshotSurvivesStorageRecreation() = runTest {
        val keyValueStore = InMemoryRideForgeKeyValueStore()
        val storage = WorkoutLocalStorage(keyValueStore)
        val sample = MetricSample(42, 210, 220, 91, 140)

        storage.saveActiveWorkout(
            StoredActiveWorkout(
                workoutId = "workout-a",
                sessionId = "local-session",
                ftpWatts = 250,
                elapsedSeconds = 42,
                samples = listOf(sample),
                controlMode = WorkoutControlMode.TRAINER,
                isPaused = false,
                ergEnabled = true,
                updatedAtEpochMillis = 1234L,
            ),
        )

        val restored = WorkoutLocalStorage(keyValueStore).getActiveWorkout()

        assertNotNull(restored)
        assertEquals("workout-a", restored.workoutId)
        assertEquals("local-session", restored.sessionId)
        assertEquals(42, restored.elapsedSeconds)
        assertEquals(listOf(sample), restored.samples)
        assertEquals(WorkoutControlMode.TRAINER, restored.controlMode)
        assertEquals(true, restored.ergEnabled)
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

private class FakeSessionRepository : SessionRepository {
    private val _syncStatus = MutableStateFlow(SyncStatus.Synced)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus
    val startedWorkoutIds = mutableListOf<String>()
    val metricsBySessionId = mutableMapOf<String, List<MetricSample>>()
    val missingSessionIds = mutableSetOf<String>()
    var failWrites = false
    private var nextRemoteId = 1

    override suspend fun startSession(workoutId: String): WorkoutSession {
        failIfNeeded()
        startedWorkoutIds += workoutId
        return emptySession(id = "remote-${nextRemoteId++}", workoutId = workoutId)
    }

    override suspend fun pauseSession(sessionId: String) {
        failIfNeeded()
        missingSessionIds.throwIfMissing(sessionId)
    }

    override suspend fun resumeSession(sessionId: String) {
        failIfNeeded()
        missingSessionIds.throwIfMissing(sessionId)
    }

    override suspend fun addMetric(sessionId: String, sample: MetricSample) {
        addMetrics(sessionId, listOf(sample))
    }

    override suspend fun addMetrics(sessionId: String, samples: List<MetricSample>) {
        failIfNeeded()
        missingSessionIds.throwIfMissing(sessionId)
        metricsBySessionId[sessionId] = metricsBySessionId[sessionId].orEmpty() + samples
    }

    override suspend fun completeSession(
        sessionId: String,
        elapsedSeconds: Int?,
        hasRealTrainerData: Boolean,
    ): WorkoutSession {
        failIfNeeded()
        missingSessionIds.throwIfMissing(sessionId)
        return emptySession(id = sessionId, workoutId = "workout-a").copy(elapsedSeconds = elapsedSeconds ?: 0)
    }

    override suspend fun getSessionMetrics(sessionId: String): List<MetricSample> {
        return metricsBySessionId[sessionId].orEmpty()
    }

    override suspend fun getSessionSummary(sessionId: String): WorkoutSession {
        return emptySession(id = sessionId, workoutId = "workout-a")
    }

    override suspend fun syncPending() = Unit

    private fun failIfNeeded() {
        if (failWrites) error("Backend unavailable")
    }

    private fun Set<String>.throwIfMissing(sessionId: String) {
        if (sessionId in this) throw ApiClientException(404, "Request failed with HTTP 404")
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
