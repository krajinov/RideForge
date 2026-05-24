package com.delminiusapps.rideforge.services

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
        val ordered = samples
            .filter { (it.elapsedSeconds ?: 0) >= 0 }
            .groupBy { it.elapsedSeconds ?: 0 }
            .map { (_, samplesAtSecond) -> samplesAtSecond.last() }
            .sortedBy { it.elapsedSeconds ?: 0 }
        if (ordered.isEmpty()) return null

        var distance = 0.0
        var previousElapsed = 0
        var previousSpeed = ordered.first().speedKmh
        ordered.forEach { sample ->
            val elapsed = sample.elapsedSeconds ?: previousElapsed
            val seconds = (elapsed - previousElapsed).coerceIn(0, MaxSampleGapSeconds)
            val speed = when {
                previousSpeed > 0.0 -> previousSpeed
                sample.speedKmh > 0.0 -> sample.speedKmh
                else -> 0.0
            }
            distance += speed * (seconds / 3600.0)
            previousElapsed = elapsed
            previousSpeed = sample.speedKmh
        }
        return distance.takeIf { it > 0.0 }
    }

    fun averageSpeedKmh(distanceKm: Double?, elapsedSeconds: Int): Double? {
        if (distanceKm == null || elapsedSeconds <= 0) return null
        return (distanceKm / (elapsedSeconds / 3600.0)).takeIf { it > 0.0 }
    }
}
