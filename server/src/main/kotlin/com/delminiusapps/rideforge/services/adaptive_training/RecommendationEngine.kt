package com.delminiusapps.rideforge.services.adaptive_training

import com.delminiusapps.rideforge.models.*
import com.delminiusapps.rideforge.repositories.SessionRepository
import com.delminiusapps.rideforge.repositories.WorkoutRepository
import com.delminiusapps.rideforge.repositories.AdaptiveTrainingRepository
import com.delminiusapps.rideforge.utils.newId
import com.delminiusapps.rideforge.utils.nowIso
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

class RecommendationEngine(
    private val workoutRepository: WorkoutRepository,
    private val sessionRepository: SessionRepository,
    private val progressionTracker: ProgressionTracker,
    private val adaptiveRepository: AdaptiveTrainingRepository
) {

    suspend fun getHomeRecommendation(
        userId: String,
        fatigueState: FatigueCalculationService.FatigueState,
        enrolledPlanId: String?
    ): AdaptiveRecommendation {
        val tsb = fatigueState.tsb

        // 1. High Fatigue Recovery Swapping (TSB < -20)
        if (tsb < -20.0) {
            val recoveryWorkout = workoutRepository.list(100, 0)
                .firstOrNull { it.workoutType == WorkoutType.RECOVERY }
            
            val rec = AdaptiveRecommendation(
                id = newId("rec"),
                userId = userId,
                type = "RECOVERY",
                workoutId = recoveryWorkout?.id,
                title = recoveryWorkout?.name ?: "Active Recovery Spin",
                description = recoveryWorkout?.description ?: "A very gentle, low-intensity ride to promote blood flow and muscle recovery.",
                reason = "Your fatigue is high (TSB: ${tsb}). Swapping today's session for active recovery will help prevent overtraining.",
                createdAt = nowIso()
            )
            return rec
        }

        // 2. Failed 2 hard workouts -> Recommend intensity reduction or recovery
        val history = sessionRepository.historyForUser(userId, limit = 5, offset = 0)
        val intensityTypes = setOf(
            WorkoutType.THRESHOLD,
            WorkoutType.VO2_MAX,
            WorkoutType.OVER_UNDER,
            WorkoutType.SWEET_SPOT
        )
        val recentIntensityAnalyses = history.mapNotNull { ride ->
            val rideAnalysis = adaptiveRepository.findAnalysisBySessionId(ride.id) ?: return@mapNotNull null
            val rideWorkout = workoutRepository.findById(ride.workoutId) ?: return@mapNotNull null
            if (intensityTypes.contains(rideWorkout.workoutType)) rideAnalysis else null
        }
        if (recentIntensityAnalyses.size >= 2) {
            val lastTwo = recentIntensityAnalyses.take(2)
            val failedBoth = lastTwo.all { 
                it.classification.equals("FAILED", ignoreCase = true) || 
                it.classification.equals("STRUGGLED", ignoreCase = true)
            }
            if (failedBoth) {
                val enduranceWorkout = workoutRepository.list(100, 0)
                    .firstOrNull { it.workoutType == WorkoutType.ENDURANCE }
                val rec = AdaptiveRecommendation(
                    id = newId("rec"),
                    userId = userId,
                    type = "RECOVERY",
                    workoutId = enduranceWorkout?.id,
                    title = "Recovery Endurance Spin",
                    description = "A steady base endurance workout to reset and build aerobic fitness.",
                    reason = "You struggled with your last 2 high-intensity sessions. We recommend reducing training intensity and doing a base ride today.",
                    createdAt = nowIso()
                )
                return rec
            }
        }

        // 3. Stale FTP check -> Recommend FTP test
        val ftpHistory = adaptiveRepository.getFtpHistory(userId)
        val lastApproved = ftpHistory.lastOrNull { it.status == "approved" }
        val isStale = if (lastApproved != null) {
            val lastDate = java.time.Instant.parse(lastApproved.createdAt)
            val thirtyDaysAgo = java.time.Instant.now().minus(30, ChronoUnit.DAYS)
            lastDate.isBefore(thirtyDaysAgo)
        } else {
            true
        }
        if (isStale) {
            val ftpTestWorkout = workoutRepository.list(100, 0)
                .firstOrNull { it.workoutType == WorkoutType.THRESHOLD || it.name.contains("test", ignoreCase = true) }
            val rec = AdaptiveRecommendation(
                id = newId("rec"),
                userId = userId,
                type = "TEST",
                workoutId = ftpTestWorkout?.id,
                title = ftpTestWorkout?.name ?: "FTP Ramp Test",
                description = ftpTestWorkout?.description ?: "Standard ramp test to determine your Functional Threshold Power.",
                reason = "It has been over 30 days since your last FTP change. We recommend taking an FTP test.",
                createdAt = nowIso()
            )
            return rec
        }

        // 4. Missed Workouts Pushing/Adaptive Recovery
        val lastSession = history.firstOrNull()
        if (lastSession != null && lastSession.completedAt != null && enrolledPlanId != null) {
            val lastDateStr = lastSession.completedAt!!.substringBefore("T")
            val lastDate = try {
                LocalDate.parse(lastDateStr)
            } catch (e: Exception) {
                null
            }
            if (lastDate != null) {
                val today = LocalDate.now()
                val daysSinceLastRide = ChronoUnit.DAYS.between(lastDate, today)

                if (daysSinceLastRide >= 3) {
                    val workouts = workoutRepository.findByPlanId(enrolledPlanId)
                        .sortedWith(compareBy<Workout> { it.weekNumber }.thenBy { it.dayNumber })
                    
                    val completedIds = sessionRepository.historyForUser(userId, 100, 0)
                        .map { it.workoutId }
                        .toSet()
                    
                    val nextWorkout = workouts.firstOrNull { it.id !in completedIds } ?: workouts.firstOrNull()
                    
                    if (nextWorkout != null) {
                        val rec = AdaptiveRecommendation(
                            id = newId("rec"),
                            userId = userId,
                            type = "TRAINING",
                            workoutId = nextWorkout.id,
                            title = "Resume: ${nextWorkout.name}",
                            description = nextWorkout.description,
                            reason = "It's been $daysSinceLastRide days since your last ride. Let's resume your training plan to maintain adaptations.",
                            createdAt = nowIso()
                        )
                        return rec
                    }
                }
            }
        }

        // 5. Recommended workout from training plan
        if (enrolledPlanId != null) {
            val workouts = workoutRepository.findByPlanId(enrolledPlanId)
                .sortedWith(compareBy<Workout> { it.weekNumber }.thenBy { it.dayNumber })
            
            val completedIds = sessionRepository.historyForUser(userId, 100, 0)
                        .map { it.workoutId }
                        .toSet()
            
            val nextWorkout = workouts.firstOrNull { it.id !in completedIds } ?: workouts.firstOrNull()
            
            if (nextWorkout != null) {
                val rec = AdaptiveRecommendation(
                    id = newId("rec"),
                    userId = userId,
                    type = "TRAINING",
                    workoutId = nextWorkout.id,
                    title = nextWorkout.name,
                    description = nextWorkout.description,
                    reason = "Your fatigue is in the optimal training zone (TSB: ${tsb}). Continue with your plan.",
                    createdAt = nowIso()
                )
                return rec
            }
        }

        val defaultWorkout = workoutRepository.list(1, 0).firstOrNull()
        val rec = AdaptiveRecommendation(
            id = newId("rec"),
            userId = userId,
            type = "TRAINING",
            workoutId = defaultWorkout?.id,
            title = defaultWorkout?.name ?: "Steady Base Endurance",
            description = defaultWorkout?.description ?: "Standard endurance zone training.",
            reason = "Ready to train. Choose a session to build cardiovascular base.",
            createdAt = nowIso()
        )
        return rec
    }

    suspend fun getCoachInsights(
        userId: String,
        fatigueState: FatigueCalculationService.FatigueState,
        recentAnalyses: List<WorkoutAnalysis>
    ): List<CoachInsight> {
        val insights = mutableListOf<CoachInsight>()
        val tsb = fatigueState.tsb

        // 1. Fatigue feedback
        if (tsb < -25.0) {
            insights.add(
                CoachInsight(
                    id = newId("insight"),
                    userId = userId,
                    title = "High Fatigue Alert",
                    message = "Your training balance is -${tsb.roundToInt()} (Extreme fatigue). Prioritize sleep and hydration; consider taking a complete rest day.",
                    severity = "warning",
                    sourceMetric = "fatigue",
                    createdAt = nowIso()
                )
            )
        } else if (tsb < -15.0) {
            insights.add(
                CoachInsight(
                    id = newId("insight"),
                    userId = userId,
                    title = "Recovery Recommended",
                    message = "Fatigue is building (TSB: ${tsb.roundToInt()}). Keep upcoming sessions aerobic and focus on high-quality recovery.",
                    severity = "neutral",
                    sourceMetric = "fatigue",
                    createdAt = nowIso()
                )
            )
        } else if (tsb > 5.0) {
            insights.add(
                CoachInsight(
                    id = newId("insight"),
                    userId = userId,
                    title = "Fresh and Ready",
                    message = "You are fresh and ready (TSB: +${tsb.roundToInt()}). This is an ideal window to schedule a hard progression effort or ramp test.",
                    severity = "positive",
                    sourceMetric = "fatigue",
                    createdAt = nowIso()
                )
            )
        }

        // 2. Recent workout feedback
        val lastAnalysis = recentAnalyses.firstOrNull()
        if (lastAnalysis != null) {
            val classification = lastAnalysis.classification
            if (classification.equals("FAILED", ignoreCase = true) || classification.equals("STRUGGLED", ignoreCase = true)) {
                insights.add(
                    CoachInsight(
                        id = newId("insight"),
                        userId = userId,
                        title = "Pacing Struggles Detected",
                        message = "In your last session, you struggled to maintain target power. We have scaled down targets for upcoming high-intensity rides by 5% to ensure full completion.",
                        severity = "warning",
                        sourceMetric = "power",
                        createdAt = nowIso()
                    )
                )
            } else if (classification.equals("OVERPERFORMED", ignoreCase = true)) {
                insights.add(
                    CoachInsight(
                        id = newId("insight"),
                        userId = userId,
                        title = "Strong Performance!",
                        message = "Outstanding execution in your last ride! Your progression level increased. Future workouts will reflect this stronger baseline.",
                        severity = "positive",
                        sourceMetric = "power",
                        createdAt = nowIso()
                    )
                )
            }

            val drift = lastAnalysis.hrDrift
            if (drift != null && drift > 8.0) {
                insights.add(
                    CoachInsight(
                        id = newId("insight"),
                        userId = userId,
                        title = "Heart Rate Decoupled",
                        message = "Your heart rate drifted by ${drift.roundToInt()}% in the second half of your last ride. This suggests decoupling and indicates a need for more aerobic base work.",
                        severity = "neutral",
                        sourceMetric = "heartRate",
                        createdAt = nowIso()
                    )
                )
            }

            val compliance = lastAnalysis.ergComplianceScore
            if (compliance != null && compliance < 80) {
                insights.add(
                    CoachInsight(
                        id = newId("insight"),
                        userId = userId,
                        title = "Low ERG Compliance",
                        message = "ERG target tracking was low (compliance: ${compliance}%). Focus on keeping a steady cadence when resistance changes.",
                        severity = "warning",
                        sourceMetric = "compliance",
                        createdAt = nowIso()
                    )
                )
            }
        }

        // 3. Cadence consistency
        val cadenceConsistency = lastAnalysis?.cadenceConsistencyScore
        if (cadenceConsistency != null && cadenceConsistency >= 85) {
            insights.add(
                CoachInsight(
                    id = newId("insight"),
                    userId = userId,
                    title = "Great Cadence Control",
                    message = "You maintained strong cadence consistency of ${cadenceConsistency}%. Excellent pedal technique.",
                    severity = "positive",
                    sourceMetric = "cadence",
                    createdAt = nowIso()
                )
            )
        }

        if (insights.isEmpty()) {
            insights.add(
                CoachInsight(
                    id = newId("insight"),
                    userId = userId,
                    title = "Optimal Progression",
                    message = "Training load is progressing nicely. Focus on maintaining cadence consistency and compliance during intervals.",
                    severity = "positive",
                    sourceMetric = "general",
                    createdAt = nowIso()
                )
            )
        }

        val finalInsights = insights.take(3)
        
        return finalInsights
    }
}
