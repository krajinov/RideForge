package com.delminiusapps.rideforge.services

import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutSession
import java.time.Instant
import kotlin.math.max

class TcxWorkoutExporter {
    fun export(
        session: WorkoutSession,
        workout: Workout,
        metrics: List<MetricSample>,
    ): String {
        val start = runCatching { Instant.parse(session.startedAt) }.getOrDefault(Instant.now())
        val ordered = metrics.sortedWith(
            compareBy<MetricSample> { it.elapsedSeconds ?: Int.MAX_VALUE }.thenBy { it.timestamp },
        )
        val resolved = ordered.mapIndexed { index, sample ->
            val elapsed = sample.elapsedSeconds
                ?: runCatching { Instant.parse(sample.timestamp).epochSecond - start.epochSecond }
                    .getOrDefault(index.toLong())
                    .toInt()
                    .coerceAtLeast(0)
            ResolvedTrackpoint(sample, elapsed)
        }.sortedBy { it.elapsedSeconds }

        val elapsedSeconds = session.elapsedSeconds
            .takeIf { it > 0 }
            ?: resolved.maxOfOrNull { it.elapsedSeconds }
            ?: workout.durationMinutes * 60
        val distanceByElapsed = distanceMetersByElapsed(resolved)
        val totalDistance = distanceByElapsed.lastOrNull()?.second ?: 0.0
        val calories = session.calories ?: 0
        val avgHeartRate = resolved.map { it.sample.heartRate }.filter { it > 0 }.averageOrNull()
        val maxHeartRate = resolved.map { it.sample.heartRate }.filter { it > 0 }.maxOrNull()

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2" xmlns:ns3="http://www.garmin.com/xmlschemas/ActivityExtension/v2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">""")
            appendLine("  <Activities>")
            appendLine("""    <Activity Sport="Biking">""")
            appendLine("      <Id>${start}</Id>")
            appendLine("""      <Lap StartTime="${start}">""")
            appendLine("        <TotalTimeSeconds>${elapsedSeconds}</TotalTimeSeconds>")
            appendLine("        <DistanceMeters>${oneDecimal(totalDistance)}</DistanceMeters>")
            appendLine("        <Calories>${calories}</Calories>")
            if (avgHeartRate != null) {
                appendLine("        <AverageHeartRateBpm><Value>${avgHeartRate.toInt()}</Value></AverageHeartRateBpm>")
            }
            if (maxHeartRate != null) {
                appendLine("        <MaximumHeartRateBpm><Value>${maxHeartRate}</Value></MaximumHeartRateBpm>")
            }
            appendLine("        <Intensity>Active</Intensity>")
            appendLine("        <TriggerMethod>Manual</TriggerMethod>")
            appendLine("        <Track>")
            resolved.forEachIndexed { index, point ->
                val distance = distanceByElapsed.getOrNull(index)?.second ?: 0.0
                appendTrackpoint(start, point, distance)
            }
            appendLine("        </Track>")
            appendLine("      </Lap>")
            appendLine("      <Creator xsi:type=\"Device_t\">")
            appendLine("        <Name>RideForge</Name>")
            appendLine("        <UnitId>0</UnitId>")
            appendLine("        <ProductID>0</ProductID>")
            appendLine("      </Creator>")
            appendLine("    </Activity>")
            appendLine("  </Activities>")
            appendLine("  <Author xsi:type=\"Application_t\">")
            appendLine("    <Name>RideForge</Name>")
            appendLine("    <Build><Version><VersionMajor>1</VersionMajor><VersionMinor>0</VersionMinor></Version></Build>")
            appendLine("    <LangID>en</LangID>")
            appendLine("    <PartNumber>RideForge</PartNumber>")
            appendLine("  </Author>")
            appendLine("</TrainingCenterDatabase>")
        }
    }

    private fun StringBuilder.appendTrackpoint(
        start: Instant,
        point: ResolvedTrackpoint,
        distanceMeters: Double,
    ) {
        val sample = point.sample
        val speedMetersPerSecond = sample.speedKmh / 3.6
        appendLine("          <Trackpoint>")
        appendLine("            <Time>${start.plusSeconds(point.elapsedSeconds.toLong())}</Time>")
        appendLine("            <DistanceMeters>${oneDecimal(distanceMeters)}</DistanceMeters>")
        if (sample.heartRate > 0) {
            appendLine("            <HeartRateBpm><Value>${sample.heartRate}</Value></HeartRateBpm>")
        }
        if (sample.cadence > 0) {
            appendLine("            <Cadence>${sample.cadence}</Cadence>")
        }
        appendLine("            <Extensions>")
        appendLine("              <ns3:TPX>")
        if (speedMetersPerSecond > 0.0) {
            appendLine("                <ns3:Speed>${threeDecimals(speedMetersPerSecond)}</ns3:Speed>")
        }
        appendLine("                <ns3:Watts>${sample.currentPower.coerceAtLeast(0)}</ns3:Watts>")
        appendLine("              </ns3:TPX>")
        appendLine("            </Extensions>")
        appendLine("          </Trackpoint>")
    }

    private fun distanceMetersByElapsed(points: List<ResolvedTrackpoint>): List<Pair<Int, Double>> {
        if (points.isEmpty()) return emptyList()
        var distance = 0.0
        var previousElapsed = 0
        var previousSpeedKmh = points.first().sample.speedKmh
        return points.map { point ->
            val deltaSeconds = max(0, point.elapsedSeconds - previousElapsed)
            if (deltaSeconds > 0) {
                val speedKmh = previousSpeedKmh.takeIf { it > 0.0 } ?: point.sample.speedKmh
                distance += (speedKmh / 3.6) * deltaSeconds
            }
            previousElapsed = point.elapsedSeconds
            previousSpeedKmh = point.sample.speedKmh
            point.elapsedSeconds to distance
        }
    }

    private fun List<Int>.averageOrNull(): Double? =
        takeIf { it.isNotEmpty() }?.average()

    private fun oneDecimal(value: Double): String = "%.1f".format(java.util.Locale.US, value)

    private fun threeDecimals(value: Double): String = "%.3f".format(java.util.Locale.US, value)
}

private data class ResolvedTrackpoint(
    val sample: MetricSample,
    val elapsedSeconds: Int,
)
