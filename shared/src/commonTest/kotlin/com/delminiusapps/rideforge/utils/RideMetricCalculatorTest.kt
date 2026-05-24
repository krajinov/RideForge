package com.delminiusapps.rideforge.utils

import com.delminiusapps.rideforge.models.MetricSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RideMetricCalculatorTest {
    @Test
    fun speedIsZeroWithoutPower() {
        assertEquals(0.0, RideMetricCalculator.speedKmh(0, riderWeightKg = 78.0))
    }

    @Test
    fun speedIncreasesWithPower() {
        val endurance = RideMetricCalculator.speedKmh(150, riderWeightKg = 78.0)
        val threshold = RideMetricCalculator.speedKmh(250, riderWeightKg = 78.0)

        assertTrue(threshold > endurance)
    }

    @Test
    fun heavierRiderIsSlowerAtSamePower() {
        val lighter = RideMetricCalculator.speedKmh(200, riderWeightKg = 65.0)
        val heavier = RideMetricCalculator.speedKmh(200, riderWeightKg = 95.0)

        assertTrue(lighter > heavier)
    }

    @Test
    fun integratesDistanceFromOrderedSamplesAndCapsLargeGaps() {
        val distance = RideMetricCalculator.distanceKm(
            listOf(
                sample(20, 36.0),
                sample(10, 36.0),
                sample(10, 40.0),
                sample(40, 36.0),
            ),
        )

        assertNotNull(distance)
        assertEquals(0.3, (distance * 10.0).toInt() / 10.0)
    }

    private fun sample(elapsedSeconds: Int, speedKmh: Double): MetricSample = MetricSample(
        elapsedSeconds = elapsedSeconds,
        currentPowerWatts = 200,
        targetPowerWatts = 200,
        cadenceRpm = 88,
        heartRateBpm = 130,
        speedKmh = speedKmh,
    )
}
