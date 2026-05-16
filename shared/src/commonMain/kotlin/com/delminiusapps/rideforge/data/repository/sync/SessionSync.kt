package com.delminiusapps.rideforge.data.repository.sync

import com.delminiusapps.rideforge.core.network.ApiClientException
import com.delminiusapps.rideforge.core.network.DataSourceMonitor
import com.delminiusapps.rideforge.data.local.WorkoutLocalStorage
import com.delminiusapps.rideforge.domain.repository.SessionRepository
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.SyncStatus
import com.delminiusapps.rideforge.models.WorkoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PendingSessionEvent {
    val id: Long
    val sessionId: String

    @Serializable
    @SerialName("start")
    data class Start(
        override val id: Long,
        override val sessionId: String,
        val workoutId: String,
    ) : PendingSessionEvent

    @Serializable
    @SerialName("pause")
    data class Pause(
        override val id: Long,
        override val sessionId: String,
    ) : PendingSessionEvent

    @Serializable
    @SerialName("resume")
    data class Resume(
        override val id: Long,
        override val sessionId: String,
    ) : PendingSessionEvent

    @Serializable
    @SerialName("metrics")
    data class Metrics(
        override val id: Long,
        override val sessionId: String,
        val samples: List<MetricSample>,
    ) : PendingSessionEvent

    @Serializable
    @SerialName("complete")
    data class Complete(
        override val id: Long,
        override val sessionId: String,
        val elapsedSeconds: Int?,
    ) : PendingSessionEvent
}

class LocalPendingSyncQueue(
    private val storage: WorkoutLocalStorage,
) {
    private val mutex = Mutex()
    private val events = mutableListOf<PendingSessionEvent>()
    private val remoteSessionIds = mutableMapOf<String, String>()
    private var nextId = 1L
    private var isLoaded = false

    suspend fun enqueueStart(localSessionId: String, workoutId: String) {
        enqueue { id -> PendingSessionEvent.Start(id, localSessionId, workoutId) }
    }

    suspend fun enqueuePause(sessionId: String) {
        enqueue { id -> PendingSessionEvent.Pause(id, sessionId) }
    }

    suspend fun enqueueResume(sessionId: String) {
        enqueue { id -> PendingSessionEvent.Resume(id, sessionId) }
    }

    suspend fun enqueueMetrics(sessionId: String, samples: List<MetricSample>) {
        if (samples.isNotEmpty()) {
            enqueue { id -> PendingSessionEvent.Metrics(id, sessionId, samples) }
        }
    }

    suspend fun enqueueComplete(sessionId: String, elapsedSeconds: Int?) {
        enqueue { id -> PendingSessionEvent.Complete(id, sessionId, elapsedSeconds) }
    }

    suspend fun bindRemoteSessionId(localSessionId: String, remoteSessionId: String) {
        mutex.withLock {
            ensureLoadedLocked()
            remoteSessionIds[localSessionId] = remoteSessionId
            storage.upsertRemoteSessionBinding(localSessionId, remoteSessionId)
        }
    }

    suspend fun resolveSessionId(sessionId: String): String? = mutex.withLock {
        ensureLoadedLocked()
        remoteSessionIds[sessionId] ?: sessionId.takeUnless { it.startsWith(LocalSessionPrefix) }
    }

    suspend fun firstEvent(): PendingSessionEvent? = mutex.withLock {
        ensureLoadedLocked()
        events.firstOrNull()
    }

    suspend fun markProcessed(event: PendingSessionEvent) {
        mutex.withLock {
            ensureLoadedLocked()
            events.removeAll { it.id == event.id }
            storage.removePendingSyncEvent(event.id)
        }
    }

    suspend fun hasPending(): Boolean = mutex.withLock {
        ensureLoadedLocked()
        events.isNotEmpty()
    }

    private suspend fun enqueue(factory: (Long) -> PendingSessionEvent) {
        mutex.withLock {
            ensureLoadedLocked()
            val event = factory(nextId++)
            events += event
            storage.appendPendingSyncEvent(event, nextId)
        }
    }

    private suspend fun ensureLoadedLocked() {
        if (isLoaded) return
        val snapshot = storage.getPendingSyncQueue()
        events.clear()
        events += snapshot.events
        remoteSessionIds.clear()
        remoteSessionIds += snapshot.remoteSessionIds
        nextId = snapshot.nextId
        isLoaded = true
    }

    companion object {
        const val LocalSessionPrefix = "local-"
    }
}

