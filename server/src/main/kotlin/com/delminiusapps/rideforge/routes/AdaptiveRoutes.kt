package com.delminiusapps.rideforge.routes

import com.delminiusapps.rideforge.dto.*
import com.delminiusapps.rideforge.plugins.ServiceRegistry
import com.delminiusapps.rideforge.utils.notFound
import com.delminiusapps.rideforge.utils.badRequest
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.application.call

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
                badRequest("Access denied")
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
    }
}
