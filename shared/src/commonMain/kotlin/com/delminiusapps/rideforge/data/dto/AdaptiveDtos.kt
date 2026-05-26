package com.delminiusapps.rideforge.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class FatigueStateDto(
    val ctl: Double,
    val atl: Double,
    val tsb: Double
)

@Serializable
data class FtpEstimateDto(
    val id: String,
    val estimatedFtp: Int,
    val previousFtp: Int,
    val message: String,
    val createdAt: String
)

@Serializable
data class AdaptiveRecommendationDto(
    val type: String,
    val workoutId: String?,
    val title: String,
    val description: String,
    val reason: String
)

@Serializable
data class AdaptiveDashboardDto(
    val fatigue: FatigueStateDto,
    val progressionLevels: Map<String, Double>,
    val pendingFtpEstimate: FtpEstimateDto?,
    val recommendation: AdaptiveRecommendationDto,
    val insights: List<String>
)

@Serializable
data class DailyFatigueDto(
    val date: String,
    val ctl: Double,
    val atl: Double,
    val tsb: Double,
    val tss: Int
)

@Serializable
data class FtpHistoryRecordDto(
    val id: String,
    val estimatedFtp: Int,
    val previousFtp: Int,
    val status: String,
    val message: String,
    val createdAt: String
)

@Serializable
data class TrendsDto(
    val fatigueHistory: List<DailyFatigueDto>,
    val ftpHistory: List<FtpHistoryRecordDto>
)

@Serializable
data class WorkoutAnalysisDto(
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
data class ApproveFtpResponseDto(
    val updatedFtp: Int
)
