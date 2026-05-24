package com.delminiusapps.rideforge.features.history.presentation

import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.RideHistoryItem
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutSession
import com.delminiusapps.rideforge.utils.RideMetricCalculator
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class WorkoutAnalysis(
    val hasRecordedMetrics: Boolean,
    val sampleCount: Int,
    val elapsedSeconds: Int,
    val distanceKm: Double?,
    val averageSpeedKmh: Double?,
    val maxSpeedKmh: Double?,
    val speedConsistencyScore: Int?,
    val averagePowerWatts: Int,
    val normalizedPowerWatts: Int,
    val intensityFactor: Double?,
    val ftpImpactLabel: String,
    val averageCadenceRpm: Int?,
    val maxCadenceRpm: Int?,
    val cadenceConsistencyScore: Int?,
    val averageHeartRateBpm: Int?,
    val maxHeartRateBpm: Int?,
    val heartRateDriftPercent: Double?,
    val recoveryTrendBpm: Int?,
    val powerSeries: List<ChartPoint>,
    val targetPowerSeries: List<ChartPoint>,
    val cadenceSeries: List<ChartPoint>,
    val heartRateSeries: List<ChartPoint>,
    val speedSeries: List<ChartPoint>,
    val intervals: List<IntervalAnalysis>,
    val powerCurve: List<PeakPower>,
    val powerZones: List<ZoneDistribution>,
    val cadenceZones: List<ZoneDistribution>,
    val heartRateZones: List<ZoneDistribution>,
    val trainerMetrics: TrainerAnalytics?,
    val insights: List<String>,
    val achievements: List<AchievementInsight>,
    val coachNotes: CoachNotes,
    val comparisons: List<ComparisonInsight>,
)

data class ChartPoint(
    val elapsedSeconds: Int,
    val value: Double,
)

data class IntervalAnalysis(
    val name: String,
    val typeLabel: String,
    val startSeconds: Int,
    val durationSeconds: Int,
    val targetZone: String,
    val targetPowerWatts: Int,
    val averagePowerWatts: Int?,
    val averageCadenceRpm: Int?,
    val cadenceTrendLabel: String,
    val completionPercent: Int,
)

data class PeakPower(
    val label: String,
    val seconds: Int,
    val watts: Int?,
)

data class ZoneDistribution(
    val label: String,
    val rangeLabel: String,
    val seconds: Int,
    val percent: Int,
)

data class TrainerAnalytics(
    val ergComplianceScore: Int,
    val targetAccuracyPercent: Int,
    val averageDeviationWatts: Int,
    val resistanceChanges: Int,
)

data class AchievementInsight(
    val title: String,
    val subtitle: String,
)

data class CoachNotes(
    val summary: String,
    val recommendation: String,
    val recovery: String,
    val nextWorkout: String,
)

data class ComparisonInsight(
    val title: String,
    val value: String,
    val detail: String,
)

