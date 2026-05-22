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
import com.delminiusapps.rideforge.database.PostgresTrainingPlanRepository
import com.delminiusapps.rideforge.database.PostgresUserRepository
import com.delminiusapps.rideforge.database.PostgresWorkoutRepository
import com.delminiusapps.rideforge.repositories.InMemoryDeviceRepository
import com.delminiusapps.rideforge.repositories.InMemoryRefreshTokenRepository
import com.delminiusapps.rideforge.repositories.InMemorySessionRepository
import com.delminiusapps.rideforge.repositories.InMemoryTrainingPlanRepository
import com.delminiusapps.rideforge.repositories.InMemoryUserRepository
import com.delminiusapps.rideforge.repositories.InMemoryWorkoutRepository
import com.delminiusapps.rideforge.repositories.DeviceRepository
import com.delminiusapps.rideforge.repositories.RefreshTokenRepository
import com.delminiusapps.rideforge.repositories.SessionRepository
import com.delminiusapps.rideforge.repositories.TrainingPlanRepository
import com.delminiusapps.rideforge.repositories.UserRepository
import com.delminiusapps.rideforge.repositories.WorkoutRepository
import com.delminiusapps.rideforge.services.AuthService
import com.delminiusapps.rideforge.services.DeviceService
import com.delminiusapps.rideforge.services.ProfileService
import com.delminiusapps.rideforge.services.SessionService
import com.delminiusapps.rideforge.services.TrainingPlanService
import com.delminiusapps.rideforge.services.WorkoutService
import kotlinx.coroutines.runBlocking

class ServiceRegistry(config: AppConfig) : AutoCloseable {
    private val postgresDatabase: PostgresDatabase?
    private val userRepository: UserRepository
    private val planRepository: TrainingPlanRepository
    private val workoutRepository: WorkoutRepository
    private val sessionRepository: SessionRepository
    private val deviceRepository: DeviceRepository
    private val refreshTokenRepository: RefreshTokenRepository

    init {
        if (config.persistenceMode == PersistenceMode.IN_MEMORY) {
            postgresDatabase = null
            userRepository = InMemoryUserRepository()
            planRepository = InMemoryTrainingPlanRepository()
            workoutRepository = InMemoryWorkoutRepository()
            sessionRepository = InMemorySessionRepository()
            deviceRepository = InMemoryDeviceRepository()
            refreshTokenRepository = InMemoryRefreshTokenRepository()
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
        }
    }

    val jwtService = JwtService(config.jwt)
    val authService = AuthService(userRepository, refreshTokenRepository, PasswordHasher(), jwtService)
    val profileService = ProfileService(userRepository)
    val trainingPlanService = TrainingPlanService(planRepository, workoutRepository, userRepository)
    val workoutService = WorkoutService(workoutRepository, userRepository)
    val sessionService = SessionService(sessionRepository, workoutRepository)
    val deviceService = DeviceService(deviceRepository)

    override fun close() {
        postgresDatabase?.close()
    }
}
