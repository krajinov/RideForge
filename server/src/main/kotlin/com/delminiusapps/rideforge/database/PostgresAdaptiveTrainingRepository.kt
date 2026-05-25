package com.delminiusapps.rideforge.database

import com.delminiusapps.rideforge.models.WorkoutAnalysis
import com.delminiusapps.rideforge.models.FtpHistoryRecord
import com.delminiusapps.rideforge.models.ProgressionLevel
import com.delminiusapps.rideforge.models.WorkoutType
import com.delminiusapps.rideforge.repositories.AdaptiveTrainingRepository
import java.sql.ResultSet

class PostgresAdaptiveTrainingRepository(private val database: PostgresDatabase) : AdaptiveTrainingRepository {

    override suspend fun saveAnalysis(analysis: WorkoutAnalysis): WorkoutAnalysis = database.query { connection ->
        connection.prepareStatement(
            """
            INSERT INTO workout_analyses (
                session_id, completion_percent, interval_success_rate, erg_compliance_score,
                cadence_consistency_score, power_fade, hr_drift, estimated_rpe, classification,
                coach_notes_summary, coach_notes_recommendation, coach_notes_recovery, coach_notes_next_workout
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (session_id) DO UPDATE SET
                completion_percent = EXCLUDED.completion_percent,
                interval_success_rate = EXCLUDED.interval_success_rate,
                erg_compliance_score = EXCLUDED.erg_compliance_score,
                cadence_consistency_score = EXCLUDED.cadence_consistency_score,
                power_fade = EXCLUDED.power_fade,
                hr_drift = EXCLUDED.hr_drift,
                estimated_rpe = EXCLUDED.estimated_rpe,
                classification = EXCLUDED.classification,
                coach_notes_summary = EXCLUDED.coach_notes_summary,
                coach_notes_recommendation = EXCLUDED.coach_notes_recommendation,
                coach_notes_recovery = EXCLUDED.coach_notes_recovery,
                coach_notes_next_workout = EXCLUDED.coach_notes_next_workout
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, analysis.sessionId)
            statement.setInt(2, analysis.completionPercent)
            statement.setInt(3, analysis.intervalSuccessRate)
            if (analysis.ergComplianceScore != null) statement.setInt(4, analysis.ergComplianceScore) else statement.setNull(4, java.sql.Types.INTEGER)
            if (analysis.cadenceConsistencyScore != null) statement.setInt(5, analysis.cadenceConsistencyScore) else statement.setNull(5, java.sql.Types.INTEGER)
            if (analysis.powerFade != null) statement.setDouble(6, analysis.powerFade) else statement.setNull(6, java.sql.Types.DOUBLE)
            if (analysis.hrDrift != null) statement.setDouble(7, analysis.hrDrift) else statement.setNull(7, java.sql.Types.DOUBLE)
            statement.setDouble(8, analysis.estimatedRpe)
            statement.setString(9, analysis.classification)
            statement.setString(10, analysis.coachNotesSummary)
            statement.setString(11, analysis.coachNotesRecommendation)
            statement.setString(12, analysis.coachNotesRecovery)
            statement.setString(13, analysis.coachNotesNextWorkout)
            statement.executeUpdate()
        }
        analysis
    }

    override suspend fun findAnalysisBySessionId(sessionId: String): WorkoutAnalysis? = database.query { connection ->
        connection.prepareStatement("SELECT * FROM workout_analyses WHERE session_id = ?").use { statement ->
            statement.setString(1, sessionId)
            statement.executeQuery().use { results ->
                if (results.next()) results.toWorkoutAnalysis() else null
            }
        }
    }

    override suspend fun saveFtpRecord(record: FtpHistoryRecord): FtpHistoryRecord = database.query { connection ->
        connection.prepareStatement(
            """
            INSERT INTO ftp_history (
                id, user_id, estimated_ftp, previous_ftp, session_id, status, message, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, record.id)
            statement.setString(2, record.userId)
            statement.setInt(3, record.estimatedFtp)
            statement.setInt(4, record.previousFtp)
            statement.setString(5, record.sessionId)
            statement.setString(6, record.status)
            statement.setString(7, record.message)
            statement.setString(8, record.createdAt)
            statement.executeUpdate()
        }
        record
    }

    override suspend fun findPendingFtpRecord(userId: String): FtpHistoryRecord? = database.query { connection ->
        connection.prepareStatement(
            "SELECT * FROM ftp_history WHERE user_id = ? AND status = 'pending_approval' ORDER BY created_at DESC LIMIT 1"
        ).use { statement ->
            statement.setString(1, userId)
            statement.executeQuery().use { results ->
                if (results.next()) results.toFtpHistoryRecord() else null
            }
        }
    }

