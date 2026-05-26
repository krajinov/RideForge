package com.delminiusapps.rideforge.services.adaptive_training

import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutAnalysis
import com.delminiusapps.rideforge.models.WorkoutType
import com.delminiusapps.rideforge.repositories.SessionRepository
import com.delminiusapps.rideforge.repositories.WorkoutRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

class RecommendationEngine(
    private val workoutRepository: WorkoutRepository,
    private val sessionRepository: SessionRepository,
    private val progressionTracker: ProgressionTracker
) {

    data class Recommendation(
        val type: String,
        val workoutId: String?,
        val title: String,
        val description: String,
        val reason: String
    )

    data class CoachInsight(
        val message: String
    )

    suspend fun getHomeRecommendation(
        userId: String,
        fatigueState: FatigueCalculationService.FatigueState,
        enrolledPlanId: String?
    ): Recommendation {
        val tsb = fatigueState.tsb

        // 1. High Fatigue Recovery Swapping
        if (tsb < -20.0) {
            val recoveryWorkout = workoutRepository.list(100, 0)
                .firstOrNull { it.workoutType == WorkoutType.RECOVERY }
            
            return Recommendation(
                type = "RECOVERY",
                workoutId = recoveryWorkout?.id,
                title = recoveryWorkout?.name ?: "Active Recovery Spin",
                description = recoveryWorkout?.description ?: "A very gentle, low-intensity ride to promote blood flow and muscle recovery.",
                reason = "Your fatigue is high (TSB: ${tsb}). Swapping today's session for active recovery will help prevent overtraining."
            )
        }

        // 2. Missed Workouts Pushing/Adaptive Recovery
        val history = sessionRepository.historyForUser(userId, 1, 0)
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
                        return Recommendation(
                            type = "TRAINING",
                            workoutId = nextWorkout.id,
                            title = "Resume: ${nextWorkout.name}",
                            description = nextWorkout.description,
                            reason = "It's been $daysSinceLastRide days since your last ride. Let's resume your training plan to maintain adaptations."
                        )
                    }
                }
            }
        }

        // 3. Recommended workout from training plan
        if (enrolledPlanId != null) {
            val workouts = workoutRepository.findByPlanId(enrolledPlanId)
                .sortedWith(compareBy<Workout> { it.weekNumber }.thenBy { it.dayNumber })
            
            val completedIds = sessionRepository.historyForUser(userId, 100, 0)
                .map { it.workoutId }
                .toSet()
            
            val nextWorkout = workouts.firstOrNull { it.id !in completedIds } ?: workouts.firstOrNull()
            
            if (nextWorkout != null) {
                return Recommendation(
                    type = "TRAINING",
                    workoutId = nextWorkout.id,
                    title = nextWorkout.name,
                    description = nextWorkout.description,
                    reason = "Your fatigue is in the optimal training zone (TSB: ${tsb}). Continue with your plan."
                )
            }
        }

        val defaultWorkout = workoutRepository.list(1, 0).firstOrNull()
        return Recommendation(
            type = "TRAINING",
            workoutId = defaultWorkout?.id,
            title = defaultWorkout?.name ?: "Steady Base Endurance",
            description = defaultWorkout?.description ?: "Standard endurance zone training.",
            reason = "Ready to train. Choose a session to build cardiovascular base."
        )
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
            insights.add(CoachInsight("Your training balance is -${tsb.roundToInt()} (Extreme fatigue). Prioritize sleep and hydration; consider taking a complete rest day."))
        } else if (tsb < -15.0) {
            insights.add(CoachInsight("Fatigue is building (TSB: ${tsb.roundToInt()}). Keep upcoming sessions aerobic and focus on high-quality recovery."))
        } else if (tsb > 5.0) {
            insights.add(CoachInsight("You are fresh and ready (TSB: +${tsb.roundToInt()}). This is an ideal window to schedule a hard progression effort or ramp test."))
        }

        // 2. Recent workout feedback
        val lastAnalysis = recentAnalyses.firstOrNull()
        if (lastAnalysis != null) {
            when (lastAnalysis.classification) {
                "Failed", "Struggled" -> {
                    insights.add(CoachInsight("In your last session, you struggled to maintain target power. We have scaled down targets for upcoming high-intensity rides by 5% to ensure full completion."))
                }
                "Overperformed" -> {
                    insights.add(CoachInsight("Outstanding execution in your last ride! Your progression level increased. Future workouts will reflect this stronger baseline."))
                }
            }

            val drift = lastAnalysis.hrDrift
            if (drift != null && drift > 8.0) {
                insights.add(CoachInsight("Your heart rate drifted by ${drift.roundToInt()}% in the second half of your last ride. This suggests decoupling and indicates a need for more aerobic base work."))
            }

            val compliance = lastAnalysis.ergComplianceScore
            if (compliance != null && compliance < 80) {
                insights.add(CoachInsight("ERG target tracking was low (compliance: ${compliance}%). Focus on keeping a steady cadence when resistance changes."))
            }
        }

        if (insights.isEmpty()) {
            insights.add(CoachInsight("Training load is progressing nicely. Focus on maintaining cadence consistency and compliance during intervals."))
        }

        return insights.take(3)
    }
}
