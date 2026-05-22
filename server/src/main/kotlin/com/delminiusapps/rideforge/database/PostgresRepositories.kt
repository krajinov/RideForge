package com.delminiusapps.rideforge.database

import com.delminiusapps.rideforge.models.Device
import com.delminiusapps.rideforge.models.IntervalType
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.RefreshTokenRecord
import com.delminiusapps.rideforge.models.SessionStatus
import com.delminiusapps.rideforge.models.StravaConnection
import com.delminiusapps.rideforge.models.StravaSync
import com.delminiusapps.rideforge.models.StravaSyncStatus
import com.delminiusapps.rideforge.models.TrainingPlan
import com.delminiusapps.rideforge.models.User
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutInterval
import com.delminiusapps.rideforge.models.WorkoutSession
import com.delminiusapps.rideforge.models.WorkoutType
import com.delminiusapps.rideforge.repositories.DeviceRepository
import com.delminiusapps.rideforge.repositories.RefreshTokenRepository
import com.delminiusapps.rideforge.repositories.SessionRepository
import com.delminiusapps.rideforge.repositories.StravaConnectionRepository
import com.delminiusapps.rideforge.repositories.StravaSyncRepository
import com.delminiusapps.rideforge.repositories.TrainingPlanRepository
import com.delminiusapps.rideforge.repositories.UserRepository
import com.delminiusapps.rideforge.repositories.WorkoutRepository
import com.delminiusapps.rideforge.utils.nowIso
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

class PostgresUserRepository(private val database: PostgresDatabase) : UserRepository {
    override suspend fun create(user: User): User = database.query { connection ->
        connection.prepareStatement(
            """
            INSERT INTO users (
                id, email, password_hash, name, ftp, weight_kg, units, created_at, enrolled_plan_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.bindUser(user)
            statement.executeUpdate()
        }
        user
    }

    override suspend fun findById(id: String): User? = database.query { connection ->
        connection.prepareStatement("SELECT * FROM users WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { results -> results.singleOrNull { it.toUser() } }
        }
    }

    override suspend fun findByEmail(email: String): User? = database.query { connection ->
        connection.prepareStatement("SELECT * FROM users WHERE LOWER(email) = LOWER(?)").use { statement ->
            statement.setString(1, email.trim())
            statement.executeQuery().use { results -> results.singleOrNull { it.toUser() } }
        }
    }

    override suspend fun update(user: User): User = database.query { connection ->
        connection.prepareStatement(
            """
            UPDATE users
            SET email = ?, password_hash = ?, name = ?, ftp = ?, weight_kg = ?, units = ?,
                created_at = ?, enrolled_plan_id = ?
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, user.email)
            statement.setString(2, user.passwordHash)
            statement.setString(3, user.name)
            statement.setInt(4, user.ftp)
            statement.setDouble(5, user.weightKg)
            statement.setString(6, user.units)
            statement.setString(7, user.createdAt)
            statement.setNullableString(8, user.enrolledPlanId)
            statement.setString(9, user.id)
            statement.executeUpdate()
        }
        user
    }
}

class PostgresTrainingPlanRepository(private val database: PostgresDatabase) : TrainingPlanRepository {
    override suspend fun list(limit: Int, offset: Int): List<TrainingPlan> = database.query { connection ->
        connection.prepareStatement("SELECT * FROM training_plans ORDER BY id LIMIT ? OFFSET ?").use { statement ->
            statement.setInt(1, limit)
            statement.setInt(2, offset)
            statement.executeQuery().use { results -> results.toList { it.toTrainingPlan() } }
        }
    }

    override suspend fun count(): Int = database.query { connection ->
        connection.prepareStatement("SELECT COUNT(*) FROM training_plans").use { statement ->
            statement.executeQuery().use { results ->
                results.next()
                results.getInt(1)
            }
        }
    }

    override suspend fun findById(id: String): TrainingPlan? = database.query { connection ->
        connection.prepareStatement("SELECT * FROM training_plans WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { results -> results.singleOrNull { it.toTrainingPlan() } }
        }
    }
}