fun buildWorkoutAnalysis(
    summary: WorkoutSession,
    workout: Workout?,
    metrics: List<MetricSample>,
    userFtp: Int,
    history: List<RideHistoryItem>,
): WorkoutAnalysis {
    val samples = metrics
        .filter { it.elapsedSeconds >= 0 }
        .groupBy { it.elapsedSeconds }
        .map { (_, samplesAtSecond) -> samplesAtSecond.last() }
        .sortedBy { it.elapsedSeconds }
    val elapsedSeconds = maxOf(summary.elapsedSeconds, samples.maxOfOrNull { it.elapsedSeconds } ?: 0)
    val powerValues = samples.map { it.currentPowerWatts }.filter { it > 0 }
    val averagePower = powerValues.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        ?: summary.averagePowerWatts
    val normalizedPower = normalizedPower(samples) ?: summary.normalizedPowerWatts.coerceAtLeast(averagePower)
    val distance = RideMetricCalculator.distanceKm(samples) ?: summary.totalDistanceKm
    val speedValues = samples.map { it.speedKmh }.filter { it > 0.0 }
    val averageSpeed = distance?.takeIf { elapsedSeconds > 0 }?.let { it / (elapsedSeconds / 3600.0) }
        ?: summary.averageSpeedKmh
        ?: speedValues.takeIf { it.isNotEmpty() }?.average()
    val maxSpeed = speedValues.maxOrNull()
    val cadenceValues = samples.map { it.cadenceRpm }.filter { it > 0 }
    val heartRateValues = samples.map { it.heartRateBpm }.filter { it > 0 }
    val intensityFactor = normalizedPower.takeIf { userFtp > 0 }?.let { it / userFtp.toDouble() }

    val powerCurve = listOf(
        PeakPower("5s", 5, peakAveragePower(samples, 5)),
        PeakPower("30s", 30, peakAveragePower(samples, 30)),
        PeakPower("1m", 60, peakAveragePower(samples, 60)),
        PeakPower("5m", 300, peakAveragePower(samples, 300)),
        PeakPower("20m", 1_200, peakAveragePower(samples, 1_200)),
    )
    val intervals = intervalAnalyses(workout, samples, userFtp)
    val trainerAnalytics = trainerAnalytics(samples)
    val powerZones = powerZoneDistribution(samples, userFtp)
    val cadenceZones = cadenceZoneDistribution(samples)
    val heartRateZones = heartRateZoneDistribution(samples)
    val cadenceConsistency = consistencyScore(cadenceValues.map { it.toDouble() })
    val speedConsistency = consistencyScore(speedValues)
    val hrDrift = heartRateDrift(samples)
    val recoveryTrend = recoveryTrend(samples, intervals)
    val ftpImpact = ftpImpactLabel(normalizedPower, powerCurve.firstOrNull { it.seconds == 1_200 }?.watts, userFtp)
    val insights = rideInsights(
        summary = summary,
        cadenceConsistencyScore = cadenceConsistency,
        trainerAnalytics = trainerAnalytics,
        ftpImpactLabel = ftpImpact,
        intervals = intervals,
        powerCurve = powerCurve,
    )
    val achievements = achievementInsights(
        samples = samples,
        summary = summary,
        history = history,
        powerCurve = powerCurve,
        maxCadence = cadenceValues.maxOrNull(),
    )
    val comparisons = comparisonInsights(summary, history)

    return WorkoutAnalysis(
        hasRecordedMetrics = samples.isNotEmpty(),
        sampleCount = samples.size,
        elapsedSeconds = elapsedSeconds,
        distanceKm = distance,
        averageSpeedKmh = averageSpeed,
        maxSpeedKmh = maxSpeed,
        speedConsistencyScore = speedConsistency,
        averagePowerWatts = averagePower,
        normalizedPowerWatts = normalizedPower,
        intensityFactor = intensityFactor,
        ftpImpactLabel = ftpImpact,
        averageCadenceRpm = cadenceValues.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        maxCadenceRpm = cadenceValues.maxOrNull(),
        cadenceConsistencyScore = cadenceConsistency,
        averageHeartRateBpm = heartRateValues.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        maxHeartRateBpm = heartRateValues.maxOrNull(),
        heartRateDriftPercent = hrDrift,
        recoveryTrendBpm = recoveryTrend,
        powerSeries = samples.map { ChartPoint(it.elapsedSeconds, it.currentPowerWatts.toDouble()) },
        targetPowerSeries = samples.map { ChartPoint(it.elapsedSeconds, it.targetPowerWatts.toDouble()) },
        cadenceSeries = samples.filter { it.cadenceRpm > 0 }.map { ChartPoint(it.elapsedSeconds, it.cadenceRpm.toDouble()) },
        heartRateSeries = samples.filter { it.heartRateBpm > 0 }.map { ChartPoint(it.elapsedSeconds, it.heartRateBpm.toDouble()) },
        speedSeries = samples.filter { it.speedKmh > 0.0 }.map { ChartPoint(it.elapsedSeconds, it.speedKmh) },
        intervals = intervals,
        powerCurve = powerCurve,
        powerZones = powerZones,
        cadenceZones = cadenceZones,
        heartRateZones = heartRateZones,
        trainerMetrics = trainerAnalytics,
        insights = insights,
        achievements = achievements,
        coachNotes = coachNotes(summary, ftpImpact, cadenceConsistency, trainerAnalytics),
        comparisons = comparisons,
    )
}

private fun normalizedPower(samples: List<MetricSample>): Int? {
    val powerSamples = samples.filter { it.currentPowerWatts > 0 }
    if (powerSamples.size < 30) return null
    val rolling = powerSamples.mapIndexedNotNull { index, sample ->
        val start = sample.elapsedSeconds
        val window = powerSamples.drop(index).takeWhile { it.elapsedSeconds < start + 30 }
        if ((window.lastOrNull()?.elapsedSeconds ?: start) - start < 24) {
            null
        } else {
            window.map { it.currentPowerWatts }.average()
        }
    }
    if (rolling.isEmpty()) return null
    val fourthPowerAverage = rolling.map { it.pow(4.0) }.average()
    return fourthPowerAverage.pow(0.25).roundToInt()
}

