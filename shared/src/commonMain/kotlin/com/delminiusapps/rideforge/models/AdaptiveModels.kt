package com.delminiusapps.rideforge.models

data class FatigueState(
    val ctl: Double,
    val atl: Double,
    val tsb: Double
)

data class FtpEstimate(
    val id: String,
    val estimatedFtp: Int,
    val previousFtp: Int,
    val message: String,
    val createdAt: String
)

data class AdaptiveRecommendation(
    val type: String,
    val workoutId: String?,
    val title: String,
    val description: String,
    val reason: String
)

data class AdaptiveDashboard(
    val fatigue: FatigueState,
    val progressionLevels: Map<String, Double>,
    val pendingFtpEstimate: FtpEstimate?,
    val recommendation: AdaptiveRecommendation,
    val insights: List<String>
)

data class DailyFatigue(
    val date: String,
    val ctl: Double,
    val atl: Double,
    val tsb: Double,
    val tss: Int
)

data class FtpHistoryRecord(
    val id: String,
    val estimatedFtp: Int,
    val previousFtp: Int,
    val status: String,
    val message: String,
    val createdAt: String
)

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
