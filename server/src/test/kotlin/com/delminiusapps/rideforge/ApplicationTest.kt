package com.delminiusapps.rideforge

import com.delminiusapps.rideforge.config.AppConfig
import com.delminiusapps.rideforge.config.JwtConfig
import com.delminiusapps.rideforge.config.PersistenceMode
import com.delminiusapps.rideforge.config.StravaConfig
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun healthCheckReturnsJson() = testApplication {
        application { module(testAppConfig()) }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("rideforge-api"))
    }

    @Test
    fun loginAndRecommendedWorkoutWork() = testApplication {
        application { module(testAppConfig()) }

        val login = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"marko@example.com","password":"password"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val token = """"accessToken":"([^"]+)"""".toRegex().find(login.bodyAsText())!!.groupValues[1]

        val recommended = client.get("/workouts/recommended") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, recommended.status)
        assertTrue(recommended.bodyAsText().contains("VO2 W1D1 VO2 Max Starter"))
    }

    @Test
    fun planWorkoutsAreScopedAndSorted() = testApplication {
        application { module(testAppConfig()) }

        val login = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"marko@example.com","password":"password"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val token = login.bodyAsText().extractToken("accessToken")

        val ftpBuilder = client.get("/plans/plan-ftp-builder/workouts") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, ftpBuilder.status)
        val ftpBody = ftpBuilder.bodyAsText()
        assertTrue(ftpBody.contains("FTP Builder W1D1 Sweet Spot Intro"))
        assertTrue(ftpBody.contains("FTP Builder W2D3 Threshold 4x6"))
        assertTrue(!ftpBody.contains("Endurance Base W1D1 Easy Endurance"))
        assertTrue(ftpBody.indexOf("ftp-w1d1") < ftpBody.indexOf("ftp-w1d2"))
        assertTrue(ftpBody.indexOf("ftp-w1d3") < ftpBody.indexOf("ftp-w2d1"))

        val enduranceBase = client.get("/plans/plan-endurance-base/workouts") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, enduranceBase.status)
        val enduranceBody = enduranceBase.bodyAsText()
        assertTrue(enduranceBody.contains("Endurance Base W1D1 Easy Endurance"))
        assertTrue(!enduranceBody.contains("FTP Builder W1D1 Sweet Spot Intro"))
    }

    @Test
    fun refreshRotatesAndLogoutRevokesToken() = testApplication {
        application { module(testAppConfig()) }

        val login = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"marko@example.com","password":"password"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val loginBody = login.bodyAsText()
        val accessToken = loginBody.extractToken("accessToken")
        val refreshToken = loginBody.extractToken("refreshToken")

        val refresh = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }
        assertEquals(HttpStatusCode.OK, refresh.status)
        val rotatedRefreshToken = refresh.bodyAsText().extractToken("refreshToken")

        val reusedRefresh = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, reusedRefresh.status)

        val logout = client.post("/auth/logout") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$rotatedRefreshToken"}""")
        }
        assertEquals(HttpStatusCode.NoContent, logout.status)

        val afterLogoutRefresh = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$rotatedRefreshToken"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, afterLogoutRefresh.status)
    }

    @Test
    fun completingSessionIgnoresNonPositiveElapsedSeconds() = testApplication {
        application { module(testAppConfig()) }

        val login = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"marko@example.com","password":"password"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val token = login.bodyAsText().extractToken("accessToken")

        val started = client.post("/sessions/start") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"workoutId":"vo2-w1d1"}""")
        }
        assertEquals(HttpStatusCode.OK, started.status)
        val sessionId = started.bodyAsText().extractToken("id")

        val completed = client.put("/sessions/$sessionId/complete") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"elapsedSeconds":-42}""")
        }

        assertEquals(HttpStatusCode.OK, completed.status)
        assertTrue(completed.bodyAsText().contains(""""elapsedSeconds":2700"""))
    }

    @Test
    fun stravaStatusStartsDisconnected() = testApplication {
        application { module(testAppConfig()) }

        val login = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"marko@example.com","password":"password"}""")
        }
        val token = login.bodyAsText().extractToken("accessToken")

        val status = client.get("/integrations/strava/status") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, status.status)
        assertTrue(status.bodyAsText().contains(""""connected":false"""))
    }

    @Test
    fun stravaSyncRequiresRealTrainerData() = testApplication {
        application { module(testAppConfig()) }

        val login = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"marko@example.com","password":"password"}""")
        }
        val token = login.bodyAsText().extractToken("accessToken")

        val started = client.post("/sessions/start") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"workoutId":"vo2-w1d1"}""")
        }
        val sessionId = started.bodyAsText().extractToken("id")

        client.put("/sessions/$sessionId/complete") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"elapsedSeconds":1200,"hasRealTrainerData":false}""")
        }

        val sync = client.post("/history/$sessionId/sync/strava") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.BadRequest, sync.status)
        assertTrue(sync.bodyAsText().contains("real trainer"))
    }
}

private fun String.extractToken(name: String): String {
    return """"$name":"([^"]+)"""".toRegex().find(this)!!.groupValues[1]
}

private fun testAppConfig(): AppConfig = AppConfig(
    port = 0,
    databaseUrl = "postgresql://localhost:5432/rideforge_test",
    jwt = JwtConfig(
        secret = "test-rideforge-secret",
        issuer = "rideforge-test-api",
        audience = "rideforge-test-client",
        realm = "rideforge-test",
        accessTokenMinutes = 60,
        refreshTokenDays = 30,
    ),
    strava = StravaConfig(
        clientId = null,
        clientSecret = null,
        redirectUri = "http://localhost/integrations/strava/callback",
    ),
    persistenceMode = PersistenceMode.IN_MEMORY,
)
