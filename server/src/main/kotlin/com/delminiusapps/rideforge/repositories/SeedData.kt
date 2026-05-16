package com.delminiusapps.rideforge.repositories

import com.delminiusapps.rideforge.models.Device
import com.delminiusapps.rideforge.models.SessionStatus
import com.delminiusapps.rideforge.models.TrainingPlan
import com.delminiusapps.rideforge.models.User
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutInterval
import com.delminiusapps.rideforge.models.WorkoutSession

object SeedData {
    const val defaultUserId = "user-marko"
    const val defaultPlanId = "plan-vo2-booster"
    const val defaultWorkoutId = "vo2-w1d3"

    val users = listOf(
        User(
            id = defaultUserId,
            email = "marko@example.com",
            passwordHash = "mock:password",
            name = "Marko",
            ftp = 240,
            weightKg = 78.0,
            units = "metric",
            createdAt = "2026-05-01T08:00:00Z",
            enrolledPlanId = defaultPlanId,
        ),
    )

    val plans = listOf(
        TrainingPlan("plan-ftp-builder", "FTP Builder", "Progressive threshold work to raise sustainable power.", 6, "Intermediate", 6),
        TrainingPlan("plan-endurance-base", "Endurance Base", "Aerobic foundation rides with steady ERG targets.", 8, "Beginner", 6),
        TrainingPlan(defaultPlanId, "VO2 Max Booster", "Short blocks of high-intensity work for top-end power.", 4, "Hard", 6),
        TrainingPlan("plan-weight-loss", "Weight Loss Ride", "Consistent calorie-focused sessions with low complexity.", 6, "Beginner", 6),
        TrainingPlan("plan-race-prep", "Race Prep", "Race-specific intensity, over-unders, and fatigue resistance.", 10, "Advanced", 6),
    )

    val workouts: List<Workout>
    val intervals: List<WorkoutInterval>

    init {
        val stream = SeedData::class.java.getResourceAsStream("/seed/workouts.json")
        if (stream != null) {
            val (w, i) = SeedDataLoader().loadWorkouts(stream)
            workouts = w
            intervals = i
            validateWorkoutPlanAssignments()
        } else {
            workouts = emptyList()
            intervals = emptyList()
        }
    }

    private fun validateWorkoutPlanAssignments() {
        val planIds = plans.map { it.id }.toSet()
        workouts.forEach { workout ->
            require(workout.planId in planIds) {
                "Workout ${workout.id} references unknown planId ${workout.planId}."
            }
        }
        plans.forEach { plan ->
            val actualCount = workouts.count { it.planId == plan.id }
            require(actualCount == plan.workoutCount) {
                "Plan ${plan.id} declares ${plan.workoutCount} workouts but has $actualCount seed workouts."
            }
        }
    }

    val sessions = listOf(
        WorkoutSession("history-vo2", defaultUserId, defaultWorkoutId, SessionStatus.completed, "2026-05-08T07:00:00Z", "2026-05-08T07:55:00Z", 55 * 60, 214, 236, 620, 68, 96),
        WorkoutSession("history-endurance", defaultUserId, "base-w1d3", SessionStatus.completed, "2026-05-06T07:00:00Z", "2026-05-06T08:30:00Z", 90 * 60, 168, 174, 540, 42, 100),
        WorkoutSession("history-ftp", defaultUserId, "ftp-w2d3", SessionStatus.completed, "2026-05-04T07:00:00Z", "2026-05-04T08:00:00Z", 60 * 60, 195, 211, 590, 57, 91),
        WorkoutSession("history-recovery", defaultUserId, "base-w2d1", SessionStatus.completed, "2026-05-02T07:00:00Z", "2026-05-02T07:30:00Z", 30 * 60, 132, 138, 310, 21, 100),
    )

    val devices = listOf(
        Device("device-wahoo-kickr", defaultUserId, "Wahoo KICKR", "smart_trainer", "connected", true, "2026-05-12T08:30:00Z"),
        Device("device-tacx-flux", defaultUserId, "Tacx Flux", "smart_trainer", "available", true, null),
        Device("device-elite-suito", defaultUserId, "Elite Suito", "smart_trainer", "available", true, null),
    )
}
