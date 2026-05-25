package com.delminiusapps.rideforge

import com.delminiusapps.rideforge.models.*
import com.delminiusapps.rideforge.repositories.InMemoryAdaptiveTrainingRepository
import com.delminiusapps.rideforge.repositories.InMemorySessionRepository
import com.delminiusapps.rideforge.repositories.InMemoryUserRepository
import com.delminiusapps.rideforge.services.adaptive_training.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdaptiveTrainingTest {

    private val user = User(
        id = "user-1",
        email = "rider@example.com",
        name = "Rider One",
        passwordHash = "hash",
        ftp = 200,
        weightKg = 70.0,
        units = "metric",
        enrolledPlanId = "plan-ftp-builder",
        createdAt = Instant.now().toString()
    )

    private val interval = WorkoutInterval(
        id = "interval-1",
        workoutId = "workout-1",
        name = "Sweet Spot Work Interval 1",
        durationSeconds = 600,
        targetPowerWatts = 180,
        targetFtpPercent = 90,
        type = IntervalType.work
    )

    private val workout = Workout(
        id = "workout-1",
        planId = "plan-ftp-builder",
        name = "Sweet Spot Test",
        description = "Sweet spot intervals",
        durationMinutes = 30,
        difficulty = "Intermediate",
        targetZones = listOf("SweetSpot"),
        intervals = listOf(interval),
        weekNumber = 1,
        dayNumber = 1,
        workoutType = WorkoutType.SWEET_SPOT
    )

    private val session = WorkoutSession(
        id = "session-1",
        userId = "user-1",
        workoutId = "workout-1",
        status = SessionStatus.completed,
        startedAt = Instant.now().toString(),
        completedAt = Instant.now().toString(),
        elapsedSeconds = 1800,
        riderWeightKg = 70.0,
        averagePower = 180,
        normalizedPower = 185,
        tss = 28,
        hasRealTrainerData = true
    )

    @Test
    fun testWorkoutCompletionAnalysis() {
        val metrics = listOf(
            MetricSample("session-1", "2026-05-25T10:00:00Z", 10, 180, 180, 90, 140, 30.0),
            MetricSample("session-1", "2026-05-25T10:00:10Z", 20, 185, 180, 92, 142, 31.0),
            MetricSample("session-1", "2026-05-25T10:00:20Z", 30, 178, 180, 88, 145, 29.5)
        )

        val analysis = WorkoutCompletionAnalyzer.analyze(
            session = session,
            workout = workout,
            intervals = listOf(interval),
            metrics = metrics,
            userFtp = 200
        )

        assertEquals("session-1", session.id)
        assertTrue(analysis.completionPercent >= 0)
        assertTrue(analysis.intervalSuccessRate >= 0)
        assertEquals(7.3, analysis.estimatedRpe) // correct intensity-based RPE estimation
    }

    @Test
    fun testWorkoutClassifier() {
        // High completion, low RPE -> Overperformed
        val analysisOverperformed = WorkoutCompletionAnalyzer.AnalysisResult(
            completionPercent = 100,
            intervalSuccessRate = 100,
            ergComplianceScore = 98,
            cadenceConsistencyScore = 95,
            powerFade = 0.0,
            hrDrift = 1.0,
            estimatedRpe = 3.0
        )
        
        // Let's create a session with high avg power to trigger Overperformed
        val sessionOverperformed = session.copy(averagePower = 220)
        val class1 = WorkoutClassifier.classify(
            session = sessionOverperformed,
            workout = workout,
            intervals = listOf(interval),
            analysis = analysisOverperformed,
            userFtp = 200
        )
        assertEquals("Overperformed", class1)

        // Lower completion, high RPE -> Failed
        val analysisFailed = WorkoutCompletionAnalyzer.AnalysisResult(
            completionPercent = 40,
            intervalSuccessRate = 20,
            ergComplianceScore = 50,
            cadenceConsistencyScore = 60,
            powerFade = 15.0,
            hrDrift = 10.0,
            estimatedRpe = 10.0
        )
        val class2 = WorkoutClassifier.classify(
            session = session,
            workout = workout,
            intervals = listOf(interval),
            analysis = analysisFailed,
            userFtp = 200
        )
        assertEquals("Failed", class2)
    }

    @Test
    fun testProgressionTracker() = runBlocking {
        val adaptiveRepo = InMemoryAdaptiveTrainingRepository()
        val tracker = ProgressionTracker(adaptiveRepo)

        // Starting levels are 1.0
        val initialLevels = tracker.getAllProgressionLevels("user-1")
        assertEquals(1.0, initialLevels[WorkoutType.SWEET_SPOT])

        // Overperformed updates progression level
        tracker.updateProgression("user-1", workout, "Overperformed")
        val updatedLevels = tracker.getAllProgressionLevels("user-1")
        assertTrue(updatedLevels[WorkoutType.SWEET_SPOT]!! > 1.0)
    }

    @Test
    fun testFatigueCalculationService() {
        val service = FatigueCalculationService()
        // Create sessions spread over past days
        val now = Instant.now()
        val sessions = (1..10).map { i ->
            val daysAgo = i * 2
            val dateStr = now.minus(daysAgo.toLong(), ChronoUnit.DAYS).toString()
            WorkoutSession(
                id = "session-$i",
                userId = "user-1",
                workoutId = "workout-1",
                status = SessionStatus.completed,
                startedAt = dateStr,
                completedAt = dateStr,
                elapsedSeconds = 3600,
                riderWeightKg = 70.0,
                averagePower = 180,
                normalizedPower = 180,
                tss = 80,
                hasRealTrainerData = true
            )
        }

        val fatigue = service.calculateCurrentFatigue(sessions)
        assertTrue(fatigue.ctl > 0.0)
        assertTrue(fatigue.atl > 0.0)
        // TSB = CTL - ATL (allowing for 0.1 tolerance due to round-to-one-decimal precision)
        assertTrue(Math.abs((fatigue.ctl - fatigue.atl) - fatigue.tsb) < 0.15)
    }

    @Test
    fun testFtpEstimationService() = runBlocking {
        val adaptiveRepo = InMemoryAdaptiveTrainingRepository()
        val sessionRepo = InMemorySessionRepository()
        val userRepo = InMemoryUserRepository()

        userRepo.create(user)

        val ftpEstimationService = FtpEstimationService(adaptiveRepo, sessionRepo, userRepo)

        // Generate 20 minutes (1200 seconds) of metric samples at 240W
        val samples = (1..1250).map { sec ->
            MetricSample("session-1", Instant.now().toString(), sec, 240, 240, 90, 160, 32.0)
        }

        val record = ftpEstimationService.checkAndEstimateFtp(user, session, workout, samples)
        assertNotNull(record)
        // 240W * 0.95 = 228W, which is > 200W + 2
        assertEquals(228, record.estimatedFtp)
        assertEquals("pending_approval", record.status)
    }
}
