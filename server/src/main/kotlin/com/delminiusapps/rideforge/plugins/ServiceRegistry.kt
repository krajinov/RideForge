package com.delminiusapps.rideforge.plugins

import com.delminiusapps.rideforge.auth.JwtService
import com.delminiusapps.rideforge.auth.PasswordHasher
import com.delminiusapps.rideforge.config.AppConfig
import com.delminiusapps.rideforge.config.PersistenceMode
import com.delminiusapps.rideforge.database.DatabaseSeeder
import com.delminiusapps.rideforge.database.PostgresDatabase
import com.delminiusapps.rideforge.database.PostgresDeviceRepository
import com.delminiusapps.rideforge.database.PostgresRefreshTokenRepository
import com.delminiusapps.rideforge.database.PostgresSessionRepository
import com.delminiusapps.rideforge.database.PostgresStravaConnectionRepository
import com.delminiusapps.rideforge.database.PostgresStravaSyncRepository
import com.delminiusapps.rideforge.database.PostgresTrainingPlanRepository
import com.delminiusapps.rideforge.database.PostgresUserRepository
import com.delminiusapps.rideforge.database.PostgresWorkoutRepository
import com.delminiusapps.rideforge.database.PostgresAdaptiveTrainingRepository
import com.delminiusapps.rideforge.repositories.InMemoryDeviceRepository
import com.delminiusapps.rideforge.repositories.InMemoryRefreshTokenRepository
import com.delminiusapps.rideforge.repositories.InMemorySessionRepository
import com.delminiusapps.rideforge.repositories.InMemoryStravaConnectionRepository
import com.delminiusapps.rideforge.repositories.InMemoryStravaSyncRepository
import com.delminiusapps.rideforge.repositories.InMemoryTrainingPlanRepository
import com.delminiusapps.rideforge.repositories.InMemoryUserRepository
import com.delminiusapps.rideforge.repositories.InMemoryWorkoutRepository
import com.delminiusapps.rideforge.repositories.InMemoryAdaptiveTrainingRepository
import com.delminiusapps.rideforge.repositories.DeviceRepository
import com.delminiusapps.rideforge.repositories.RefreshTokenRepository
import com.delminiusapps.rideforge.repositories.SessionRepository
import com.delminiusapps.rideforge.repositories.StravaConnectionRepository
import com.delminiusapps.rideforge.repositories.StravaSyncRepository
import com.delminiusapps.rideforge.repositories.TrainingPlanRepository
import com.delminiusapps.rideforge.repositories.UserRepository
import com.delminiusapps.rideforge.repositories.WorkoutRepository
import com.delminiusapps.rideforge.repositories.AdaptiveTrainingRepository
import com.delminiusapps.rideforge.services.AuthService
import com.delminiusapps.rideforge.services.DeviceService
import com.delminiusapps.rideforge.services.ProfileService
import com.delminiusapps.rideforge.services.SessionService
import com.delminiusapps.rideforge.services.StravaApiClient
import com.delminiusapps.rideforge.services.StravaService
import com.delminiusapps.rideforge.services.StravaStateService
import com.delminiusapps.rideforge.services.TcxWorkoutExporter
import com.delminiusapps.rideforge.services.TrainingPlanService
import com.delminiusapps.rideforge.services.WorkoutService
import com.delminiusapps.rideforge.services.adaptive_training.ProgressionTracker
import com.delminiusapps.rideforge.services.adaptive_training.FatigueCalculationService
import com.delminiusapps.rideforge.services.adaptive_training.FtpEstimationService
import com.delminiusapps.rideforge.services.adaptive_training.RecommendationEngine
import kotlinx.coroutines.runBlocking

class ServiceRegistry(config: AppConfig) : AutoCloseable {
    private val postgresDatabase: PostgresDatabase?
    val userRepository: UserRepository
    val planRepository: TrainingPlanRepository
    val workoutRepository: WorkoutRepository
    val sessionRepository: SessionRepository
    val deviceRepository: DeviceRepository
    private val refreshTokenRepository: RefreshTokenRepository
    private val stravaConnectionRepository: StravaConnectionRepository
    private val stravaSyncRepository: StravaSyncRepository
    val adaptiveTrainingRepository: AdaptiveTrainingRepository

    init {
        if (config.persistenceMode == PersistenceMode.IN_MEMORY) {
            postgresDatabase = null
            userRepository = InMemoryUserRepository()
            planRepository = InMemoryTrainingPlanRepository()
            workoutRepository = InMemoryWorkoutRepository()
            sessionRepository = InMemorySessionRepository()
            deviceRepository = InMemoryDeviceRepository()
            refreshTokenRepository = InMemoryRefreshTokenRepository()
            stravaConnectionRepository = InMemoryStravaConnectionRepository()
            stravaSyncRepository = InMemoryStravaSyncRepository()
            adaptiveTrainingRepository = InMemoryAdaptiveTrainingRepository()
        } else {
            val database = PostgresDatabase.create(config)
            try {
                if (config.migrateDatabaseOnStart) database.migrate()
                if (config.seedDatabaseOnStart) runBlocking { DatabaseSeeder(database).seed() }
            } catch (throwable: Throwable) {
                database.close()
                throw throwable
            }

            postgresDatabase = database
            userRepository = PostgresUserRepository(database)
            planRepository = PostgresTrainingPlanRepository(database)
            workoutRepository = PostgresWorkoutRepository(database)
            sessionRepository = PostgresSessionRepository(database)
            deviceRepository = PostgresDeviceRepository(database)
            refreshTokenRepository = PostgresRefreshTokenRepository(database)
            stravaConnectionRepository = PostgresStravaConnectionRepository(database)
            stravaSyncRepository = PostgresStravaSyncRepository(database)
            adaptiveTrainingRepository = PostgresAdaptiveTrainingRepository(database)
        }
    }

    val progressionTracker = ProgressionTracker(adaptiveTrainingRepository)
    val fatigueCalculationService = FatigueCalculationService()
    val ftpEstimationService = FtpEstimationService(adaptiveTrainingRepository, sessionRepository, userRepository, workoutRepository)
    val recommendationEngine = RecommendationEngine(workoutRepository, sessionRepository, progressionTracker, adaptiveTrainingRepository)

    val jwtService = JwtService(config.jwt)
    val authService = AuthService(userRepository, refreshTokenRepository, PasswordHasher(), jwtService)
    val profileService = ProfileService(userRepository)
    val trainingPlanService = TrainingPlanService(planRepository, workoutRepository, userRepository)
    val workoutService = WorkoutService(workoutRepository, userRepository, progressionTracker)
    val sessionService = SessionService(sessionRepository, workoutRepository, deviceRepository, userRepository, adaptiveTrainingRepository, progressionTracker, ftpEstimationService)
    val deviceService = DeviceService(deviceRepository)
    val stravaService = StravaService(
        config = config.strava,
        connections = stravaConnectionRepository,
        syncs = stravaSyncRepository,
        sessions = sessionRepository,
        workouts = workoutRepository,
        stateService = StravaStateService(config.jwt.secret),
        apiClient = StravaApiClient(config.strava),
        tcxExporter = TcxWorkoutExporter(),
    )

    override fun close() {
        postgresDatabase?.close()
    }
}

