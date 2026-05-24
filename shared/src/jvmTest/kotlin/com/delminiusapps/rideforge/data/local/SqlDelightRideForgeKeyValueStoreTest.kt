package com.delminiusapps.rideforge.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.delminiusapps.rideforge.data.repository.local.LocalWorkoutSessionRepository
import com.delminiusapps.rideforge.data.repository.sync.PendingSessionEvent
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.WorkoutSession
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqlDelightRideForgeKeyValueStoreTest {

    @Test
    fun storesWorkoutStateAndSyncQueueInSqlite() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        RideForgeDatabase.Schema.create(driver)
        val store = SqlDelightRideForgeKeyValueStore(driver)
        val storage = WorkoutLocalStorage(store)
        val sample = MetricSample(5, 200, 210, 88, 130, speedKmh = 31.4)
        val startEvent = PendingSessionEvent.Start(1L, "local-session", "workout-a")
        val metricsEvent = PendingSessionEvent.Metrics(2L, "local-session", listOf(sample))
        val completeEvent = PendingSessionEvent.Complete(3L, "local-session", 5)

        storage.saveActiveWorkout(
            StoredActiveWorkout(
                workoutId = "workout-a",
                sessionId = "local-session",
                ftpWatts = 240,
                elapsedSeconds = 5,
                samples = listOf(sample),
                controlMode = WorkoutControlMode.TRAINER,
                isPaused = false,
                ergEnabled = true,
                updatedAtEpochMillis = 1_000L,
            ),
        )
        storage.savePendingSyncQueue(
            PendingSyncQueueSnapshot(
                events = listOf(startEvent, metricsEvent, completeEvent),
                remoteSessionIds = mapOf("local-session" to "remote-session"),
                nextId = 4L,
            ),
        )

        val restoredWorkout = storage.getActiveWorkout()
        val restoredQueue = storage.getPendingSyncQueue()

        assertEquals("workout-a", restoredWorkout?.workoutId)
        assertEquals(listOf(sample), restoredWorkout?.samples)
        assertEquals(WorkoutControlMode.TRAINER, restoredWorkout?.controlMode)
        assertEquals(null, store.readString("active_workout"))
        assertEquals(listOf(startEvent, metricsEvent, completeEvent), restoredQueue.events)
        assertEquals(mapOf("local-session" to "remote-session"), restoredQueue.remoteSessionIds)
        assertEquals(4L, restoredQueue.nextId)
        assertEquals(null, store.readString("pending_sync_queue"))
    }

    @Test
    fun storesActiveWorkoutAsTypedSqlRowsAndClearsIt() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        RideForgeDatabase.Schema.create(driver)
        val store = SqlDelightRideForgeKeyValueStore(driver)
        val storage = WorkoutLocalStorage(store)
        val first = MetricSample(5, 200, 210, 88, 130, speedKmh = 30.8)
        val second = MetricSample(6, 202, 210, 89, 131, speedKmh = 31.1)
        val activeWorkout = StoredActiveWorkout(
            workoutId = "workout-a",
            sessionId = "local-session",
            ftpWatts = 240,
            elapsedSeconds = 6,
            samples = listOf(first, second),
            controlMode = WorkoutControlMode.TRAINER,
            isPaused = false,
            ergEnabled = true,
            updatedAtEpochMillis = 1_000L,
        )

        storage.saveActiveWorkout(activeWorkout)

        val restored = WorkoutLocalStorage(SqlDelightRideForgeKeyValueStore(driver)).getActiveWorkout()

        assertEquals(activeWorkout, restored)
        assertEquals(null, store.readString("active_workout"))

        storage.clearActiveWorkout()

        assertEquals(null, storage.getActiveWorkout())
    }

    @Test
    fun activeWorkoutSamplePersistenceAppendsAndTrimsWithoutRewritingRetainedRows() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        RideForgeDatabase.Schema.create(driver)
        val database = RideForgeDatabase(driver)
        val storage = WorkoutLocalStorage(SqlDelightRideForgeKeyValueStore(driver))
        val initialSamples = (1..96).map { second ->
            MetricSample(second, 180 + second, 210, 88, 130)
        }
        val baseWorkout = StoredActiveWorkout(
            workoutId = "workout-a",
            sessionId = "local-session",
            ftpWatts = 240,
            elapsedSeconds = 96,
            samples = initialSamples,
            controlMode = WorkoutControlMode.TRAINER,
            isPaused = false,
            ergEnabled = true,
            updatedAtEpochMillis = 1_000L,
        )

        storage.saveActiveWorkout(baseWorkout)
        val initialRows = activeWorkoutSampleRows(database)
        val retainedRowId = initialRows.first { it.second.elapsedSeconds == 2 }.first

        val rolledSamples = initialSamples.drop(1) + MetricSample(97, 277, 210, 88, 130)
        storage.saveActiveWorkout(
            baseWorkout.copy(
                elapsedSeconds = 97,
                samples = rolledSamples,
                updatedAtEpochMillis = 2_000L,
            ),
        )
        val rolledRows = activeWorkoutSampleRows(database)

        assertEquals(96, rolledRows.size)
        assertEquals(2, rolledRows.first().second.elapsedSeconds)
        assertEquals(97, rolledRows.last().second.elapsedSeconds)
        assertEquals(retainedRowId, rolledRows.first().first)

        val updatedLatestSample = rolledRows.last().second.copy(currentPowerWatts = 300)
        storage.saveActiveWorkout(
            baseWorkout.copy(
                elapsedSeconds = 97,
                samples = rolledSamples.dropLast(1) + updatedLatestSample,
                updatedAtEpochMillis = 3_000L,
            ),
        )
        val updatedRows = activeWorkoutSampleRows(database)

        assertEquals(rolledRows.map { it.first }, updatedRows.map { it.first })
        assertEquals(updatedLatestSample, updatedRows.last().second)
    }

    @Test
    fun storesMetricUploadBufferAsTypedSqlRows() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        RideForgeDatabase.Schema.create(driver)
        val store = SqlDelightRideForgeKeyValueStore(driver)
        val storage = WorkoutLocalStorage(store)
        val first = MetricSample(5, 200, 210, 88, 130)
        val second = MetricSample(6, 202, 210, 89, 131)
        val other = MetricSample(1, 180, 185, 82, 120)

        storage.appendMetricUploadSample("session-a", first)
        storage.appendMetricUploadSample("session-a", second)
        storage.appendMetricUploadSample("session-b", other)

        val restored = WorkoutLocalStorage(SqlDelightRideForgeKeyValueStore(driver)).getMetricUploadBuffer()
        val drained = storage.drainMetricUploadSamples("session-a")

        assertEquals(listOf(first, second), restored.samplesBySessionId["session-a"])
        assertEquals(listOf(other), restored.samplesBySessionId["session-b"])
        assertEquals(listOf(first, second), drained)
        assertEquals(mapOf("session-b" to listOf(other)), storage.getMetricUploadBuffer().samplesBySessionId)

        storage.prependMetricUploadSamples("session-a", drained)

        assertEquals(listOf(first, second), storage.getMetricUploadBuffer().samplesBySessionId["session-a"])
        assertEquals(null, store.readString("metric_upload_buffer"))
    }

    @Test
    fun migratesLegacyPendingSyncQueueJsonIntoTypedSqlRows() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        RideForgeDatabase.Schema.create(driver)
        val store = SqlDelightRideForgeKeyValueStore(driver)
        val storage = WorkoutLocalStorage(store)
        val sample = MetricSample(10, 240, 245, 92, 142)
        val legacySnapshot = PendingSyncQueueSnapshot(
            events = listOf(PendingSessionEvent.Metrics(9L, "local-session", listOf(sample))),
            remoteSessionIds = mapOf("local-session" to "remote-session"),
            nextId = 10L,
        )

        store.writeString("pending_sync_queue", Json.encodeToString(legacySnapshot))

        val restored = storage.getPendingSyncQueue()
        val reread = WorkoutLocalStorage(SqlDelightRideForgeKeyValueStore(driver)).getPendingSyncQueue()

        assertEquals(listOf(PendingSessionEvent.Metrics(1L, "local-session", listOf(sample))), restored.events)
        assertEquals(mapOf("local-session" to "remote-session"), restored.remoteSessionIds)
        assertEquals(10L, restored.nextId)
        assertEquals(restored, reread)
        assertEquals(null, store.readString("pending_sync_queue"))
    }

    @Test
    fun pendingSyncQueueSupportsIncrementalAppendBindAndRemove() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        RideForgeDatabase.Schema.create(driver)
        val database = RideForgeDatabase(driver)
        val storage = WorkoutLocalStorage(SqlDelightRideForgeKeyValueStore(driver))
        val sample = MetricSample(10, 240, 245, 92, 142)
        val startEvent = PendingSessionEvent.Start(1L, "local-session", "workout-a")
        val metricsEvent = PendingSessionEvent.Metrics(2L, "local-session", listOf(sample))

        storage.appendPendingSyncEvent(startEvent, nextId = 2L)
        storage.appendPendingSyncEvent(metricsEvent, nextId = 3L)
        storage.upsertRemoteSessionBinding("local-session", "remote-session")

        assertEquals(
            PendingSyncQueueSnapshot(
                events = listOf(startEvent, metricsEvent),
                remoteSessionIds = mapOf("local-session" to "remote-session"),
                nextId = 3L,
            ),
            storage.getPendingSyncQueue(),
        )
        assertEquals(listOf(1L, 2L), pendingSessionEventIds(database))
        assertEquals(listOf(sample), pendingSessionEventMetricSamples(database, 2L))

        storage.removePendingSyncEvent(1L)

        assertEquals(listOf(metricsEvent), storage.getPendingSyncQueue().events)
        assertEquals(listOf(2L), pendingSessionEventIds(database))
        assertEquals(listOf(sample), pendingSessionEventMetricSamples(database, 2L))

        storage.removePendingSyncEvent(2L)

        assertEquals(emptyList(), storage.getPendingSyncQueue().events)
        assertEquals(emptyList(), pendingSessionEventIds(database))
        assertEquals(emptyList(), pendingSessionEventMetricSamples(database, 2L))
    }


    @Test
    fun migratesLegacyActiveWorkoutJsonIntoTypedSqlRows() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        RideForgeDatabase.Schema.create(driver)
        val store = SqlDelightRideForgeKeyValueStore(driver)
        val storage = WorkoutLocalStorage(store)
        val sample = MetricSample(15, 230, 235, 92, 140)
        val legacyWorkout = StoredActiveWorkout(
            workoutId = "workout-a",
            sessionId = "local-session",
            ftpWatts = 250,
            elapsedSeconds = 15,
            samples = listOf(sample),
            controlMode = WorkoutControlMode.SIMULATION,
            isPaused = true,
            ergEnabled = false,
            updatedAtEpochMillis = 2_000L,
        )

        store.writeString("active_workout", Json.encodeToString(legacyWorkout))

        val restored = storage.getActiveWorkout()
        val reread = WorkoutLocalStorage(SqlDelightRideForgeKeyValueStore(driver)).getActiveWorkout()

        assertEquals(legacyWorkout, restored)
        assertEquals(legacyWorkout, reread)
        assertEquals(null, store.readString("active_workout"))
    }

    @Test
    fun storesLocalWorkoutSessionsAsTypedSqlRows() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        RideForgeDatabase.Schema.create(driver)
        val store = SqlDelightRideForgeKeyValueStore(driver)
        val storage = WorkoutLocalStorage(store)
        val session = WorkoutSession(
            id = "local-session",
            workoutId = "workout-a",
            workoutName = "Workout A",
            elapsedSeconds = 5,
            averagePowerWatts = 200,
            normalizedPowerWatts = 216,
            calories = 4,
            tss = 1,
            completionPercent = 10,
            completedAtEpochMillis = 1_778_888_000_000L,
        )
        val first = MetricSample(5, 200, 210, 88, 130)
        val second = MetricSample(6, 202, 210, 89, 131)

        storage.saveLocalSessions(
            LocalWorkoutSessionSnapshot(
                sessions = listOf(session),
                metricsBySessionId = mapOf(session.id to listOf(first)),
            ),
        )
        storage.appendLocalSessionMetrics(session.id, listOf(second))
        storage.upsertLocalSession(session.copy(elapsedSeconds = 6, completionPercent = 20))

        val restored = WorkoutLocalStorage(SqlDelightRideForgeKeyValueStore(driver)).getLocalSessions()

        assertEquals(listOf(session.copy(elapsedSeconds = 6, completionPercent = 20)), restored.sessions)
        assertEquals(listOf(first, second), restored.metricsBySessionId[session.id])
        assertEquals(null, store.readString("local_workout_sessions"))
    }

    @Test
    fun migratesLegacyLocalWorkoutSessionJsonIntoTypedSqlRows() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        RideForgeDatabase.Schema.create(driver)
        val store = SqlDelightRideForgeKeyValueStore(driver)
        val storage = WorkoutLocalStorage(store)
        val session = WorkoutSession(
            id = "legacy-session",
            workoutId = "workout-a",
            workoutName = "Workout A",
            elapsedSeconds = 12,
            averagePowerWatts = 190,
            normalizedPowerWatts = 205,
            calories = 8,
            tss = 2,
            completionPercent = 100,
        )
        val sample = MetricSample(12, 190, 200, 86, 126)
        val legacySnapshot = LocalWorkoutSessionSnapshot(
            sessions = listOf(session),
            metricsBySessionId = mapOf(session.id to listOf(sample)),
        )

        store.writeString("local_workout_sessions", Json.encodeToString(legacySnapshot))

        val restored = storage.getLocalSessions()
        val reread = WorkoutLocalStorage(SqlDelightRideForgeKeyValueStore(driver)).getLocalSessions()

        assertEquals(listOf(session), restored.sessions)
        assertEquals(listOf(sample), restored.metricsBySessionId[session.id])
        assertEquals(restored, reread)
        assertEquals(null, store.readString("local_workout_sessions"))
    }

    @Test
    fun localWorkoutSessionRepositoryUsesTypedSqlStorage() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        RideForgeDatabase.Schema.create(driver)
        val store = SqlDelightRideForgeKeyValueStore(driver)
        val storage = WorkoutLocalStorage(store)
        val repository = LocalWorkoutSessionRepository(storage)
        val first = MetricSample(1, 180, 190, 86, 126, speedKmh = 28.2)
        val second = MetricSample(2, 220, 225, 90, 132, speedKmh = 32.5)

        val session = repository.startSession("workout-a")
        repository.addMetrics(session.id, listOf(first, second))
        val summary = repository.completeSession(session.id, 2)

        assertTrue(session.id.startsWith("local-workout-a-"))
        assertEquals(listOf(first, second), repository.getSessionMetrics(session.id))
        assertEquals(200, summary.averagePowerWatts)
        assertEquals(216, summary.normalizedPowerWatts)
        assertNotNull(summary.averageSpeedKmh)
        assertNotNull(summary.totalDistanceKm)
        assertNotNull(summary.completedAtEpochMillis)
        assertEquals(summary, repository.getSessionSummary(session.id))
        assertEquals(null, store.readString("local_workout_sessions"))
    }

    private fun activeWorkoutSampleRows(database: RideForgeDatabase): List<Pair<Long, MetricSample>> {
        return database.rideForgeDatabaseQueries.selectActiveWorkoutSampleRows { id, elapsedSeconds, currentPowerWatts, targetPowerWatts, cadenceRpm, heartRateBpm, speedKmh ->
            id to MetricSample(
                elapsedSeconds = elapsedSeconds.toInt(),
                currentPowerWatts = currentPowerWatts.toInt(),
                targetPowerWatts = targetPowerWatts.toInt(),
                cadenceRpm = cadenceRpm.toInt(),
                heartRateBpm = heartRateBpm.toInt(),
                speedKmh = speedKmh,
            )
        }.executeAsList()
    }

    private fun pendingSessionEventIds(database: RideForgeDatabase): List<Long> {
        return database.rideForgeDatabaseQueries.selectPendingSessionEvents { id, _, _, _, _ -> id }.executeAsList()
    }

    private fun pendingSessionEventMetricSamples(database: RideForgeDatabase, eventId: Long): List<MetricSample> {
        return database.rideForgeDatabaseQueries.selectPendingSessionEventMetricSamples(eventId) { elapsedSeconds, currentPowerWatts, targetPowerWatts, cadenceRpm, heartRateBpm, speedKmh ->
            MetricSample(
                elapsedSeconds = elapsedSeconds.toInt(),
                currentPowerWatts = currentPowerWatts.toInt(),
                targetPowerWatts = targetPowerWatts.toInt(),
                cadenceRpm = cadenceRpm.toInt(),
                heartRateBpm = heartRateBpm.toInt(),
                speedKmh = speedKmh,
            )
        }.executeAsList()
    }
}
