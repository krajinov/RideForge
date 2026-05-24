package com.delminiusapps.rideforge.utils

import com.delminiusapps.rideforge.models.MetricSample
import kotlin.math.pow

object RideMetricCalculator {
    const val BikeWeightKg = 10.0
    const val DefaultRiderWeightKg = 78.0

    private const val Gravity = 9.80665
    private const val RollingResistanceCoefficient = 0.004
    private const val DragCoefficientArea = 0.32
    private const val AirDensity = 1.225
    private const val DrivetrainEfficiency = 0.975
    private const val MaxReasonableSpeedMetersPerSecond = 25.0
    private const val MaxSampleGapSeconds = 10

    fun speedKmh(powerWatts: Int, riderWeightKg: Double): Double {
        val wheelPower = powerWatts.coerceAtLeast(0) * DrivetrainEfficiency
        if (wheelPower <= 0.0) return 0.0

        val totalMass = riderWeightKg.coerceIn(35.0, 180.0) + BikeWeightKg
        val rollingForce = RollingResistanceCoefficient * totalMass * Gravity
        val aeroFactor = 0.5 * AirDensity * DragCoefficientArea

        var low = 0.0
        var high = MaxReasonableSpeedMetersPerSecond
        repeat(48) {
            val mid = (low + high) / 2.0
            val requiredPower = rollingForce * mid + aeroFactor * mid.pow(3.0)
            if (requiredPower < wheelPower) {
                low = mid
            } else {
                high = mid
            }
        }
        return ((low + high) / 2.0) * 3.6
    }

    fun distanceKm(samples: List<MetricSample>): Double? {
        val ordered = orderedSamples(samples)
        if (ordered.isEmpty()) return null
        var distance = 0.0
        var previous = MetricSample(
            elapsedSeconds = 0,
            currentPowerWatts = 0,
            targetPowerWatts = 0,
            cadenceRpm = 0,
            heartRateBpm = 0,
            speedKmh = ordered.first().speedKmh,
        )
        ordered.forEach { sample ->
            distance += distanceDeltaKm(previous, sample)
            previous = sample
        }
        return distance.takeIf { it > 0.0 }
    }

    fun distanceDeltaKm(previous: MetricSample, next: MetricSample): Double {
        val seconds = (next.elapsedSeconds - previous.elapsedSeconds).coerceIn(0, MaxSampleGapSeconds)
        if (seconds <= 0) return 0.0
        val speedKmh = when {
            previous.speedKmh > 0.0 -> previous.speedKmh
            next.speedKmh > 0.0 -> next.speedKmh
            else -> 0.0
        }
        return speedKmh * (seconds / 3600.0)
    }

    fun averageSpeedKmh(distanceKm: Double?, elapsedSeconds: Int): Double? {
        if (distanceKm == null || elapsedSeconds <= 0) return null
        return (distanceKm / (elapsedSeconds / 3600.0)).takeIf { it > 0.0 }
    }

    private fun orderedSamples(samples: List<MetricSample>): List<MetricSample> =
        samples
            .filter { it.elapsedSeconds >= 0 }
            .groupBy { it.elapsedSeconds }
            .map { (_, samplesAtSecond) -> samplesAtSecond.last() }
            .sortedBy { it.elapsedSeconds }
}