    override suspend fun findFtpRecordById(id: String): FtpHistoryRecord? = database.query { connection ->
        connection.prepareStatement("SELECT * FROM ftp_history WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { results ->
                if (results.next()) results.toFtpHistoryRecord() else null
            }
        }
    }

    override suspend fun updateFtpRecord(record: FtpHistoryRecord): FtpHistoryRecord = database.query { connection ->
        connection.prepareStatement(
            """
            UPDATE ftp_history
            SET status = ?, message = ?
            WHERE id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, record.status)
            statement.setString(2, record.message)
            statement.setString(3, record.id)
            statement.executeUpdate()
        }
        record
    }

    override suspend fun getFtpHistory(userId: String): List<FtpHistoryRecord> = database.query { connection ->
        connection.prepareStatement(
            "SELECT * FROM ftp_history WHERE user_id = ? ORDER BY created_at ASC"
        ).use { statement ->
            statement.setString(1, userId)
            statement.executeQuery().use { results ->
                val list = mutableListOf<FtpHistoryRecord>()
                while (results.next()) {
                    list.add(results.toFtpHistoryRecord())
                }
                list
            }
        }
    }

    override suspend fun saveProgressionLevel(level: ProgressionLevel): ProgressionLevel = database.query { connection ->
        connection.prepareStatement(
            """
            INSERT INTO progression_levels (
                id, user_id, workout_type, level, updated_at
            ) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (user_id, workout_type) DO UPDATE SET
                level = EXCLUDED.level,
                updated_at = EXCLUDED.updated_at
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, level.id)
            statement.setString(2, level.userId)
            statement.setString(3, level.workoutType.name)
            statement.setDouble(4, level.level)
            statement.setString(5, level.updatedAt)
            statement.executeUpdate()
        }
        level
    }

    override suspend fun getProgressionLevels(userId: String): List<ProgressionLevel> = database.query { connection ->
        connection.prepareStatement(
            "SELECT * FROM progression_levels WHERE user_id = ?"
        ).use { statement ->
            statement.setString(1, userId)
            statement.executeQuery().use { results ->
                val list = mutableListOf<ProgressionLevel>()
                while (results.next()) {
                    list.add(results.toProgressionLevel())
                }
                list
            }
        }
    }

    override suspend fun getProgressionLevel(userId: String, workoutType: WorkoutType): ProgressionLevel? = database.query { connection ->
        connection.prepareStatement(
            "SELECT * FROM progression_levels WHERE user_id = ? AND workout_type = ?"
        ).use { statement ->
            statement.setString(1, userId)
            statement.setString(2, workoutType.name)
            statement.executeQuery().use { results ->
                if (results.next()) results.toProgressionLevel() else null
            }
        }
    }

    private fun ResultSet.toWorkoutAnalysis(): WorkoutAnalysis = WorkoutAnalysis(
        sessionId = getString("session_id"),
        completionPercent = getInt("completion_percent"),
        intervalSuccessRate = getInt("interval_success_rate"),
        ergComplianceScore = getInt("erg_compliance_score").let { if (wasNull()) null else it },
        cadenceConsistencyScore = getInt("cadence_consistency_score").let { if (wasNull()) null else it },
        powerFade = getDouble("power_fade").let { if (wasNull()) null else it },
        hrDrift = getDouble("hr_drift").let { if (wasNull()) null else it },
        estimatedRpe = getDouble("estimated_rpe"),
        classification = getString("classification"),
        coachNotesSummary = getString("coach_notes_summary"),
        coachNotesRecommendation = getString("coach_notes_recommendation"),
        coachNotesRecovery = getString("coach_notes_recovery"),
        coachNotesNextWorkout = getString("coach_notes_next_workout")
    )

    private fun ResultSet.toFtpHistoryRecord(): FtpHistoryRecord = FtpHistoryRecord(
        id = getString("id"),
        userId = getString("user_id"),
        estimatedFtp = getInt("estimated_ftp"),
        previousFtp = getInt("previous_ftp"),
        sessionId = getString("session_id"),
        status = getString("status"),
        message = getString("message"),
        createdAt = getString("created_at")
    )

    private fun ResultSet.toProgressionLevel(): ProgressionLevel = ProgressionLevel(
        id = getString("id"),
        userId = getString("user_id"),
        workoutType = WorkoutType.valueOf(getString("workout_type")),
        level = getDouble("level"),
        updatedAt = getString("updated_at")
    )
}
