package com.delminiusapps.rideforge.repositories

import com.delminiusapps.rideforge.models.Device
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.RefreshTokenRecord
import com.delminiusapps.rideforge.models.StravaConnection
import com.delminiusapps.rideforge.models.StravaSync
import com.delminiusapps.rideforge.models.TrainingPlan
import com.delminiusapps.rideforge.models.User
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutInterval
import com.delminiusapps.rideforge.models.WorkoutSession

interface UserRepository {
    suspend fun create(user: User): User
    suspend fun findById(id: String): User?
    suspend fun findByEmail(email: String): User?
    suspend fun update(user: User): User
}

interface TrainingPlanRepository {
    suspend fun list(limit: Int, offset: Int): List<TrainingPlan>
    suspend fun count(): Int
    suspend fun findById(id: String): TrainingPlan?
}

interface WorkoutRepository {
    suspend fun list(limit: Int, offset: Int): List<Workout>
    suspend fun count(): Int
    suspend fun findById(id: String): Workout?
    suspend fun findByPlanId(planId: String): List<Workout>
    suspend fun intervalsForWorkout(workoutId: String): List<WorkoutInterval>
}

interface SessionRepository {
    suspend fun create(session: WorkoutSession): WorkoutSession
    suspend fun findById(id: String): WorkoutSession?
    suspend fun update(session: WorkoutSession): WorkoutSession
    suspend fun addMetric(sample: MetricSample): MetricSample
    suspend fun metricsForSession(sessionId: String): List<MetricSample>
    suspend fun historyForUser(userId: String, limit: Int, offset: Int): List<WorkoutSession>
    suspend fun historyCount(userId: String): Int
    suspend fun deleteHistory(userId: String, sessionId: String): Boolean
}

interface DeviceRepository {
    suspend fun listAvailable(userId: String): List<Device>
    suspend fun current(userId: String): Device?
    suspend fun connect(device: Device): Device
    suspend fun disconnect(userId: String): Device?
}

interface RefreshTokenRepository {
    suspend fun save(record: RefreshTokenRecord): RefreshTokenRecord
    suspend fun findByHash(tokenHash: String): RefreshTokenRecord?
    suspend fun revoke(tokenHash: String)
    suspend fun revokeIfActive(tokenHash: String): Boolean
    suspend fun revokeAllForUser(userId: String)
}

interface StravaConnectionRepository {
    suspend fun save(connection: StravaConnection): StravaConnection
    suspend fun findByUserId(userId: String): StravaConnection?
    suspend fun deleteByUserId(userId: String)
}

interface StravaSyncRepository {
    suspend fun upsert(sync: StravaSync): StravaSync
    suspend fun tryStartSync(sync: StravaSync): Boolean
    suspend fun findBySessionId(sessionId: String): StravaSync?
    suspend fun deleteBySessionId(sessionId: String)
}
