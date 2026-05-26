package com.delminiusapps.rideforge.database

import com.delminiusapps.rideforge.models.WorkoutAnalysis
import com.delminiusapps.rideforge.models.FtpHistoryRecord
import com.delminiusapps.rideforge.models.ProgressionLevel
import com.delminiusapps.rideforge.models.WorkoutType
import com.delminiusapps.rideforge.models.FtpEstimateDetail
import com.delminiusapps.rideforge.models.FatigueSnapshot
import com.delminiusapps.rideforge.models.AdaptiveRecommendation
import com.delminiusapps.rideforge.models.CoachInsight
import com.delminiusapps.rideforge.repositories.AdaptiveTrainingRepository
import java.sql.ResultSet

class PostgresAdaptiveTrainingRepository(private val database: PostgresDatabase) : AdaptiveTrainingRepository {

    override suspend fun saveAnalysis(analysis: WorkoutAnalysis): WorkoutAnalysis = database.query { connection ->
        connection.prepareStatement(
            """
            INSERT INTO workout_analyses (
                session_id, completion_percent, interval_success_rate, erg_compliance_score,
                cadence_consistency_score, power_fade, hr_drift, estimated_rpe, classification,
                coach_notes_summary, coach_notes_recommendation, coach_notes_recovery, coach_notes_next_workout,
                avg_deviation_power, best_5s_power, best_30s_power, best_1m_power, best_5m_power, best_20m_power
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                coach_notes_next_workout = EXCLUDED.coach_notes_next_workout,
                avg_deviation_power = EXCLUDED.avg_deviation_power,
                best_5s_power = EXCLUDED.best_5s_power,
                best_30s_power = EXCLUDED.best_30s_power,
                best_1m_power = EXCLUDED.best_1m_power,
                best_5m_power = EXCLUDED.best_5m_power,
                best_20m_power = EXCLUDED.best_20m_power
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
            if (analysis.avgDeviationPower != null) statement.setDouble(14, analysis.avgDeviationPower) else statement.setNull(14, java.sql.Types.DOUBLE)
            if (analysis.best5sPower != null) statement.setInt(15, analysis.best5sPower) else statement.setNull(15, java.sql.Types.INTEGER)
            if (analysis.best30sPower != null) statement.setInt(16, analysis.best30sPower) else statement.setNull(16, java.sql.Types.INTEGER)
            if (analysis.best1mPower != null) statement.setInt(17, analysis.best1mPower) else statement.setNull(17, java.sql.Types.INTEGER)
            if (analysis.best5mPower != null) statement.setInt(18, analysis.best5mPower) else statement.setNull(18, java.sql.Types.INTEGER)
            if (analysis.best20mPower != null) statement.setInt(19, analysis.best20mPower) else statement.setNull(19, java.sql.Types.INTEGER)
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

    override suspend fun saveFtpEstimate(estimate: FtpEstimateDetail): FtpEstimateDetail = database.query { connection ->
        connection.prepareStatement(
            """
            INSERT INTO ftp_estimates (
                id, user_id, current_ftp, estimated_ftp, confidence_score, recommendation, status, message, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                message = EXCLUDED.message
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, estimate.id)
            statement.setString(2, estimate.userId)
            statement.setInt(3, estimate.currentFtp)
            statement.setInt(4, estimate.estimatedFtp)
            statement.setInt(5, estimate.confidenceScore)
            statement.setString(6, estimate.recommendation)
            statement.setString(7, estimate.status)
            statement.setString(8, estimate.message)
            statement.setString(9, estimate.createdAt)
            statement.executeUpdate()
        }
        estimate
    }

    override suspend fun findPendingFtpEstimate(userId: String): FtpEstimateDetail? = database.query { connection ->
        connection.prepareStatement(
            "SELECT * FROM ftp_estimates WHERE user_id = ? AND status = 'pending_approval' ORDER BY created_at DESC LIMIT 1"
        ).use { statement ->
            statement.setString(1, userId)
            statement.executeQuery().use { results ->
                if (results.next()) results.toFtpEstimateDetail() else null
            }
        }
    }

