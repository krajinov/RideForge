package com.delminiusapps.rideforge.services.adaptive_training

import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutInterval
import com.delminiusapps.rideforge.models.WorkoutSession
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object WorkoutCompletionAnalyzer {

    data class AnalysisResult(
        val completionPercent: Int,
        val intervalSuccessRate: Int,
        val ergComplianceScore: Int?,
        val cadenceConsistencyScore: Int?,
        val powerFade: Double?,
        val hrDrift: Double?,
        val estimatedRpe: Double,
        val avgDeviationPower: Double? = null,
        val best5sPower: Int? = null,
        val best30sPower: Int? = null,
        val best1mPower: Int? = null,
        val best5mPower: Int? = null,
        val best20mPower: Int? = null
    )

    fun analyze(
        session: WorkoutSession,
        workout: Workout,
        intervals: List<WorkoutInterval>,
        metrics: List<MetricSample>,
        userFtp: Int
    ): AnalysisResult {
        val samples = metrics
            .filter { (it.elapsedSeconds ?: 0) >= 0 }
            .groupBy { it.elapsedSeconds ?: 0 }
            .map { (_, samplesAtSecond) -> samplesAtSecond.last() }
            .sortedBy { it.elapsedSeconds ?: 0 }

        val elapsed = session.elapsedSeconds.coerceAtLeast(1)
        val completionPercent = ((elapsed.toDouble() / (workout.durationMinutes * 60)) * 100).roundToInt().coerceIn(0, 100)

        // 1. Interval Success Rate
        var currentOffset = 0
        var workIntervalsCount = 0
        var successfulWorkIntervals = 0

        intervals.forEach { interval ->
            val startSec = currentOffset
            val endSec = currentOffset + interval.durationSeconds
            currentOffset = endSec

            // Check if it is a WORK interval (ignore warmup/recovery/cooldown for success rate calculation)
            val isWork = interval.name.contains("work", ignoreCase = true) || 
                         (!interval.name.contains("warm", ignoreCase = true) && 
                          !interval.name.contains("cool", ignoreCase = true) && 
                          !interval.name.contains("recov", ignoreCase = true) && 
                          !interval.name.contains("rest", ignoreCase = true) && 
                          !interval.name.contains("easy", ignoreCase = true))

            if (isWork) {
                workIntervalsCount++
                val intervalSamples = samples.filter { (it.elapsedSeconds ?: 0) in startSec until endSec }
                val targetPower = interval.targetPowerWatts ?: (interval.targetFtpPercent?.let { (userFtp * it) / 100 }) ?: userFtp
                
                if (intervalSamples.isNotEmpty()) {
                     val avgPower = intervalSamples.map { it.currentPower }.average()
                     val compliance = intervalSamples.count { abs(it.currentPower - targetPower) <= maxOf(15, (targetPower * 0.08).roundToInt()) }
                     val compliancePercent = (compliance.toDouble() / intervalSamples.size) * 100.0
                     
                     // Successful if user held at least 88% of target power, OR had 80% sample compliance
                     if (avgPower >= targetPower * 0.88 || compliancePercent >= 80.0) {
                         successfulWorkIntervals++
                     }
                }
            }
        }

        val intervalSuccessRate = if (workIntervalsCount > 0) {
            ((successfulWorkIntervals.toDouble() / workIntervalsCount) * 100).roundToInt().coerceIn(0, 100)
        } else {
            completionPercent
        }

        // 2. ERG Compliance Score and Average Deviation
        val targetSamples = samples.filter { it.targetPower > 0 && it.currentPower > 0 }
        val ergComplianceScore = if (targetSamples.size >= 5) {
            val compliant = targetSamples.count { abs(it.currentPower - it.targetPower) <= maxOf(10, (it.targetPower * 0.05).roundToInt()) }
            ((compliant.toDouble() / targetSamples.size) * 100).roundToInt().coerceIn(0, 100)
        } else null

        val avgDeviationPower = if (targetSamples.isNotEmpty()) {
            val totalDeviation = targetSamples.sumOf { abs(it.currentPower - it.targetPower).toDouble() }
            ((totalDeviation / targetSamples.size) * 10).roundToInt() / 10.0
        } else null

        // 3. Cadence Consistency Score
        val cadenceValues = samples.map { it.cadence }.filter { it > 0 }
        val cadenceConsistencyScore = calculateConsistency(cadenceValues.map { it.toDouble() })

        // 4. Power Fade
        val powerFade = calculatePowerFade(samples, intervals, userFtp)

        // 5. HR Drift
        val hrDrift = calculateHrDrift(samples)

        // 6. Estimated RPE
        val np = session.normalizedPower ?: session.averagePower ?: 0
        val intensityFactor = if (userFtp > 0) np.toDouble() / userFtp else 0.8
        val durationRatio = workout.durationMinutes / 60.0
        val driftModifier = if (hrDrift != null && hrDrift > 0.0) hrDrift * 0.08 else 0.0
        
        val rawRpe = (intensityFactor * 7.5) + driftModifier + (durationRatio * 0.8) + (100 - completionPercent) * 0.05
        val estimatedRpe = rawRpe.coerceIn(1.0, 10.0)

        // 7. Peak Power Efforts: 5s, 30s, 1m, 5m, 20m
        val best5sPower = peakAveragePower(samples, 5)
        val best30sPower = peakAveragePower(samples, 30)
        val best1mPower = peakAveragePower(samples, 60)
        val best5mPower = peakAveragePower(samples, 300)
        val best20mPower = peakAveragePower(samples, 1200)

        return AnalysisResult(
            completionPercent = completionPercent,
            intervalSuccessRate = intervalSuccessRate,
            ergComplianceScore = ergComplianceScore,
            cadenceConsistencyScore = cadenceConsistencyScore,
            powerFade = powerFade,
            hrDrift = hrDrift,
            estimatedRpe = ((estimatedRpe * 10).roundToInt() / 10.0),
            avgDeviationPower = avgDeviationPower,
            best5sPower = best5sPower,
            best30sPower = best30sPower,
            best1mPower = best1mPower,
            best5mPower = best5mPower,
            best20mPower = best20mPower
        )
    }

    private fun calculateConsistency(values: List<Double>): Int? {
        if (values.size < 5) return null
        val average = values.average()
        if (average <= 0.0) return null
        val variance = values.map { (it - average).pow(2.0) }.average()
        val coefficient = sqrt(variance) / average
        return (100.0 - coefficient * 160.0).roundToInt().coerceIn(0, 100)
    }

    private fun calculatePowerFade(
        samples: List<MetricSample>,
        intervals: List<WorkoutInterval>,
        userFtp: Int
    ): Double? {
        var currentOffset = 0
        val workIntervalPowers = mutableListOf<Double>()

        intervals.forEach { interval ->
            val startSec = currentOffset
            val endSec = currentOffset + interval.durationSeconds
            currentOffset = endSec

            val isWork = interval.name.contains("work", ignoreCase = true) || 
                         (!interval.name.contains("warm", ignoreCase = true) && 
                          !interval.name.contains("cool", ignoreCase = true) && 
                          !interval.name.contains("recov", ignoreCase = true) && 
                          !interval.name.contains("rest", ignoreCase = true) && 
                          !interval.name.contains("easy", ignoreCase = true))

            if (isWork) {
                val intervalSamples = samples.filter { (it.elapsedSeconds ?: 0) in startSec until endSec }
                if (intervalSamples.isNotEmpty()) {
                    workIntervalPowers.add(intervalSamples.map { it.currentPower }.average())
                }
            }
        }

        if (workIntervalPowers.size < 2) return null
        val firstHalfAvg = workIntervalPowers.take((workIntervalPowers.size + 1) / 2).average()
        val secondHalfAvg = workIntervalPowers.takeLast(workIntervalPowers.size / 2).average()
        if (firstHalfAvg <= 0.0) return null
        
        val fade = ((firstHalfAvg - secondHalfAvg) / firstHalfAvg) * 100.0
        return ((fade * 10).roundToInt() / 10.0).coerceAtLeast(0.0)
    }

    private fun calculateHrDrift(samples: List<MetricSample>): Double? {
        val hrSamples = samples.filter { it.heartRate > 0 && it.currentPower > 0 }
        if (hrSamples.size < 30) return null
        
        val half = hrSamples.size / 2
        val first = hrSamples.take(half)
        val second = hrSamples.drop(half)
        
        val firstRatio = first.map { it.heartRate.toDouble() / it.currentPower }.average()
        val secondRatio = second.map { it.heartRate.toDouble() / it.currentPower }.average()
        
        if (firstRatio <= 0.0) return null
        val drift = ((secondRatio - firstRatio) / firstRatio) * 100.0
        return ((drift * 10).roundToInt() / 10.0)
    }

    private fun peakAveragePower(samples: List<MetricSample>, windowSeconds: Int): Int? {
        if (samples.isEmpty()) return null
        val powerValues = samples.associate { (it.elapsedSeconds ?: 0) to it.currentPower }
        val minSec = samples.minOf { it.elapsedSeconds ?: 0 }
        val maxSec = samples.maxOf { it.elapsedSeconds ?: 0 }
        val duration = maxSec - minSec + 1
        if (duration < windowSeconds) return null

        var bestAverage = 0.0
        var found = false
        var currentSum = 0
        var currentCount = 0
        
        for (sec in minSec..maxSec) {
            val power = powerValues[sec] ?: 0
            currentSum += power
            currentCount++
            
            if (currentCount > windowSeconds) {
                val outSec = sec - windowSeconds
                currentSum -= powerValues[outSec] ?: 0
                currentCount--
            }
            
            if (currentCount == windowSeconds) {
                val avg = currentSum.toDouble() / windowSeconds
                if (!found || avg > bestAverage) {
                    bestAverage = avg
                    found = true
                }
            }
        }
        return if (found) bestAverage.roundToInt() else null
    }
}
