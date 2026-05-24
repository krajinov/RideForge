package com.delminiusapps.rideforge.data.local

import app.cash.sqldelight.db.SqlDriver
import com.delminiusapps.rideforge.data.repository.sync.PendingSessionEvent
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.WorkoutSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SqlDelightRideForgeKeyValueStore(
    driver: SqlDriver,
) : RideForgeKeyValueStore,
    MetricUploadBufferStore,
    PendingSyncQueueStore,
    LocalWorkoutSessionStore,
    ActiveWorkoutStore {
    private val database = RideForgeDatabase(driver)
    private val mutex = Mutex()

    override suspend fun readString(key: String): String? = mutex.withLock {
        database.rideForgeDatabaseQueries.selectValue(key).executeAsOneOrNull()
    }

    override suspend fun writeString(key: String, value: String) {
        mutex.withLock {
            database.rideForgeDatabaseQueries.upsertValue(key, value)
        }
    }

    override suspend fun remove(key: String) {
        mutex.withLock {
            database.rideForgeDatabaseQueries.deleteValue(key)
        }
    }

    override suspend fun getMetricUploadBuffer(): MetricUploadBufferSnapshot = mutex.withLock {
        val rows = database.rideForgeDatabaseQueries.selectMetricUploadBuffer { sessionId, elapsedSeconds, currentPowerWatts, targetPowerWatts, cadenceRpm, heartRateBpm, speedKmh ->
            sessionId to metricSample(
                elapsedSeconds = elapsedSeconds,
                currentPowerWatts = currentPowerWatts,
                targetPowerWatts = targetPowerWatts,
                cadenceRpm = cadenceRpm,
                heartRateBpm = heartRateBpm,
                speedKmh = speedKmh,
            )
        }.executeAsList()
        MetricUploadBufferSnapshot(
            samplesBySessionId = rows
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .filterValues { it.isNotEmpty() },
        )
    }

    override suspend fun replaceMetricUploadBuffer(snapshot: MetricUploadBufferSnapshot) {
        mutex.withLock {
            database.transaction {
                database.rideForgeDatabaseQueries.deleteAllMetricUploadSamples()
                snapshot.samplesBySessionId.forEach { (sessionId, samples) ->
                    samples.forEach { sample ->
                        insertMetricUploadSample(sessionId, sample)
                    }
                }
            }
        }
    }

    override suspend fun getMetricUploadSamples(sessionId: String): List<MetricSample> = mutex.withLock {
        database.rideForgeDatabaseQueries.selectMetricUploadSamples(sessionId) { elapsedSeconds, currentPowerWatts, targetPowerWatts, cadenceRpm, heartRateBpm, speedKmh ->
            metricSample(
                elapsedSeconds = elapsedSeconds,
                currentPowerWatts = currentPowerWatts,
                targetPowerWatts = targetPowerWatts,
                cadenceRpm = cadenceRpm,
                heartRateBpm = heartRateBpm,
                speedKmh = speedKmh,
            )
        }.executeAsList()
    }

    override suspend fun appendMetricUploadSample(sessionId: String, sample: MetricSample) {
        mutex.withLock {
            insertMetricUploadSample(sessionId, sample)
        }
    }

    override suspend fun replaceMetricUploadSamples(sessionId: String, samples: List<MetricSample>) {
        mutex.withLock {
            database.transaction {
                database.rideForgeDatabaseQueries.deleteMetricUploadSamples(sessionId)
                samples.forEach { sample ->
                    insertMetricUploadSample(sessionId, sample)
                }
            }
        }
    }

    override suspend fun clearMetricUploadSamples(sessionId: String) {
        mutex.withLock {
            database.rideForgeDatabaseQueries.deleteMetricUploadSamples(sessionId)
        }
    }

    override suspend fun getPendingSyncQueue(): PendingSyncQueueSnapshot = mutex.withLock {
        val eventRows = database.rideForgeDatabaseQueries.selectPendingSessionEvents { id, eventType, sessionId, workoutId, elapsedSeconds ->
            PendingSessionEventRow(
                id = id,
                eventType = eventType,
                sessionId = sessionId,
                workoutId = workoutId,
                elapsedSeconds = elapsedSeconds,
            )
        }.executeAsList()
        val events = eventRows.mapNotNull { row -> pendingSessionEvent(row) }
        val remoteSessionIds = database.rideForgeDatabaseQueries.selectRemoteSessionBindings { localSessionId, remoteSessionId ->
            localSessionId to remoteSessionId
        }.executeAsList().toMap()
        val nextId = database.rideForgeDatabaseQueries.selectPendingSyncNextId().executeAsOneOrNull()
            ?: ((events.maxOfOrNull { it.id } ?: 0L) + 1L)

        PendingSyncQueueSnapshot(
            events = events,
            remoteSessionIds = remoteSessionIds,
            nextId = nextId,
        )
    }

    override suspend fun replacePendingSyncQueue(snapshot: PendingSyncQueueSnapshot) {
        mutex.withLock {
            database.transaction {
                database.rideForgeDatabaseQueries.deletePendingSessionEventMetricSamples()
                database.rideForgeDatabaseQueries.deletePendingSessionEvents()
                database.rideForgeDatabaseQueries.deleteRemoteSessionBindings()
                database.rideForgeDatabaseQueries.deletePendingSyncMetadata()

                snapshot.events.forEach { event ->
                    insertPendingSessionEvent(event)
                }
                snapshot.remoteSessionIds.forEach { (localSessionId, remoteSessionId) ->
                    database.rideForgeDatabaseQueries.insertRemoteSessionBinding(
                        local_session_id = localSessionId,
                        remote_session_id = remoteSessionId,
                    )
                }
                database.rideForgeDatabaseQueries.upsertPendingSyncNextId(snapshot.nextId)
            }
        }
    }

    override suspend fun appendPendingSyncEvent(event: PendingSessionEvent, nextId: Long) {
        mutex.withLock {
            database.transaction {
                insertPendingSessionEvent(event)
                database.rideForgeDatabaseQueries.upsertPendingSyncNextId(nextId)
            }
        }
    }

    override suspend fun removePendingSyncEvent(eventId: Long) {
        mutex.withLock {
            database.transaction {
                database.rideForgeDatabaseQueries.deletePendingSessionEventMetricSamplesForEvent(eventId)
                database.rideForgeDatabaseQueries.deletePendingSessionEvent(eventId)
            }
        }
    }

    override suspend fun upsertRemoteSessionBinding(localSessionId: String, remoteSessionId: String) {
        mutex.withLock {
            database.rideForgeDatabaseQueries.insertRemoteSessionBinding(
                local_session_id = localSessionId,
                remote_session_id = remoteSessionId,
            )
        }
    }

    override suspend fun getLocalSessions(): LocalWorkoutSessionSnapshot = mutex.withLock {
        val sessions = database.rideForgeDatabaseQueries.selectLocalWorkoutSessions { id, workoutId, workoutName, elapsedSeconds, averagePowerWatts, normalizedPowerWatts, calories, tss, completionPercent, completedAtEpochMillis, averageSpeedKmh, totalDistanceKm ->
            workoutSession(
                id = id,
                workoutId = workoutId,
                workoutName = workoutName,
                elapsedSeconds = elapsedSeconds,
                averagePowerWatts = averagePowerWatts,
                normalizedPowerWatts = normalizedPowerWatts,
                calories = calories,
                tss = tss,
                completionPercent = completionPercent,
                completedAtEpochMillis = completedAtEpochMillis,
                averageSpeedKmh = averageSpeedKmh,
                totalDistanceKm = totalDistanceKm,
            )
        }.executeAsList()
        val metricRows = database.rideForgeDatabaseQueries.selectLocalWorkoutMetricSamples { sessionId, elapsedSeconds, currentPowerWatts, targetPowerWatts, cadenceRpm, heartRateBpm, speedKmh ->
            sessionId to metricSample(
                elapsedSeconds = elapsedSeconds,
                currentPowerWatts = currentPowerWatts,
                targetPowerWatts = targetPowerWatts,
                cadenceRpm = cadenceRpm,
                heartRateBpm = heartRateBpm,
                speedKmh = speedKmh,
            )
        }.executeAsList()

        LocalWorkoutSessionSnapshot(
            sessions = sessions,
            metricsBySessionId = metricRows
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .filterValues { it.isNotEmpty() },
        )
    }

    override suspend fun replaceLocalSessions(snapshot: LocalWorkoutSessionSnapshot) {
        mutex.withLock {
            database.transaction {
                database.rideForgeDatabaseQueries.deleteLocalWorkoutMetricSamples()
                database.rideForgeDatabaseQueries.deleteLocalWorkoutSessions()
                snapshot.sessions.forEach { session ->
                    insertLocalWorkoutSession(session)
                }
                snapshot.metricsBySessionId.forEach { (sessionId, samples) ->
                    samples.forEach { sample ->
                        insertLocalWorkoutMetricSample(sessionId, sample)
                    }
                }
            }
        }
    }

    override suspend fun getLocalSession(sessionId: String): WorkoutSession? = mutex.withLock {
        database.rideForgeDatabaseQueries.selectLocalWorkoutSession(sessionId) { id, workoutId, workoutName, elapsedSeconds, averagePowerWatts, normalizedPowerWatts, calories, tss, completionPercent, completedAtEpochMillis, averageSpeedKmh, totalDistanceKm ->
            workoutSession(
                id = id,
                workoutId = workoutId,
                workoutName = workoutName,
                elapsedSeconds = elapsedSeconds,
                averagePowerWatts = averagePowerWatts,
                normalizedPowerWatts = normalizedPowerWatts,
                calories = calories,
                tss = tss,
                completionPercent = completionPercent,
                completedAtEpochMillis = completedAtEpochMillis,
                averageSpeedKmh = averageSpeedKmh,
                totalDistanceKm = totalDistanceKm,
            )
        }.executeAsOneOrNull()
    }

    override suspend fun upsertLocalSession(session: WorkoutSession) {
        mutex.withLock {
            insertLocalWorkoutSession(session)
        }
    }

    override suspend fun getLocalSessionMetrics(sessionId: String): List<MetricSample> = mutex.withLock {
        database.rideForgeDatabaseQueries.selectLocalWorkoutSessionMetricSamples(sessionId) { elapsedSeconds, currentPowerWatts, targetPowerWatts, cadenceRpm, heartRateBpm, speedKmh ->
            metricSample(
                elapsedSeconds = elapsedSeconds,
                currentPowerWatts = currentPowerWatts,
                targetPowerWatts = targetPowerWatts,
                cadenceRpm = cadenceRpm,
                heartRateBpm = heartRateBpm,
                speedKmh = speedKmh,
            )
        }.executeAsList()
    }

    override suspend fun appendLocalSessionMetrics(sessionId: String, samples: List<MetricSample>) {
        if (samples.isEmpty()) return

        mutex.withLock {
            database.transaction {
                samples.forEach { sample ->
                    insertLocalWorkoutMetricSample(sessionId, sample)
                }
            }
        }
    }

    override suspend fun replaceLocalSessionMetrics(sessionId: String, samples: List<MetricSample>) {
        mutex.withLock {
            database.transaction {
                database.rideForgeDatabaseQueries.deleteLocalWorkoutSessionMetricSamples(sessionId)
                samples.forEach { sample ->
                    insertLocalWorkoutMetricSample(sessionId, sample)
                }
            }
        }
    }

    override suspend fun getActiveWorkout(): StoredActiveWorkout? = mutex.withLock {
        val row = database.rideForgeDatabaseQueries.selectActiveWorkout { workoutId, sessionId, ftpWatts, elapsedSeconds, controlMode, isPaused, ergEnabled, updatedAtEpochMillis, riderWeightKg, distanceKm ->
            ActiveWorkoutRow(
                workoutId = workoutId,
                sessionId = sessionId,
                ftpWatts = ftpWatts,
                elapsedSeconds = elapsedSeconds,
                controlMode = controlMode,
                isPaused = isPaused,
                ergEnabled = ergEnabled,
                updatedAtEpochMillis = updatedAtEpochMillis,
                riderWeightKg = riderWeightKg,
                distanceKm = distanceKm,
            )
        }.executeAsOneOrNull() ?: return@withLock null
        activeWorkout(row)
    }

    override suspend fun replaceActiveWorkout(workout: StoredActiveWorkout) {
        mutex.withLock {
            database.transaction {
                database.rideForgeDatabaseQueries.upsertActiveWorkout(
                    workout_id = workout.workoutId,
                    session_id = workout.sessionId,
                    ftp_watts = workout.ftpWatts.toLong(),
                    elapsed_seconds = workout.elapsedSeconds.toLong(),
                    control_mode = workout.controlMode.name,
                    is_paused = workout.isPaused.toLong(),
                    erg_enabled = workout.ergEnabled.toLong(),
                    updated_at_epoch_millis = workout.updatedAtEpochMillis,
                    rider_weight_kg = workout.riderWeightKg,
                    distance_km = workout.distanceKm,
                )
                syncActiveWorkoutSamples(workout.samples)
            }
        }
    }

    override suspend fun clearActiveWorkout() {
        mutex.withLock {
            database.transaction {
                database.rideForgeDatabaseQueries.deleteActiveWorkoutSamples()
                database.rideForgeDatabaseQueries.deleteActiveWorkout()
            }
        }
    }

    private fun insertMetricUploadSample(sessionId: String, sample: MetricSample) {
        database.rideForgeDatabaseQueries.insertMetricUploadSample(
            session_id = sessionId,
            elapsed_seconds = sample.elapsedSeconds.toLong(),
            current_power_watts = sample.currentPowerWatts.toLong(),
            target_power_watts = sample.targetPowerWatts.toLong(),
            cadence_rpm = sample.cadenceRpm.toLong(),
            heart_rate_bpm = sample.heartRateBpm.toLong(),
            speed_kmh = sample.speedKmh,
        )
    }

    private fun insertActiveWorkoutSample(sample: MetricSample) {
        database.rideForgeDatabaseQueries.insertActiveWorkoutSample(
            elapsed_seconds = sample.elapsedSeconds.toLong(),
            current_power_watts = sample.currentPowerWatts.toLong(),
            target_power_watts = sample.targetPowerWatts.toLong(),
            cadence_rpm = sample.cadenceRpm.toLong(),
            heart_rate_bpm = sample.heartRateBpm.toLong(),
            speed_kmh = sample.speedKmh,
        )
    }

    private fun updateActiveWorkoutSample(rowId: Long, sample: MetricSample) {
        database.rideForgeDatabaseQueries.updateActiveWorkoutSample(
            elapsed_seconds = sample.elapsedSeconds.toLong(),
            current_power_watts = sample.currentPowerWatts.toLong(),
            target_power_watts = sample.targetPowerWatts.toLong(),
            cadence_rpm = sample.cadenceRpm.toLong(),
            heart_rate_bpm = sample.heartRateBpm.toLong(),
            speed_kmh = sample.speedKmh,
            id = rowId,
        )
    }

    private fun metricSample(
        elapsedSeconds: Long,
        currentPowerWatts: Long,
        targetPowerWatts: Long,
        cadenceRpm: Long,
        heartRateBpm: Long,
        speedKmh: Double = 0.0,
    ): MetricSample {
        return MetricSample(
            elapsedSeconds = elapsedSeconds.toInt(),
            currentPowerWatts = currentPowerWatts.toInt(),
            targetPowerWatts = targetPowerWatts.toInt(),
            cadenceRpm = cadenceRpm.toInt(),
            heartRateBpm = heartRateBpm.toInt(),
            speedKmh = speedKmh,
        )
    }

    private fun activeWorkout(row: ActiveWorkoutRow): StoredActiveWorkout? {
        val controlMode = runCatching { WorkoutControlMode.valueOf(row.controlMode) }.getOrNull() ?: return null
        return StoredActiveWorkout(
            workoutId = row.workoutId,
            sessionId = row.sessionId,
            ftpWatts = row.ftpWatts.toInt(),
            elapsedSeconds = row.elapsedSeconds.toInt(),
            samples = activeWorkoutSamples(),
            controlMode = controlMode,
            isPaused = row.isPaused != 0L,
            ergEnabled = row.ergEnabled != 0L,
            updatedAtEpochMillis = row.updatedAtEpochMillis,
            riderWeightKg = row.riderWeightKg,
            distanceKm = row.distanceKm,
        )
    }

    private fun activeWorkoutSamples(): List<MetricSample> {
        return database.rideForgeDatabaseQueries.selectActiveWorkoutSamples { elapsedSeconds, currentPowerWatts, targetPowerWatts, cadenceRpm, heartRateBpm, speedKmh ->
            metricSample(
                elapsedSeconds = elapsedSeconds,
                currentPowerWatts = currentPowerWatts,
                targetPowerWatts = targetPowerWatts,
                cadenceRpm = cadenceRpm,
                heartRateBpm = heartRateBpm,
                speedKmh = speedKmh,
            )
        }.executeAsList()
    }

    private fun activeWorkoutSampleRows(): List<ActiveWorkoutSampleRow> {
        return database.rideForgeDatabaseQueries.selectActiveWorkoutSampleRows { id, elapsedSeconds, currentPowerWatts, targetPowerWatts, cadenceRpm, heartRateBpm, speedKmh ->
            ActiveWorkoutSampleRow(
                id = id,
                sample = metricSample(
                    elapsedSeconds = elapsedSeconds,
                    currentPowerWatts = currentPowerWatts,
                    targetPowerWatts = targetPowerWatts,
                    cadenceRpm = cadenceRpm,
                    heartRateBpm = heartRateBpm,
                    speedKmh = speedKmh,
                ),
            )
        }.executeAsList()
    }

    private fun syncActiveWorkoutSamples(newSamples: List<MetricSample>) {
        val existingRows = activeWorkoutSampleRows()
        val existingSamples = existingRows.map { it.sample }

        when {
            newSamples == existingSamples -> return
            newSamples.isEmpty() -> database.rideForgeDatabaseQueries.deleteActiveWorkoutSamples()
            existingRows.isEmpty() -> {
                newSamples.forEach(::insertActiveWorkoutSample)
                trimActiveWorkoutSamples()
            }
            canUpdateLatestSample(existingSamples, newSamples) -> {
                updateActiveWorkoutSample(existingRows.last().id, newSamples.last())
            }
            else -> {
                val overlap = suffixPrefixOverlap(existingSamples, newSamples)
                if (overlap > 0) {
                    existingRows.dropLast(overlap).forEach { row ->
                        database.rideForgeDatabaseQueries.deleteActiveWorkoutSampleById(row.id)
                    }
                    newSamples.drop(overlap).forEach(::insertActiveWorkoutSample)
                    trimActiveWorkoutSamples()
                } else {
                    database.rideForgeDatabaseQueries.deleteActiveWorkoutSamples()
                    newSamples.forEach(::insertActiveWorkoutSample)
                    trimActiveWorkoutSamples()
                }
            }
        }
    }

    private fun canUpdateLatestSample(
        existingSamples: List<MetricSample>,
        newSamples: List<MetricSample>,
    ): Boolean {
        if (existingSamples.size != newSamples.size || existingSamples.isEmpty()) return false
        return existingSamples.dropLast(1) == newSamples.dropLast(1) &&
            existingSamples.last().elapsedSeconds == newSamples.last().elapsedSeconds
    }

    private fun suffixPrefixOverlap(
        existingSamples: List<MetricSample>,
        newSamples: List<MetricSample>,
    ): Int {
        val maxOverlap = minOf(existingSamples.size, newSamples.size)
        for (size in maxOverlap downTo 1) {
            if (existingSamples.takeLast(size) == newSamples.take(size)) {
                return size
            }
        }
        return 0
    }

    private fun trimActiveWorkoutSamples() {
        database.rideForgeDatabaseQueries.trimActiveWorkoutSamples(MaxActiveWorkoutSamples.toLong())
    }

    private fun insertLocalWorkoutSession(session: WorkoutSession) {
        database.rideForgeDatabaseQueries.upsertLocalWorkoutSession(
            id = session.id,
            workout_id = session.workoutId,
            workout_name = session.workoutName,
            elapsed_seconds = session.elapsedSeconds.toLong(),
            average_power_watts = session.averagePowerWatts.toLong(),
            normalized_power_watts = session.normalizedPowerWatts.toLong(),
            calories = session.calories.toLong(),
            tss = session.tss.toLong(),
            completion_percent = session.completionPercent.toLong(),
            completed_at_epoch_millis = session.completedAtEpochMillis,
            average_speed_kmh = session.averageSpeedKmh,
            total_distance_km = session.totalDistanceKm,
        )
    }

    private fun insertLocalWorkoutMetricSample(sessionId: String, sample: MetricSample) {
        database.rideForgeDatabaseQueries.insertLocalWorkoutMetricSample(
            session_id = sessionId,
            elapsed_seconds = sample.elapsedSeconds.toLong(),
            current_power_watts = sample.currentPowerWatts.toLong(),
            target_power_watts = sample.targetPowerWatts.toLong(),
            cadence_rpm = sample.cadenceRpm.toLong(),
            heart_rate_bpm = sample.heartRateBpm.toLong(),
            speed_kmh = sample.speedKmh,
        )
    }

    private fun workoutSession(
        id: String,
        workoutId: String,
        workoutName: String,
        elapsedSeconds: Long,
        averagePowerWatts: Long,
        normalizedPowerWatts: Long,
        calories: Long,
        tss: Long,
        completionPercent: Long,
        completedAtEpochMillis: Long?,
        averageSpeedKmh: Double?,
        totalDistanceKm: Double?,
    ): WorkoutSession {
        return WorkoutSession(
            id = id,
            workoutId = workoutId,
            workoutName = workoutName,
            elapsedSeconds = elapsedSeconds.toInt(),
            averagePowerWatts = averagePowerWatts.toInt(),
            normalizedPowerWatts = normalizedPowerWatts.toInt(),
            calories = calories.toInt(),
            tss = tss.toInt(),
            completionPercent = completionPercent.toInt(),
            completedAtEpochMillis = completedAtEpochMillis,
            averageSpeedKmh = averageSpeedKmh,
            totalDistanceKm = totalDistanceKm,
        )
    }

    private fun pendingSessionEvent(row: PendingSessionEventRow): PendingSessionEvent? {
        return when (row.eventType) {
            PendingEventTypeStart -> PendingSessionEvent.Start(
                id = row.id,
                sessionId = row.sessionId,
                workoutId = row.workoutId ?: return null,
            )
            PendingEventTypePause -> PendingSessionEvent.Pause(
                id = row.id,
                sessionId = row.sessionId,
            )
            PendingEventTypeResume -> PendingSessionEvent.Resume(
                id = row.id,
                sessionId = row.sessionId,
            )
            PendingEventTypeMetrics -> PendingSessionEvent.Metrics(
                id = row.id,
                sessionId = row.sessionId,
                samples = pendingMetricSamples(row.id),
            )
            PendingEventTypeComplete -> PendingSessionEvent.Complete(
                id = row.id,
                sessionId = row.sessionId,
                elapsedSeconds = row.elapsedSeconds?.toInt(),
            )
            else -> null
        }
    }

    private fun pendingMetricSamples(eventId: Long): List<MetricSample> {
        return database.rideForgeDatabaseQueries.selectPendingSessionEventMetricSamples(eventId) { elapsedSeconds, currentPowerWatts, targetPowerWatts, cadenceRpm, heartRateBpm, speedKmh ->
            metricSample(
                elapsedSeconds = elapsedSeconds,
                currentPowerWatts = currentPowerWatts,
                targetPowerWatts = targetPowerWatts,
                cadenceRpm = cadenceRpm,
                heartRateBpm = heartRateBpm,
                speedKmh = speedKmh,
            )
        }.executeAsList()
    }

    private fun insertPendingSessionEvent(event: PendingSessionEvent) {
        val eventType = when (event) {
            is PendingSessionEvent.Start -> PendingEventTypeStart
            is PendingSessionEvent.Pause -> PendingEventTypePause
            is PendingSessionEvent.Resume -> PendingEventTypeResume
            is PendingSessionEvent.Metrics -> PendingEventTypeMetrics
            is PendingSessionEvent.Complete -> PendingEventTypeComplete
        }
        database.rideForgeDatabaseQueries.deletePendingSessionEventMetricSamplesForEvent(event.id)
        database.rideForgeDatabaseQueries.insertPendingSessionEvent(
            id = event.id,
            event_type = eventType,
            session_id = event.sessionId,
            workout_id = (event as? PendingSessionEvent.Start)?.workoutId,
            elapsed_seconds = (event as? PendingSessionEvent.Complete)?.elapsedSeconds?.toLong(),
        )
        if (event is PendingSessionEvent.Metrics) {
            event.samples.forEachIndexed { index, sample ->
                database.rideForgeDatabaseQueries.insertPendingSessionEventMetricSample(
                    event_id = event.id,
                    sample_index = index.toLong(),
                    elapsed_seconds = sample.elapsedSeconds.toLong(),
                    current_power_watts = sample.currentPowerWatts.toLong(),
                    target_power_watts = sample.targetPowerWatts.toLong(),
                    cadence_rpm = sample.cadenceRpm.toLong(),
                    heart_rate_bpm = sample.heartRateBpm.toLong(),
                    speed_kmh = sample.speedKmh,
                )
            }
        }
    }

    private data class PendingSessionEventRow(
        val id: Long,
        val eventType: String,
        val sessionId: String,
        val workoutId: String?,
        val elapsedSeconds: Long?,
    )

    private data class ActiveWorkoutRow(
        val workoutId: String,
        val sessionId: String,
        val ftpWatts: Long,
        val elapsedSeconds: Long,
        val controlMode: String,
        val isPaused: Long,
        val ergEnabled: Long,
        val updatedAtEpochMillis: Long,
        val riderWeightKg: Double,
        val distanceKm: Double,
    )

    private data class ActiveWorkoutSampleRow(
        val id: Long,
        val sample: MetricSample,
    )

    private companion object {
        const val MaxActiveWorkoutSamples = 96
        const val PendingEventTypeStart = "start"
        const val PendingEventTypePause = "pause"
        const val PendingEventTypeResume = "resume"
        const val PendingEventTypeMetrics = "metrics"
        const val PendingEventTypeComplete = "complete"
    }
}

private fun Boolean.toLong(): Long = if (this) 1L else 0L
