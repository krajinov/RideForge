package com.delminiusapps.rideforge.data.mock

import com.delminiusapps.rideforge.models.*
import kotlinx.datetime.LocalDate

object MockData {
    val powerZones = listOf(
        PowerZone("z1", "Z1 Recovery", "< 132 W", "#57D68D"),
        PowerZone("z2", "Z2 Endurance", "133-180 W", "#36C8FF"),
        PowerZone("z3", "Z3 Tempo", "181-216 W", "#FFD166"),
        PowerZone("z4", "Z4 Threshold", "217-252 W", "#FF8A3D"),
        PowerZone("z5", "Z5 VO2 Max", "253+ W", "#FF4D6D"),
    )

    val userProfile = UserProfile(
        name = "Marko",
        ftpWatts = 240,
        weightKg = 78.0,
        units = "Metric",
        connectedDevice = "Wahoo KICKR",
        subscription = "Free Plan",
        powerZones = powerZones,
    )

    val trainingPlans = listOf(
        TrainingPlan("plan-ftp-builder", "FTP Builder", 6, "Intermediate", 6, "Progressive threshold work to raise sustainable power."),
        TrainingPlan("plan-endurance-base", "Endurance Base", 8, "Beginner", 6, "Aerobic foundation rides with steady ERG targets."),
        TrainingPlan("plan-vo2-booster", "VO2 Max Booster", 4, "Hard", 6, "Short blocks of high-intensity work for top-end power."),
        TrainingPlan("plan-weight-loss", "Weight Loss Ride", 6, "Beginner", 6, "Consistent calorie-focused sessions with low complexity."),
        TrainingPlan("plan-race-prep", "Race Prep", 10, "Advanced", 6, "Race-specific intensity, over-unders, and fatigue resistance."),
    )

