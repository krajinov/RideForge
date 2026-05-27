package com.delminiusapps.rideforge.data.repository.remote

import com.delminiusapps.rideforge.core.network.ApiClient
import com.delminiusapps.rideforge.core.network.DataSourceMonitor
import com.delminiusapps.rideforge.data.dto.*
import com.delminiusapps.rideforge.domain.repository.AdaptiveRepository
import com.delminiusapps.rideforge.models.*

class RemoteAdaptiveRepository(
    private val api: ApiClient,
    private val fallback: AdaptiveRepository,
    private val monitor: DataSourceMonitor,
) : AdaptiveRepository {

    override suspend fun getDashboard(): AdaptiveDashboard = remoteOrFallback(
        monitor = monitor,
        fallback = { fallback.getDashboard() }
    ) {
        api.get<AdaptiveDashboardDto>("/adaptive/dashboard").toDomain()
    }

    override suspend fun getTrends(): Pair<List<DailyFatigue>, List<FtpHistoryRecord>> = remoteOrFallback(
        monitor = monitor,
        fallback = { fallback.getTrends() }
    ) {
        val trends = api.get<TrendsDto>("/adaptive/trends")
        Pair(
            trends.fatigueHistory.map { it.toDomain() },
            trends.ftpHistory.map { it.toDomain() }
        )
    }

    override suspend fun approveFtpEstimate(id: String): Int = remoteOrFallback(
        monitor = monitor,
        fallback = { fallback.approveFtpEstimate(id) }
    ) {
        val response = api.post<Unit, ApproveFtpResponseDto>("/adaptive/ftp-estimate/$id/approve", Unit)
        response.updatedFtp
    }

    override suspend fun dismissFtpEstimate(id: String): Unit = remoteOrFallback(
        monitor = monitor,
        fallback = { fallback.dismissFtpEstimate(id) }
    ) {
        api.post<Unit, Unit>("/adaptive/ftp-estimate/$id/dismiss", Unit)
    }

    override suspend fun getSessionAnalysis(sessionId: String): WorkoutAnalysis = remoteOrFallback(
        monitor = monitor,
        fallback = { fallback.getSessionAnalysis(sessionId) }
    ) {
        api.get<WorkoutAnalysisDto>("/adaptive/sessions/$sessionId/analysis").toDomain()
    }
}

private fun AdaptiveDashboardDto.toDomain(): AdaptiveDashboard = AdaptiveDashboard(
    fatigue = FatigueState(fatigue.ctl, fatigue.atl, fatigue.tsb),
    progressionLevels = progressionLevels,
    pendingFtpEstimate = pendingFtpEstimate?.let { 
        FtpEstimate(it.id, it.estimatedFtp, it.previousFtp, it.message, it.createdAt)
    },
    recommendation = AdaptiveRecommendation(
        recommendation.type,
        recommendation.workoutId,
        recommendation.title,
        recommendation.description,
        recommendation.reason
    ),
    insights = insights
)

private fun DailyFatigueDto.toDomain(): DailyFatigue = DailyFatigue(
    date = date,
    ctl = ctl,
    atl = atl,
    tsb = tsb,
    tss = tss
)

private fun FtpHistoryRecordDto.toDomain(): FtpHistoryRecord = FtpHistoryRecord(
    id = id,
    estimatedFtp = estimatedFtp,
    previousFtp = previousFtp,
    status = status,
    message = message,
    createdAt = createdAt
)

private fun WorkoutAnalysisDto.toDomain(): WorkoutAnalysis = WorkoutAnalysis(
    sessionId = sessionId,
    completionPercent = completionPercent,
    intervalSuccessRate = intervalSuccessRate,
    ergComplianceScore = ergComplianceScore,
    cadenceConsistencyScore = cadenceConsistencyScore,
    powerFade = powerFade,
    hrDrift = hrDrift,
    estimatedRpe = estimatedRpe,
    classification = classification,
    coachNotesSummary = coachNotesSummary,
    coachNotesRecommendation = coachNotesRecommendation,
    coachNotesRecovery = coachNotesRecovery,
    coachNotesNextWorkout = coachNotesNextWorkout,
    avgDeviationPower = avgDeviationPower,
    best5sPower = best5sPower,
    best30sPower = best30sPower,
    best1mPower = best1mPower,
    best5mPower = best5mPower,
    best20mPower = best20mPower
)