class PostgresWorkoutRepository(private val database: PostgresDatabase) : WorkoutRepository {
    override suspend fun list(limit: Int, offset: Int): List<Workout> = database.query { connection ->
        connection.prepareStatement(
            """
            SELECT * FROM workouts
            ORDER BY plan_id, week_number, day_number, id
            LIMIT ? OFFSET ?
            """.trimIndent(),
        ).use { statement ->
            statement.setInt(1, limit)
            statement.setInt(2, offset)
            statement.executeQuery().use { results -> results.toList { it.toWorkout() } }
        }
    }

    override suspend fun count(): Int = database.query { connection ->
        connection.prepareStatement("SELECT COUNT(*) FROM workouts").use { statement ->
            statement.executeQuery().use { results ->
                results.next()
                results.getInt(1)
            }
        }
    }

    override suspend fun findById(id: String): Workout? = database.query { connection ->
        connection.prepareStatement("SELECT * FROM workouts WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { results -> results.singleOrNull { it.toWorkout() } }
        }
    }

    override suspend fun findByPlanId(planId: String): List<Workout> = database.query { connection ->
        connection.prepareStatement(
            """
            SELECT * FROM workouts
            WHERE plan_id = ?
            ORDER BY week_number, day_number, id
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, planId)
            statement.executeQuery().use { results -> results.toList { it.toWorkout() } }
        }
    }

    override suspend fun intervalsForWorkout(workoutId: String): List<WorkoutInterval> = database.query { connection ->
        connection.prepareStatement(
            """
            SELECT * FROM workout_intervals
            WHERE workout_id = ?
            ORDER BY id
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, workoutId)
            statement.executeQuery().use { results -> results.toList { it.toWorkoutInterval() } }
        }
    }
}

class PostgresSessionRepository(private val database: PostgresDatabase) : SessionRepository {
    override suspend fun create(session: WorkoutSession): WorkoutSession = database.query { connection ->
        connection.prepareStatement(
            """
            INSERT INTO workout_sessions (
                id, user_id, workout_id, status, started_at, completed_at, elapsed_seconds,
                average_power, normalized_power, calories, tss, completion_percent, has_real_trainer_data
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.bindSession(session)
            statement.executeUpdate()
        }
        session
    }

    override suspend fun findById(id: String): WorkoutSession? = database.query { connection ->
        connection.prepareStatement("SELECT * FROM workout_sessions WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { results -> results.singleOrNull { it.toWorkoutSession() } }
        }
    }

    override suspend fun update(session: WorkoutSession): WorkoutSession = database.query { connection ->
        connection.prepareStatement(
            """
            UPDATE workout_sessions
            SET user_id = ?, workout_id = ?, status = ?, started_at = ?, completed_at = ?,
                elapsed_seconds = ?, average_power = ?, normalized_power = ?, calories = ?,
                tss = ?, completion_percent = ?, has_real_trainer_data = ?
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, session.userId)
            statement.setString(2, session.workoutId)
            statement.setString(3, session.status.name)
            statement.setString(4, session.startedAt)
            statement.setNullableString(5, session.completedAt)
            statement.setInt(6, session.elapsedSeconds)
            statement.setNullableInt(7, session.averagePower)
            statement.setNullableInt(8, session.normalizedPower)
            statement.setNullableInt(9, session.calories)
            statement.setNullableInt(10, session.tss)
            statement.setNullableInt(11, session.completionPercent)
            statement.setBoolean(12, session.hasRealTrainerData)
            statement.setString(13, session.id)
            statement.executeUpdate()
        }
        session
    }

    override suspend fun addMetric(sample: MetricSample): MetricSample = database.query { connection ->
        connection.prepareStatement(
            """
            INSERT INTO metric_samples (
                session_id, timestamp, elapsed_seconds, current_power, target_power, cadence, heart_rate, speed_kmh
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, sample.sessionId)
            statement.setString(2, sample.timestamp)
            statement.setNullableInt(3, sample.elapsedSeconds)
            statement.setInt(4, sample.currentPower)
            statement.setInt(5, sample.targetPower)
            statement.setInt(6, sample.cadence)
            statement.setInt(7, sample.heartRate)
            statement.setDouble(8, sample.speedKmh)
            statement.executeUpdate()
        }
        sample
    }

    override suspend fun metricsForSession(sessionId: String): List<MetricSample> = database.query { connection ->
        connection.prepareStatement(
            """
            SELECT * FROM metric_samples
            WHERE session_id = ?
            ORDER BY id
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, sessionId)
            statement.executeQuery().use { results -> results.toList { it.toMetricSample() } }
        }
    }

    override suspend fun historyForUser(userId: String, limit: Int, offset: Int): List<WorkoutSession> =
        database.query { connection ->
            connection.prepareStatement(
                """
                SELECT * FROM workout_sessions
                WHERE user_id = ? AND status = ?
                ORDER BY COALESCE(completed_at, started_at) DESC, id DESC
                LIMIT ? OFFSET ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, userId)
                statement.setString(2, SessionStatus.completed.name)
                statement.setInt(3, limit)
                statement.setInt(4, offset)
                statement.executeQuery().use { results -> results.toList { it.toWorkoutSession() } }
            }
        }

    override suspend fun historyCount(userId: String): Int = database.query { connection ->
        connection.prepareStatement(
            "SELECT COUNT(*) FROM workout_sessions WHERE user_id = ? AND status = ?",
        ).use { statement ->
            statement.setString(1, userId)
            statement.setString(2, SessionStatus.completed.name)
            statement.executeQuery().use { results ->
                results.next()
                results.getInt(1)
            }
        }
    }

    override suspend fun deleteHistory(userId: String, sessionId: String): Boolean = database.query { connection ->
        connection.prepareStatement(
            "DELETE FROM workout_sessions WHERE id = ? AND user_id = ? AND status = ?",
        ).use { statement ->
            statement.setString(1, sessionId)
            statement.setString(2, userId)
            statement.setString(3, SessionStatus.completed.name)
            statement.executeUpdate() > 0
        }
    }
}

class PostgresDeviceRepository(private val database: PostgresDatabase) : DeviceRepository {
    override suspend fun listAvailable(userId: String): List<Device> = database.query { connection ->
        connection.prepareStatement(
            """
            SELECT * FROM devices
            WHERE user_id = ? OR connection_status = 'available'
            ORDER BY id
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, userId)
            statement.executeQuery().use { results -> results.toList { it.toDevice() } }
        }
    }

    override suspend fun current(userId: String): Device? = database.query { connection ->
        connection.currentDevice(userId)
    }

    override suspend fun connect(device: Device): Device = database.transaction { connection ->
        connection.prepareStatement(
            "UPDATE devices SET connection_status = 'available' WHERE user_id = ? AND connection_status = 'connected'",
        ).use { statement ->
            statement.setString(1, device.userId)
            statement.executeUpdate()
        }
        connection.upsertDevice(device)
        device
    }

    override suspend fun disconnect(userId: String): Device? = database.transaction { connection ->
        val current = connection.currentDevice(userId) ?: return@transaction null
        val updated = current.copy(connectionStatus = "disconnected")
        connection.upsertDevice(updated)
        updated
    }
}