    val workouts = listOf(
        workout("ftp-w1d1", "plan-ftp-builder", 1, 1, WorkoutType.SWEET_SPOT, "FTP Builder W1D1 Sweet Spot Intro", 45, "Intermediate", "Two controlled sweet spot blocks to start raising sustainable power.", 90),
        workout("ftp-w1d2", "plan-ftp-builder", 1, 2, WorkoutType.ENDURANCE, "FTP Builder W1D2 Endurance Support", 60, "Beginner", "Steady aerobic support between quality FTP sessions.", 70),
        workout("ftp-w1d3", "plan-ftp-builder", 1, 3, WorkoutType.THRESHOLD, "FTP Builder W1D3 Threshold Starter", 55, "Intermediate", "Threshold efforts at FTP with calm recoveries.", 100),
        workout("ftp-w2d1", "plan-ftp-builder", 2, 1, WorkoutType.SWEET_SPOT, "FTP Builder W2D1 Sweet Spot 3x8", 50, "Intermediate", "More sweet spot volume in repeatable blocks.", 92),
        workout("ftp-w2d2", "plan-ftp-builder", 2, 2, WorkoutType.OVER_UNDER, "FTP Builder W2D2 Over Under Intro", 45, "Hard", "Alternating under-threshold and over-threshold pressure.", 102),
        workout("ftp-w2d3", "plan-ftp-builder", 2, 3, WorkoutType.THRESHOLD, "FTP Builder W2D3 Threshold 4x6", 60, "Hard", "Four firm threshold intervals with short recoveries.", 102),

        workout("base-w1d1", "plan-endurance-base", 1, 1, WorkoutType.ENDURANCE, "Endurance Base W1D1 Easy Endurance", 45, "Beginner", "Gentle aerobic work to start base building.", 65),
        workout("base-w1d2", "plan-endurance-base", 1, 2, WorkoutType.ENDURANCE, "Endurance Base W1D2 Cadence Endurance", 60, "Beginner", "Steady endurance with a smooth cadence focus.", 68),
        workout("base-w1d3", "plan-endurance-base", 1, 3, WorkoutType.ENDURANCE, "Endurance Base W1D3 Long Z2 Ride", 90, "Intermediate", "Long zone two work for aerobic durability.", 72),
        workout("base-w2d1", "plan-endurance-base", 2, 1, WorkoutType.RECOVERY, "Endurance Base W2D1 Recovery Spin", 30, "Beginner", "Easy recovery to absorb training load.", 52),
        workout("base-w2d2", "plan-endurance-base", 2, 2, WorkoutType.TEMPO, "Endurance Base W2D2 Tempo Endurance", 60, "Intermediate", "Endurance work with a tempo middle block.", 82),
        workout("base-w2d3", "plan-endurance-base", 2, 3, WorkoutType.ENDURANCE, "Endurance Base W2D3 Endurance Plus", 75, "Intermediate", "Aerobic work with a slightly stronger finish.", 75),

        workout("vo2-w1d1", "plan-vo2-booster", 1, 1, WorkoutType.VO2_MAX, "VO2 W1D1 VO2 Max Starter", 45, "Hard", "A controlled first dose of top-end aerobic work.", 110),
        workout("vo2-w1d2", "plan-vo2-booster", 1, 2, WorkoutType.RECOVERY, "VO2 W1D2 Recovery Spin", 30, "Beginner", "Easy spinning after the first VO2 session.", 50),
        workout("vo2-w1d3", "plan-vo2-booster", 1, 3, WorkoutType.VO2_MAX, "VO2 W1D3 5x3 VO2 Max", 55, "Hard", "Five three-minute VO2 max intervals with equal recovery.", 115),
        workout("vo2-w2d1", "plan-vo2-booster", 2, 1, WorkoutType.VO2_MAX, "VO2 W2D1 Anaerobic Openers", 45, "Hard", "Short hard openers for top-end power.", 125),
        workout("vo2-w2d2", "plan-vo2-booster", 2, 2, WorkoutType.ENDURANCE, "VO2 W2D2 Endurance Reset", 60, "Intermediate", "Steady endurance reset between hard days.", 70),
        workout("vo2-w2d3", "plan-vo2-booster", 2, 3, WorkoutType.VO2_MAX, "VO2 W2D3 VO2 Max Progression", 60, "Hard", "Longer VO2 progression with reduced recovery.", 115),

        workout("weight-w1d1", "plan-weight-loss", 1, 1, WorkoutType.ENDURANCE, "Weight Loss W1D1 Easy Burn", 45, "Beginner", "Low intensity aerobic volume for consistency.", 62),
        workout("weight-w1d2", "plan-weight-loss", 1, 2, WorkoutType.ENDURANCE, "Weight Loss W1D2 Steady Endurance", 60, "Beginner", "Steady aerobic work with simple ERG targets.", 65),
        workout("weight-w1d3", "plan-weight-loss", 1, 3, WorkoutType.TEMPO, "Weight Loss W1D3 Tempo Burn", 50, "Intermediate", "Tempo work to increase aerobic demand.", 80),
        workout("weight-w2d1", "plan-weight-loss", 2, 1, WorkoutType.RECOVERY, "Weight Loss W2D1 Recovery Spin", 30, "Beginner", "Very easy work between aerobic blocks.", 50),
        workout("weight-w2d2", "plan-weight-loss", 2, 2, WorkoutType.ENDURANCE, "Weight Loss W2D2 Fat Oxidation Ride", 80, "Intermediate", "Long steady endurance for aerobic volume.", 68),
        workout("weight-w2d3", "plan-weight-loss", 2, 3, WorkoutType.ENDURANCE, "Weight Loss W2D3 Longer Endurance", 90, "Intermediate", "Longer endurance work focused on consistency.", 70),

        workout("race-w1d1", "plan-race-prep", 1, 1, WorkoutType.THRESHOLD, "Race Prep W1D1 Race Openers", 45, "Intermediate", "Short race openers with threshold settling.", 105),
        workout("race-w1d2", "plan-race-prep", 1, 2, WorkoutType.OVER_UNDER, "Race Prep W1D2 Over Under Race Efforts", 60, "Hard", "Race-like over-unders with repeated surges.", 108),
        workout("race-w1d3", "plan-race-prep", 1, 3, WorkoutType.ENDURANCE, "Race Prep W1D3 Endurance Support", 75, "Intermediate", "Endurance support for race-specific intensity.", 72),
        workout("race-w2d1", "plan-race-prep", 2, 1, WorkoutType.VO2_MAX, "Race Prep W2D1 VO2 Race Surges", 50, "Hard", "Sharp VO2 surges to simulate attacks.", 125),
        workout("race-w2d2", "plan-race-prep", 2, 2, WorkoutType.THRESHOLD, "Race Prep W2D2 Threshold Endurance", 65, "Hard", "Long threshold work for fatigue resistance.", 100),
        workout("race-w2d3", "plan-race-prep", 2, 3, WorkoutType.RACE_SIMULATION, "Race Prep W2D3 Race Simulation", 60, "Hard", "Mixed intensity race simulation with attacks and chase work.", 110),
    )

    val vo2Workout = workouts.first { it.id == "vo2-w1d3" }

    val trainerDevices = listOf(
        SmartTrainerDevice("wahoo-kickr", "Wahoo KICKR", -41, supportsErg = true),
        SmartTrainerDevice("tacx-flux", "Tacx Flux", -56, supportsErg = true),
        SmartTrainerDevice("elite-suito", "Elite Suito", -63, supportsErg = true),
    )

