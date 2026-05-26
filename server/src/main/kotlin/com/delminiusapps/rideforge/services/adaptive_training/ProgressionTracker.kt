package com.delminiusapps.rideforge.services.adaptive_training

import com.delminiusapps.rideforge.models.ProgressionLevel
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutType
import com.delminiusapps.rideforge.repositories.AdaptiveTrainingRepository
import com.delminiusapps.rideforge.utils.newId
import com.delminiusapps.rideforge.utils.nowIso

class ProgressionTracker(private val repository: AdaptiveTrainingRepository) {

    suspend fun getProgressionLevel(userId: String, type: WorkoutType): Double {
        val levelRecord = repository.getProgressionLevel(userId, type)
        return levelRecord?.level ?: 1.0
    }

    suspend fun getAllProgressionLevels(userId: String): Map<WorkoutType, Double> {
        val levels = repository.getProgressionLevels(userId)
        val map = WorkoutType.values().associateWith { 1.0 }.toMutableMap()
        levels.forEach { levelRecord ->
            map[levelRecord.workoutType] = levelRecord.level
        }
        return map
    }

    suspend fun updateProgression(userId: String, workout: Workout, classification: String): Double {
        val type = workout.workoutType
        val currentLevel = getProgressionLevel(userId, type)
        val workoutLevel = getWorkoutProgressionLevel(workout)
        val scaling = getIntensityScalingFactor(userId, workout)
        val completedLevel = workoutLevel * scaling

        val newLevel = when (classification) {
            "Overperformed" -> maxOf(currentLevel, completedLevel) + 0.3
            "Successful" -> maxOf(currentLevel, completedLevel)
            "Struggled" -> (currentLevel - 0.2).coerceAtLeast(1.0)
            "Failed" -> (currentLevel - 0.5).coerceAtLeast(1.0)
            else -> currentLevel
        }.coerceIn(1.0, 10.0)

        // Save to repository
        val roundedLevel = (newLevel * 10).toInt() / 10.0
        val existing = repository.getProgressionLevel(userId, type)
        val toSave = ProgressionLevel(
            id = existing?.id ?: newId("pl"),
            userId = userId,
            workoutType = type,
            level = roundedLevel,
            updatedAt = nowIso()
        )
        repository.saveProgressionLevel(toSave)

        return roundedLevel
    }

    suspend fun getIntensityScalingFactor(userId: String, workout: Workout): Double {
        val type = workout.workoutType
        val userLevel = getProgressionLevel(userId, type)
        val workoutLevel = getWorkoutProgressionLevel(workout)

        return when {
            workoutLevel > userLevel -> {
                // Workout is too hard, scale down target power
                (userLevel / workoutLevel).coerceIn(0.90, 1.0)
            }
            workoutLevel < userLevel - 1.5 -> {
                // Workout is too easy, scale up target power slightly
                1.03
            }
            else -> 1.0
        }
    }

    companion object {
        fun getWorkoutProgressionLevel(workout: Workout): Double {
            return when (workout.id) {
                "ftp-w1d1" -> 2.5
                "ftp-w1d2" -> 2.0
                "ftp-w1d3" -> 3.0
                "ftp-w2d1" -> 3.5
                "ftp-w2d2" -> 3.2
                "ftp-w2d3" -> 4.2
                "base-w1d1" -> 1.5
                "base-w1d2" -> 2.2
                "base-w1d3" -> 3.5
                "base-w2d1" -> 1.0
                "base-w2d2" -> 2.5
                "base-w2d3" -> 2.8
                "vo2-w1d1" -> 2.0
                "vo2-w1d2" -> 1.0
                "vo2-w1d3" -> 3.5
                "vo2-w2d1" -> 3.8
                "vo2-w2d2" -> 2.2
                "vo2-w2d3" -> 4.8
                "weight-w1d1" -> 1.5
                "weight-w1d2" -> 2.2
                "weight-w1d3" -> 2.0
                "weight-w2d1" -> 1.0
                "weight-w2d2" -> 3.0
                "weight-w2d3" -> 3.5
                "race-w1d1" -> 2.8
                "race-w1d2" -> 3.5
                "race-w1d3" -> 2.8
                "race-w2d1" -> 3.0
                "race-w2d2" -> 4.5
                "race-w2d3" -> 4.0
                else -> {
                    val base = when (workout.difficulty.lowercase()) {
                        "beginner" -> 1.5
                        "intermediate" -> 3.0
                        "hard" -> 4.5
                        "advanced" -> 6.0
                        else -> 2.0
                    }
                    val durationModifier = (workout.durationMinutes - 45) * 0.05
                    (base + durationModifier).coerceIn(1.0, 10.0)
                }
            }
        }
    }
}