class PostgresRefreshTokenRepository(private val database: PostgresDatabase) : RefreshTokenRepository {
    override suspend fun save(record: RefreshTokenRecord): RefreshTokenRecord = database.query { connection ->
        connection.prepareStatement(
            """
            INSERT INTO refresh_tokens (token_hash, user_id, created_at, revoked_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (token_hash) DO UPDATE
            SET user_id = EXCLUDED.user_id,
                created_at = EXCLUDED.created_at,
                revoked_at = EXCLUDED.revoked_at
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, record.tokenHash)
            statement.setString(2, record.userId)
            statement.setString(3, record.createdAt)
            statement.setNullableString(4, record.revokedAt)
            statement.executeUpdate()
        }
        record
    }

    override suspend fun findByHash(tokenHash: String): RefreshTokenRecord? = database.query { connection ->
        connection.prepareStatement("SELECT * FROM refresh_tokens WHERE token_hash = ?").use { statement ->
            statement.setString(1, tokenHash)
            statement.executeQuery().use { results -> results.singleOrNull { it.toRefreshTokenRecord() } }
        }
    }

    override suspend fun revoke(tokenHash: String) {
        database.query { connection ->
            connection.prepareStatement("UPDATE refresh_tokens SET revoked_at = ? WHERE token_hash = ?").use { statement ->
                statement.setString(1, nowIso())
                statement.setString(2, tokenHash)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun revokeIfActive(tokenHash: String): Boolean = database.query { connection ->
        connection.prepareStatement(
            "UPDATE refresh_tokens SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL",
        ).use { statement ->
            statement.setString(1, nowIso())
            statement.setString(2, tokenHash)
            statement.executeUpdate() > 0
        }
    }

    override suspend fun revokeAllForUser(userId: String) {
        database.query { connection ->
            connection.prepareStatement(
                "UPDATE refresh_tokens SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL",
            ).use { statement ->
                statement.setString(1, nowIso())
                statement.setString(2, userId)
                statement.executeUpdate()
            }
        }
    }
}

class PostgresStravaConnectionRepository(private val database: PostgresDatabase) : StravaConnectionRepository {
    override suspend fun save(connection: StravaConnection): StravaConnection = database.query { db ->
        db.prepareStatement(
            """
            INSERT INTO strava_connections (
                user_id, athlete_id, access_token, refresh_token, expires_at_epoch_seconds,
                scope, connected_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id) DO UPDATE
            SET athlete_id = EXCLUDED.athlete_id,
                access_token = EXCLUDED.access_token,
                refresh_token = EXCLUDED.refresh_token,
                expires_at_epoch_seconds = EXCLUDED.expires_at_epoch_seconds,
                scope = EXCLUDED.scope,
                connected_at = EXCLUDED.connected_at,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, connection.userId)
            statement.setNullableString(2, connection.athleteId)
            statement.setString(3, connection.accessToken)
            statement.setString(4, connection.refreshToken)
            statement.setLong(5, connection.expiresAtEpochSeconds)
            statement.setString(6, connection.scope)
            statement.setString(7, connection.connectedAt)
            statement.setString(8, connection.updatedAt)
            statement.executeUpdate()
        }
        connection
    }

    override suspend fun findByUserId(userId: String): StravaConnection? = database.query { db ->
        db.prepareStatement("SELECT * FROM strava_connections WHERE user_id = ?").use { statement ->
            statement.setString(1, userId)
            statement.executeQuery().use { results -> results.singleOrNull { it.toStravaConnection() } }
        }
    }

    override suspend fun deleteByUserId(userId: String) {
        database.query { db ->
            db.prepareStatement("DELETE FROM strava_connections WHERE user_id = ?").use { statement ->
                statement.setString(1, userId)
                statement.executeUpdate()
            }
        }
    }
}