    val history = listOf(
        RideHistoryItem("ride-1", "VO2 Max Intervals", LocalDate(2026, 5, 8), 45, 214, 96),
        RideHistoryItem("ride-2", "Endurance Base Ride", LocalDate(2026, 5, 6), 60, 168, 100),
        RideHistoryItem("ride-3", "FTP Builder Week 2", LocalDate(2026, 5, 4), 50, 195, 91),
        RideHistoryItem("ride-4", "Recovery Spin", LocalDate(2026, 5, 2), 35, 132, 100),
    )

    val historySummaries = listOf(
        WorkoutSession(
            workoutId = "vo2-w1d3",
            elapsedSeconds = 55 * 60,
            averagePowerWatts = 214,
            normalizedPowerWatts = 236,
            calories = 620,
            tss = 68,
            completionPercent = 96,
            id = "ride-1",
            workoutName = "VO2 W1D3 5x3 VO2 Max",
        ),
        WorkoutSession(
            workoutId = "base-w1d3",
            elapsedSeconds = 90 * 60,
            averagePowerWatts = 168,
            normalizedPowerWatts = 174,
            calories = 540,
            tss = 42,
            completionPercent = 100,
            id = "ride-2",
            workoutName = "Endurance Base W1D3 Long Z2 Ride",
        ),
        WorkoutSession(
            workoutId = "ftp-w2d3",
            elapsedSeconds = 60 * 60,
            averagePowerWatts = 195,
            normalizedPowerWatts = 211,
            calories = 590,
            tss = 57,
            completionPercent = 91,
            id = "ride-3",
            workoutName = "FTP Builder W2D3 Threshold 4x6",
        ),
        WorkoutSession(
            workoutId = "base-w2d1",
            elapsedSeconds = 30 * 60,
            averagePowerWatts = 132,
            normalizedPowerWatts = 138,
            calories = 310,
            tss = 21,
            completionPercent = 100,
            id = "ride-4",
            workoutName = "Endurance Base W2D1 Recovery Spin",
        ),
    ).flatMap { summary ->
        val serverId = when (summary.id) {
            "ride-1" -> "history-vo2"
            "ride-2" -> "history-endurance"
            "ride-3" -> "history-ftp"
            "ride-4" -> "history-recovery"
            else -> summary.id
        }
        listOf(summary, summary.copy(id = serverId))
    }.associateBy { it.id }

    val latestWorkoutSummary = historySummaries.getValue("ride-1")

    val weeklyProgress = WeeklyProgress(completedWorkouts = 3, plannedWorkouts = 5)

    private fun workout(
        id: String,
        planId: String,
        week: Int,
        day: Int,
        type: WorkoutType,
        name: String,
        durationMinutes: Int,
        difficulty: String,
        description: String,
        workPercent: Int,
    ): Workout {
        val warmupSeconds = if (durationMinutes <= 30) 5 * 60 else 10 * 60
        val cooldownSeconds = if (durationMinutes <= 30) 5 * 60 else 10 * 60
        val workSeconds = durationMinutes * 60 - warmupSeconds - cooldownSeconds
        val intervalSuffix = "W${week}D$day"
        return Workout(
            id = id,
            name = name,
            durationMinutes = durationMinutes,
            difficulty = difficulty,
            description = description,
            targetZones = zonesFor(type),
            intervals = listOf(
                WorkoutInterval("Warmup $intervalSuffix", warmupSeconds, 55, "Z2"),
                WorkoutInterval("${typeLabel(type)} $intervalSuffix", workSeconds, workPercent, zoneForPercent(workPercent)),
                WorkoutInterval("Cooldown $intervalSuffix", cooldownSeconds, 45, "Z1"),
            ),
            planId = planId,
            weekNumber = week,
            dayNumber = day,
            workoutType = type,
        )
    }

    private fun zonesFor(type: WorkoutType): List<String> = when (type) {
        WorkoutType.RECOVERY -> listOf("Z1")
        WorkoutType.ENDURANCE -> listOf("Z2")
        WorkoutType.TEMPO -> listOf("Z2", "Z3")
        WorkoutType.SWEET_SPOT -> listOf("Z3", "Z4")
        WorkoutType.THRESHOLD -> listOf("Z4")
        WorkoutType.VO2_MAX -> listOf("Z5")
        WorkoutType.OVER_UNDER -> listOf("Z4", "Z5")
        WorkoutType.RACE_SIMULATION -> listOf("Z3", "Z4", "Z5")
    }

    private fun typeLabel(type: WorkoutType): String = type.name
        .lowercase()
        .split("_")
        .joinToString(" ") { it.replaceFirstChar { char -> char.titlecase() } }

    private fun zoneForPercent(percent: Int): String = when {
        percent < 56 -> "Z1"
        percent <= 75 -> "Z2"
        percent <= 90 -> "Z3"
        percent <= 105 -> "Z4"
        else -> "Z5"
    }
}
