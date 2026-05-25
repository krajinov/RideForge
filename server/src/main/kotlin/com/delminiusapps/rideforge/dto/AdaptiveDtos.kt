package com.delminiusapps.rideforge.dto

import kotlinx.serialization.Serializable

@Serializable
data class FatigueStateResponse(
    val ctl: Double,
    val atl: Double,
    val tsb: Double
)

@Serializable
data class FtpEstimateResponse(
    val id: String,
    val estimatedFtp: Int,
    val previousFtp: Int,
    val message: String,
    val createdAt: String
)

@Serializable
data class AdaptiveRecommendationResponse(
    val type: String,
    val workoutId: String?,
    val title: String,
    val description: String,
    val reason: String
)

@Serializable
data class AdaptiveDashboardResponse(
    val fatigue: FatigueStateResponse,
    val progressionLevels: Map<String, Double>,
    val pendingFtpEstimate: FtpEstimateResponse?,
    val recommendation: AdaptiveRecommendationResponse,
    val insights: List<String>
)

@Serializable
data class DailyFatigueResponse(
    val date: String,
    val ctl: Double,
    val atl: Double,
    val tsb: Double,
    val tss: Int
)

@Serializable
data class FtpHistoryRecordResponse(
    val id: String,
    val estimatedFtp: Int,
    val previousFtp: Int,
    val status: String,
    val message: String,
    val createdAt: String
)

@Serializable
data class TrendsResponse(
    val fatigueHistory: List<DailyFatigueResponse>,
    val ftpHistory: List<FtpHistoryRecordResponse>
)

@Serializable
data class WorkoutAnalysisResponse(
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
data class ApproveFtpResponse(
    val updatedFtp: Int
)