class SessionSyncManager(
    private val remote: SessionRepository,
    private val fallback: SessionRepository,
    private val queue: LocalPendingSyncQueue,
    private val monitor: DataSourceMonitor,
) : SessionRepository {
    private val _syncStatus = MutableStateFlow(SyncStatus.Synced)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus
    private val syncMutex = Mutex()

    override suspend fun startSession(workoutId: String): WorkoutSession {
        val localSession = fallback.startSession(workoutId)
        queue.enqueueStart(localSession.id, workoutId)
        _syncStatus.value = SyncStatus.PendingSync
        return localSession
    }

    override suspend fun pauseSession(sessionId: String) {
        fallback.pauseSession(sessionId)
        queue.enqueuePause(sessionId)
        _syncStatus.value = SyncStatus.PendingSync
    }

    override suspend fun resumeSession(sessionId: String) {
        fallback.resumeSession(sessionId)
        queue.enqueueResume(sessionId)
        _syncStatus.value = SyncStatus.PendingSync
    }

    override suspend fun addMetric(sessionId: String, sample: MetricSample) {
        addMetrics(sessionId, listOf(sample))
    }

    override suspend fun addMetrics(sessionId: String, samples: List<MetricSample>) {
        if (samples.isEmpty()) return
        fallback.addMetrics(sessionId, samples)
        queue.enqueueMetrics(sessionId, samples)
        _syncStatus.value = SyncStatus.PendingSync
    }

    override suspend fun completeSession(sessionId: String, elapsedSeconds: Int?): WorkoutSession {
        val localSummary = fallback.completeSession(sessionId, elapsedSeconds)
        queue.enqueueComplete(sessionId, elapsedSeconds)
        _syncStatus.value = SyncStatus.PendingSync
        return localSummary
    }

    override suspend fun getSessionMetrics(sessionId: String): List<MetricSample> {
        val remoteId = queue.resolveSessionId(sessionId)
        return if (remoteId == null) {
            fallback.getSessionMetrics(sessionId)
        } else {
            runCatching { remote.getSessionMetrics(remoteId) }
                .getOrElse { fallback.getSessionMetrics(sessionId) }
        }
    }

    override suspend fun getSessionSummary(sessionId: String): WorkoutSession {
        val remoteId = queue.resolveSessionId(sessionId)
        return if (remoteId == null) {
            fallback.getSessionSummary(sessionId)
        } else {
            runCatching { remote.getSessionSummary(remoteId) }
                .getOrElse { fallback.getSessionSummary(sessionId) }
        }
    }

    override suspend fun syncPending() = syncMutex.withLock {
        if (!queue.hasPending()) {
            _syncStatus.value = SyncStatus.Synced
            return@withLock
        }

        _syncStatus.value = SyncStatus.Syncing
        while (true) {
            val event = queue.firstEvent() ?: break
            val result = runCatching { syncEvent(event) }
            if (result.isFailure) {
                monitor.markFallback(result.exceptionOrNull() ?: IllegalStateException("Sync failed"))
                _syncStatus.value = SyncStatus.SyncFailed
                return@withLock
            }
            queue.markProcessed(event)
        }
        _syncStatus.value = SyncStatus.Synced
    }

    private suspend fun syncEvent(event: PendingSessionEvent) {
        val result = runCatching { syncResolvedEvent(event) }
        if (result.isSuccess) return

        val error = result.exceptionOrNull() ?: return
        if (isRemoteSessionMissing(error) && recoverMissingRemoteSession(event)) return
        throw error
    }

    private suspend fun syncResolvedEvent(event: PendingSessionEvent) {
        when (event) {
            is PendingSessionEvent.Start -> {
                val session = remote.startSession(event.workoutId)
                queue.bindRemoteSessionId(event.sessionId, session.id)
            }
            is PendingSessionEvent.Pause -> {
                remote.pauseSession(requireRemoteId(event.sessionId))
            }
            is PendingSessionEvent.Resume -> {
                remote.resumeSession(requireRemoteId(event.sessionId))
            }
            is PendingSessionEvent.Metrics -> {
                remote.addMetrics(requireRemoteId(event.sessionId), event.samples)
            }
            is PendingSessionEvent.Complete -> {
                remote.completeSession(requireRemoteId(event.sessionId), event.elapsedSeconds)
            }
        }
    }

    private suspend fun recoverMissingRemoteSession(event: PendingSessionEvent): Boolean {
        if (event is PendingSessionEvent.Start) return false

        if (!event.sessionId.startsWith(LocalPendingSyncQueue.LocalSessionPrefix)) {
            // Older app versions could enqueue remote-only session ids. If the
            // in-memory dev backend restarted, that remote session cannot be
            // reconstructed safely because the local queue has no workout id.
            monitor.markRemote()
            return true
        }

        val workoutId = fallback.getSessionSummary(event.sessionId).workoutId
            .takeIf { it.isNotBlank() }
            ?: return false
        val newRemoteSession = remote.startSession(workoutId)
        queue.bindRemoteSessionId(event.sessionId, newRemoteSession.id)
        syncResolvedEvent(event)
        return true
    }

    private fun isRemoteSessionMissing(error: Throwable): Boolean {
        return generateSequence(error) { it.cause }
            .filterIsInstance<ApiClientException>()
            .any { it.statusCode == 404 }
    }

    private suspend fun requireRemoteId(sessionId: String): String {
        return queue.resolveSessionId(sessionId) ?: error("Session has not been created remotely yet")
    }

}
