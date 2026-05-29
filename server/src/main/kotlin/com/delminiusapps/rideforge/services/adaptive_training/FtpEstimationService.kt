package com.delminiusapps.rideforge.services.adaptive_training

import com.delminiusapps.rideforge.models.*
import com.delminiusapps.rideforge.repositories.AdaptiveTrainingRepository
import com.delminiusapps.rideforge.repositories.SessionRepository
import com.delminiusapps.rideforge.repositories.UserRepository
import com.delminiusapps.rideforge.repositories.WorkoutRepository
import com.delminiusapps.rideforge.utils.newId
import com.delminiusapps.rideforge.utils.nowIso
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

class FtpEstimationService(
    private val adaptiveRepository: AdaptiveTrainingRepository,
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val workoutRepository: WorkoutRepository
) {

    suspend fun checkAndEstimateFtp(
        user: User,
        session: WorkoutSession,
        workout: Workout,
        metrics: List<MetricSample>
    ): FtpHistoryRecord? {
        val samples = metrics
            .filter { (it.elapsedSeconds ?: 0) >= 0 }
            .groupBy { it.elapsedSeconds ?: 0 }
            .map { (_, samplesAtSecond) -> samplesAtSecond.last() }
            .sortedBy { it.elapsedSeconds ?: 0 }

        var bestEstFtp = user.ftp
        var bestConfidence = 0
        var bestSource = "KEEP"
        var bestMessage = "FTP is optimal."

        // 1. Peak 20 min power * 0.95 (confidence: 90%)
        if (samples.isNotEmpty()) {
            val peak20MinPower = peakAveragePower(samples, 1200)
            if (peak20MinPower != null) {
                val estimated = (peak20MinPower * 0.95).roundToInt()
                if (estimated > user.ftp) {
                    bestEstFtp = estimated
                    bestConfidence = 90
                    bestSource = "INCREASE"
                    bestMessage = "Based on your 20-minute peak power of $peak20MinPower W during '${workout.name}', we estimate your FTP has increased from ${user.ftp} W to $estimated W!"
                }
            }
        }

        // 2. Normalized Power Trend (NP > current FTP for 30m+, confidence: 80%)
        val elapsed = session.elapsedSeconds ?: 0
        val np = session.normalizedPower ?: 0
        if (elapsed >= 1800 && np > user.ftp) {
            val confidence = 80
            if (confidence > bestConfidence || (confidence == bestConfidence && np > bestEstFtp)) {
                bestEstFtp = np
                bestConfidence = confidence
                bestSource = "INCREASE"
                bestMessage = "Your Normalized Power of $np W over ${elapsed / 60} minutes indicates your FTP has increased from ${user.ftp} W to $np W!"
            }
        }

        // 3. Threshold Interval performance (confidence: 70%)
        val isThresholdWorkout = workout.workoutType == WorkoutType.THRESHOLD || workout.workoutType == WorkoutType.OVER_UNDER
        val analysis = adaptiveRepository.findAnalysisBySessionId(session.id)
        if (isThresholdWorkout && analysis != null) {
            val successRate = analysis.intervalSuccessRate
            val compliance = analysis.ergComplianceScore ?: 0
            val rpe = analysis.estimatedRpe
            if (successRate >= 95 && compliance >= 90 && rpe < 6.0) {
                val estimated = (user.ftp * 1.025).roundToInt()
                val confidence = 70
                if (confidence > bestConfidence || (confidence == bestConfidence && estimated > bestEstFtp)) {
                    bestEstFtp = estimated
                    bestConfidence = confidence
                    bestSource = "INCREASE"
                    bestMessage = "You successfully completed hard threshold intervals with high compliance. We estimate your FTP increased to $estimated W (+2.5%)."
                }
            }
        }

        // 4. Repeated Overperformance (confidence: 75%)
        val history = sessionRepository.historyForUser(user.id, limit = 10, offset = 0)
        val analyses = history.mapNotNull { adaptiveRepository.findAnalysisBySessionId(it.id) }
        if (analyses.size >= 3) {
            val lastThree = analyses.take(3)
            val overperformedCount = lastThree.count { it.classification.equals("OVERPERFORMED", ignoreCase = true) }
            if (overperformedCount >= 2) {
                val estimated = (user.ftp * 1.03).roundToInt()
                val confidence = 75
                if (confidence > bestConfidence || (confidence == bestConfidence && estimated > bestEstFtp)) {
                    bestEstFtp = estimated
                    bestConfidence = confidence
                    bestSource = "INCREASE"
                    bestMessage = "You have overperformed in ${overperformedCount} of your last 3 workouts. We recommend increasing your FTP to $estimated W (+3%)."
                }
            }
        }

        // 5. FTP too high check (3 struggles or failures in a row)
        val intensityTypes = setOf(
            WorkoutType.THRESHOLD,
            WorkoutType.VO2_MAX,
            WorkoutType.OVER_UNDER,
            WorkoutType.SWEET_SPOT
        )
        val completedIntensityRides = history
            .filter { it.status.name.equals("completed", ignoreCase = true) }
            .mapNotNull { ride ->
                val rideAnalysis = adaptiveRepository.findAnalysisBySessionId(ride.id) ?: return@mapNotNull null
                val rideWorkout = workoutRepository.findById(ride.workoutId) ?: return@mapNotNull null
                if (intensityTypes.contains(rideWorkout.workoutType)) rideAnalysis else null
            }
        if (completedIntensityRides.size >= 3) {
            val lastThree = completedIntensityRides.take(3)
            val allStruggledOrFailed = lastThree.all { 
                it.classification.equals("STRUGGLED", ignoreCase = true) || 
                it.classification.equals("FAILED", ignoreCase = true) 
            }
            if (allStruggledOrFailed) {
                val suggestedFtp = (user.ftp * 0.95).roundToInt()
                bestEstFtp = suggestedFtp
                bestConfidence = 85
                bestSource = "DECREASE"
                bestMessage = "FTP may be too high: You have struggled with your last 3 high-intensity sessions. We recommend reducing your FTP from ${user.ftp} W to $suggestedFtp W (-5%)."
            }
        }

        // 6. Stale FTP test check
        val ftpHistory = adaptiveRepository.getFtpHistory(user.id)
        val lastApproved = ftpHistory.lastOrNull { it.status == "approved" }
        val isStale = if (lastApproved != null) {
            val lastDate = Instant.parse(lastApproved.createdAt)
            val thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS)
            lastDate.isBefore(thirtyDaysAgo)
        } else {
            false
        }
        if (isStale && bestSource == "KEEP") {
            bestEstFtp = user.ftp
            bestConfidence = 60
            bestSource = "TEST_REQUIRED"
            bestMessage = "FTP test recommended: It has been over 30 days since your last FTP change. We recommend taking an FTP test to ensure training zones are correct."
        }

        // Save FtpHistoryRecord for compatibility / auditing
        var historyRecord: FtpHistoryRecord? = null
        if (bestSource == "INCREASE" || bestSource == "DECREASE") {
            if (bestEstFtp != user.ftp) {
                val existingPending = adaptiveRepository.findPendingFtpRecord(user.id)
                if (existingPending != null) {
                    if (existingPending.estimatedFtp >= bestEstFtp && bestSource == "INCREASE") {
                        historyRecord = existingPending
                    } else {
                        adaptiveRepository.updateFtpRecord(existingPending.copy(
                            status = "dismissed",
                            message = "Superseded by a newer estimate of $bestEstFtp W"
                        ))
                    }
                }

                if (historyRecord == null) {
                    val record = FtpHistoryRecord(
                        id = newId("ftp"),
                        userId = user.id,
                        estimatedFtp = bestEstFtp,
                        previousFtp = user.ftp,
                        sessionId = session.id,
                        status = "pending_approval",
                        message = bestMessage,
                        createdAt = nowIso()
                    )
                    historyRecord = adaptiveRepository.saveFtpRecord(record)
                }
            }
        }

        // Also save detailed estimate
        val estimateRecord = FtpEstimateDetail(
            id = historyRecord?.id ?: newId("ftpe"),
            userId = user.id,
            currentFtp = user.ftp,
            estimatedFtp = bestEstFtp,
            confidenceScore = bestConfidence,
            recommendation = bestSource,
            status = if (bestSource == "INCREASE" || bestSource == "DECREASE") "pending_approval" else "approved",
            message = bestMessage,
            createdAt = nowIso()
        )
        
        // Manage pending state duplicates — only supersede an existing pending
        // estimate when the new result is actionable (INCREASE / DECREASE).
        // Neutral checks (KEEP, TEST_REQUIRED) must not dismiss a pending
        // estimate because the matching ftp_history row stays pending and the
        // rider must still be able to approve it.
        val existingPendingEstimate = adaptiveRepository.findPendingFtpEstimate(user.id)
        if (bestSource == "INCREASE" || bestSource == "DECREASE") {
            if (existingPendingEstimate != null) {
                if (existingPendingEstimate.estimatedFtp != bestEstFtp || bestSource != existingPendingEstimate.recommendation) {
                    adaptiveRepository.updateFtpEstimate(existingPendingEstimate.copy(
                        status = "dismissed",
                        message = "Superseded by a newer estimate"
                    ))
                    adaptiveRepository.saveFtpEstimate(estimateRecord)
                }
            } else {
                adaptiveRepository.saveFtpEstimate(estimateRecord)
            }
        } else if (existingPendingEstimate == null) {
            // No pending estimate exists; safe to save the neutral record.
            adaptiveRepository.saveFtpEstimate(estimateRecord)
        }
        // else: neutral result + existing pending estimate → keep the pending
        // estimate untouched so the rider can still approve it.

        return historyRecord
    }

    suspend fun approveFtp(userId: String, recordId: String): User? {
        // Approve history record and estimates
        val record = adaptiveRepository.findFtpRecordById(recordId)
        val estimate = adaptiveRepository.findFtpEstimateById(recordId)
        
        val targetUserId = record?.userId ?: estimate?.userId ?: return null
        if (targetUserId != userId) return null
        
        if (record != null && record.status != "pending_approval") return null
        if (estimate != null && estimate.status != "pending_approval") return null
        
        val prevFtp = record?.previousFtp ?: estimate?.currentFtp ?: return null
        val estFtp = record?.estimatedFtp ?: estimate?.estimatedFtp ?: return null

        val user = userRepository.findById(userId) ?: return null
        if (user.ftp != prevFtp) {
            record?.let {
                adaptiveRepository.updateFtpRecord(it.copy(
                    status = "dismissed",
                    message = "Dismissed: FTP has changed since this estimate was generated."
                ))
            }
            estimate?.let {
                adaptiveRepository.updateFtpEstimate(it.copy(
                    status = "dismissed",
                    message = "Dismissed: FTP has changed since this estimate was generated."
                ))
            }
            return null
        }

        val updatedUser = user.copy(ftp = estFtp)
        userRepository.update(updatedUser)

        record?.let {
            adaptiveRepository.updateFtpRecord(it.copy(
                status = "approved",
                message = "Approved: FTP updated to $estFtp W"
            ))
        }
        estimate?.let {
            adaptiveRepository.updateFtpEstimate(it.copy(
                status = "approved",
                message = "Approved: FTP updated to $estFtp W"
            ))
        }

        return updatedUser
    }

    suspend fun dismissFtp(userId: String, recordId: String): Boolean {
        val record = adaptiveRepository.findFtpRecordById(recordId)
        val estimate = adaptiveRepository.findFtpEstimateById(recordId)
        
        val targetUserId = record?.userId ?: estimate?.userId ?: return false
        if (targetUserId != userId) return false

        if (record != null && record.status != "pending_approval") return false
        if (estimate != null && estimate.status != "pending_approval") return false

        record?.let {
            adaptiveRepository.updateFtpRecord(it.copy(
                status = "dismissed",
                message = "Dismissed by rider"
            ))
        }

        estimate?.let {
            adaptiveRepository.updateFtpEstimate(it.copy(
                status = "dismissed",
                message = "Dismissed by rider"
            ))
        }
        return true
    }

    private fun peakAveragePower(samples: List<MetricSample>, windowSeconds: Int): Int? {
        val powerSamples = samples.filter { it.currentPower > 0 }
        if (powerSamples.isEmpty()) return null
        val firstSec = powerSamples.first().elapsedSeconds ?: 0
        val lastSec = powerSamples.last().elapsedSeconds ?: 0
        val totalDuration = lastSec - firstSec + 1
        if (totalDuration < windowSeconds) return null

        var best: Double? = null
        powerSamples.forEachIndexed { index, sample ->
            val start = sample.elapsedSeconds ?: 0
            val windowEnd = start + windowSeconds
            val window = powerSamples.drop(index).takeWhile { (it.elapsedSeconds ?: 0) < windowEnd }
            val coveredSeconds = (window.lastOrNull()?.elapsedSeconds ?: start) - start
            if (coveredSeconds >= (windowSeconds * 0.8).roundToInt() && window.isNotEmpty()) {
                val average = window.map { it.currentPower }.average()
                best = maxOf(best ?: average, average)
            }
        }
        return best?.roundToInt()
    }
}