private fun peakAveragePower(samples: List<MetricSample>, windowSeconds: Int): Int? {
    val powerSamples = samples.filter { it.currentPowerWatts > 0 }
    val totalDuration = (powerSamples.lastOrNull()?.elapsedSeconds ?: 0) - (powerSamples.firstOrNull()?.elapsedSeconds ?: 0)
    if (powerSamples.isEmpty() || totalDuration < windowSeconds) return null
    var best: Double? = null
    powerSamples.forEachIndexed { index, sample ->
        val windowEnd = sample.elapsedSeconds + windowSeconds
        val window = powerSamples.drop(index).takeWhile { it.elapsedSeconds < windowEnd }
        val coveredSeconds = (window.lastOrNull()?.elapsedSeconds ?: sample.elapsedSeconds) - sample.elapsedSeconds
        if (coveredSeconds >= (windowSeconds * 0.8).roundToInt() && window.isNotEmpty()) {
            val average = window.map { it.currentPowerWatts }.average()
            best = maxOf(best ?: average, average)
        }
    }
    return best?.roundToInt()
}

private fun intervalAnalyses(
    workout: Workout?,
    samples: List<MetricSample>,
    ftp: Int,
): List<IntervalAnalysis> {
    val intervals = workout?.intervals.orEmpty()
    if (intervals.isEmpty()) return emptyList()
    var startSeconds = 0
    return intervals.mapIndexed { index, interval ->
        val endSeconds = startSeconds + interval.durationSeconds
        val intervalSamples = samples.filter { it.elapsedSeconds in startSeconds until endSeconds }
        val cadenceTrend = cadenceTrend(intervalSamples)
        val analysis = IntervalAnalysis(
            name = interval.name,
            typeLabel = intervalType(index, intervals.lastIndex, interval.name),
            startSeconds = startSeconds,
            durationSeconds = interval.durationSeconds,
            targetZone = interval.zone,
            targetPowerWatts = interval.targetPower(ftp),
            averagePowerWatts = intervalSamples.map { it.currentPowerWatts }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            averageCadenceRpm = intervalSamples.map { it.cadenceRpm }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            cadenceTrendLabel = cadenceTrend,
            completionPercent = if (interval.durationSeconds <= 0) {
                0
            } else {
                ((intervalSamples.size.toDouble() / interval.durationSeconds) * 100.0).roundToInt().coerceIn(0, 100)
            },
        )
        startSeconds = endSeconds
        analysis
    }
}

private fun intervalType(index: Int, lastIndex: Int, name: String): String {
    val lower = name.lowercase()
    return when {
        index == 0 || "warm" in lower -> "Warmup"
        index == lastIndex || "cool" in lower -> "Cooldown"
        "recover" in lower || "easy" in lower || "rest" in lower -> "Recovery"
        else -> "Work"
    }
}

private fun cadenceTrend(samples: List<MetricSample>): String {
    val cadence = samples.map { it.cadenceRpm }.filter { it > 0 }
    if (cadence.size < 6) return "Not enough data"
    val third = (cadence.size / 3).coerceAtLeast(1)
    val early = cadence.take(third).average()
    val late = cadence.takeLast(third).average()
    val drop = early - late
    return when {
        drop >= 6.0 -> "Dropped ${drop.roundToInt()} rpm late"
        late - early >= 6.0 -> "Built ${(late - early).roundToInt()} rpm"
        else -> "Stable cadence"
    }
}

private fun sampleDurations(samples: List<MetricSample>): List<Pair<MetricSample, Int>> {
    return samples.mapIndexed { index, sample ->
        val next = samples.getOrNull(index + 1)
        val duration = next?.let { (it.elapsedSeconds - sample.elapsedSeconds).coerceIn(1, 10) } ?: 1
        sample to duration
    }
}

