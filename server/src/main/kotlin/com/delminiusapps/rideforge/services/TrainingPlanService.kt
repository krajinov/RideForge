package com.delminiusapps.rideforge.services

import com.delminiusapps.rideforge.dto.EnrollResponse
import com.delminiusapps.rideforge.dto.PageResponse
import com.delminiusapps.rideforge.dto.toResponse
import com.delminiusapps.rideforge.models.TrainingPlan
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.repositories.TrainingPlanRepository
import com.delminiusapps.rideforge.repositories.UserRepository
import com.delminiusapps.rideforge.repositories.WorkoutRepository
import com.delminiusapps.rideforge.utils.notFound

class TrainingPlanService(
    private val plans: TrainingPlanRepository,
    private val workouts: WorkoutRepository,
    private val users: UserRepository,
) {
    suspend fun list(limit: Int, offset: Int): PageResponse<TrainingPlan> =
        PageResponse(plans.list(limit, offset), plans.count(), limit, offset)

    suspend fun get(id: String): TrainingPlan = plans.findById(id) ?: notFound("Training plan")

    suspend fun workoutsForPlan(userId: String, id: String): List<Workout> {
        get(id)
        val user = users.findById(userId) ?: notFound("User")
        return workouts.findByPlanId(id).sortedWith(
            compareBy<Workout> { it.weekNumber }
                .thenBy { it.dayNumber }
        ).map { workout ->
            workout.copy(
                intervals = workouts.intervalsForWorkout(workout.id).map { interval ->
                    if (interval.targetPowerWatts == null && interval.targetFtpPercent != null) {
                        interval.copy(targetPowerWatts = (user.ftp * interval.targetFtpPercent) / 100)
                    } else {
                        interval
                    }
                }
            )
        }
    }

    suspend fun enroll(userId: String, planId: String): EnrollResponse {
        val plan = get(planId)
        val user = users.findById(userId) ?: notFound("User")
        val updated = users.update(user.copy(enrolledPlanId = planId))
        return EnrollResponse(plan, updated.toResponse())
    }

    suspend fun myPlan(userId: String): TrainingPlan? {
        val user = users.findById(userId) ?: notFound("User")
        return user.enrolledPlanId?.let { plans.findById(it) }
    }
}
