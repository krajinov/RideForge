package com.delminiusapps.rideforge.features.history.presentation

import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutInterval
import com.delminiusapps.rideforge.models.WorkoutSession
import com.delminiusapps.rideforge.models.WorkoutType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutPostRideAnalyticsTest {

    @Test
    fun derivesPostRideAnalyticsFromRecordedMetricSamples() {
        val samples = (0..1_260).map { second ->
            val target = if (second < 120) 140 else 240
            MetricSample(
                elapsedSeconds = second,
                currentPowerWatts = target + if (second % 12 == 0) 8 else -3,
                targetPowerWatts = target,
                cadenceRpm = 88 + (second % 4),
                heartRateBpm = 118 + (second / 40).coerceAtMost(42),
                speedKmh = 31.0 + (second % 6) * 0.2,
            )
        }
        val summary = WorkoutSession(
            id = "session-a",
            workoutId = "workout-a",
            workoutName = "Threshold Builder",
            elapsedSeconds = samples.last().elapsedSeconds,
            averagePowerWatts = 228,
            normalizedPowerWatts = 240,
            calories = 720,
            tss = 82,
            completionPercent = 100,
        )

        val analysis = buildWorkoutAnalysis(
            summary = summary,
            workout = workout(),
            metrics = samples,
            userFtp = 250,
            history = emptyList(),
        )

        assertTrue(analysis.hasRecordedMetrics)
        assertNotNull(analysis.distanceKm)
        assertNotNull(analysis.averageSpeedKmh)
        assertEquals(5, analysis.cadenceZones.size)
        assertEquals(6, analysis.powerZones.size)
        assertNotNull(analysis.powerCurve.first { it.seconds == 1_200 }.watts)
        assertNotNull(analysis.trainerMetrics)
        assertTrue(analysis.insights.isNotEmpty())
    }

    @Test
    fun leavesSpeedAndDistanceUnavailableWhenNoSpeedWasRecorded() {
        val samples = (0..120).map { second ->
            MetricSample(
                elapsedSeconds = second,
                currentPowerWatts = 180,
                targetPowerWatts = 180,
                cadenceRpm = 86,
                heartRateBpm = 130,
            )
        }
        val summary = WorkoutSession(
            id = "session-b",
            workoutId = "workout-a",
            workoutName = "Endurance",
            elapsedSeconds = samples.last().elapsedSeconds,
            averagePowerWatts = 180,
            normalizedPowerWatts = 190,
            calories = 120,
            tss = 18,
            completionPercent = 100,
        )

        val analysis = buildWorkoutAnalysis(
            summary = summary,
            workout = workout(),
            metrics = samples,
            userFtp = 250,
            history = emptyList(),
        )

        assertNull(analysis.distanceKm)
        assertNull(analysis.averageSpeedKmh)
        assertTrue(analysis.speedSeries.isEmpty())
    }

    @Test
    fun usesSummarySpeedAndDistanceWhenSampleSpeedIsUnavailable() {
        val summary = WorkoutSession(
            id = "session-c",
            workoutId = "workout-a",
            workoutName = "Endurance",
            elapsedSeconds = 1_800,
            averagePowerWatts = 180,
            normalizedPowerWatts = 190,
            calories = 360,
            tss = 42,
            completionPercent = 100,
            averageSpeedKmh = 30.0,
            totalDistanceKm = 15.0,
        )

        val analysis = buildWorkoutAnalysis(
            summary = summary,
            workout = workout(),
            metrics = emptyList(),
            userFtp = 250,
            history = emptyList(),
        )

        assertEquals(15.0, analysis.distanceKm)
        assertEquals(30.0, analysis.averageSpeedKmh)
    }

    private fun workout(): Workout = Workout(
        id = "workout-a",
        name = "Threshold Builder",
        durationMinutes = 21,
        difficulty = "Hard",
        description = "Threshold intervals",
        targetZones = listOf("Z2", "Z4"),
        intervals = listOf(
            WorkoutInterval("Warmup", 120, 56, "Z2"),
            WorkoutInterval("Threshold", 1_140, 96, "Z4"),
        ),
        planId = "plan-a",
        workoutType = WorkoutType.THRESHOLD,
    )
}