private fun powerZoneDistribution(samples: List<MetricSample>, ftp: Int): List<ZoneDistribution> {
    val zones = listOf(
        ZoneSpec("Z1 Recovery", "0-55% FTP") { watts: Int -> watts in 1 until (ftp * 0.55).roundToInt() },
        ZoneSpec("Z2 Endurance", "56-75% FTP") { watts -> watts >= (ftp * 0.55).roundToInt() && watts < (ftp * 0.76).roundToInt() },
        ZoneSpec("Z3 Tempo", "76-90% FTP") { watts -> watts >= (ftp * 0.76).roundToInt() && watts < (ftp * 0.91).roundToInt() },
        ZoneSpec("Z4 Threshold", "91-105% FTP") { watts -> watts >= (ftp * 0.91).roundToInt() && watts < (ftp * 1.06).roundToInt() },
        ZoneSpec("Z5 VO2 Max", "106-120% FTP") { watts -> watts >= (ftp * 1.06).roundToInt() && watts < (ftp * 1.21).roundToInt() },
        ZoneSpec("Z6 Anaerobic", "121%+ FTP") { watts -> watts >= (ftp * 1.21).roundToInt() },
    )
    return distribution(samples.filter { it.currentPowerWatts > 0 }, zones) { it.currentPowerWatts }
}

private fun cadenceZoneDistribution(samples: List<MetricSample>): List<ZoneDistribution> {
    val zones = listOf(
        ZoneSpec("Low", "<70 rpm") { value: Int -> value in 1..69 },
        ZoneSpec("Controlled", "70-84 rpm") { value -> value in 70..84 },
        ZoneSpec("Optimal", "85-94 rpm") { value -> value in 85..94 },
        ZoneSpec("High", "95-104 rpm") { value -> value in 95..104 },
        ZoneSpec("Spin", "105+ rpm") { value -> value >= 105 },
    )
    return distribution(samples.filter { it.cadenceRpm > 0 }, zones) { it.cadenceRpm }
}

private fun heartRateZoneDistribution(samples: List<MetricSample>): List<ZoneDistribution> {
    val zones = listOf(
        ZoneSpec("Easy", "<120 bpm") { value: Int -> value in 1..119 },
        ZoneSpec("Aerobic", "120-139 bpm") { value -> value in 120..139 },
        ZoneSpec("Tempo", "140-154 bpm") { value -> value in 140..154 },
        ZoneSpec("Threshold", "155-169 bpm") { value -> value in 155..169 },
        ZoneSpec("VO2", "170+ bpm") { value -> value >= 170 },
    )
    return distribution(samples.filter { it.heartRateBpm > 0 }, zones) { it.heartRateBpm }
}

private fun distribution(
    samples: List<MetricSample>,
    zones: List<ZoneSpec>,
    value: (MetricSample) -> Int,
): List<ZoneDistribution> {
    val durations = sampleDurations(samples)
    val totalSeconds = durations.sumOf { it.second }.coerceAtLeast(1)
    return zones.map { zone ->
        val seconds = durations.sumOf { (sample, duration) ->
            if (zone.matches(value(sample))) duration else 0
        }
        ZoneDistribution(
            label = zone.label,
            rangeLabel = zone.rangeLabel,
            seconds = seconds,
            percent = ((seconds.toDouble() / totalSeconds) * 100.0).roundToInt().coerceIn(0, 100),
        )
    }
}

private fun trainerAnalytics(samples: List<MetricSample>): TrainerAnalytics? {
    val targetSamples = samples.filter { it.targetPowerWatts > 0 && it.currentPowerWatts > 0 }
    if (targetSamples.size < 5) return null
    val deviations = targetSamples.map { abs(it.currentPowerWatts - it.targetPowerWatts) }
    val avgDeviation = deviations.average().roundToInt()
    val compliant = targetSamples.count { abs(it.currentPowerWatts - it.targetPowerWatts) <= maxOf(10, (it.targetPowerWatts * 0.05).roundToInt()) }
    val targetChanges = targetSamples.zipWithNext().count { (previous, next) ->
        previous.targetPowerWatts != next.targetPowerWatts
    }
    val targetAccuracy = ((compliant.toDouble() / targetSamples.size) * 100.0).roundToInt().coerceIn(0, 100)
    return TrainerAnalytics(
        ergComplianceScore = targetAccuracy,
        targetAccuracyPercent = (100 - ((avgDeviation.toDouble() / targetSamples.map { it.targetPowerWatts }.average()) * 100.0)).roundToInt().coerceIn(0, 100),
        averageDeviationWatts = avgDeviation,
        resistanceChanges = targetChanges,
    )
}

private fun consistencyScore(values: List<Double>): Int? {
    if (values.size < 5) return null
    val average = values.average()
    if (average <= 0.0) return null
    val variance = values.map { (it - average).pow(2.0) }.average()
    val coefficient = sqrt(variance) / average
    return (100.0 - coefficient * 160.0).roundToInt().coerceIn(0, 100)
}