class PostgresStravaSyncRepository(private val database: PostgresDatabase) : StravaSyncRepository {
    override suspend fun upsert(sync: StravaSync): StravaSync = database.query { db ->
        db.prepareStatement(
            """
            INSERT INTO strava_syncs (
                session_id, user_id, status, athlete_id, upload_id, activity_id, activity_url,
                error, synced_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (session_id) DO UPDATE
            SET user_id = EXCLUDED.user_id,
                status = EXCLUDED.status,
                athlete_id = EXCLUDED.athlete_id,
                upload_id = EXCLUDED.upload_id,
                activity_id = EXCLUDED.activity_id,
                activity_url = EXCLUDED.activity_url,
                error = EXCLUDED.error,
                synced_at = EXCLUDED.synced_at,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, sync.sessionId)
            statement.setString(2, sync.userId)
            statement.setString(3, sync.status.name)
            statement.setNullableString(4, sync.athleteId)
            statement.setNullableString(5, sync.uploadId)
            statement.setNullableString(6, sync.activityId)
            statement.setNullableString(7, sync.activityUrl)
            statement.setNullableString(8, sync.error)
            statement.setNullableString(9, sync.syncedAt)
            statement.setString(10, sync.updatedAt)
            statement.executeUpdate()
        }
        sync
    }

    override suspend fun tryStartSync(sync: StravaSync): Boolean = database.query { db ->
        db.prepareStatement(
            """
            INSERT INTO strava_syncs (
                session_id, user_id, status, athlete_id, upload_id, activity_id, activity_url,
                error, synced_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (session_id) DO UPDATE
            SET user_id = EXCLUDED.user_id,
                status = EXCLUDED.status,
                athlete_id = EXCLUDED.athlete_id,
                upload_id = EXCLUDED.upload_id,
                activity_id = EXCLUDED.activity_id,
                activity_url = EXCLUDED.activity_url,
                error = EXCLUDED.error,
                synced_at = EXCLUDED.synced_at,
                updated_at = EXCLUDED.updated_at
            WHERE strava_syncs.athlete_id IS DISTINCT FROM EXCLUDED.athlete_id
                OR strava_syncs.status NOT IN (?, ?)
            RETURNING session_id
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, sync.sessionId)
            statement.setString(2, sync.userId)
            statement.setString(3, sync.status.name)
            statement.setNullableString(4, sync.athleteId)
            statement.setNullableString(5, sync.uploadId)
            statement.setNullableString(6, sync.activityId)
            statement.setNullableString(7, sync.activityUrl)
            statement.setNullableString(8, sync.error)
            statement.setNullableString(9, sync.syncedAt)
            statement.setString(10, sync.updatedAt)
            statement.setString(11, StravaSyncStatus.syncing.name)
            statement.setString(12, StravaSyncStatus.synced.name)
            statement.executeQuery().use { results -> results.next() }
        }
    }

