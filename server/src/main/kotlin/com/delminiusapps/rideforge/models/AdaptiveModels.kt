package com.delminiusapps.rideforge.models

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutAnalysis(
    val sessionId: String,
    val completionPercent: Int,
    val intervalSuccessRate: Int,
    val ergComplianceScore: Int?,
    val cadenceConsistencyScore: Int?,
    val powerFade: Double?,
    val hrDrift: Double?,
    val estimatedRpe: Double,
    val classification: String,
    val coachNotesSummary: String,
    val coachNotesRecommendation: String,
    val coachNotesRecovery: String,
    val coachNotesNextWorkout: String,
    val avgDeviationPower: Double? = null,
    val best5sPower: Int? = null,
    val best30sPower: Int? = null,
    val best1mPower: Int? = null,
    val best5mPower: Int? = null,
    val best20mPower: Int? = null
)

@Serializable
data class FtpHistoryRecord(
    val id: String,
    val userId: String,
    val estimatedFtp: Int,
    val previousFtp: Int,
    val sessionId: String,
    val status: String, // 'pending_approval', 'approved', 'dismissed', 'system_adjusted'
    val message: String,
    val createdAt: String
)

@Serializable
data class FtpEstimateDetail(
    val id: String,
    val userId: String,
    val currentFtp: Int,
    val estimatedFtp: Int,
    val confidenceScore: Int,
    val recommendation: String, // 'KEEP', 'INCREASE', 'DECREASE', 'TEST_REQUIRED'
    val status: String, // 'pending_approval', 'approved', 'dismissed'
    val message: String,
    val createdAt: String
)

@Serializable
data class FatigueSnapshot(
    val id: String,
    val userId: String,
    val date: String,
    val ctl: Double,
    val atl: Double,
    val tsb: Double,
    val freshnessStatus: String, // 'FRESH', 'BALANCED', 'FATIGUED', 'OVERREACHED'
    val createdAt: String
)

@Serializable
data class AdaptiveRecommendation(
    val id: String,
    val userId: String,
    val type: String, // 'RECOVERY', 'TRAINING', 'TEST'
    val workoutId: String?,
    val title: String,
    val description: String,
    val reason: String,
    val createdAt: String
)

@Serializable
data class CoachInsight(
    val id: String,
    val userId: String,
    val title: String,
    val message: String,
    val severity: String, // 'positive', 'neutral', 'warning'
    val sourceMetric: String,
    val createdAt: String
)

@Serializable
data class ProgressionLevel(
    val id: String,
    val userId: String,
    val workoutType: WorkoutType,
    val level: Double,
    val updatedAt: String
)
