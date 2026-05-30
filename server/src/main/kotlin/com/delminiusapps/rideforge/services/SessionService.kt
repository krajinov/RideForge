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
import com.delminiusapps.rideforge.models.WorkoutAnalysis
import com.delminiusapps.rideforge.models.FatigueSnapshot
import com.delminiusapps.rideforge.repositories.DeviceRepository
import com.delminiusapps.rideforge.repositories.SessionRepository
import com.delminiusapps.rideforge.repositories.UserRepository
import com.delminiusapps.rideforge.repositories.WorkoutRepository
import com.delminiusapps.rideforge.repositories.AdaptiveTrainingRepository
import com.delminiusapps.rideforge.repositories.TrainingPlanRepository
import com.delminiusapps.rideforge.services.adaptive_training.ProgressionTracker
import com.delminiusapps.rideforge.services.adaptive_training.FtpEstimationService
import com.delminiusapps.rideforge.services.adaptive_training.WorkoutCompletionAnalyzer
import com.delminiusapps.rideforge.services.adaptive_training.WorkoutClassifier
import com.delminiusapps.rideforge.services.adaptive_training.FatigueCalculationService
import com.delminiusapps.rideforge.utils.badRequest
import com.delminiusapps.rideforge.utils.forbidden
import com.delminiusapps.rideforge.utils.newId
import com.delminiusapps.rideforge.utils.notFound
import com.delminiusapps.rideforge.utils.nowIso
import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.roundToInt

