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

import com.delminiusapps.rideforge.models.WorkoutAnalysis
import com.delminiusapps.rideforge.models.FtpHistoryRecord
import com.delminiusapps.rideforge.models.ProgressionLevel
import com.delminiusapps.rideforge.models.WorkoutType
import com.delminiusapps.rideforge.models.FtpEstimateDetail
import com.delminiusapps.rideforge.models.FatigueSnapshot
import com.delminiusapps.rideforge.models.AdaptiveRecommendation
import com.delminiusapps.rideforge.models.CoachInsight

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
    private val mutex = Mutex()
    private val userJoinedPlans = mutableMapOf<String, MutableSet<String>>()
    private val userCompletedWorkouts = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()

    override suspend fun list(limit: Int, offset: Int): List<TrainingPlan> = plans.values.drop(offset).take(limit)
    override suspend fun count(): Int = plans.size
    override suspend fun findById(id: String): TrainingPlan? = plans[id]

    override suspend fun joinPlan(userId: String, planId: String): Unit = mutex.withLock {
        userJoinedPlans.getOrPut(userId) { mutableSetOf() }.add(planId)
    }

    override suspend fun leavePlan(userId: String, planId: String): Unit = mutex.withLock {
        userJoinedPlans[userId]?.remove(planId)
    }

    override suspend fun getJoinedPlans(userId: String): List<String> = mutex.withLock {
        userJoinedPlans[userId]?.toList() ?: emptyList()
    }

    override suspend fun completeWorkout(userId: String, planId: String, workoutId: String): Unit = mutex.withLock {
        userCompletedWorkouts.getOrPut(userId) { mutableMapOf() }
            .getOrPut(planId) { mutableSetOf() }.add(workoutId)
    }

    override suspend fun getCompletedWorkouts(userId: String, planId: String): List<String> = mutex.withLock {
        userCompletedWorkouts[userId]?.get(planId)?.toList() ?: emptyList()
    }

    override suspend fun resetProgress(userId: String, planId: String): Unit = mutex.withLock {
        userCompletedWorkouts[userId]?.remove(planId)
    }
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

class InMemoryAdaptiveTrainingRepository : AdaptiveTrainingRepository {
    private val mutex = Mutex()
    private val analyses = mutableMapOf<String, WorkoutAnalysis>()
    private val ftpRecords = mutableMapOf<String, FtpHistoryRecord>()
    private val progressionLevels = mutableMapOf<String, ProgressionLevel>()
    private val ftpEstimates = mutableMapOf<String, FtpEstimateDetail>()
    private val fatigueSnapshots = mutableMapOf<String, FatigueSnapshot>()
    private val recommendations = mutableMapOf<String, AdaptiveRecommendation>()
    private val insightsList = mutableListOf<CoachInsight>()

    override suspend fun saveAnalysis(analysis: WorkoutAnalysis): WorkoutAnalysis = mutex.withLock {
        analyses[analysis.sessionId] = analysis
        analysis
    }

    override suspend fun findAnalysisBySessionId(sessionId: String): WorkoutAnalysis? = mutex.withLock {
        analyses[sessionId]
    }

    override suspend fun saveFtpRecord(record: FtpHistoryRecord): FtpHistoryRecord = mutex.withLock {
        ftpRecords[record.id] = record
        record
    }

    override suspend fun findPendingFtpRecord(userId: String): FtpHistoryRecord? = mutex.withLock {
        ftpRecords.values
            .filter { it.userId == userId && it.status == "pending_approval" }
            .sortedByDescending { it.createdAt }
            .firstOrNull()
    }

    override suspend fun findFtpRecordById(id: String): FtpHistoryRecord? = mutex.withLock {
        ftpRecords[id]
    }

    override suspend fun updateFtpRecord(record: FtpHistoryRecord): FtpHistoryRecord = mutex.withLock {
        ftpRecords[record.id] = record
        record
    }

    override suspend fun getFtpHistory(userId: String): List<FtpHistoryRecord> = mutex.withLock {
        ftpRecords.values
            .filter { it.userId == userId }
            .sortedBy { it.createdAt }
    }

    override suspend fun saveFtpEstimate(estimate: FtpEstimateDetail): FtpEstimateDetail = mutex.withLock {
        ftpEstimates[estimate.id] = estimate
        estimate
    }

    override suspend fun findPendingFtpEstimate(userId: String): FtpEstimateDetail? = mutex.withLock {
        ftpEstimates.values
            .filter { it.userId == userId && it.status == "pending_approval" }
            .sortedByDescending { it.createdAt }
            .firstOrNull()
    }

    override suspend fun findFtpEstimateById(id: String): FtpEstimateDetail? = mutex.withLock {
        ftpEstimates[id]
    }

    override suspend fun updateFtpEstimate(estimate: FtpEstimateDetail): FtpEstimateDetail = mutex.withLock {
        ftpEstimates[estimate.id] = estimate
        estimate
    }

    override suspend fun getFtpEstimates(userId: String): List<FtpEstimateDetail> = mutex.withLock {
        ftpEstimates.values
            .filter { it.userId == userId }
            .sortedBy { it.createdAt }
    }

    override suspend fun saveFatigueSnapshot(snapshot: FatigueSnapshot): FatigueSnapshot = mutex.withLock {
        fatigueSnapshots["${snapshot.userId}_${snapshot.date}"] = snapshot
        snapshot
    }

    override suspend fun getLatestFatigueSnapshot(userId: String): FatigueSnapshot? = mutex.withLock {
        fatigueSnapshots.values
            .filter { it.userId == userId }
            .sortedByDescending { it.date }
            .firstOrNull()
    }

    override suspend fun getFatigueHistory(userId: String): List<FatigueSnapshot> = mutex.withLock {
        fatigueSnapshots.values
            .filter { it.userId == userId }
            .sortedBy { it.date }
    }

    override suspend fun saveRecommendation(recommendation: AdaptiveRecommendation): AdaptiveRecommendation = mutex.withLock {
        recommendations[recommendation.id] = recommendation
        recommendation
    }

    override suspend fun getLatestRecommendation(userId: String): AdaptiveRecommendation? = mutex.withLock {
        recommendations.values
            .filter { it.userId == userId }
            .sortedByDescending { it.createdAt }
            .firstOrNull()
    }

    override suspend fun saveCoachInsights(insights: List<CoachInsight>): Unit = mutex.withLock {
        insightsList.addAll(insights)
        Unit
    }

    override suspend fun getRecentCoachInsights(userId: String, limit: Int): List<CoachInsight> = mutex.withLock {
        insightsList
            .filter { it.userId == userId }
            .sortedByDescending { it.createdAt }
            .take(limit)
    }

    override suspend fun saveProgressionLevel(level: ProgressionLevel): ProgressionLevel = mutex.withLock {
        val key = "${level.userId}_${level.workoutType.name}"
        progressionLevels[key] = level
        level
    }

    override suspend fun getProgressionLevels(userId: String): List<ProgressionLevel> = mutex.withLock {
        progressionLevels.values.filter { it.userId == userId }
    }

    override suspend fun getProgressionLevel(userId: String, workoutType: WorkoutType): ProgressionLevel? = mutex.withLock {
        val key = "${userId}_${workoutType.name}"
        progressionLevels[key]
    }
}

