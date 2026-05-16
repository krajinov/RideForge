package com.delminiusapps.rideforge.data.mapper

import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.WorkoutSession

class WorkoutSummaryMapper {
    fun fromMetrics(
        sessionId: String,
        workoutId: String,
        workoutName: String,
        elapsedSeconds: Int,
        samples: List<MetricSample>,
    ): WorkoutSession {
        val averagePower = samples.map { it.currentPowerWatts }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toInt()
            ?: 0
        val normalizedPower = (averagePower * 1.10).toInt().coerceAtLeast(averagePower)
        val calories = ((averagePower * elapsedSeconds) / 1000.0 * 3.6).toInt().coerceAtLeast(0)
        return WorkoutSession(
            id = sessionId,
            workoutId = workoutId,
            workoutName = workoutName,
            elapsedSeconds = elapsedSeconds,
            averagePowerWatts = averagePower,
            normalizedPowerWatts = normalizedPower,
            calories = calories,
            tss = 68,
            completionPercent = 96,
        )
    }
}
