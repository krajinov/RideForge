package com.delminiusapps.rideforge.database

import com.delminiusapps.rideforge.models.Device
import com.delminiusapps.rideforge.models.TrainingPlan
import com.delminiusapps.rideforge.models.User
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutInterval
import com.delminiusapps.rideforge.models.WorkoutSession
import com.delminiusapps.rideforge.repositories.SeedData
import java.sql.Connection
import java.sql.PreparedStatement

class DatabaseSeeder(private val database: PostgresDatabase) {
    suspend fun seed() {
        database.transaction { connection ->
            connection.seedTrainingPlans(SeedData.plans)
            connection.seedWorkouts(SeedData.workouts)
            connection.seedWorkoutIntervals(SeedData.intervals)
            connection.seedUsers(SeedData.users)
            connection.seedWorkoutSessions(SeedData.sessions)
            connection.seedDevices(SeedData.devices)
        }
    }
}

private fun Connection.seedTrainingPlans(plans: List<TrainingPlan>) {
    prepareStatement(
        """
        INSERT INTO training_plans (id, name, description, duration_weeks, difficulty, workout_count)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE
        SET name = EXCLUDED.name,
            description = EXCLUDED.description,
            duration_weeks = EXCLUDED.duration_weeks,
            difficulty = EXCLUDED.difficulty,
            workout_count = EXCLUDED.workout_count
        """.trimIndent(),
    ).use { statement ->
        plans.forEach { plan ->
            statement.setString(1, plan.id)
            statement.setString(2, plan.name)
            statement.setString(3, plan.description)
            statement.setInt(4, plan.durationWeeks)
            statement.setString(5, plan.difficulty)
            statement.setInt(6, plan.workoutCount)
            statement.addBatch()
        }
        statement.executeBatch()
    }
}

private fun Connection.seedWorkouts(workouts: List<Workout>) {
    prepareStatement(
        """
        INSERT INTO workouts (
            id, plan_id, name, description, duration_minutes, difficulty, target_zones,
            week_number, day_number, workout_type
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE
        SET plan_id = EXCLUDED.plan_id,
            name = EXCLUDED.name,
            description = EXCLUDED.description,
            duration_minutes = EXCLUDED.duration_minutes,
            difficulty = EXCLUDED.difficulty,
            target_zones = EXCLUDED.target_zones,
            week_number = EXCLUDED.week_number,
            day_number = EXCLUDED.day_number,
            workout_type = EXCLUDED.workout_type
        """.trimIndent(),
    ).use { statement ->
        workouts.forEach { workout ->
            statement.setString(1, workout.id)
            statement.setString(2, workout.planId)
            statement.setString(3, workout.name)
            statement.setString(4, workout.description)
            statement.setInt(5, workout.durationMinutes)
            statement.setString(6, workout.difficulty)
            statement.setString(7, targetZonesJson(workout.targetZones))
            statement.setInt(8, workout.weekNumber)
            statement.setInt(9, workout.dayNumber)
            statement.setString(10, workout.workoutType.name)
            statement.addBatch()
        }
        statement.executeBatch()
    }
}

private fun Connection.seedWorkoutIntervals(intervals: List<WorkoutInterval>) {
    prepareStatement(
        """
        INSERT INTO workout_intervals (
            id, workout_id, name, duration_seconds, target_power_watts, target_ftp_percent, type
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE
        SET workout_id = EXCLUDED.workout_id,
            name = EXCLUDED.name,
            duration_seconds = EXCLUDED.duration_seconds,
            target_power_watts = EXCLUDED.target_power_watts,
            target_ftp_percent = EXCLUDED.target_ftp_percent,
            type = EXCLUDED.type
        """.trimIndent(),
    ).use { statement ->
        intervals.forEach { interval ->
            statement.setString(1, interval.id)
            statement.setString(2, interval.workoutId)
            statement.setString(3, interval.name)
            statement.setInt(4, interval.durationSeconds)
            statement.setNullableInt(5, interval.targetPowerWatts)
            statement.setNullableInt(6, interval.targetFtpPercent)
            statement.setString(7, interval.type.name)
            statement.addBatch()
        }
        statement.executeBatch()
    }
}

private fun Connection.seedUsers(users: List<User>) {
    prepareStatement(
        """
        INSERT INTO users (
            id, email, password_hash, name, ftp, weight_kg, units, created_at, enrolled_plan_id
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """.trimIndent(),
    ).use { statement ->
        users.forEach { user ->
            statement.setString(1, user.id)
            statement.setString(2, user.email)
            statement.setString(3, user.passwordHash)
            statement.setString(4, user.name)
            statement.setInt(5, user.ftp)
            statement.setDouble(6, user.weightKg)
            statement.setString(7, user.units)
            statement.setString(8, user.createdAt)
            statement.setNullableString(9, user.enrolledPlanId)
            statement.addBatch()
        }
        statement.executeBatch()
    }
}

private fun Connection.seedWorkoutSessions(sessions: List<WorkoutSession>) {
    prepareStatement(
        """
        INSERT INTO workout_sessions (
            id, user_id, workout_id, status, started_at, completed_at, elapsed_seconds,
            average_power, normalized_power, calories, tss, completion_percent, has_real_trainer_data,
            average_speed_kmh, total_distance_km
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """.trimIndent(),
    ).use { statement ->
        sessions.forEach { session ->
            statement.setString(1, session.id)
            statement.setString(2, session.userId)
            statement.setString(3, session.workoutId)
            statement.setString(4, session.status.name)
            statement.setString(5, session.startedAt)
            statement.setNullableString(6, session.completedAt)
            statement.setInt(7, session.elapsedSeconds)
            statement.setNullableInt(8, session.averagePower)
            statement.setNullableInt(9, session.normalizedPower)
            statement.setNullableInt(10, session.calories)
            statement.setNullableInt(11, session.tss)
            statement.setNullableInt(12, session.completionPercent)
            statement.setBoolean(13, session.hasRealTrainerData)
            statement.setNullableDouble(14, session.averageSpeedKmh)
            statement.setNullableDouble(15, session.totalDistanceKm)
            statement.addBatch()
        }
        statement.executeBatch()
    }
}

private fun Connection.seedDevices(devices: List<Device>) {
    prepareStatement(
        """
        INSERT INTO devices (
            id, user_id, name, type, connection_status, supports_erg, last_connected_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """.trimIndent(),
    ).use { statement ->
        devices.forEach { device ->
            statement.setString(1, device.id)
            statement.setString(2, device.userId)
            statement.setString(3, device.name)
            statement.setString(4, device.type)
            statement.setString(5, device.connectionStatus)
            statement.setBoolean(6, device.supportsErg)
            statement.setNullableString(7, device.lastConnectedAt)
            statement.addBatch()
        }
        statement.executeBatch()
    }
}

private fun PreparedStatement.setNullableString(index: Int, value: String?) {
    if (value == null) {
        setNull(index, java.sql.Types.VARCHAR)
    } else {
        setString(index, value)
    }
}

private fun PreparedStatement.setNullableInt(index: Int, value: Int?) {
    if (value == null) {
        setNull(index, java.sql.Types.INTEGER)
    } else {
        setInt(index, value)
    }
}

private fun PreparedStatement.setNullableDouble(index: Int, value: Double?) {
    if (value == null) {
        setNull(index, java.sql.Types.DOUBLE)
    } else {
        setDouble(index, value)
    }
}
