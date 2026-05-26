package com.delminiusapps.rideforge

import com.delminiusapps.rideforge.models.*
import com.delminiusapps.rideforge.repositories.InMemoryAdaptiveTrainingRepository
import com.delminiusapps.rideforge.repositories.InMemorySessionRepository
import com.delminiusapps.rideforge.repositories.InMemoryUserRepository
import com.delminiusapps.rideforge.services.adaptive_training.*
import com.delminiusapps.rideforge.services.SessionService
import com.delminiusapps.rideforge.dto.CompleteSessionRequest
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        assertEquals("OVERPERFORMED", class1)

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
        assertEquals("FAILED", class2)
    }

    @Test
    fun testProgressionTracker() = runBlocking {
        val adaptiveRepo = InMemoryAdaptiveTrainingRepository()
        val tracker = ProgressionTracker(adaptiveRepo)

        // Starting levels are 1.0
        val initialLevels = tracker.getAllProgressionLevels("user-1")
        assertEquals(1.0, initialLevels[WorkoutType.SWEET_SPOT])

        // Overperformed updates progression level
        tracker.updateProgression("user-1", workout, "OVERPERFORMED")
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
        val workoutRepo = object : com.delminiusapps.rideforge.repositories.WorkoutRepository {
            override suspend fun list(limit: Int, offset: Int): List<Workout> = emptyList()
            override suspend fun count(): Int = 0
            override suspend fun findById(id: String): Workout? = if (id == workout.id) workout else null
            override suspend fun findByPlanId(planId: String): List<Workout> = emptyList()
            override suspend fun intervalsForWorkout(workoutId: String): List<WorkoutInterval> = emptyList()
        }

        userRepo.create(user)

        val ftpEstimationService = FtpEstimationService(adaptiveRepo, sessionRepo, userRepo, workoutRepo)

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

    @Test
    fun testFtpSetTooHigh() = runBlocking {
        val adaptiveRepo = InMemoryAdaptiveTrainingRepository()
        val sessionRepo = InMemorySessionRepository()
        val userRepo = InMemoryUserRepository()
        val workoutRepo = object : com.delminiusapps.rideforge.repositories.WorkoutRepository {
            override suspend fun list(limit: Int, offset: Int): List<Workout> = emptyList()
            override suspend fun count(): Int = 0
            override suspend fun findById(id: String): Workout? = if (id == workout.id) workout else null
            override suspend fun findByPlanId(planId: String): List<Workout> = emptyList()
            override suspend fun intervalsForWorkout(workoutId: String): List<WorkoutInterval> = emptyList()
        }

        userRepo.create(user)

        // Create 3 completed sessions of intensity (sweet spot)
        val s1 = session.copy(id = "s-1", completedAt = "2026-05-25T09:00:00Z")
        val s2 = session.copy(id = "s-2", completedAt = "2026-05-25T09:10:00Z")
        val s3 = session.copy(id = "s-3", completedAt = "2026-05-25T09:20:00Z")
        sessionRepo.create(s1)
        sessionRepo.create(s2)
        sessionRepo.create(s3)

        // Add Struggled/Failed analysis records for them
        val a1 = WorkoutAnalysis(
            sessionId = "s-1",
            completionPercent = 90,
            intervalSuccessRate = 80,
            ergComplianceScore = 65,
            cadenceConsistencyScore = 80,
            powerFade = 16.0,
            hrDrift = 5.0,
            estimatedRpe = 8.0,
            classification = "STRUGGLED",
            coachNotesSummary = "",
            coachNotesRecommendation = "",
            coachNotesRecovery = "",
            coachNotesNextWorkout = ""
        )
        val a2 = a1.copy(sessionId = "s-2", classification = "FAILED")
        val a3 = a1.copy(sessionId = "s-3", classification = "STRUGGLED")
        adaptiveRepo.saveAnalysis(a1)
        adaptiveRepo.saveAnalysis(a2)
        adaptiveRepo.saveAnalysis(a3)

        val ftpEstimationService = FtpEstimationService(adaptiveRepo, sessionRepo, userRepo, workoutRepo)

        // Run check (with a single sample so no FTP increase is detected but it bypasses the empty check)
        val dummySamples = listOf(MetricSample("session-1", Instant.now().toString(), 10, 100, 100, 90, 120, 25.0))
        val record = ftpEstimationService.checkAndEstimateFtp(user, session, workout, dummySamples)
        
        assertNotNull(record)
        // 200W * 0.95 = 190W
        assertEquals(190, record.estimatedFtp)
        assertEquals("pending_approval", record.status)
        assertTrue(record.message.contains("struggled with your last 3 high-intensity sessions"))
    }

    @Test
    fun testFtpEstimationExact20Minutes() = runBlocking {
        val adaptiveRepo = InMemoryAdaptiveTrainingRepository()
        val sessionRepo = InMemorySessionRepository()
        val userRepo = InMemoryUserRepository()
        val workoutRepo = object : com.delminiusapps.rideforge.repositories.WorkoutRepository {
            override suspend fun list(limit: Int, offset: Int): List<Workout> = emptyList()
            override suspend fun count(): Int = 0
            override suspend fun findById(id: String): Workout? = if (id == workout.id) workout else null
            override suspend fun findByPlanId(planId: String): List<Workout> = emptyList()
            override suspend fun intervalsForWorkout(workoutId: String): List<WorkoutInterval> = emptyList()
        }

        userRepo.create(user)

        val ftpEstimationService = FtpEstimationService(adaptiveRepo, sessionRepo, userRepo, workoutRepo)

        // Generate exactly 20 minutes (1200 seconds: 0 to 1199) of metric samples at 240W
        val samples = (0..1199).map { sec ->
            MetricSample("session-1", Instant.now().toString(), sec, 240, 240, 90, 160, 32.0)
        }

        val record = ftpEstimationService.checkAndEstimateFtp(user, session, workout, samples)
        assertNotNull(record)
        // 240W * 0.95 = 228W
        assertEquals(228, record.estimatedFtp)
        assertEquals("pending_approval", record.status)
    }

    @Test
    fun testSessionServiceScaledWorkoutAnalysis() = runBlocking {
        val adaptiveRepo = InMemoryAdaptiveTrainingRepository()
        val sessionRepo = InMemorySessionRepository()
        val userRepo = InMemoryUserRepository()
        
        // Define a custom workout with id "ftp-w1d3" (hardcoded to level 3.0) and 10m duration
        val customWorkout = workout.copy(
            id = "ftp-w1d3",
            difficulty = "Intermediate",
            durationMinutes = 10,
            workoutType = WorkoutType.SWEET_SPOT,
            intervals = listOf(
                WorkoutInterval(
                    id = "custom-interval-1",
                    workoutId = "ftp-w1d3",
                    name = "Work Interval",
                    durationSeconds = 600,
                    targetPowerWatts = 180, // 90% of 200W FTP
                    targetFtpPercent = 90,
                    type = IntervalType.work
                )
            )
        )

        val workoutRepo = object : com.delminiusapps.rideforge.repositories.WorkoutRepository {
            override suspend fun list(limit: Int, offset: Int): List<Workout> = emptyList()
            override suspend fun count(): Int = 0
            override suspend fun findById(id: String): Workout? = if (id == customWorkout.id) customWorkout else null
            override suspend fun findByPlanId(planId: String): List<Workout> = emptyList()
            override suspend fun intervalsForWorkout(workoutId: String): List<WorkoutInterval> = 
                if (workoutId == customWorkout.id) customWorkout.intervals else emptyList()
        }

        userRepo.create(user)

        // Create a progression level record of 2.7 for sweet spot, forcing scaling of 2.7 / 3.0 = 0.90
        val pl = ProgressionLevel(
            id = "pl-1",
            userId = user.id,
            workoutType = WorkoutType.SWEET_SPOT,
            level = 2.7,
            updatedAt = Instant.now().toString()
        )
        adaptiveRepo.saveProgressionLevel(pl)

        val ftpEstimationService = FtpEstimationService(adaptiveRepo, sessionRepo, userRepo, workoutRepo)
        val dummyDeviceRepo = object : com.delminiusapps.rideforge.repositories.DeviceRepository {
            override suspend fun listAvailable(userId: String): List<Device> = emptyList()
            override suspend fun current(userId: String): Device? = null
            override suspend fun connect(device: Device): Device = device
            override suspend fun disconnect(userId: String): Device? = null
        }

        val sessionService = SessionService(
            sessionRepo,
            workoutRepo,
            dummyDeviceRepo,
            userRepo,
            adaptiveRepo,
            ProgressionTracker(adaptiveRepo),
            ftpEstimationService
        )

        // Create the active session
        val activeSession = WorkoutSession(
            id = "custom-session-1",
            userId = user.id,
            workoutId = customWorkout.id,
            status = SessionStatus.active,
            startedAt = Instant.now().toString(),
            elapsedSeconds = 0,
            riderWeightKg = 70.0
        )
        sessionRepo.create(activeSession)

        // Add metrics at 162W (which is exactly 180W * 0.90 target)
        // Duration of work interval is 600s
        for (sec in 0..600) {
            sessionRepo.addMetric(
                MetricSample("custom-session-1", Instant.now().toString(), sec, 162, 162, 90, 150, 30.0)
            )
        }

        // Complete the session. It should evaluate compliance against the SCALED target of 162W.
        val completedSession = sessionService.complete(user.id, "custom-session-1", CompleteSessionRequest(elapsedSeconds = 600))
        
        // Fetch the saved analysis
        val analysis = adaptiveRepo.findAnalysisBySessionId("custom-session-1")
        assertNotNull(analysis)
        
        // If it used unscaled targets, compliance would be very low/0% and classification would be "Struggled" or "Failed"
        // Since it uses scaled targets, it should be Successful or Easy
        assertTrue(analysis.classification == "SUCCESSFUL" || analysis.classification == "EASY")
    }

    @Test
    fun testProgressionTrackerScaledSuccess() = runBlocking {
        val adaptiveRepo = InMemoryAdaptiveTrainingRepository()
        val tracker = ProgressionTracker(adaptiveRepo)

        // Set user level to 2.0
        val pl = ProgressionLevel(
            id = "pl-1",
            userId = "user-1",
            workoutType = WorkoutType.SWEET_SPOT,
            level = 2.0,
            updatedAt = Instant.now().toString()
        )
        adaptiveRepo.saveProgressionLevel(pl)

        // Define a workout of level 3.0 (ftp-w1d3 is hardcoded to level 3.0)
        val customWorkout = workout.copy(
            id = "ftp-w1d3",
            workoutType = WorkoutType.SWEET_SPOT
        )

        // Scaling factor: 2.0 / 3.0 = 0.666 -> coerced to 0.90
        // Completed level: 3.0 * 0.90 = 2.7
        // Let's run updateProgression with "Successful"
        val newLevel = tracker.updateProgression("user-1", customWorkout, "SUCCESSFUL")
        
        // Assert it is exactly 2.7 instead of 3.0
        assertEquals(2.7, newLevel)
    }

    @Test
    fun testFtpSetTooHighWithoutSamples() = runBlocking {
        val adaptiveRepo = InMemoryAdaptiveTrainingRepository()
        val sessionRepo = InMemorySessionRepository()
        val userRepo = InMemoryUserRepository()
        val workoutRepo = object : com.delminiusapps.rideforge.repositories.WorkoutRepository {
            override suspend fun list(limit: Int, offset: Int): List<Workout> = emptyList()
            override suspend fun count(): Int = 0
            override suspend fun findById(id: String): Workout? = if (id == workout.id) workout else null
            override suspend fun findByPlanId(planId: String): List<Workout> = emptyList()
            override suspend fun intervalsForWorkout(workoutId: String): List<WorkoutInterval> = emptyList()
        }

        userRepo.create(user)

        // Create 3 completed sessions of intensity (sweet spot)
        val s1 = session.copy(id = "s-1", completedAt = "2026-05-25T09:00:00Z")
        val s2 = session.copy(id = "s-2", completedAt = "2026-05-25T09:10:00Z")
        val s3 = session.copy(id = "s-3", completedAt = "2026-05-25T09:20:00Z")
        sessionRepo.create(s1)
        sessionRepo.create(s2)
        sessionRepo.create(s3)

        // Add Struggled/Failed analysis records for them
        val a1 = WorkoutAnalysis(
            sessionId = "s-1",
            completionPercent = 90,
            intervalSuccessRate = 80,
            ergComplianceScore = 65,
            cadenceConsistencyScore = 80,
            powerFade = 16.0,
            hrDrift = 5.0,
            estimatedRpe = 8.0,
            classification = "STRUGGLED",
            coachNotesSummary = "",
            coachNotesRecommendation = "",
            coachNotesRecovery = "",
            coachNotesNextWorkout = ""
        )
        val a2 = a1.copy(sessionId = "s-2", classification = "FAILED")
        val a3 = a1.copy(sessionId = "s-3", classification = "STRUGGLED")
        adaptiveRepo.saveAnalysis(a1)
        adaptiveRepo.saveAnalysis(a2)
        adaptiveRepo.saveAnalysis(a3)

        val ftpEstimationService = FtpEstimationService(adaptiveRepo, sessionRepo, userRepo, workoutRepo)

        // Run check with EMPTY metrics list
        val record = ftpEstimationService.checkAndEstimateFtp(user, session, workout, emptyList())
        
        assertNotNull(record)
        assertEquals(190, record.estimatedFtp)
        assertEquals("pending_approval", record.status)
        assertTrue(record.message.contains("struggled with your last 3 high-intensity sessions"))
    }

    @Test
    fun testFtpApprovalStaleRejection() = runBlocking {
        val adaptiveRepo = InMemoryAdaptiveTrainingRepository()
        val sessionRepo = InMemorySessionRepository()
        val userRepo = InMemoryUserRepository()
        val workoutRepo = object : com.delminiusapps.rideforge.repositories.WorkoutRepository {
            override suspend fun list(limit: Int, offset: Int): List<Workout> = emptyList()
            override suspend fun count(): Int = 0
            override suspend fun findById(id: String): Workout? = null
            override suspend fun findByPlanId(planId: String): List<Workout> = emptyList()
            override suspend fun intervalsForWorkout(workoutId: String): List<WorkoutInterval> = emptyList()
        }

        userRepo.create(user)

        val record = FtpHistoryRecord(
            id = "ftp-1",
            userId = user.id,
            estimatedFtp = 210,
            previousFtp = 180, // different from user's current FTP (200)
            sessionId = "s-1",
            status = "pending_approval",
            message = "Some message",
            createdAt = Instant.now().toString()
        )
        adaptiveRepo.saveFtpRecord(record)

        val ftpEstimationService = FtpEstimationService(adaptiveRepo, sessionRepo, userRepo, workoutRepo)
        
        val result = ftpEstimationService.approveFtp(user.id, "ftp-1")
        assertNull(result)

        val updatedRecord = adaptiveRepo.findFtpRecordById("ftp-1")
        assertNotNull(updatedRecord)
        assertEquals("dismissed", updatedRecord.status)
        assertTrue(updatedRecord.message.contains("FTP has changed"))
    }

    @Test
    fun testFtpEstimationSupersedesPreviousPending() = runBlocking {
        val adaptiveRepo = InMemoryAdaptiveTrainingRepository()
        val sessionRepo = InMemorySessionRepository()
        val userRepo = InMemoryUserRepository()
        val workoutRepo = object : com.delminiusapps.rideforge.repositories.WorkoutRepository {
            override suspend fun list(limit: Int, offset: Int): List<Workout> = emptyList()
            override suspend fun count(): Int = 0
            override suspend fun findById(id: String): Workout? = if (id == workout.id) workout else null
            override suspend fun findByPlanId(planId: String): List<Workout> = emptyList()
            override suspend fun intervalsForWorkout(workoutId: String): List<WorkoutInterval> = emptyList()
        }

        userRepo.create(user)

        val oldRecord = FtpHistoryRecord(
            id = "ftp-old",
            userId = user.id,
            estimatedFtp = 205,
            previousFtp = user.ftp,
            sessionId = "s-old",
            status = "pending_approval",
            message = "Old estimate",
            createdAt = Instant.now().toString()
        )
        adaptiveRepo.saveFtpRecord(oldRecord)

        val ftpEstimationService = FtpEstimationService(adaptiveRepo, sessionRepo, userRepo, workoutRepo)

        val samples = (0..1200 step 10).map { sec ->
            MetricSample("session-1", Instant.now().toString(), sec, 240, 240, 90, 150, 30.0)
        }

        val record = ftpEstimationService.checkAndEstimateFtp(user, session, workout, samples)

        assertNotNull(record)
        assertEquals(228, record.estimatedFtp)
        assertEquals("pending_approval", record.status)

        val updatedOld = adaptiveRepo.findFtpRecordById("ftp-old")
        assertNotNull(updatedOld)
        assertEquals("dismissed", updatedOld.status)
        assertTrue(updatedOld.message.contains("Superseded by a newer estimate"))
    }

    @Test
    fun testFtpApprovalRequiresPendingStatus() = runBlocking {
        val adaptiveRepo = InMemoryAdaptiveTrainingRepository()
        val sessionRepo = InMemorySessionRepository()
        val userRepo = InMemoryUserRepository()
        val workoutRepo = object : com.delminiusapps.rideforge.repositories.WorkoutRepository {
            override suspend fun list(limit: Int, offset: Int): List<Workout> = emptyList()
            override suspend fun count(): Int = 0
            override suspend fun findById(id: String): Workout? = null
            override suspend fun findByPlanId(planId: String): List<Workout> = emptyList()
            override suspend fun intervalsForWorkout(workoutId: String): List<WorkoutInterval> = emptyList()
        }

        userRepo.create(user)

        val record = FtpHistoryRecord(
            id = "ftp-1",
            userId = user.id,
            estimatedFtp = 210,
            previousFtp = user.ftp,
            sessionId = "s-1",
            status = "approved", // already approved
            message = "Approved: FTP updated",
            createdAt = Instant.now().toString()
        )
        adaptiveRepo.saveFtpRecord(record)

        val ftpEstimationService = FtpEstimationService(adaptiveRepo, sessionRepo, userRepo, workoutRepo)
        
        val result = ftpEstimationService.approveFtp(user.id, "ftp-1")
        assertNull(result)
    }

    @Test
    fun testFtpDismissalRequiresPendingStatus() = runBlocking {
        val adaptiveRepo = InMemoryAdaptiveTrainingRepository()
        val sessionRepo = InMemorySessionRepository()
        val userRepo = InMemoryUserRepository()
        val workoutRepo = object : com.delminiusapps.rideforge.repositories.WorkoutRepository {
            override suspend fun list(limit: Int, offset: Int): List<Workout> = emptyList()
            override suspend fun count(): Int = 0
            override suspend fun findById(id: String): Workout? = null
            override suspend fun findByPlanId(planId: String): List<Workout> = emptyList()
            override suspend fun intervalsForWorkout(workoutId: String): List<WorkoutInterval> = emptyList()
        }

        userRepo.create(user)

        val record = FtpHistoryRecord(
            id = "ftp-1",
            userId = user.id,
            estimatedFtp = 210,
            previousFtp = user.ftp,
            sessionId = "s-1",
            status = "dismissed", // already dismissed
            message = "Dismissed by rider",
            createdAt = Instant.now().toString()
        )
        adaptiveRepo.saveFtpRecord(record)

        val ftpEstimationService = FtpEstimationService(adaptiveRepo, sessionRepo, userRepo, workoutRepo)
        
        val success = ftpEstimationService.dismissFtp(user.id, "ftp-1")
        assertTrue(!success)
    }

    @Test
    fun testProgressionReset() = runBlocking {
        val adaptiveRepo = InMemoryAdaptiveTrainingRepository()
        val tracker = ProgressionTracker(adaptiveRepo)
        val userId = "user-test"

        // Set progression level to 5.0 for Sweet Spot
        val pl1 = ProgressionLevel(
            id = "pl-ss",
            userId = userId,
            workoutType = WorkoutType.SWEET_SPOT,
            level = 5.0,
            updatedAt = Instant.now().toString()
        )
        adaptiveRepo.saveProgressionLevel(pl1)

        // Set progression level to 3.0 for Threshold
        val pl2 = ProgressionLevel(
            id = "pl-th",
            userId = userId,
            workoutType = WorkoutType.THRESHOLD,
            level = 3.0,
            updatedAt = Instant.now().toString()
        )
        adaptiveRepo.saveProgressionLevel(pl2)

        // Reset
        tracker.resetProgression(userId)

        // Verify all levels are reset to 1.0
        val levels = tracker.getAllProgressionLevels(userId)
        levels.forEach { (type, level) ->
            assertEquals(1.0, level, "Level for $type should be reset to 1.0")
        }
    }
}

