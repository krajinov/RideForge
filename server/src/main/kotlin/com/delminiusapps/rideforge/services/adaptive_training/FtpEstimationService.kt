package com.delminiusapps.rideforge.services.adaptive_training

import com.delminiusapps.rideforge.models.FtpHistoryRecord
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.User
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutSession
import com.delminiusapps.rideforge.models.WorkoutType
import com.delminiusapps.rideforge.repositories.AdaptiveTrainingRepository
import com.delminiusapps.rideforge.repositories.SessionRepository
import com.delminiusapps.rideforge.repositories.UserRepository
import com.delminiusapps.rideforge.repositories.WorkoutRepository
import com.delminiusapps.rideforge.utils.newId
import com.delminiusapps.rideforge.utils.nowIso
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

        if (samples.isNotEmpty()) {
            val peak20MinPower = peakAveragePower(samples, 1200)
            if (peak20MinPower != null) {
                val estimatedFtp = (peak20MinPower * 0.95).roundToInt()
                if (estimatedFtp > user.ftp + 2) {
                    // Check if we already have a pending recommendation for a higher FTP to avoid duplicate prompts
                    val existingPending = adaptiveRepository.findPendingFtpRecord(user.id)
                    if (existingPending != null && existingPending.estimatedFtp >= estimatedFtp) {
                        return existingPending
                    }

                    val record = FtpHistoryRecord(
                        id = newId("ftp"),
                        userId = user.id,
                        estimatedFtp = estimatedFtp,
                        previousFtp = user.ftp,
                        sessionId = session.id,
                        status = "pending_approval",
                        message = "Based on your 20-minute peak power of $peak20MinPower W during '${workout.name}', we estimate your FTP has increased from ${user.ftp} W to $estimatedFtp W!",
                        createdAt = nowIso()
                    )
                    return adaptiveRepository.saveFtpRecord(record)
                }
            }
        }

        // FTP set too high check
        val history = sessionRepository.historyForUser(user.id, limit = 10, offset = 0)
        val intensityTypes = setOf(
            WorkoutType.THRESHOLD,
            WorkoutType.VO2_MAX,
            WorkoutType.OVER_UNDER,
            WorkoutType.SWEET_SPOT
        )

        val completedIntensityRides = history
            .filter { ride ->
                ride.status.name.equals("completed", ignoreCase = true)
            }
            .mapNotNull { ride ->
                // Try to find the analysis
                val analysis = adaptiveRepository.findAnalysisBySessionId(ride.id) ?: return@mapNotNull null
                val rideWorkout = workoutRepository.findById(ride.workoutId) ?: return@mapNotNull null
                val isIntensity = intensityTypes.contains(rideWorkout.workoutType)
                if (isIntensity) analysis else null
            }
        // Check if the last 3 intensity workouts were failed or struggled
        if (completedIntensityRides.size >= 3) {
            val lastThree = completedIntensityRides.take(3)
            val allStruggledOrFailed = lastThree.all { it.classification == "Struggled" || it.classification == "Failed" }
            if (allStruggledOrFailed) {
                val suggestedFtp = (user.ftp * 0.95).roundToInt()
                val existingPending = adaptiveRepository.findPendingFtpRecord(user.id)
                if (existingPending == null || existingPending.estimatedFtp != suggestedFtp) {
                    val record = FtpHistoryRecord(
                        id = newId("ftp"),
                        userId = user.id,
                        estimatedFtp = suggestedFtp,
                        previousFtp = user.ftp,
                        sessionId = session.id,
                        status = "pending_approval",
                        message = "You have struggled with your last 3 high-intensity sessions. We recommend reducing your FTP from ${user.ftp} W to $suggestedFtp W (-5%) to scale workout intensities appropriately.",
                        createdAt = nowIso()
                    )
                    return adaptiveRepository.saveFtpRecord(record)
                }
            }
        }

        return null
    }

    suspend fun approveFtp(userId: String, recordId: String): User? {
        val record = adaptiveRepository.findFtpRecordById(recordId) ?: return null
        if (record.userId != userId || record.status != "pending_approval") return null

        val user = userRepository.findById(userId) ?: return null
        val updatedUser = user.copy(ftp = record.estimatedFtp)
        userRepository.update(updatedUser)

        val updatedRecord = record.copy(
            status = "approved",
            message = "Approved: FTP updated to ${record.estimatedFtp} W"
        )
        adaptiveRepository.updateFtpRecord(updatedRecord)

        return updatedUser
    }

    suspend fun dismissFtp(userId: String, recordId: String): Boolean {
        val record = adaptiveRepository.findFtpRecordById(recordId) ?: return false
        if (record.userId != userId || record.status != "pending_approval") return false

        val updatedRecord = record.copy(
            status = "dismissed",
            message = "Dismissed by rider"
        )
        adaptiveRepository.updateFtpRecord(updatedRecord)
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