    override suspend fun findBySessionId(sessionId: String): StravaSync? = database.query { db ->
        db.prepareStatement("SELECT * FROM strava_syncs WHERE session_id = ?").use { statement ->
            statement.setString(1, sessionId)
            statement.executeQuery().use { results -> results.singleOrNull { it.toStravaSync() } }
        }
    }

    override suspend fun deleteBySessionId(sessionId: String) {
        database.query { db ->
            db.prepareStatement("DELETE FROM strava_syncs WHERE session_id = ?").use { statement ->
                statement.setString(1, sessionId)
                statement.executeUpdate()
            }
        }
    }
}

private val repositoryJson = Json { ignoreUnknownKeys = true }

private fun PreparedStatement.bindUser(user: User) {
    setString(1, user.id)
    setString(2, user.email)
    setString(3, user.passwordHash)
    setString(4, user.name)
    setInt(5, user.ftp)
    setDouble(6, user.weightKg)
    setString(7, user.units)
    setString(8, user.createdAt)
    setNullableString(9, user.enrolledPlanId)
}

private fun PreparedStatement.bindSession(session: WorkoutSession) {
    setString(1, session.id)
    setString(2, session.userId)
    setString(3, session.workoutId)
    setString(4, session.status.name)
    setString(5, session.startedAt)
    setNullableString(6, session.completedAt)
    setInt(7, session.elapsedSeconds)
    setNullableInt(8, session.averagePower)
    setNullableInt(9, session.normalizedPower)
    setNullableInt(10, session.calories)
    setNullableInt(11, session.tss)
    setNullableInt(12, session.completionPercent)
    setBoolean(13, session.hasRealTrainerData)
}

private fun Connection.currentDevice(userId: String): Device? =
    prepareStatement(
        """
        SELECT * FROM devices
        WHERE user_id = ? AND connection_status = 'connected'
        ORDER BY COALESCE(last_connected_at, '') DESC, id
        LIMIT 1
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, userId)
        statement.executeQuery().use { results -> results.singleOrNull { it.toDevice() } }
    }

private fun Connection.upsertDevice(device: Device) {
    prepareStatement(
        """
        INSERT INTO devices (
            id, user_id, name, type, connection_status, supports_erg, last_connected_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE
        SET user_id = EXCLUDED.user_id,
            name = EXCLUDED.name,
            type = EXCLUDED.type,
            connection_status = EXCLUDED.connection_status,
            supports_erg = EXCLUDED.supports_erg,
            last_connected_at = EXCLUDED.last_connected_at
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, device.id)
        statement.setString(2, device.userId)
        statement.setString(3, device.name)
        statement.setString(4, device.type)
        statement.setString(5, device.connectionStatus)
        statement.setBoolean(6, device.supportsErg)
        statement.setNullableString(7, device.lastConnectedAt)
        statement.executeUpdate()
    }
}

private fun ResultSet.toUser(): User = User(
    id = getString("id"),
    email = getString("email"),
    passwordHash = getString("password_hash"),
    name = getString("name"),
    ftp = getInt("ftp"),
    weightKg = getDouble("weight_kg"),
    units = getString("units"),
    createdAt = getString("created_at"),
    enrolledPlanId = getStringOrNull("enrolled_plan_id"),
)

private fun ResultSet.toTrainingPlan(): TrainingPlan = TrainingPlan(
    id = getString("id"),
    name = getString("name"),
    description = getString("description"),
    durationWeeks = getInt("duration_weeks"),
    difficulty = getString("difficulty"),
    workoutCount = getInt("workout_count"),
)

