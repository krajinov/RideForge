package com.delminiusapps.rideforge.data.local

import com.delminiusapps.rideforge.data.repository.sync.PendingSessionEvent
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.WorkoutSession
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class WorkoutControlMode {
    TRAINER,
    SIMULATION,
}

@Serializable
data class StoredActiveWorkout(
    val workoutId: String,
    val sessionId: String,
    val ftpWatts: Int,
    val elapsedSeconds: Int,
    val samples: List<MetricSample>,
    val controlMode: WorkoutControlMode,
    val isPaused: Boolean,
    val ergEnabled: Boolean,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class PendingSyncQueueSnapshot(
    val events: List<PendingSessionEvent> = emptyList(),
    val remoteSessionIds: Map<String, String> = emptyMap(),
    val nextId: Long = 1L,
)

@Serializable
data class LocalWorkoutSessionSnapshot(
    val sessions: List<WorkoutSession> = emptyList(),
    val metricsBySessionId: Map<String, List<MetricSample>> = emptyMap(),
)

@Serializable
data class MetricUploadBufferSnapshot(
    val samplesBySessionId: Map<String, List<MetricSample>> = emptyMap(),
)

interface MetricUploadBufferStore {
    suspend fun getMetricUploadBuffer(): MetricUploadBufferSnapshot

    suspend fun replaceMetricUploadBuffer(snapshot: MetricUploadBufferSnapshot)

    suspend fun getMetricUploadSamples(sessionId: String): List<MetricSample>

    suspend fun appendMetricUploadSample(sessionId: String, sample: MetricSample)

    suspend fun replaceMetricUploadSamples(sessionId: String, samples: List<MetricSample>)

    suspend fun clearMetricUploadSamples(sessionId: String)
}

interface PendingSyncQueueStore {
    suspend fun getPendingSyncQueue(): PendingSyncQueueSnapshot

    suspend fun replacePendingSyncQueue(snapshot: PendingSyncQueueSnapshot)

    suspend fun appendPendingSyncEvent(event: PendingSessionEvent, nextId: Long)

    suspend fun removePendingSyncEvent(eventId: Long)

    suspend fun upsertRemoteSessionBinding(localSessionId: String, remoteSessionId: String)
}

interface LocalWorkoutSessionStore {
    suspend fun getLocalSessions(): LocalWorkoutSessionSnapshot

    suspend fun replaceLocalSessions(snapshot: LocalWorkoutSessionSnapshot)

    suspend fun getLocalSession(sessionId: String): WorkoutSession?

    suspend fun upsertLocalSession(session: WorkoutSession)

    suspend fun getLocalSessionMetrics(sessionId: String): List<MetricSample>

    suspend fun appendLocalSessionMetrics(sessionId: String, samples: List<MetricSample>)

    suspend fun replaceLocalSessionMetrics(sessionId: String, samples: List<MetricSample>)
}

interface ActiveWorkoutStore {
    suspend fun getActiveWorkout(): StoredActiveWorkout?

    suspend fun replaceActiveWorkout(workout: StoredActiveWorkout)

    suspend fun clearActiveWorkout()
}

class WorkoutLocalStorage(
    private val keyValueStore: RideForgeKeyValueStore,
) {
    private val metricUploadBufferStore = keyValueStore as? MetricUploadBufferStore
    private val pendingSyncQueueStore = keyValueStore as? PendingSyncQueueStore
    private val localWorkoutSessionStore = keyValueStore as? LocalWorkoutSessionStore
    private val activeWorkoutStore = keyValueStore as? ActiveWorkoutStore
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private var migrationPerformed = false

    private suspend fun ensureMigrationPerformed() {
        if (migrationPerformed) return
        if (keyValueStore.readString(MigrationDoneKey) == "true") {
            migrationPerformed = true
            return
        }
        // Migrations will be triggered lazily by individual methods, 
        // but we'll mark it as done once any migration method is called and finds nothing or completes.
    }

    suspend fun getActiveWorkout(): StoredActiveWorkout? {
        val typedStore = activeWorkoutStore
        if (typedStore != null) {
            migrateLegacyActiveWorkout(typedStore)
            return typedStore.getActiveWorkout()
        }
        return read(ActiveWorkoutKey)
    }

    suspend fun saveActiveWorkout(workout: StoredActiveWorkout) {
        val typedStore = activeWorkoutStore
        if (typedStore != null) {
            typedStore.replaceActiveWorkout(workout)
            keyValueStore.remove(ActiveWorkoutKey)
            return
        }
        write(ActiveWorkoutKey, workout)
    }

    suspend fun clearActiveWorkout() {
        activeWorkoutStore?.clearActiveWorkout()
        keyValueStore.remove(ActiveWorkoutKey)
    }

    suspend fun getPendingSyncQueue(): PendingSyncQueueSnapshot {
        val typedStore = pendingSyncQueueStore
        if (typedStore != null) {
            migrateLegacyPendingSyncQueue(typedStore)
            return typedStore.getPendingSyncQueue()
        }
        return read(PendingSyncQueueKey) ?: PendingSyncQueueSnapshot()
    }

    suspend fun savePendingSyncQueue(snapshot: PendingSyncQueueSnapshot) {
        val typedStore = pendingSyncQueueStore
        if (typedStore != null) {
            typedStore.replacePendingSyncQueue(snapshot)
            keyValueStore.remove(PendingSyncQueueKey)
            return
        }
        write(PendingSyncQueueKey, snapshot)
    }

    suspend fun appendPendingSyncEvent(event: PendingSessionEvent, nextId: Long) {
        val typedStore = pendingSyncQueueStore
        if (typedStore != null) {
            migrateLegacyPendingSyncQueue(typedStore)
            typedStore.appendPendingSyncEvent(event, nextId)
            return
        }
        val snapshot = getPendingSyncQueue()
        savePendingSyncQueue(
            snapshot.copy(
                events = snapshot.events + event,
                nextId = nextId,
            ),
        )
    }

    suspend fun removePendingSyncEvent(eventId: Long) {
        val typedStore = pendingSyncQueueStore
        if (typedStore != null) {
            migrateLegacyPendingSyncQueue(typedStore)
            typedStore.removePendingSyncEvent(eventId)
            return
        }
        val snapshot = getPendingSyncQueue()
        savePendingSyncQueue(
            snapshot.copy(
                events = snapshot.events.filterNot { it.id == eventId },
            ),
        )
    }

    suspend fun upsertRemoteSessionBinding(localSessionId: String, remoteSessionId: String) {
        val typedStore = pendingSyncQueueStore
        if (typedStore != null) {
            migrateLegacyPendingSyncQueue(typedStore)
            typedStore.upsertRemoteSessionBinding(localSessionId, remoteSessionId)
            return
        }
        val snapshot = getPendingSyncQueue()
        savePendingSyncQueue(
            snapshot.copy(
                remoteSessionIds = snapshot.remoteSessionIds + (localSessionId to remoteSessionId),
            ),
        )
    }

    suspend fun getLocalSessions(): LocalWorkoutSessionSnapshot {
        val typedStore = localWorkoutSessionStore
        if (typedStore != null) {
            migrateLegacyLocalSessions(typedStore)
            return typedStore.getLocalSessions()
        }
        return read(LocalSessionsKey) ?: LocalWorkoutSessionSnapshot()
    }

    suspend fun saveLocalSessions(snapshot: LocalWorkoutSessionSnapshot) {
        val typedStore = localWorkoutSessionStore
        if (typedStore != null) {
            typedStore.replaceLocalSessions(snapshot)
            keyValueStore.remove(LocalSessionsKey)
            return
        }
        write(LocalSessionsKey, snapshot)
    }

    suspend fun getLocalSession(sessionId: String): WorkoutSession? {
        val typedStore = localWorkoutSessionStore
        if (typedStore != null) {
            migrateLegacyLocalSessions(typedStore)
            return typedStore.getLocalSession(sessionId)
        }
        return getLocalSessions().sessions.firstOrNull { it.id == sessionId }
    }

    suspend fun upsertLocalSession(session: WorkoutSession) {
        val typedStore = localWorkoutSessionStore
        if (typedStore != null) {
            migrateLegacyLocalSessions(typedStore)
            typedStore.upsertLocalSession(session)
            return
        }
        val snapshot = getLocalSessions()
        saveLocalSessions(
            snapshot.copy(
                sessions = snapshot.sessions.filterNot { it.id == session.id } + session,
            ),
        )
    }

    suspend fun getLocalSessionMetrics(sessionId: String): List<MetricSample> {
        val typedStore = localWorkoutSessionStore
        if (typedStore != null) {
            migrateLegacyLocalSessions(typedStore)
            return typedStore.getLocalSessionMetrics(sessionId)
        }
        return getLocalSessions().metricsBySessionId[sessionId].orEmpty()
    }

    suspend fun appendLocalSessionMetrics(sessionId: String, samples: List<MetricSample>) {
        if (samples.isEmpty()) return

        val typedStore = localWorkoutSessionStore
        if (typedStore != null) {
            migrateLegacyLocalSessions(typedStore)
            typedStore.appendLocalSessionMetrics(sessionId, samples)
            return
        }
        val snapshot = getLocalSessions()
        val existing = snapshot.metricsBySessionId[sessionId].orEmpty()
        saveLocalSessions(
            snapshot.copy(
                metricsBySessionId = snapshot.metricsBySessionId + (sessionId to (existing + samples)),
            ),
        )
    }

    suspend fun replaceLocalSessionMetrics(sessionId: String, samples: List<MetricSample>) {
        val typedStore = localWorkoutSessionStore
        if (typedStore != null) {
            migrateLegacyLocalSessions(typedStore)
            typedStore.replaceLocalSessionMetrics(sessionId, samples)
            return
        }
        val snapshot = getLocalSessions()
        saveLocalSessions(
            snapshot.copy(
                metricsBySessionId = snapshot.metricsBySessionId + (sessionId to samples),
            ),
        )
    }

    suspend fun getMetricUploadBuffer(): MetricUploadBufferSnapshot {
        val typedStore = metricUploadBufferStore
        if (typedStore != null) {
            migrateLegacyMetricUploadBuffer(typedStore)
            return typedStore.getMetricUploadBuffer()
        }
        return read(MetricUploadBufferKey) ?: MetricUploadBufferSnapshot()
    }

    suspend fun saveMetricUploadBuffer(snapshot: MetricUploadBufferSnapshot) {
        val typedStore = metricUploadBufferStore
        if (typedStore != null) {
            typedStore.replaceMetricUploadBuffer(snapshot)
            keyValueStore.remove(MetricUploadBufferKey)
            return
        }
        write(MetricUploadBufferKey, snapshot)
    }

    suspend fun appendMetricUploadSample(sessionId: String, sample: MetricSample) {
        val typedStore = metricUploadBufferStore
        if (typedStore != null) {
            migrateLegacyMetricUploadBuffer(typedStore)
            typedStore.appendMetricUploadSample(sessionId, sample)
            return
        }
        val snapshot = getMetricUploadBuffer()
        val current = snapshot.samplesBySessionId[sessionId].orEmpty()
        saveMetricUploadBuffer(
            snapshot.copy(
                samplesBySessionId = snapshot.samplesBySessionId + (sessionId to (current + sample)),
            ),
        )
    }

    suspend fun drainMetricUploadSamples(sessionId: String): List<MetricSample> {
        val typedStore = metricUploadBufferStore
        if (typedStore != null) {
            migrateLegacyMetricUploadBuffer(typedStore)
            val samples = typedStore.getMetricUploadSamples(sessionId)
            typedStore.clearMetricUploadSamples(sessionId)
            return samples
        }
        val snapshot = getMetricUploadBuffer()
        val samples = snapshot.samplesBySessionId[sessionId].orEmpty()
        saveMetricUploadBuffer(
            MetricUploadBufferSnapshot(
                samplesBySessionId = snapshot.samplesBySessionId - sessionId,
            ),
        )
        return samples
    }

    suspend fun prependMetricUploadSamples(sessionId: String, samples: List<MetricSample>) {
        if (samples.isEmpty()) return

        val typedStore = metricUploadBufferStore
        if (typedStore != null) {
            migrateLegacyMetricUploadBuffer(typedStore)
            val current = typedStore.getMetricUploadSamples(sessionId)
            typedStore.replaceMetricUploadSamples(sessionId, samples + current)
            return
        }
        val snapshot = getMetricUploadBuffer()
        val current = snapshot.samplesBySessionId[sessionId].orEmpty()
        saveMetricUploadBuffer(
            snapshot.copy(
                samplesBySessionId = snapshot.samplesBySessionId + (sessionId to (samples + current)),
            ),
        )
    }

    private suspend inline fun <reified T> read(key: String): T? {
        val raw = keyValueStore.readString(key) ?: return null
        return runCatching { json.decodeFromString<T>(raw) }.getOrNull()
    }

    private suspend inline fun <reified T> write(key: String, value: T) {
        keyValueStore.writeString(key, json.encodeToString(value))
    }

    private suspend fun migrateLegacyMetricUploadBuffer(typedStore: MetricUploadBufferStore) {
        ensureMigrationPerformed()
        if (migrationPerformed) return
        
        val legacySnapshot = read<MetricUploadBufferSnapshot>(MetricUploadBufferKey)
        if (legacySnapshot != null && legacySnapshot.samplesBySessionId.isNotEmpty()) {
            val typedSnapshot = typedStore.getMetricUploadBuffer()
            typedStore.replaceMetricUploadBuffer(mergeMetricUploadBuffers(typedSnapshot, legacySnapshot))
        }
        keyValueStore.remove(MetricUploadBufferKey)
        markMigrationDone()
    }

    private suspend fun migrateLegacyPendingSyncQueue(typedStore: PendingSyncQueueStore) {
        ensureMigrationPerformed()
        if (migrationPerformed) return

        val legacySnapshot = read<PendingSyncQueueSnapshot>(PendingSyncQueueKey)
        if (legacySnapshot != null && (legacySnapshot.events.isNotEmpty() || legacySnapshot.remoteSessionIds.isNotEmpty() || legacySnapshot.nextId != 1L)) {
            val typedSnapshot = typedStore.getPendingSyncQueue()
            typedStore.replacePendingSyncQueue(mergePendingSyncQueues(typedSnapshot, legacySnapshot))
        }
        keyValueStore.remove(PendingSyncQueueKey)
        markMigrationDone()
    }

    private suspend fun migrateLegacyLocalSessions(typedStore: LocalWorkoutSessionStore) {
        ensureMigrationPerformed()
        if (migrationPerformed) return

        val legacySnapshot = read<LocalWorkoutSessionSnapshot>(LocalSessionsKey)
        if (legacySnapshot != null && (legacySnapshot.sessions.isNotEmpty() || legacySnapshot.metricsBySessionId.isNotEmpty())) {
            val typedSnapshot = typedStore.getLocalSessions()
            typedStore.replaceLocalSessions(mergeLocalSessions(typedSnapshot, legacySnapshot))
        }
        keyValueStore.remove(LocalSessionsKey)
        markMigrationDone()
    }

    private suspend fun migrateLegacyActiveWorkout(typedStore: ActiveWorkoutStore) {
        ensureMigrationPerformed()
        if (migrationPerformed) return

        val legacyWorkout = read<StoredActiveWorkout>(ActiveWorkoutKey)
        if (legacyWorkout != null) {
            val typedWorkout = typedStore.getActiveWorkout()
            if (typedWorkout == null || legacyWorkout.updatedAtEpochMillis > typedWorkout.updatedAtEpochMillis) {
                typedStore.replaceActiveWorkout(legacyWorkout)
            }
        }
        keyValueStore.remove(ActiveWorkoutKey)
        markMigrationDone()
    }

    private suspend fun markMigrationDone() {
        migrationPerformed = true
        keyValueStore.writeString(MigrationDoneKey, "true")
    }

    private fun mergeMetricUploadBuffers(
        primary: MetricUploadBufferSnapshot,
        legacy: MetricUploadBufferSnapshot,
    ): MetricUploadBufferSnapshot {
        val merged = primary.samplesBySessionId.toMutableMap()
        legacy.samplesBySessionId.forEach { (sessionId, samples) ->
            merged[sessionId] = samples + merged[sessionId].orEmpty()
        }
        return MetricUploadBufferSnapshot(merged)
    }

    private fun mergePendingSyncQueues(
        primary: PendingSyncQueueSnapshot,
        legacy: PendingSyncQueueSnapshot,
    ): PendingSyncQueueSnapshot {
        val events = (legacy.events + primary.events).mapIndexed { index, event ->
            event.withId(index + 1L)
        }
        return PendingSyncQueueSnapshot(
            events = events,
            remoteSessionIds = legacy.remoteSessionIds + primary.remoteSessionIds,
            nextId = maxOf(
                primary.nextId,
                legacy.nextId,
                (events.maxOfOrNull { it.id } ?: 0L) + 1L,
            ),
        )
    }

    private fun PendingSessionEvent.withId(id: Long): PendingSessionEvent {
        return when (this) {
            is PendingSessionEvent.Start -> copy(id = id)
            is PendingSessionEvent.Pause -> copy(id = id)
            is PendingSessionEvent.Resume -> copy(id = id)
            is PendingSessionEvent.Metrics -> copy(id = id)
            is PendingSessionEvent.Complete -> copy(id = id)
        }
    }

    private fun mergeLocalSessions(
        primary: LocalWorkoutSessionSnapshot,
        legacy: LocalWorkoutSessionSnapshot,
    ): LocalWorkoutSessionSnapshot {
        val sessions = (legacy.sessions + primary.sessions)
            .associateBy { it.id }
            .values
            .toList()
        val metrics = legacy.metricsBySessionId.toMutableMap()
        primary.metricsBySessionId.forEach { (sessionId, samples) ->
            metrics[sessionId] = metrics[sessionId].orEmpty() + samples
        }
        return LocalWorkoutSessionSnapshot(
            sessions = sessions,
            metricsBySessionId = metrics.filterValues { it.isNotEmpty() },
        )
    }

    private companion object {
        const val ActiveWorkoutKey = "active_workout"
        const val PendingSyncQueueKey = "pending_sync_queue"
        const val LocalSessionsKey = "local_workout_sessions"
        const val MetricUploadBufferKey = "metric_upload_buffer"
        const val MigrationDoneKey = "migration_performed"
    }
}
