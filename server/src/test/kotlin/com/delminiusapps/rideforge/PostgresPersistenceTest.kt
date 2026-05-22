package com.delminiusapps.rideforge

import com.delminiusapps.rideforge.config.AppConfig
import com.delminiusapps.rideforge.config.JwtConfig
import com.delminiusapps.rideforge.config.PersistenceMode
import com.delminiusapps.rideforge.config.StravaConfig
import com.delminiusapps.rideforge.dto.CompleteSessionRequest
import com.delminiusapps.rideforge.dto.LoginRequest
import com.delminiusapps.rideforge.dto.MetricSampleRequest
import com.delminiusapps.rideforge.dto.RegisterRequest
import com.delminiusapps.rideforge.dto.StartSessionRequest
import com.delminiusapps.rideforge.dto.UpdateFtpRequest
import com.delminiusapps.rideforge.plugins.ServiceRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostgresPersistenceTest {
    @Test
    fun postgresRepositoriesPersistAcrossRegistryRestart() = runBlocking {
        val databaseUrl = System.getenv("TEST_DATABASE_URL")
        assumeTrue("Set TEST_DATABASE_URL to run PostgreSQL persistence tests.", !databaseUrl.isNullOrBlank())

        val config = postgresTestConfig(databaseUrl)
        val email = "persist-${UUID.randomUUID()}@example.com"
        val password = "persistent-password"
        var userId = ""
        var sessionId = ""
        var refreshToken = ""

        ServiceRegistry(config).use { registry ->
            val auth = registry.authService.register(
                RegisterRequest(
                    email = email,
                    password = password,
                    name = "Persistent Rider",
                    ftp = 230,
                    weightKg = 76.5,
                ),
            )
            userId = auth.user.id
            refreshToken = auth.refreshToken

            registry.profileService.updateFtp(userId, UpdateFtpRequest(261))
            val started = registry.sessionService.start(userId, StartSessionRequest("vo2-w1d1"))
            sessionId = started.session.id
            registry.sessionService.addMetric(
                userId,
                sessionId,
                MetricSampleRequest(
                    timestamp = "2026-05-19T10:00:00Z",
                    currentPower = 255,
                    targetPower = 260,
                    cadence = 92,
                    heartRate = 151,
                    speedKmh = 34.2,
                ),
            )
            registry.sessionService.complete(userId, sessionId, CompleteSessionRequest(elapsedSeconds = 1800))
        }

        ServiceRegistry(config).use { registry ->
            val login = registry.authService.login(LoginRequest(email, password))
            assertEquals(userId, login.user.id)
            assertEquals(261, registry.profileService.getProfile(userId).ftp)

            val refreshed = registry.authService.refresh(refreshToken)
            assertEquals(userId, refreshed.user.id)

            val history = registry.sessionService.history(userId, limit = 20, offset = 0)
            assertTrue(history.items.any { it.id == sessionId })
            assertEquals(1, registry.sessionService.getMetrics(userId, sessionId).size)
        }
    }
}

private fun postgresTestConfig(databaseUrl: String): AppConfig = AppConfig(
    port = 0,
    databaseUrl = databaseUrl,
    jwt = JwtConfig(
        secret = "test-persistence-rideforge-secret",
        issuer = "rideforge-persistence-test-api",
        audience = "rideforge-persistence-test-client",
        realm = "rideforge-persistence-test",
        accessTokenMinutes = 60,
        refreshTokenDays = 30,
    ),
    strava = StravaConfig(
        clientId = null,
        clientSecret = null,
        redirectUri = "http://localhost/integrations/strava/callback",
    ),
    databaseUser = System.getenv("TEST_DATABASE_USER"),
    databasePassword = System.getenv("TEST_DATABASE_PASSWORD"),
    databaseMaxPoolSize = 2,
    persistenceMode = PersistenceMode.POSTGRES,
)
