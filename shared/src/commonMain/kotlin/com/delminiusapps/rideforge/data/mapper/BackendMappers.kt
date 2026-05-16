package com.delminiusapps.rideforge.data.mapper

import com.delminiusapps.rideforge.data.dto.TrainingPlanDto
import com.delminiusapps.rideforge.data.dto.UserDto
import com.delminiusapps.rideforge.data.dto.WorkoutDto
import com.delminiusapps.rideforge.data.dto.WorkoutIntervalDto
import com.delminiusapps.rideforge.data.dto.WorkoutSessionDto
import com.delminiusapps.rideforge.models.*
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

fun TrainingPlanDto.toDomain(): TrainingPlan = TrainingPlan(
    id = id,
    name = name,
    durationWeeks = durationWeeks,
    difficulty = difficulty,
    workoutCount = workoutCount,
    description = description,
)

fun WorkoutDto.toDomain(intervals: List<WorkoutInterval> = emptyList()): Workout = Workout(
    id = id,
    name = name,
    durationMinutes = durationMinutes,
    difficulty = difficulty,
    description = description,
    targetZones = targetZones,
    intervals = intervals.ifEmpty { this.intervals.map { it.toDomain() } },
    planId = planId,
    weekNumber = weekNumber ?: 1,
    dayNumber = dayNumber ?: 1,
    workoutType = workoutType?.let { runCatching { WorkoutType.valueOf(it) }.getOrNull() } ?: WorkoutType.ENDURANCE,
)

fun WorkoutIntervalDto.toDomain(): WorkoutInterval = WorkoutInterval(
    name = name,
    durationSeconds = durationSeconds,
    targetFtpPercent = targetFtpPercent ?: (targetPowerWatts / 2.5).toInt(), // fallback if missing
    zone = when {
        targetFtpPercent != null -> when {
            targetFtpPercent < 56 -> "Z1"
            targetFtpPercent <= 75 -> "Z2"
            targetFtpPercent <= 90 -> "Z3"
            targetFtpPercent <= 105 -> "Z4"
            else -> "Z5"
        }
        type == "work" || targetPowerWatts >= 250 -> "Z5"
        type == "warmup" -> "Z2"
        else -> "Z1"
    },
)

fun UserDto.toDomainProfile(connectedDevice: String = "Wahoo KICKR"): UserProfile = UserProfile(
    name = name,
    ftpWatts = ftp,
    weightKg = weightKg,
    units = units.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
    connectedDevice = connectedDevice,
    subscription = "Free Plan",
    powerZones = powerZonesForFtp(ftp),
)

fun WorkoutSessionDto.toHistoryItem(workoutName: String): RideHistoryItem = RideHistoryItem(
    id = id,
    workoutName = workoutName,
    date = parseDate(completedAt ?: startedAt),
    durationMinutes = (elapsedSeconds / 60).coerceAtLeast(1),
    averagePowerWatts = averagePower ?: 0,
    completionPercent = completionPercent ?: 0,
)

fun WorkoutSessionDto.toDomainSummary(workoutName: String): WorkoutSession = WorkoutSession(
    workoutId = workoutId,
    elapsedSeconds = elapsedSeconds,
    averagePowerWatts = averagePower ?: 0,
    normalizedPowerWatts = normalizedPower ?: averagePower ?: 0,
    calories = calories ?: 0,
    tss = tss ?: 0,
    completionPercent = completionPercent ?: 0,
    id = id,
    workoutName = workoutName,
    completedAtEpochMillis = parseEpochMillis(completedAt),
)

fun powerZonesForFtp(ftp: Int): List<PowerZone> = listOf(
    PowerZone("z1", "Z1 Recovery", "< ${(ftp * 0.55).toInt()} W", "#57D68D"),
    PowerZone("z2", "Z2 Endurance", "${(ftp * 0.56).toInt()}-${(ftp * 0.75).toInt()} W", "#36C8FF"),
    PowerZone("z3", "Z3 Tempo", "${(ftp * 0.76).toInt()}-${(ftp * 0.90).toInt()} W", "#FFD166"),
    PowerZone("z4", "Z4 Threshold", "${(ftp * 0.91).toInt()}-${(ftp * 1.05).toInt()} W", "#FF8A3D"),
    PowerZone("z5", "Z5 VO2 Max", "${(ftp * 1.06).toInt()}+ W", "#FF4D6D"),
)

private fun parseDate(value: String): LocalDate {
    val date = value.take(10)
    return runCatching { LocalDate.parse(date) }.getOrElse { LocalDate(2026, 5, 12) }
}

private fun parseEpochMillis(value: String?): Long? {
    return value?.let { timestamp ->
        runCatching { Instant.parse(timestamp).toEpochMilliseconds() }.getOrNull()
    }
}
