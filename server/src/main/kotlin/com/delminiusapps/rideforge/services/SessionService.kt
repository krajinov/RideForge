package com.delminiusapps.rideforge.services

import com.delminiusapps.rideforge.dto.CompleteSessionRequest
import com.delminiusapps.rideforge.dto.MetricSampleRequest
import com.delminiusapps.rideforge.dto.MetricsAcceptedResponse
import com.delminiusapps.rideforge.dto.PageResponse
import com.delminiusapps.rideforge.dto.SessionResponse
import com.delminiusapps.rideforge.dto.StartSessionRequest
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.SessionStatus
import com.delminiusapps.rideforge.models.WorkoutSession
import com.delminiusapps.rideforge.repositories.DeviceRepository
import com.delminiusapps.rideforge.repositories.SessionRepository
import com.delminiusapps.rideforge.repositories.UserRepository
import com.delminiusapps.rideforge.repositories.WorkoutRepository
import com.delminiusapps.rideforge.utils.badRequest
import com.delminiusapps.rideforge.utils.forbidden
import com.delminiusapps.rideforge.utils.newId
import com.delminiusapps.rideforge.utils.notFound
import com.delminiusapps.rideforge.utils.nowIso

class SessionService(
    private val sessions: SessionRepository,
    private val workouts: WorkoutRepository,
    private val devices: DeviceRepository,
    private val users: UserRepository,
) {
    suspend fun start(userId: String, request: StartSessionRequest): SessionResponse {
        val workout = workouts.findById(request.workoutId) ?: notFound("Workout")
        val riderWeightKg = users.findById(userId)?.weightKg ?: RideMetricCalculator.DefaultRiderWeightKg
        val session = sessions.create(
            WorkoutSession(
                id = newId("session"),
                userId = userId,
                workoutId = workout.id,
                status = SessionStatus.active,
                startedAt = nowIso(),
                riderWeightKg = riderWeightKg,
            ),
        )
        return SessionResponse(session, workout)
    }

    suspend fun pause(userId: String, sessionId: String): WorkoutSession =
        updateOwned(userId, sessionId) {
            if (it.status != SessionStatus.active) badRequest("Only active sessions can be paused")
            it.copy(status = SessionStatus.paused)
        }

    suspend fun resume(userId: String, sessionId: String): WorkoutSession =
        updateOwned(userId, sessionId) {
            if (it.status != SessionStatus.paused) badRequest("Only paused sessions can be resumed")
            it.copy(status = SessionStatus.active)
        }

    suspend fun complete(userId: String, sessionId: String, request: CompleteSessionRequest): WorkoutSession {
        val session = requireOwned(userId, sessionId)
        val workout = workouts.findById(session.workoutId) ?: notFound("Workout")
        val metrics = sessions.metricsForSession(sessionId)
        val elapsed = request.elapsedSeconds?.takeIf { it > 0 }
            ?: session.elapsedSeconds.takeIf { it > 0 }
            ?: workout.durationMinutes * 60
        val averagePower = metrics.map { it.currentPower }.takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 214
        val normalizedPower = (averagePower * 1.10).toInt().coerceAtLeast(averagePower)
        val calories = ((averagePower * elapsed) / 1000.0 * 3.6).toInt().coerceAtLeast(120)
        val tss = ((elapsed / 3600.0) * (normalizedPower / 240.0) * (normalizedPower / 240.0) * 100).toInt().coerceAtLeast(1)
        val completion = ((elapsed.toDouble() / (workout.durationMinutes * 60)) * 100).toInt().coerceIn(1, 100)
        val distanceKm = RideMetricCalculator.distanceKm(metrics)
        val hasRealTrainerData = session.hasRealTrainerData ||
            hasServerVerifiedTrainerData(userId, metrics) ||
            hasClientReportedTrainerData(request, metrics)
        return sessions.update(
            session.copy(
                status = SessionStatus.completed,
                completedAt = nowIso(),
                elapsedSeconds = elapsed,
                averagePower = averagePower,
                normalizedPower = normalizedPower,
                calories = calories,
                tss = tss,
                completionPercent = completion,
                hasRealTrainerData = hasRealTrainerData,
                averageSpeedKmh = RideMetricCalculator.averageSpeedKmh(distanceKm, elapsed),
                totalDistanceKm = distanceKm,
            ),
        )
    }

    suspend fun addMetric(userId: String, sessionId: String, request: MetricSampleRequest): MetricsAcceptedResponse {
        val session = requireOwned(userId, sessionId)
        if (session.status == SessionStatus.completed) badRequest("Completed sessions cannot accept metrics")
        if (request.currentPower !in 0..2500) badRequest("Current power must be between 0 and 2500 watts")
        if (request.targetPower !in 0..2500) badRequest("Target power must be between 0 and 2500 watts")
        if (request.cadence !in 0..220) badRequest("Cadence must be between 0 and 220 rpm")
        if (request.heartRate !in 0..240) badRequest("Heart rate must be between 0 and 240 bpm")
        if (request.speedKmh !in 0.0..140.0) badRequest("Speed must be between 0 and 140 km/h")
        if (request.elapsedSeconds != null && request.elapsedSeconds < 0) badRequest("Elapsed seconds cannot be negative")

        val sample = sessions.addMetric(
            MetricSample(
                sessionId = sessionId,
                timestamp = request.timestamp ?: nowIso(),
                elapsedSeconds = request.elapsedSeconds,
                currentPower = request.currentPower,
                targetPower = request.targetPower,
                cadence = request.cadence,
                heartRate = request.heartRate,
                speedKmh = RideMetricCalculator.speedKmh(request.currentPower, session.riderWeightKg),
            ),
        )
        return MetricsAcceptedResponse(sessions.metricsForSession(sessionId).size, sample)
    }

    suspend fun getMetrics(userId: String, sessionId: String): List<MetricSample> {
        requireOwned(userId, sessionId)
        return sessions.metricsForSession(sessionId)
    }

    suspend fun history(userId: String, limit: Int, offset: Int): PageResponse<WorkoutSession> =
        PageResponse(sessions.historyForUser(userId, limit, offset), sessions.historyCount(userId), limit, offset)

    suspend fun historyItem(userId: String, sessionId: String): WorkoutSession {
        val session = requireOwned(userId, sessionId)
        if (session.status != SessionStatus.completed) notFound("History item")
        return session
    }

    suspend fun deleteHistory(userId: String, sessionId: String) {
        if (!sessions.deleteHistory(userId, sessionId)) notFound("History item")
    }

    private suspend fun updateOwned(
        userId: String,
        sessionId: String,
        transform: (WorkoutSession) -> WorkoutSession,
    ): WorkoutSession = sessions.update(transform(requireOwned(userId, sessionId)))

    private suspend fun requireOwned(userId: String, sessionId: String): WorkoutSession {
        val session = sessions.findById(sessionId) ?: notFound("Session")
        if (session.userId != userId) forbidden()
        return session
    }

    private suspend fun hasServerVerifiedTrainerData(userId: String, metrics: List<MetricSample>): Boolean {
        if (metrics.isEmpty()) return false
        val device = devices.current(userId) ?: return false
        return device.type.contains("trainer", ignoreCase = true) || device.supportsErg
    }

    private fun hasClientReportedTrainerData(request: CompleteSessionRequest, metrics: List<MetricSample>): Boolean =
        request.hasRealTrainerData && metrics.isNotEmpty()
}