class SessionService(
    private val sessions: SessionRepository,
    private val workouts: WorkoutRepository,
    private val devices: DeviceRepository,
    private val users: UserRepository,
    private val adaptiveRepository: AdaptiveTrainingRepository,
    private val progressionTracker: ProgressionTracker,
    private val ftpEstimationService: FtpEstimationService,
    private val plans: TrainingPlanRepository,
    private val fatigueCalculationService: FatigueCalculationService = FatigueCalculationService()
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
        val user = users.findById(userId) ?: notFound("User")
        val metrics = sessions.metricsForSession(sessionId)
        
        val elapsed = request.elapsedSeconds?.takeIf { it > 0 }
            ?: session.elapsedSeconds.takeIf { it > 0 }
            ?: workout.durationMinutes * 60

        // 1. Raw Average Power
        val averagePower = metrics.map { it.currentPower }.takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 214

        // 2. Properly calculated NP
        val powerSamples = metrics
            .filter { (it.elapsedSeconds ?: 0) >= 0 }
            .groupBy { it.elapsedSeconds ?: 0 }
            .map { (_, samplesAtSecond) -> samplesAtSecond.last() }
            .sortedBy { it.elapsedSeconds ?: 0 }
            .filter { it.currentPower > 0 }

        val normalizedPower = if (powerSamples.size >= 30) {
            val rolling = powerSamples.mapIndexedNotNull { index, sample ->
                val start = sample.elapsedSeconds ?: 0
                val window = powerSamples.drop(index).takeWhile { (it.elapsedSeconds ?: 0) < start + 30 }
                val coveredSeconds = (window.lastOrNull()?.elapsedSeconds ?: start) - start
                if (coveredSeconds < 24) {
                    null
                } else {
                    window.map { it.currentPower }.average()
                }
            }
            if (rolling.isEmpty()) {
                (averagePower * 1.10).toInt().coerceAtLeast(averagePower)
            } else {
                val fourthPowerAverage = rolling.map { it.pow(4.0) }.average()
                fourthPowerAverage.pow(0.25).roundToInt().coerceAtLeast(averagePower)
            }
        } else {
            (averagePower * 1.10).toInt().coerceAtLeast(averagePower)
        }

        // 3. Properly calculated calories
        val calories = ((averagePower * elapsed) / 1000.0 * 3.6).toInt().coerceAtLeast(120)

        // 4. Properly calculated TSS using user's actual FTP
        val userFtp = user.ftp.coerceAtLeast(50)
        val tss = ((elapsed / 3600.0) * (normalizedPower / userFtp.toDouble()) * (normalizedPower / userFtp.toDouble()) * 100).toInt().coerceAtLeast(1)

        val completion = ((elapsed.toDouble() / (workout.durationMinutes * 60)) * 100).toInt().coerceIn(1, 100)
        val distanceKm = RideMetricCalculator.distanceKm(metrics)
        val hasRealTrainerData = session.hasRealTrainerData ||
            hasServerVerifiedTrainerData(userId, metrics) ||
            hasClientReportedTrainerData(request, metrics)

        val completedSession = sessions.update(
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

        // Record completed workout progress for the plan if joined
        val planId = workout.planId
        if (plans.getJoinedPlans(userId).contains(planId)) {
            plans.completeWorkout(userId, planId, workout.id)
        }

        // Run Adaptive Training analysis post-ride
        try {
            val scaling = progressionTracker.getIntensityScalingFactor(userId, workout)
            val rawIntervals = workouts.intervalsForWorkout(workout.id)
            val intervals = rawIntervals.map { interval ->
                val targetPercent = interval.targetFtpPercent ?: 100
                val unscaledPower = interval.targetPowerWatts ?: ((user.ftp * targetPercent) / 100)
                interval.copy(
                    targetPowerWatts = (unscaledPower * scaling).roundToInt(),
                    targetFtpPercent = (targetPercent * scaling).roundToInt()
                )
            }
            val analysisResult = WorkoutCompletionAnalyzer.analyze(completedSession, workout, intervals, metrics, user.ftp)
            val classification = WorkoutClassifier.classify(completedSession, workout, intervals, analysisResult, user.ftp)
            
            // Generate coach notes
            val summaryNote = when {
                analysisResult.ergComplianceScore != null && analysisResult.ergComplianceScore < 80 -> "Trainer control was the main execution limiter."
                analysisResult.cadenceConsistencyScore != null && analysisResult.cadenceConsistencyScore >= 85 -> "Cadence control was a strength in this ride."
                else -> "The ride is complete; pacing and cadence stability are the next focus."
            }
            val recommendationNote = when {
                completion >= 98 && classification == "OVERPERFORMED" -> "Progress to the next scheduled intensity workout."
                completion >= 90 -> "Repeat the same target structure if late intervals felt unstable."
                else -> "Reduce the next hard block by 3-5% and prioritize full completion."
            }
            val recoveryNote = when {
                tss >= 90 -> "High stress: plan an easy spin or rest day before the next hard ride."
                tss >= 50 -> "Moderate stress: keep the next 24 hours aerobic."
                else -> "Low stress: normal training can continue if legs feel fresh."
            }
            val nextWorkoutNote = if (completion >= 95) "Next planned workout" else "Repeat or easier aerobic session"

            val analysis = WorkoutAnalysis(
                sessionId = completedSession.id,
                completionPercent = analysisResult.completionPercent,
                intervalSuccessRate = analysisResult.intervalSuccessRate,
                ergComplianceScore = analysisResult.ergComplianceScore,
                cadenceConsistencyScore = analysisResult.cadenceConsistencyScore,
                powerFade = analysisResult.powerFade,
                hrDrift = analysisResult.hrDrift,
                estimatedRpe = analysisResult.estimatedRpe,
                classification = classification,
                coachNotesSummary = summaryNote,
                coachNotesRecommendation = recommendationNote,
                coachNotesRecovery = recoveryNote,
                coachNotesNextWorkout = nextWorkoutNote,
                avgDeviationPower = analysisResult.avgDeviationPower,
                best5sPower = analysisResult.best5sPower,
                best30sPower = analysisResult.best30sPower,
                best1mPower = analysisResult.best1mPower,
                best5mPower = analysisResult.best5mPower,
                best20mPower = analysisResult.best20mPower
            )

            // Save analysis to DB
            adaptiveRepository.saveAnalysis(analysis)

            // Update progression levels
            progressionTracker.updateProgression(userId, workout, classification)

            // Check FTP adjustments
            ftpEstimationService.checkAndEstimateFtp(user, completedSession, workout, metrics)

            // Save fatigue snapshot
            val allSessions = sessions.historyForUser(userId, 200, 0)
            val fatigueState = fatigueCalculationService.calculateCurrentFatigue(allSessions)
            val freshnessStatus = when {
                fatigueState.tsb > 5.0 -> "FRESH"
                fatigueState.tsb < -30.0 -> "OVERREACHED"
                fatigueState.tsb < -10.0 -> "FATIGUED"
                else -> "BALANCED"
            }
            adaptiveRepository.saveFatigueSnapshot(
                FatigueSnapshot(
                    id = newId("fs"),
                    userId = userId,
                    date = LocalDate.now().toString(),
                    ctl = fatigueState.ctl,
                    atl = fatigueState.atl,
                    tsb = fatigueState.tsb,
                    freshnessStatus = freshnessStatus,
                    createdAt = nowIso()
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return completedSession
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
