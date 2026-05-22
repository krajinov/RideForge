package com.delminiusapps.rideforge.repositories

import com.delminiusapps.rideforge.models.Device
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.RefreshTokenRecord
import com.delminiusapps.rideforge.models.SessionStatus
import com.delminiusapps.rideforge.models.StravaConnection
import com.delminiusapps.rideforge.models.StravaSync
import com.delminiusapps.rideforge.models.StravaSyncStatus
import com.delminiusapps.rideforge.models.TrainingPlan
import com.delminiusapps.rideforge.models.User
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutInterval
import com.delminiusapps.rideforge.models.WorkoutSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryUserRepository : UserRepository {
    private val mutex = Mutex()
    private val users = SeedData.users.associateBy { it.id }.toMutableMap()

    override suspend fun create(user: User): User = mutex.withLock {
        users[user.id] = user
        user
    }

    override suspend fun findById(id: String): User? = mutex.withLock { users[id] }

    override suspend fun findByEmail(email: String): User? = mutex.withLock {
        users.values.firstOrNull { it.email.equals(email, ignoreCase = true) }
    }

    override suspend fun update(user: User): User = mutex.withLock {
        users[user.id] = user
        user
    }
}

class InMemoryTrainingPlanRepository : TrainingPlanRepository {
    private val plans = SeedData.plans.associateBy { it.id }

    override suspend fun list(limit: Int, offset: Int): List<TrainingPlan> = plans.values.drop(offset).take(limit)
    override suspend fun count(): Int = plans.size
    override suspend fun findById(id: String): TrainingPlan? = plans[id]
}

class InMemoryWorkoutRepository : WorkoutRepository {
    private val workouts = SeedData.workouts.associateBy { it.id }
    private val intervals = SeedData.intervals.groupBy { it.workoutId }

    override suspend fun list(limit: Int, offset: Int): List<Workout> = workouts.values.drop(offset).take(limit)
    override suspend fun count(): Int = workouts.size
    override suspend fun findById(id: String): Workout? = workouts[id]
    override suspend fun findByPlanId(planId: String): List<Workout> = workouts.values.filter { it.planId == planId }
    override suspend fun intervalsForWorkout(workoutId: String): List<WorkoutInterval> = intervals[workoutId].orEmpty()
}

class InMemorySessionRepository : SessionRepository {
    private val mutex = Mutex()
    private val sessions = SeedData.sessions.associateBy { it.id }.toMutableMap()
    private val metrics = mutableMapOf<String, MutableList<MetricSample>>()

    override suspend fun create(session: WorkoutSession): WorkoutSession = mutex.withLock {
        sessions[session.id] = session
        session
    }

    override suspend fun findById(id: String): WorkoutSession? = mutex.withLock { sessions[id] }

    override suspend fun update(session: WorkoutSession): WorkoutSession = mutex.withLock {
        sessions[session.id] = session
        session
    }

    override suspend fun addMetric(sample: MetricSample): MetricSample = mutex.withLock {
        metrics.getOrPut(sample.sessionId) { mutableListOf() }.add(sample)
        sample
    }

    override suspend fun metricsForSession(sessionId: String): List<MetricSample> = mutex.withLock {
        metrics[sessionId].orEmpty().toList()
    }

    override suspend fun historyForUser(userId: String, limit: Int, offset: Int): List<WorkoutSession> = mutex.withLock {
        sessions.values
            .filter { it.userId == userId && it.status == SessionStatus.completed }
            .sortedByDescending { it.completedAt ?: it.startedAt }
            .drop(offset)
            .take(limit)
    }

    override suspend fun historyCount(userId: String): Int = mutex.withLock {
        sessions.values.count { it.userId == userId && it.status == SessionStatus.completed }
    }

    override suspend fun deleteHistory(userId: String, sessionId: String): Boolean = mutex.withLock {
        val session = sessions[sessionId]
        if (session?.userId == userId && session.status == SessionStatus.completed) {
            sessions.remove(sessionId)
            metrics.remove(sessionId)
            true
        } else {
            false
        }
    }
}

