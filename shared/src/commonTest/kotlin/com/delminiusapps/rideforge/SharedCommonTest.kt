package com.delminiusapps.rideforge

import com.delminiusapps.rideforge.data.mock.MockTrainingPlanRepository
import com.delminiusapps.rideforge.data.mock.MockWorkoutRepository
import com.delminiusapps.rideforge.domain.repository.SessionRepository
import com.delminiusapps.rideforge.domain.usecase.CompleteWorkoutSessionUseCase
import com.delminiusapps.rideforge.models.WorkoutSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun testCompleteWorkoutSessionUseCaseUpdatesMockTrainingPlanRepository() = runTest {
        val planRepo = MockTrainingPlanRepository()
        val workoutRepo = MockWorkoutRepository()
        
        val sessionRepo = object : SessionRepository {
            override val syncStatus = kotlinx.coroutines.flow.MutableStateFlow(com.delminiusapps.rideforge.models.SyncStatus.Synced)
            override suspend fun startSession(workoutId: String) = TODO()
            override suspend fun pauseSession(sessionId: String) = TODO()
            override suspend fun resumeSession(sessionId: String) = TODO()
            override suspend fun addMetric(sessionId: String, sample: com.delminiusapps.rideforge.models.MetricSample) = TODO()
            override suspend fun addMetrics(sessionId: String, samples: List<com.delminiusapps.rideforge.models.MetricSample>) = TODO()
            override suspend fun completeSession(sessionId: String, elapsedSeconds: Int?, hasRealTrainerData: Boolean) =
                WorkoutSession(
                    workoutId = "ftp-w1d1", // maps to plan-ftp-builder in MockData
                    elapsedSeconds = 1200,
                    averagePowerWatts = 200,
                    normalizedPowerWatts = 210,
                    calories = 300,
                    tss = 45,
                    completionPercent = 100,
                    id = sessionId
                )
            override suspend fun getSessionMetrics(sessionId: String) = TODO()
            override suspend fun getSessionSummary(sessionId: String) = TODO()
            override suspend fun syncPending() = TODO()
        }

        val useCase = CompleteWorkoutSessionUseCase(
            repository = sessionRepo,
            trainingPlanRepository = planRepo,
            workoutRepository = workoutRepo
        )

        // Initially completed workouts list is empty
        assertTrue(planRepo.getPlanCompletedWorkoutIds("plan-ftp-builder").isEmpty())

        // Execute use case
        useCase("session-123", 1200)

        // Now the completed workouts list should contain "ftp-w1d1"
        val completed = planRepo.getPlanCompletedWorkoutIds("plan-ftp-builder")
        assertTrue(completed.contains("ftp-w1d1"))
    }
}