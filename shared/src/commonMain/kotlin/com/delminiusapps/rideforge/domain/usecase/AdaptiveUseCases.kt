package com.delminiusapps.rideforge.domain.usecase

import com.delminiusapps.rideforge.domain.repository.AdaptiveRepository
import com.delminiusapps.rideforge.domain.repository.AuthRepository
import com.delminiusapps.rideforge.models.AdaptiveDashboard
import com.delminiusapps.rideforge.models.DailyFatigue
import com.delminiusapps.rideforge.models.FtpHistoryRecord
import com.delminiusapps.rideforge.models.WorkoutAnalysis

class GetAdaptiveDashboardUseCase(private val repository: AdaptiveRepository) {
    suspend operator fun invoke(): AdaptiveDashboard = repository.getDashboard()
}

class GetAdaptiveTrendsUseCase(private val repository: AdaptiveRepository) {
    suspend operator fun invoke(): Pair<List<DailyFatigue>, List<FtpHistoryRecord>> = repository.getTrends()
}

class ApproveFtpEstimateUseCase(
    private val adaptiveRepository: AdaptiveRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(id: String) {
        val newFtp = adaptiveRepository.approveFtpEstimate(id)
        val profile = authRepository.currentUser()
        authRepository.updateProfile(
            ftpWatts = newFtp,
            weightKg = profile.weightKg,
            units = profile.units
        )
    }
}

class DismissFtpEstimateUseCase(private val repository: AdaptiveRepository) {
    suspend operator fun invoke(id: String) {
        repository.dismissFtpEstimate(id)
    }
}

class GetSessionAnalysisUseCase(private val repository: AdaptiveRepository) {
    suspend operator fun invoke(sessionId: String): WorkoutAnalysis = repository.getSessionAnalysis(sessionId)
}
