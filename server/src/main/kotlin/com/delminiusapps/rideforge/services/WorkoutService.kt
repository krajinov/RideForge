package com.delminiusapps.rideforge.services

import com.delminiusapps.rideforge.dto.PageResponse
import com.delminiusapps.rideforge.dto.WorkoutIntervalResponse
import com.delminiusapps.rideforge.dto.toResponse
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.repositories.UserRepository
import com.delminiusapps.rideforge.repositories.WorkoutRepository
import com.delminiusapps.rideforge.utils.notFound

class WorkoutService(
    private val workouts: WorkoutRepository,
    private val users: UserRepository,
) {
    suspend fun list(userId: String, limit: Int, offset: Int): PageResponse<Workout> {
        val user = users.findById(userId) ?: notFound("User")
        val items = workouts.list(limit, offset).map { workout ->
            val intervals = workouts.intervalsForWorkout(workout.id).map { interval ->
                // Ensure targetPowerWatts is populated based on user FTP if missing
                if (interval.targetPowerWatts == null && interval.targetFtpPercent != null) {
                    interval.copy(targetPowerWatts = (user.ftp * interval.targetFtpPercent) / 100)
                } else {
                    interval
                }
            }
            workout.copy(intervals = intervals)
        }
        return PageResponse(items, workouts.count(), limit, offset)
    }

    suspend fun get(userId: String, id: String): Workout {
        val user = users.findById(userId) ?: notFound("User")
        val workout = workouts.findById(id) ?: notFound("Workout")
        val intervals = workouts.intervalsForWorkout(workout.id).map { interval ->
            if (interval.targetPowerWatts == null && interval.targetFtpPercent != null) {
                interval.copy(targetPowerWatts = (user.ftp * interval.targetFtpPercent) / 100)
            } else {
                interval
            }
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
        val intervals = workouts.intervalsForWorkout(workout.id).map { interval ->
            if (interval.targetPowerWatts == null && interval.targetFtpPercent != null) {
                interval.copy(targetPowerWatts = (user.ftp * interval.targetFtpPercent) / 100)
            } else {
                interval
            }
        }
        return workout.copy(intervals = intervals)
    }

    suspend fun intervals(userId: String, workoutId: String): List<WorkoutIntervalResponse> {
        val user = users.findById(userId) ?: notFound("User")
        get(userId, workoutId)
        return workouts.intervalsForWorkout(workoutId).map { it.toResponse(user.ftp) }
    }
}