private fun ResultSet.toWorkout(): Workout = Workout(
    id = getString("id"),
    planId = getString("plan_id"),
    name = getString("name"),
    description = getString("description"),
    durationMinutes = getInt("duration_minutes"),
    difficulty = getString("difficulty"),
    targetZones = repositoryJson.decodeFromString(getString("target_zones")),
    weekNumber = getInt("week_number"),
    dayNumber = getInt("day_number"),
    workoutType = WorkoutType.valueOf(getString("workout_type")),
)

private fun ResultSet.toWorkoutInterval(): WorkoutInterval = WorkoutInterval(
    id = getString("id"),
    workoutId = getString("workout_id"),
    name = getString("name"),
    durationSeconds = getInt("duration_seconds"),
    targetPowerWatts = getIntOrNull("target_power_watts"),
    targetFtpPercent = getIntOrNull("target_ftp_percent"),
    type = IntervalType.valueOf(getString("type")),
)

private fun ResultSet.toWorkoutSession(): WorkoutSession = WorkoutSession(
    id = getString("id"),
    userId = getString("user_id"),
    workoutId = getString("workout_id"),
    status = SessionStatus.valueOf(getString("status")),
    startedAt = getString("started_at"),
    completedAt = getStringOrNull("completed_at"),
    elapsedSeconds = getInt("elapsed_seconds"),
    averagePower = getIntOrNull("average_power"),
    normalizedPower = getIntOrNull("normalized_power"),
    calories = getIntOrNull("calories"),
    tss = getIntOrNull("tss"),
    completionPercent = getIntOrNull("completion_percent"),
    hasRealTrainerData = getBoolean("has_real_trainer_data"),
)

private fun ResultSet.toMetricSample(): MetricSample = MetricSample(
    sessionId = getString("session_id"),
    timestamp = getString("timestamp"),
    elapsedSeconds = getIntOrNull("elapsed_seconds"),
    currentPower = getInt("current_power"),
    targetPower = getInt("target_power"),
    cadence = getInt("cadence"),
    heartRate = getInt("heart_rate"),
    speedKmh = getDouble("speed_kmh"),
)

private fun ResultSet.toDevice(): Device = Device(
    id = getString("id"),
    userId = getString("user_id"),
    name = getString("name"),
    type = getString("type"),
    connectionStatus = getString("connection_status"),
    supportsErg = getBoolean("supports_erg"),
    lastConnectedAt = getStringOrNull("last_connected_at"),
)

private fun ResultSet.toRefreshTokenRecord(): RefreshTokenRecord = RefreshTokenRecord(
    tokenHash = getString("token_hash"),
    userId = getString("user_id"),
    createdAt = getString("created_at"),
    revokedAt = getStringOrNull("revoked_at"),
)

private fun ResultSet.toStravaConnection(): StravaConnection = StravaConnection(
    userId = getString("user_id"),
    athleteId = getStringOrNull("athlete_id"),
    accessToken = getString("access_token"),
    refreshToken = getString("refresh_token"),
    expiresAtEpochSeconds = getLong("expires_at_epoch_seconds"),
    scope = getString("scope"),
    connectedAt = getString("connected_at"),
    updatedAt = getString("updated_at"),
)

private fun ResultSet.toStravaSync(): StravaSync = StravaSync(
    sessionId = getString("session_id"),
    userId = getString("user_id"),
    status = StravaSyncStatus.valueOf(getString("status")),
    athleteId = getStringOrNull("athlete_id"),
    uploadId = getStringOrNull("upload_id"),
    activityId = getStringOrNull("activity_id"),
    activityUrl = getStringOrNull("activity_url"),
    error = getStringOrNull("error"),
    syncedAt = getStringOrNull("synced_at"),
    updatedAt = getString("updated_at"),
)

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

private fun ResultSet.getStringOrNull(column: String): String? =
    getString(column).takeUnless { wasNull() }

private fun ResultSet.getIntOrNull(column: String): Int? {
    val value = getInt(column)
    return value.takeUnless { wasNull() }
}

private inline fun <T> ResultSet.toList(map: (ResultSet) -> T): List<T> {
    val items = mutableListOf<T>()
    while (next()) {
        items += map(this)
    }
    return items
}

private inline fun <T> ResultSet.singleOrNull(map: (ResultSet) -> T): T? =
    if (next()) map(this) else null

internal fun targetZonesJson(targetZones: List<String>): String = repositoryJson.encodeToString(targetZones)
