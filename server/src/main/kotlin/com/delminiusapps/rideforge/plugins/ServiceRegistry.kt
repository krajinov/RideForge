package com.delminiusapps.rideforge.plugins

import com.delminiusapps.rideforge.auth.JwtService
import com.delminiusapps.rideforge.auth.PasswordHasher
import com.delminiusapps.rideforge.config.AppConfig
import com.delminiusapps.rideforge.repositories.InMemoryDeviceRepository
import com.delminiusapps.rideforge.repositories.InMemoryRefreshTokenRepository
import com.delminiusapps.rideforge.repositories.InMemorySessionRepository
import com.delminiusapps.rideforge.repositories.InMemoryTrainingPlanRepository
import com.delminiusapps.rideforge.repositories.InMemoryUserRepository
import com.delminiusapps.rideforge.repositories.InMemoryWorkoutRepository
import com.delminiusapps.rideforge.services.AuthService
import com.delminiusapps.rideforge.services.DeviceService
import com.delminiusapps.rideforge.services.ProfileService
import com.delminiusapps.rideforge.services.SessionService
import com.delminiusapps.rideforge.services.TrainingPlanService
import com.delminiusapps.rideforge.services.WorkoutService

class ServiceRegistry(config: AppConfig) {
    private val userRepository = InMemoryUserRepository()
    private val planRepository = InMemoryTrainingPlanRepository()
    private val workoutRepository = InMemoryWorkoutRepository()
    private val sessionRepository = InMemorySessionRepository()
    private val deviceRepository = InMemoryDeviceRepository()
    private val refreshTokenRepository = InMemoryRefreshTokenRepository()

    val jwtService = JwtService(config.jwt)
    val authService = AuthService(userRepository, refreshTokenRepository, PasswordHasher(), jwtService)
    val profileService = ProfileService(userRepository)
    val trainingPlanService = TrainingPlanService(planRepository, workoutRepository, userRepository)
    val workoutService = WorkoutService(workoutRepository, userRepository)
    val sessionService = SessionService(sessionRepository, workoutRepository)
    val deviceService = DeviceService(deviceRepository)
}