private fun heartRateDrift(samples: List<MetricSample>): Double? {
    val hrSamples = samples.filter { it.heartRateBpm > 0 && it.currentPowerWatts > 0 }
    if (hrSamples.size < 20) return null
    val half = hrSamples.size / 2
    val first = hrSamples.take(half)
    val second = hrSamples.drop(half)
    val firstRatio = first.map { it.heartRateBpm.toDouble() / it.currentPowerWatts }.average()
    val secondRatio = second.map { it.heartRateBpm.toDouble() / it.currentPowerWatts }.average()
    if (firstRatio <= 0.0) return null
    return ((secondRatio - firstRatio) / firstRatio) * 100.0
}

private fun recoveryTrend(samples: List<MetricSample>, intervals: List<IntervalAnalysis>): Int? {
    val recovery = intervals.firstOrNull { it.typeLabel == "Recovery" } ?: return null
    val recoverySamples = samples.filter {
        it.heartRateBpm > 0 && it.elapsedSeconds in recovery.startSeconds until recovery.startSeconds + recovery.durationSeconds
    }
    if (recoverySamples.size < 6) return null
    val first = recoverySamples.take((recoverySamples.size / 3).coerceAtLeast(1)).map { it.heartRateBpm }.average()
    val last = recoverySamples.takeLast((recoverySamples.size / 3).coerceAtLeast(1)).map { it.heartRateBpm }.average()
    return (first - last).roundToInt()
}

private fun ftpImpactLabel(normalizedPower: Int, twentyMinutePower: Int?, ftp: Int): String {
    if (ftp <= 0) return "FTP unavailable"
    val npRatio = normalizedPower / ftp.toDouble()
    val twentyMinuteRatio = twentyMinutePower?.let { it / ftp.toDouble() }
    return when {
        twentyMinuteRatio != null && twentyMinuteRatio >= 1.02 -> "FTP validation effort"
        npRatio >= 0.95 -> "High FTP load"
        npRatio >= 0.85 -> "Productive FTP stimulus"
        npRatio >= 0.70 -> "Aerobic support"
        else -> "Recovery load"
    }
}

private fun rideInsights(
    summary: WorkoutSession,
    cadenceConsistencyScore: Int?,
    trainerAnalytics: TrainerAnalytics?,
    ftpImpactLabel: String,
    intervals: List<IntervalAnalysis>,
    powerCurve: List<PeakPower>,
): List<String> {
    val insights = mutableListOf<String>()
    if ((cadenceConsistencyScore ?: 0) >= 86) {
        insights += "Strong cadence consistency across the recorded intervals."
    }
    val finalWork = intervals.filter { it.typeLabel == "Work" }.takeLast(2)
    if (finalWork.size == 2) {
        val first = finalWork.first().averagePowerWatts
        val last = finalWork.last().averagePowerWatts
        if (first != null && last != null && first > 0) {
            val fade = ((first - last) / first.toDouble()) * 100.0
            insights += if (fade >= 5.0) {
                "Power faded ${fade.roundToInt()}% in the final work block."
            } else {
                "Power stayed stable through the final work block."
            }
        }
    }
    trainerAnalytics?.let {
        if (it.ergComplianceScore >= 90) {
            insights += "ERG control stayed tight with ${it.averageDeviationWatts} W average deviation."
        } else {
            insights += "Power target tracking needs attention: ${it.averageDeviationWatts} W average deviation."
        }
    }
    powerCurve.firstOrNull { it.seconds == 1_200 && it.watts != null }?.watts?.let {
        insights += "You held $it W for 20 minutes, classified as $ftpImpactLabel."
    }
    if (insights.isEmpty()) {
        insights += when {
            summary.completionPercent >= 98 -> "Workout execution was complete with no missing summary data."
            summary.completionPercent >= 90 -> "Workout was mostly completed; review late intervals for pacing."
            else -> "Completion was limited; keep the next attempt conservative."
        }
    }
    return insights.take(4)
}

