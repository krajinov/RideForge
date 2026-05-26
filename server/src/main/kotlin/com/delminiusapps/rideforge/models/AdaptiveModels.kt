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
    val coachNotesNextWorkout: String
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
data class ProgressionLevel(
    val id: String,
    val userId: String,
    val workoutType: WorkoutType,
    val level: Double,
    val updatedAt: String
)