    override suspend fun findFtpEstimateById(id: String): FtpEstimateDetail? = database.query { connection ->
        connection.prepareStatement("SELECT * FROM ftp_estimates WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { results ->
                if (results.next()) results.toFtpEstimateDetail() else null
            }
        }
    }

    override suspend fun updateFtpEstimate(estimate: FtpEstimateDetail): FtpEstimateDetail = database.query { connection ->
        connection.prepareStatement(
            """
            UPDATE ftp_estimates
            SET status = ?, message = ?
            WHERE id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, estimate.status)
            statement.setString(2, estimate.message)
            statement.setString(3, estimate.id)
            statement.executeUpdate()
        }
        estimate
    }

    override suspend fun getFtpEstimates(userId: String): List<FtpEstimateDetail> = database.query { connection ->
        connection.prepareStatement(
            "SELECT * FROM ftp_estimates WHERE user_id = ? ORDER BY created_at ASC"
        ).use { statement ->
            statement.setString(1, userId)
            statement.executeQuery().use { results ->
                val list = mutableListOf<FtpEstimateDetail>()
                while (results.next()) {
                    list.add(results.toFtpEstimateDetail())
                }
                list
            }
        }
    }

    override suspend fun saveFatigueSnapshot(snapshot: FatigueSnapshot): FatigueSnapshot = database.query { connection ->
        connection.prepareStatement(
            """
            INSERT INTO fatigue_snapshots (
                id, user_id, date, ctl, atl, tsb, freshness_status, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, date) DO UPDATE SET
                ctl = EXCLUDED.ctl,
                atl = EXCLUDED.atl,
                tsb = EXCLUDED.tsb,
                freshness_status = EXCLUDED.freshness_status,
                created_at = EXCLUDED.created_at
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, snapshot.id)
            statement.setString(2, snapshot.userId)
            statement.setString(3, snapshot.date)
            statement.setDouble(4, snapshot.ctl)
            statement.setDouble(5, snapshot.atl)
            statement.setDouble(6, snapshot.tsb)
            statement.setString(7, snapshot.freshnessStatus)
            statement.setString(8, snapshot.createdAt)
            statement.executeUpdate()
        }
        snapshot
    }

    override suspend fun getLatestFatigueSnapshot(userId: String): FatigueSnapshot? = database.query { connection ->
        connection.prepareStatement(
            "SELECT * FROM fatigue_snapshots WHERE user_id = ? ORDER BY date DESC LIMIT 1"
        ).use { statement ->
            statement.setString(1, userId)
            statement.executeQuery().use { results ->
                if (results.next()) results.toFatigueSnapshot() else null
            }
        }
    }

    override suspend fun getFatigueHistory(userId: String): List<FatigueSnapshot> = database.query { connection ->
        connection.prepareStatement(
            "SELECT * FROM fatigue_snapshots WHERE user_id = ? ORDER BY date ASC"
        ).use { statement ->
            statement.setString(1, userId)
            statement.executeQuery().use { results ->
                val list = mutableListOf<FatigueSnapshot>()
                while (results.next()) {
                    list.add(results.toFatigueSnapshot())
                }
                list
            }
        }
    }

    override suspend fun saveRecommendation(recommendation: AdaptiveRecommendation): AdaptiveRecommendation = database.query { connection ->
        connection.prepareStatement(
            """
            INSERT INTO adaptive_recommendations (
                id, user_id, type, workout_id, title, description, reason, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                type = EXCLUDED.type,
                workout_id = EXCLUDED.workout_id,
                title = EXCLUDED.title,
                description = EXCLUDED.description,
                reason = EXCLUDED.reason,
                created_at = EXCLUDED.created_at
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, recommendation.id)
            statement.setString(2, recommendation.userId)
            statement.setString(3, recommendation.type)
            if (recommendation.workoutId != null) statement.setString(4, recommendation.workoutId) else statement.setNull(4, java.sql.Types.VARCHAR)
            statement.setString(5, recommendation.title)
            statement.setString(6, recommendation.description)
            statement.setString(7, recommendation.reason)
            statement.setString(8, recommendation.createdAt)
            statement.executeUpdate()
        }
        recommendation
    }

    override suspend fun getLatestRecommendation(userId: String): AdaptiveRecommendation? = database.query { connection ->
        connection.prepareStatement(
            "SELECT * FROM adaptive_recommendations WHERE user_id = ? ORDER BY created_at DESC LIMIT 1"
        ).use { statement ->
            statement.setString(1, userId)
            statement.executeQuery().use { results ->
                if (results.next()) results.toAdaptiveRecommendation() else null
            }
        }
    }

    override suspend fun saveCoachInsights(insights: List<CoachInsight>) = database.query { connection ->
        if (insights.isEmpty()) return@query
        connection.prepareStatement(
            """
            INSERT INTO coach_insights (
                id, user_id, title, message, severity, source_metric, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            for (insight in insights) {
                statement.setString(1, insight.id)
                statement.setString(2, insight.userId)
                statement.setString(3, insight.title)
                statement.setString(4, insight.message)
                statement.setString(5, insight.severity)
                statement.setString(6, insight.sourceMetric)
                statement.setString(7, insight.createdAt)
                statement.addBatch()
            }
            statement.executeBatch()
        }
        Unit
    }

    override suspend fun getRecentCoachInsights(userId: String, limit: Int): List<CoachInsight> = database.query { connection ->
        connection.prepareStatement(
            "SELECT * FROM coach_insights WHERE user_id = ? ORDER BY created_at DESC LIMIT ?"
        ).use { statement ->
            statement.setString(1, userId)
            statement.setInt(2, limit)
            statement.executeQuery().use { results ->
                val list = mutableListOf<CoachInsight>()
                while (results.next()) {
                    list.add(results.toCoachInsight())
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
        coachNotesNextWorkout = getString("coach_notes_next_workout"),
        avgDeviationPower = getDouble("avg_deviation_power").let { if (wasNull()) null else it },
        best5sPower = getInt("best_5s_power").let { if (wasNull()) null else it },
        best30sPower = getInt("best_30s_power").let { if (wasNull()) null else it },
        best1mPower = getInt("best_1m_power").let { if (wasNull()) null else it },
        best5mPower = getInt("best_5m_power").let { if (wasNull()) null else it },
        best20mPower = getInt("best_20m_power").let { if (wasNull()) null else it }
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

    private fun ResultSet.toFtpEstimateDetail(): FtpEstimateDetail = FtpEstimateDetail(
        id = getString("id"),
        userId = getString("user_id"),
        currentFtp = getInt("current_ftp"),
        estimatedFtp = getInt("estimated_ftp"),
        confidenceScore = getInt("confidence_score"),
        recommendation = getString("recommendation"),
        status = getString("status"),
        message = getString("message"),
        createdAt = getString("created_at")
    )

    private fun ResultSet.toFatigueSnapshot(): FatigueSnapshot = FatigueSnapshot(
        id = getString("id"),
        userId = getString("user_id"),
        date = getString("date"),
        ctl = getDouble("ctl"),
        atl = getDouble("atl"),
        tsb = getDouble("tsb"),
        freshnessStatus = getString("freshness_status"),
        createdAt = getString("created_at")
    )

    private fun ResultSet.toAdaptiveRecommendation(): AdaptiveRecommendation = AdaptiveRecommendation(
        id = getString("id"),
        userId = getString("user_id"),
        type = getString("type"),
        workoutId = getString("workout_id"),
        title = getString("title"),
        description = getString("description"),
        reason = getString("reason"),
        createdAt = getString("created_at")
    )

    private fun ResultSet.toCoachInsight(): CoachInsight = CoachInsight(
        id = getString("id"),
        userId = getString("user_id"),
        title = getString("title"),
        message = getString("message"),
        severity = getString("severity"),
        sourceMetric = getString("source_metric"),
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
