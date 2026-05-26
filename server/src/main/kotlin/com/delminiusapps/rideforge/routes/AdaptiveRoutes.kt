package com.delminiusapps.rideforge.routes

import com.delminiusapps.rideforge.dto.*
import com.delminiusapps.rideforge.models.*
import com.delminiusapps.rideforge.plugins.ServiceRegistry
import com.delminiusapps.rideforge.services.adaptive_training.WorkoutCompletionAnalyzer
import com.delminiusapps.rideforge.services.adaptive_training.WorkoutClassifier
import com.delminiusapps.rideforge.utils.notFound
import com.delminiusapps.rideforge.utils.badRequest
import com.delminiusapps.rideforge.utils.forbidden
import com.delminiusapps.rideforge.utils.newId
import com.delminiusapps.rideforge.utils.nowIso
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.application.call
import java.time.LocalDate
import kotlin.math.roundToInt

fun Route.adaptiveRoutes(registry: ServiceRegistry) {
    route("/adaptive") {
        get("/dashboard") {
            val userId = call.userId()
            val user = registry.userRepository.findById(userId) ?: notFound("User")
            val sessions = registry.sessionRepository.historyForUser(userId, 200, 0)
            
            val fatigue = registry.fatigueCalculationService.calculateCurrentFatigue(sessions)
            val levels = registry.progressionTracker.getAllProgressionLevels(userId)
            val pendingFtp = registry.adaptiveTrainingRepository.findPendingFtpRecord(userId)
            val rec = registry.recommendationEngine.getHomeRecommendation(userId, fatigue, user.enrolledPlanId)
            
            val recentSessions = registry.sessionRepository.historyForUser(userId, 5, 0)
            val recentAnalyses = recentSessions.mapNotNull { 
                registry.adaptiveTrainingRepository.findAnalysisBySessionId(it.id)
            }
            val insights = registry.recommendationEngine.getCoachInsights(userId, fatigue, recentAnalyses)

            val response = AdaptiveDashboardResponse(
                fatigue = FatigueStateResponse(fatigue.ctl, fatigue.atl, fatigue.tsb),
                progressionLevels = levels.mapKeys { it.key.name },
                pendingFtpEstimate = pendingFtp?.let { 
                    FtpEstimateResponse(it.id, it.estimatedFtp, it.previousFtp, it.message, it.createdAt)
                },
                recommendation = AdaptiveRecommendationResponse(rec.type, rec.workoutId, rec.title, rec.description, rec.reason),
                insights = insights.map { it.message }
            )
            call.respond(response)
        }

        get("/trends") {
            val userId = call.userId()
            val sessions = registry.sessionRepository.historyForUser(userId, 200, 0)
            
            val fatigueHistory = registry.fatigueCalculationService.calculateFatigueHistory(sessions)
            val ftpHistory = registry.adaptiveTrainingRepository.getFtpHistory(userId)

            val response = TrendsResponse(
                fatigueHistory = fatigueHistory.map { 
                    DailyFatigueResponse(it.date, it.ctl, it.atl, it.tsb, it.tss)
                },
                ftpHistory = ftpHistory.map { 
                    FtpHistoryRecordResponse(it.id, it.estimatedFtp, it.previousFtp, it.status, it.message, it.createdAt)
                }
            )
            call.respond(response)
        }

        post("/ftp-estimate/{id}/approve") {
            val userId = call.userId()
            val id = call.requiredPath("id")
            val updatedUser = registry.ftpEstimationService.approveFtp(userId, id)
                ?: badRequest("Could not approve FTP estimate. Record might be missing or already processed.")
            call.respond(ApproveFtpResponse(updatedUser.ftp))
        }

        post("/ftp-estimate/{id}/dismiss") {
            val userId = call.userId()
            val id = call.requiredPath("id")
            val success = registry.ftpEstimationService.dismissFtp(userId, id)
            if (!success) {
                badRequest("Could not dismiss FTP estimate.")
            }
            call.respond(mapOf("status" to "dismissed"))
        }

        get("/sessions/{id}/analysis") {
            val sessionId = call.requiredPath("id")
            val userId = call.userId()
            val session = registry.sessionRepository.findById(sessionId) ?: notFound("Session")
            if (session.userId != userId) {
                forbidden()
            }

            val analysis = registry.adaptiveTrainingRepository.findAnalysisBySessionId(sessionId)
                ?: notFound("Analysis")

            call.respond(
                WorkoutAnalysisResponse(
                    sessionId = analysis.sessionId,
                    completionPercent = analysis.completionPercent,
                    intervalSuccessRate = analysis.intervalSuccessRate,
                    ergComplianceScore = analysis.ergComplianceScore,
                    cadenceConsistencyScore = analysis.cadenceConsistencyScore,
                    powerFade = analysis.powerFade,
                    hrDrift = analysis.hrDrift,
                    estimatedRpe = analysis.estimatedRpe,
                    classification = analysis.classification,
                    coachNotesSummary = analysis.coachNotesSummary,
                    coachNotesRecommendation = analysis.coachNotesRecommendation,
                    coachNotesRecovery = analysis.coachNotesRecovery,
                    coachNotesNextWorkout = analysis.coachNotesNextWorkout
                )
            )
        }

        get("/summary") {
            val userId = call.userId()
            val sessions = registry.sessionRepository.historyForUser(userId, 200, 0)
            val analyses = sessions.mapNotNull { registry.adaptiveTrainingRepository.findAnalysisBySessionId(it.id) }
            
            val totalWorkouts = sessions.size
            val completionRate = if (analyses.isNotEmpty()) analyses.map { it.completionPercent }.average().roundToInt() else 0
            val complianceRate = if (analyses.isNotEmpty()) analyses.mapNotNull { it.ergComplianceScore }.average().let { if (it.isNaN()) 0 else it.roundToInt() } else 0
            val cadenceConsistency = if (analyses.isNotEmpty()) analyses.mapNotNull { it.cadenceConsistencyScore }.average().let { if (it.isNaN()) 0 else it.roundToInt() } else 0
            val averageRpe = if (analyses.isNotEmpty()) analyses.map { it.estimatedRpe }.average().let { if (it.isNaN()) 0.0 else (it * 10).roundToInt() / 10.0 } else 0.0
            val totalTss = sessions.sumOf { it.tss ?: 0 }

            call.respond(AdaptiveSummaryResponse(totalWorkouts, completionRate, complianceRate, cadenceConsistency, averageRpe, totalTss))
        }

        get("/ftp-estimate") {
            val userId = call.userId()
            val estimate = registry.adaptiveTrainingRepository.findPendingFtpEstimate(userId)
                ?: registry.adaptiveTrainingRepository.getFtpEstimates(userId).lastOrNull()
            
            if (estimate != null) {
                call.respond(FtpEstimateDetailResponse(
                    id = estimate.id,
                    currentFtp = estimate.currentFtp,
                    estimatedFtp = estimate.estimatedFtp,
                    confidenceScore = estimate.confidenceScore,
                    recommendation = estimate.recommendation,
                    status = estimate.status,
                    message = estimate.message,
                    createdAt = estimate.createdAt
                ))
            } else {
                val user = registry.userRepository.findById(userId) ?: notFound("User")
                call.respond(FtpEstimateDetailResponse(
                    id = "none",
                    currentFtp = user.ftp,
                    estimatedFtp = user.ftp,
                    confidenceScore = 100,
                    recommendation = "KEEP",
                    status = "approved",
                    message = "FTP is optimal.",
                    createdAt = user.createdAt
                ))
            }
        }

        get("/fatigue") {
            val userId = call.userId()
            val snapshot = registry.adaptiveTrainingRepository.getLatestFatigueSnapshot(userId)
            
            if (snapshot != null) {
                call.respond(FatigueDetailResponse(
                    fitness = snapshot.ctl,
                    fatigue = snapshot.atl,
                    form = snapshot.tsb,
                    freshnessStatus = snapshot.freshnessStatus
                ))
            } else {
                val sessions = registry.sessionRepository.historyForUser(userId, 200, 0)
                val fatigue = registry.fatigueCalculationService.calculateCurrentFatigue(sessions)
                val freshnessStatus = when {
                    fatigue.tsb > 5.0 -> "FRESH"
                    fatigue.tsb < -30.0 -> "OVERREACHED"
                    fatigue.tsb < -10.0 -> "FATIGUED"
                    else -> "BALANCED"
                }
                call.respond(FatigueDetailResponse(
                    fitness = fatigue.ctl,
                    fatigue = fatigue.atl,
                    form = fatigue.tsb,
                    freshnessStatus = freshnessStatus
                ))
            }
        }

        get("/recommendation") {
            val userId = call.userId()
            val rec = registry.adaptiveTrainingRepository.getLatestRecommendation(userId)
            
            if (rec != null) {
                call.respond(AdaptiveRecommendationResponse(
                    type = rec.type,
                    workoutId = rec.workoutId,
                    title = rec.title,
                    description = rec.description,
                    reason = rec.reason
                ))
            } else {
                val user = registry.userRepository.findById(userId) ?: notFound("User")
                val sessions = registry.sessionRepository.historyForUser(userId, 200, 0)
                val fatigue = registry.fatigueCalculationService.calculateCurrentFatigue(sessions)
                val activeRec = registry.recommendationEngine.getHomeRecommendation(userId, fatigue, user.enrolledPlanId)
                
                call.respond(AdaptiveRecommendationResponse(
                    type = activeRec.type,
                    workoutId = activeRec.workoutId,
                    title = activeRec.title,
                    description = activeRec.description,
                    reason = activeRec.reason
                ))
            }
        }

        get("/insights") {
            val userId = call.userId()
            val insights = registry.adaptiveTrainingRepository.getRecentCoachInsights(userId, 10)
            
            if (insights.isNotEmpty()) {
                call.respond(insights.map { 
                    CoachInsightResponse(it.title, it.message, it.severity, it.sourceMetric)
                })
            } else {
                val sessions = registry.sessionRepository.historyForUser(userId, 200, 0)
                val fatigue = registry.fatigueCalculationService.calculateCurrentFatigue(sessions)
                
                val recentSessions = registry.sessionRepository.historyForUser(userId, 5, 0)
                val recentAnalyses = recentSessions.mapNotNull { 
                    registry.adaptiveTrainingRepository.findAnalysisBySessionId(it.id)
                }
                val activeInsights = registry.recommendationEngine.getCoachInsights(userId, fatigue, recentAnalyses)
                
                call.respond(activeInsights.map { 
                    CoachInsightResponse(it.title, it.message, it.severity, it.sourceMetric)
                })
            }
        }

        get("/progression") {
            val userId = call.userId()
            val levels = registry.progressionTracker.getAllProgressionLevels(userId)
            call.respond(levels.mapKeys { it.key.name })
        }

        post("/recalculate") {
            val userId = call.userId()
            val user = registry.userRepository.findById(userId) ?: notFound("User")
            val sessions = registry.sessionRepository.historyForUser(userId, 200, 0)
                .sortedBy { it.completedAt ?: it.startedAt }
            
            // 1. Re-analyze and classify each session
            for (session in sessions) {
                val workout = registry.workoutRepository.findById(session.workoutId) ?: continue
                val metrics = registry.sessionRepository.metricsForSession(session.id)
                
                val scaling = registry.progressionTracker.getIntensityScalingFactor(userId, workout)
                val rawIntervals = registry.workoutRepository.intervalsForWorkout(workout.id)
                val intervals = rawIntervals.map { interval ->
                    val targetPercent = interval.targetFtpPercent ?: 100
                    val unscaledPower = interval.targetPowerWatts ?: ((user.ftp * targetPercent) / 100)
                    interval.copy(
                        targetPowerWatts = (unscaledPower * scaling).roundToInt(),
                        targetFtpPercent = (targetPercent * scaling).roundToInt()
                    )
                }
                
                val analysisResult = WorkoutCompletionAnalyzer.analyze(session, workout, intervals, metrics, user.ftp)
                val classification = WorkoutClassifier.classify(session, workout, intervals, analysisResult, user.ftp)
                
                val summaryNote = when {
                    analysisResult.ergComplianceScore != null && analysisResult.ergComplianceScore < 80 -> "Trainer control was the main execution limiter."
                    analysisResult.cadenceConsistencyScore != null && analysisResult.cadenceConsistencyScore >= 85 -> "Cadence control was a strength in this ride."
                    else -> "The ride is complete; pacing and cadence stability are the next focus."
                }
                val recommendationNote = when {
                    analysisResult.completionPercent >= 98 && classification == "OVERPERFORMED" -> "Progress to the next scheduled intensity workout."
                    analysisResult.completionPercent >= 90 -> "Repeat the same target structure if late intervals felt unstable."
                    else -> "Reduce the next hard block by 3-5% and prioritize full completion."
                }
                val recoveryNote = when {
                    (session.tss ?: 0) >= 90 -> "High stress: plan an easy spin or rest day before the next hard ride."
                    (session.tss ?: 0) >= 50 -> "Moderate stress: keep the next 24 hours aerobic."
                    else -> "Low stress: normal training can continue if legs feel fresh."
                }
                val nextWorkoutNote = if (analysisResult.completionPercent >= 95) "Next planned workout" else "Repeat or easier aerobic session"

                val analysis = WorkoutAnalysis(
                    sessionId = session.id,
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
                registry.adaptiveTrainingRepository.saveAnalysis(analysis)
                registry.progressionTracker.updateProgression(userId, workout, classification)
            }

            // 2. Re-calculate fatigue history
            val ctlHistory = registry.fatigueCalculationService.calculateFatigueHistory(sessions)
            for (day in ctlHistory) {
                val freshnessStatus = when {
                    day.tsb > 5.0 -> "FRESH"
                    day.tsb < -30.0 -> "OVERREACHED"
                    day.tsb < -10.0 -> "FATIGUED"
                    else -> "BALANCED"
                }
                registry.adaptiveTrainingRepository.saveFatigueSnapshot(
                    FatigueSnapshot(
                        id = newId("fs"),
                        userId = userId,
                        date = day.date,
                        ctl = day.ctl,
                        atl = day.atl,
                        tsb = day.tsb,
                        freshnessStatus = freshnessStatus,
                        createdAt = nowIso()
                    )
                )
            }

            // 3. Re-estimate FTP & Recommendations
            val lastSession = sessions.lastOrNull()
            if (lastSession != null) {
                val workout = registry.workoutRepository.findById(lastSession.workoutId)
                if (workout != null) {
                    val metrics = registry.sessionRepository.metricsForSession(lastSession.id)
                    registry.ftpEstimationService.checkAndEstimateFtp(user, lastSession, workout, metrics)
                }
            }

            val fatigue = registry.fatigueCalculationService.calculateCurrentFatigue(sessions)
            registry.recommendationEngine.getHomeRecommendation(userId, fatigue, user.enrolledPlanId)
            
            val recentSessionsForInsights = registry.sessionRepository.historyForUser(userId, 5, 0)
            val recentAnalysesForInsights = recentSessionsForInsights.mapNotNull { 
                registry.adaptiveTrainingRepository.findAnalysisBySessionId(it.id)
            }
            registry.recommendationEngine.getCoachInsights(userId, fatigue, recentAnalysesForInsights)

            call.respond(mapOf("status" to "recalculated"))
        }
    }
}
