package com.delminiusapps.rideforge.services.adaptive_training

import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutInterval
import com.delminiusapps.rideforge.models.WorkoutSession

object WorkoutClassifier {

    fun classify(
        session: WorkoutSession,
        workout: Workout,
        intervals: List<WorkoutInterval>,
        analysis: WorkoutCompletionAnalyzer.AnalysisResult,
        userFtp: Int
    ): String {
        val completion = analysis.completionPercent
        val successRate = analysis.intervalSuccessRate

        // 1. Failed Check
        if (completion < 50 || successRate < 50) {
            return "Failed"
        }

        // 2. Struggled Check
        val powerFade = analysis.powerFade ?: 0.0
        val ergCompliance = analysis.ergComplianceScore ?: 100
        if (completion < 90 || successRate < 80 || (ergCompliance < 70 && powerFade > 15.0)) {
            return "Struggled"
        }

        // 3. Overperformed Check
        val totalSeconds = intervals.sumOf { it.durationSeconds }.coerceAtLeast(1)
        val targetWorkPowerSum = intervals.sumOf { interval ->
            val target = interval.targetPowerWatts 
                ?: (interval.targetFtpPercent?.let { (userFtp * it) / 100 }) 
                ?: userFtp
            interval.durationSeconds * target.toDouble()
        }
        val targetAvgPower = targetWorkPowerSum / totalSeconds
        val actualAvgPower = session.averagePower ?: 0

        val intensityThresholdReached = actualAvgPower >= targetAvgPower * 1.05
        if (completion >= 98 && successRate >= 98 && intensityThresholdReached) {
            return "Overperformed"
        }

        // 4. Easy Check
        if (completion >= 95 && successRate >= 95 && analysis.estimatedRpe < 4.0) {
            return "Easy"
        }

        // 5. Successful Default
        return "Successful"
    }
}
