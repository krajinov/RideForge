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
        val powerFade = analysis.powerFade ?: 0.0
        val hrDrift = analysis.hrDrift
        val compliance = analysis.ergComplianceScore ?: 100
        val cadenceConsistency = analysis.cadenceConsistencyScore ?: 100

        // 1. FAILED Check
        if (completion < 75 || successRate < 60) {
            return "FAILED"
        }

        // 2. STRUGGLED Check
        if (completion in 75..90 || powerFade > 15.0 || successRate in 60..79) {
            return "STRUGGLED"
        }

        // 3. OVERPERFORMED Check
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
        if (completion >= 95 && intensityThresholdReached && powerFade < 5.0) {
            return "OVERPERFORMED"
        }

        // 4. EASY Check
        val isHrDriftLow = hrDrift == null || hrDrift < 4.0
        if (completion >= 90 && isHrDriftLow && compliance >= 90 && cadenceConsistency >= 85 && analysis.estimatedRpe < 4.0) {
            return "EASY"
        }

        // 5. SUCCESSFUL default
        return "SUCCESSFUL"
    }
}
