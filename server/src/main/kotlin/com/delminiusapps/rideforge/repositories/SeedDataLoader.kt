package com.delminiusapps.rideforge.repositories

import com.delminiusapps.rideforge.models.IntervalType
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutInterval
import com.delminiusapps.rideforge.models.WorkoutType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream

@Serializable
data class SeedWorkoutDto(
    val id: String,
    val planId: String,
    val name: String,
    val description: String,
    val durationMinutes: Int,
    val difficulty: String,
    val targetZones: List<String>,
    val intervals: List<SeedWorkoutIntervalDto>,
    val weekNumber: Int,
    val dayNumber: Int,
    val workoutType: WorkoutType,
)

@Serializable
data class SeedWorkoutIntervalDto(
    val name: String,
    val durationSeconds: Int,
    val targetFtpPercent: Int? = null,
    val targetPowerWatts: Int? = null,
    val type: IntervalType,
)

class SeedDataLoader(private val json: Json = Json { ignoreUnknownKeys = true }) {

    fun loadWorkouts(inputStream: InputStream): Pair<List<Workout>, List<WorkoutInterval>> {
        val content = inputStream.bufferedReader().use { it.readText() }
        val seedWorkouts = try {
            json.decodeFromString<List<SeedWorkoutDto>>(content)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse workouts.json: ${e.message}", e)
        }

        val workouts = mutableListOf<Workout>()
        val intervals = mutableListOf<WorkoutInterval>()
        val workoutIds = mutableSetOf<String>()
        val intervalSignatures = mutableMapOf<String, String>()

        seedWorkouts.forEach { dto ->
            if (!workoutIds.add(dto.id)) {
                throw IllegalArgumentException("Duplicate workout id ${dto.id}.")
            }
            if (dto.planId.isBlank()) {
                throw IllegalArgumentException("Workout ${dto.id} must have a planId.")
            }
            if (dto.weekNumber < 1 || dto.dayNumber < 1) {
                throw IllegalArgumentException("Workout ${dto.id} must have positive weekNumber and dayNumber.")
            }
            if (dto.durationMinutes !in 30..90) {
                throw IllegalArgumentException("Workout ${dto.id} duration must be between 30 and 90 minutes.")
            }
            if (dto.intervals.isEmpty()) {
                throw IllegalArgumentException("Workout ${dto.id} must define at least one interval.")
            }
            if (dto.intervals.any { it.targetFtpPercent == null || it.targetPowerWatts != null }) {
                throw IllegalArgumentException("Workout ${dto.id} must use targetFtpPercent and must not use targetPowerWatts.")
            }

            val totalIntervalSeconds = dto.intervals.sumOf { it.durationSeconds }
            val expectedSeconds = dto.durationMinutes * 60
            if (totalIntervalSeconds != expectedSeconds) {
                throw IllegalArgumentException(
                    "Workout ${dto.id} duration mismatch: expected $expectedSeconds s (${dto.durationMinutes} min), " +
                    "but intervals sum to $totalIntervalSeconds s."
                )
            }

            val intervalSignature = dto.intervals.joinToString("|") {
                "${it.name}:${it.durationSeconds}:${it.targetFtpPercent}:${it.type}"
            }
            val duplicateWorkoutId = intervalSignatures.putIfAbsent(intervalSignature, dto.id)
            if (duplicateWorkoutId != null) {
                throw IllegalArgumentException("Workout ${dto.id} duplicates interval structure from $duplicateWorkoutId.")
            }

            workouts.add(
                Workout(
                    id = dto.id,
                    planId = dto.planId,
                    name = dto.name,
                    description = dto.description,
                    durationMinutes = dto.durationMinutes,
                    difficulty = dto.difficulty,
                    targetZones = dto.targetZones,
                    weekNumber = dto.weekNumber,
                    dayNumber = dto.dayNumber,
                    workoutType = dto.workoutType,
                )
            )

            dto.intervals.forEachIndexed { index, intervalDto ->
                intervals.add(
                    WorkoutInterval(
                        id = "${dto.id}-int-$index",
                        workoutId = dto.id,
                        name = intervalDto.name,
                        durationSeconds = intervalDto.durationSeconds,
                        targetPowerWatts = intervalDto.targetPowerWatts,
                        targetFtpPercent = intervalDto.targetFtpPercent,
                        type = intervalDto.type
                    )
                )
            }
        }

        return workouts to intervals
    }
}
