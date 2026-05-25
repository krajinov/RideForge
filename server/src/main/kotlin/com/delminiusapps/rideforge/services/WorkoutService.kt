package com.delminiusapps.rideforge.services

import com.delminiusapps.rideforge.dto.PageResponse
import com.delminiusapps.rideforge.dto.WorkoutIntervalResponse
import com.delminiusapps.rideforge.dto.toResponse
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.repositories.UserRepository
import com.delminiusapps.rideforge.repositories.WorkoutRepository
import com.delminiusapps.rideforge.services.adaptive_training.ProgressionTracker
import com.delminiusapps.rideforge.utils.notFound
import kotlin.math.roundToInt

class WorkoutService(
    private val workouts: WorkoutRepository,
    private val users: UserRepository,
    private val progressionTracker: ProgressionTracker,
) {
    suspend fun list(userId: String, limit: Int, offset: Int): PageResponse<Workout> {
        val user = users.findById(userId) ?: notFound("User")
        val items = workouts.list(limit, offset).map { workout ->
            val scaling = progressionTracker.getIntensityScalingFactor(userId, workout)
            val intervals = workouts.intervalsForWorkout(workout.id).map { interval ->
                val targetPercent = interval.targetFtpPercent ?: 100
                val unscaledPower = interval.targetPowerWatts ?: ((user.ftp * targetPercent) / 100)
                interval.copy(
                    targetPowerWatts = (unscaledPower * scaling).roundToInt(),
                    targetFtpPercent = (targetPercent * scaling).roundToInt()
                )
            }
            workout.copy(intervals = intervals)
        }
        return PageResponse(items, workouts.count(), limit, offset)
    }

    suspend fun get(userId: String, id: String): Workout {
        val user = users.findById(userId) ?: notFound("User")
        val workout = workouts.findById(id) ?: notFound("Workout")
        val scaling = progressionTracker.getIntensityScalingFactor(userId, workout)
        val intervals = workouts.intervalsForWorkout(workout.id).map { interval ->
            val targetPercent = interval.targetFtpPercent ?: 100
            val unscaledPower = interval.targetPowerWatts ?: ((user.ftp * targetPercent) / 100)
            interval.copy(
                targetPowerWatts = (unscaledPower * scaling).roundToInt(),
                targetFtpPercent = (targetPercent * scaling).roundToInt()
            )
        }
        return workout.copy(intervals = intervals)
    }

    suspend fun recommended(userId: String): Workout {
        val user = users.findById(userId) ?: notFound("User")
        val planWorkout = user.enrolledPlanId
            ?.let { planId ->
                workouts.findByPlanId(planId)
                    .sortedWith(compareBy<Workout> { it.weekNumber }.thenBy { it.dayNumber })
                    .firstOrNull()
            }
        val workout = planWorkout ?: workouts.list(limit = 1, offset = 0).firstOrNull() ?: notFound("Workout")
        val scaling = progressionTracker.getIntensityScalingFactor(userId, workout)
        val intervals = workouts.intervalsForWorkout(workout.id).map { interval ->
            val targetPercent = interval.targetFtpPercent ?: 100
            val unscaledPower = interval.targetPowerWatts ?: ((user.ftp * targetPercent) / 100)
            interval.copy(
                targetPowerWatts = (unscaledPower * scaling).roundToInt(),
                targetFtpPercent = (targetPercent * scaling).roundToInt()
            )
        }
        return workout.copy(intervals = intervals)
    }

    suspend fun intervals(userId: String, workoutId: String): List<WorkoutIntervalResponse> {
        val user = users.findById(userId) ?: notFound("User")
        val workout = get(userId, workoutId)
        // Since get(...) already returns scaled intervals, we can map those
        return workout.intervals.map { it.toResponse(user.ftp) }
    }
}