class InMemoryDeviceRepository : DeviceRepository {
    private val mutex = Mutex()
    private val devices = SeedData.devices.associateBy { it.id }.toMutableMap()

    override suspend fun listAvailable(userId: String): List<Device> = mutex.withLock {
        devices.values.filter { it.userId == userId || it.connectionStatus == "available" }
    }

    override suspend fun current(userId: String): Device? = mutex.withLock {
        devices.values.firstOrNull { it.userId == userId && it.connectionStatus == "connected" }
    }

    override suspend fun connect(device: Device): Device = mutex.withLock {
        devices.replaceAll { _, old ->
            if (old.userId == device.userId && old.connectionStatus == "connected") old.copy(connectionStatus = "available") else old
        }
        devices[device.id] = device
        device
    }

    override suspend fun disconnect(userId: String): Device? = mutex.withLock {
        val current = devices.values.firstOrNull { it.userId == userId && it.connectionStatus == "connected" }
        if (current != null) {
            val updated = current.copy(connectionStatus = "disconnected")
            devices[current.id] = updated
            updated
        } else {
            null
        }
    }
}

class InMemoryRefreshTokenRepository : RefreshTokenRepository {
    private val mutex = Mutex()
    private val tokens = mutableMapOf<String, RefreshTokenRecord>()

    override suspend fun save(record: RefreshTokenRecord): RefreshTokenRecord = mutex.withLock {
        tokens[record.tokenHash] = record
        record
    }

    override suspend fun findByHash(tokenHash: String): RefreshTokenRecord? = mutex.withLock {
        tokens[tokenHash]
    }

    override suspend fun revoke(tokenHash: String) {
        mutex.withLock {
            val record = tokens[tokenHash]
            if (record != null) {
                tokens[tokenHash] = record.copy(revokedAt = com.delminiusapps.rideforge.utils.nowIso())
            }
        }
    }

    override suspend fun revokeIfActive(tokenHash: String): Boolean = mutex.withLock {
        val record = tokens[tokenHash]
        if (record != null && record.revokedAt == null) {
            tokens[tokenHash] = record.copy(revokedAt = com.delminiusapps.rideforge.utils.nowIso())
            true
        } else {
            false
        }
    }

    override suspend fun revokeAllForUser(userId: String) {
        mutex.withLock {
            tokens.replaceAll { _, record ->
                if (record.userId == userId && record.revokedAt == null) {
                    record.copy(revokedAt = com.delminiusapps.rideforge.utils.nowIso())
                } else {
                    record
                }
            }
        }
    }
}

class InMemoryStravaConnectionRepository : StravaConnectionRepository {
    private val mutex = Mutex()
    private val connections = mutableMapOf<String, StravaConnection>()

    override suspend fun save(connection: StravaConnection): StravaConnection = mutex.withLock {
        connections[connection.userId] = connection
        connection
    }

    override suspend fun findByUserId(userId: String): StravaConnection? = mutex.withLock {
        connections[userId]
    }

    override suspend fun deleteByUserId(userId: String) {
        mutex.withLock {
            connections.remove(userId)
        }
    }
}

class InMemoryStravaSyncRepository : StravaSyncRepository {
    private val mutex = Mutex()
    private val syncs = mutableMapOf<String, StravaSync>()

    override suspend fun upsert(sync: StravaSync): StravaSync = mutex.withLock {
        syncs[sync.sessionId] = sync
        sync
    }

    override suspend fun tryStartSync(sync: StravaSync): Boolean = mutex.withLock {
        val existing = syncs[sync.sessionId]
        val canStart = existing == null ||
            existing.athleteId != sync.athleteId ||
            (existing.status != StravaSyncStatus.syncing && existing.status != StravaSyncStatus.synced)
        if (canStart) {
            syncs[sync.sessionId] = sync
        }
        canStart
    }

    override suspend fun findBySessionId(sessionId: String): StravaSync? = mutex.withLock {
        syncs[sessionId]
    }

    override suspend fun deleteBySessionId(sessionId: String) {
        mutex.withLock {
            syncs.remove(sessionId)
        }
    }
}