private fun achievementInsights(
    samples: List<MetricSample>,
    summary: WorkoutSession,
    history: List<RideHistoryItem>,
    powerCurve: List<PeakPower>,
    maxCadence: Int?,
): List<AchievementInsight> {
    val achievements = mutableListOf<AchievementInsight>()
    powerCurve.firstOrNull { it.seconds == 300 && it.watts != null }?.watts?.let {
        achievements += AchievementInsight("5 min power marker", "$it W saved for this ride")
    }
    maxCadence?.takeIf { it > 0 }?.let {
        achievements += AchievementInsight("Highest cadence", "$it rpm recorded")
    }
    val currentMinutes = (summary.elapsedSeconds / 60).coerceAtLeast(0)
    val longestSavedRide = history.maxOfOrNull { it.durationMinutes } ?: currentMinutes
    if (currentMinutes > 0 && currentMinutes >= longestSavedRide) {
        achievements += AchievementInsight("Longest saved ride", "$currentMinutes min in workout history")
    }
    val savedWorkoutCount = history.size
    if (savedWorkoutCount >= 3) {
        achievements += AchievementInsight("Consistency streak", "$savedWorkoutCount saved workouts in history")
    }
    if (achievements.isEmpty()) {
        achievements += AchievementInsight("PR tracking", "More saved rides are needed to verify personal records")
    }
    if (samples.isEmpty()) {
        achievements += AchievementInsight("Data quality", "No metric samples were saved for this workout")
    }
    return achievements.take(4)
}

private fun coachNotes(
    summary: WorkoutSession,
    ftpImpactLabel: String,
    cadenceConsistencyScore: Int?,
    trainerAnalytics: TrainerAnalytics?,
): CoachNotes {
    val recommendation = when {
        summary.completionPercent >= 98 && ftpImpactLabel.contains("FTP") -> "Progress to the next scheduled intensity workout."
        summary.completionPercent >= 90 -> "Repeat the same target structure if late intervals felt unstable."
        else -> "Reduce the next hard block by 3-5% and prioritize full completion."
    }
    val recovery = when {
        summary.tss >= 90 -> "High stress: plan an easy spin or rest day before the next hard ride."
        summary.tss >= 50 -> "Moderate stress: keep the next 24 hours aerobic."
        else -> "Low stress: normal training can continue if legs feel fresh."
    }
    val summaryNote = when {
        trainerAnalytics != null && trainerAnalytics.ergComplianceScore < 80 -> "Trainer control was the main execution limiter."
        (cadenceConsistencyScore ?: 0) >= 85 -> "Cadence control was a strength in this ride."
        else -> "The ride is complete; pacing and cadence stability are the next focus."
    }
    return CoachNotes(
        summary = summaryNote,
        recommendation = recommendation,
        recovery = recovery,
        nextWorkout = if (summary.completionPercent >= 95) "Next planned workout" else "Repeat or easier aerobic session",
    )
}

private fun comparisonInsights(
    summary: WorkoutSession,
    history: List<RideHistoryItem>,
): List<ComparisonInsight> {
    val previous = history
        .filterNot { it.id == summary.id }
        .sortedWith(compareBy<RideHistoryItem> { it.date }.thenBy { it.id })
        .lastOrNull()
    val comparisons = mutableListOf<ComparisonInsight>()
    previous?.let {
        val powerDelta = summary.averagePowerWatts - it.averagePowerWatts
        comparisons += ComparisonInsight(
            title = "Previous ride",
            value = signed(powerDelta, " W"),
            detail = "Average power vs ${it.workoutName}",
        )
        val completionDelta = summary.completionPercent - it.completionPercent
        comparisons += ComparisonInsight(
            title = "Completion trend",
            value = signed(completionDelta, "%"),
            detail = "Compared to the previous saved workout",
        )
    }
    val recent = history.takeLast(30).filterNot { it.id == summary.id }
    if (recent.isNotEmpty()) {
        val recentAveragePower = recent.map { it.averagePowerWatts }.average().roundToInt()
        comparisons += ComparisonInsight(
            title = "Last 30 saved rides",
            value = signed(summary.averagePowerWatts - recentAveragePower, " W"),
            detail = "Average power vs recent history",
        )
    }
    if (comparisons.isEmpty()) {
        comparisons += ComparisonInsight(
            title = "Comparison baseline",
            value = "Pending",
            detail = "More saved rides are needed for verified trends",
        )
    }
    return comparisons.take(3)
}

private fun signed(value: Int, suffix: String): String {
    return when {
        value > 0 -> "+$value$suffix"
        value < 0 -> "$value$suffix"
        else -> "0$suffix"
    }
}

private data class ZoneSpec(
    val label: String,
    val rangeLabel: String,
    val matches: (Int) -> Boolean,
)
