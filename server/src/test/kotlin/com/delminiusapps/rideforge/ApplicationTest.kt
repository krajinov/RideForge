package com.delminiusapps.rideforge

import com.delminiusapps.rideforge.config.AppConfig
import com.delminiusapps.rideforge.config.JwtConfig
import com.delminiusapps.rideforge.config.PersistenceMode
import com.delminiusapps.rideforge.config.StravaConfig
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.URI
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.Collections
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

    @Test
    fun stravaDisconnectDeauthorizesRemoteConnection() {
        MockStravaServer().use { strava ->
            testApplication {
                application { module(strava.appConfig()) }

                val token = loginToken()
                connectStrava(token)

                val disconnect = client.post("/integrations/strava/disconnect") {
                    bearerAuth(token)
                }

                assertEquals(HttpStatusCode.OK, disconnect.status)
                assertTrue(disconnect.bodyAsText().contains(""""connected":false"""))
                assertEquals(1, strava.count("POST", "/oauth/deauthorize"))
                assertEquals("Bearer test-access-token", strava.authorizationFor("POST", "/oauth/deauthorize"))
                assertEquals("access_token=test-access-token", strava.bodyFor("POST", "/oauth/deauthorize"))
            }
        }
    }

    @Test
    fun stravaDisconnectClearsLocalConnectionWhenRemoteDeauthorizeFails() {
        MockStravaServer(deauthorizeStatusCode = 401).use { strava ->
            testApplication {
                application { module(strava.appConfig()) }

                val token = loginToken()
                connectStrava(token)

                val disconnect = client.post("/integrations/strava/disconnect") {
                    bearerAuth(token)
                }

                assertEquals(HttpStatusCode.OK, disconnect.status)
                assertTrue(disconnect.bodyAsText().contains(""""connected":false"""))
                assertEquals(1, strava.count("POST", "/oauth/deauthorize"))

                val status = client.get("/integrations/strava/status") {
                    bearerAuth(token)
                }
                assertEquals(HttpStatusCode.OK, status.status)
                assertTrue(status.bodyAsText().contains(""""connected":false"""))
            }
        }
    }

    @Test
    fun stravaSyncStoresFailedStatusWhenUploadStartFails() {
        MockStravaServer(uploadStatusCode = 500).use { strava ->
            testApplication {
                application { module(strava.appConfig()) }

                val token = loginToken()
                connectStrava(token)
                val sessionId = completedTrainerSession(token)

                val sync = client.post("/history/$sessionId/sync/strava") {
                    bearerAuth(token)
                }
                assertEquals(HttpStatusCode.BadGateway, sync.status)

                val status = client.get("/history/$sessionId/sync-status") {
                    bearerAuth(token)
                }

                assertEquals(HttpStatusCode.OK, status.status)
                val body = status.bodyAsText()
                assertTrue(body.contains(""""status":"failed""""))
                assertTrue(body.contains("Strava upload request failed with HTTP 500"))
                assertEquals(1, strava.count("POST", "/api/v3/uploads"))
            }
        }
    }

    @Test
    fun stravaSyncReturnsInProgressStatusForRetryDuringUploadStart() {
        MockStravaServer(uploadDelayMillis = 500).use { strava ->
            testApplication {
                application { module(strava.appConfig()) }

                val token = loginToken()
                connectStrava(token)
                val sessionId = completedTrainerSession(token)

                coroutineScope {
                    val firstSync = async {
                        client.post("/history/$sessionId/sync/strava") {
                            bearerAuth(token)
                        }
                    }
                    strava.awaitCount("POST", "/api/v3/uploads", expected = 1)

                    val retry = client.post("/history/$sessionId/sync/strava") {
                        bearerAuth(token)
                    }
                    assertEquals(HttpStatusCode.OK, retry.status)
                    assertTrue(retry.bodyAsText().contains(""""status":"syncing""""))
                    assertEquals(1, strava.count("POST", "/api/v3/uploads"))

                    val firstResponse = firstSync.await()
                    assertEquals(HttpStatusCode.OK, firstResponse.status)
                    assertTrue(firstResponse.bodyAsText().contains(""""status":"synced""""))
                    assertEquals(1, strava.count("POST", "/api/v3/uploads"))
                }
            }
        }
    }

    @Test
    fun stravaSyncIgnoresClientProvidedTrainerFlagWithoutConnectedDevice() {
        MockStravaServer().use { strava ->
            testApplication {
                application { module(strava.appConfig()) }

                val token = loginToken()
                val deviceDisconnect = client.post("/devices/disconnect") {
                    bearerAuth(token)
                }
                assertEquals(HttpStatusCode.OK, deviceDisconnect.status)
                connectStrava(token)
                val started = client.post("/sessions/start") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody("""{"workoutId":"vo2-w1d1"}""")
                }
                assertEquals(HttpStatusCode.OK, started.status)
                val sessionId = started.bodyAsText().extractToken("id")

                val metrics = client.post("/sessions/$sessionId/metrics") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {"elapsedSeconds":30,"currentPower":215,"targetPower":220,"cadence":91,"heartRate":151,"speedKmh":32.5}
                        """.trimIndent(),
                    )
                }
                assertEquals(HttpStatusCode.OK, metrics.status)

                val completed = client.put("/sessions/$sessionId/complete") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody("""{"elapsedSeconds":1200,"hasRealTrainerData":true}""")
                }
                assertEquals(HttpStatusCode.OK, completed.status)
                assertTrue(!completed.bodyAsText().contains(""""hasRealTrainerData":true"""))

                val sync = client.post("/history/$sessionId/sync/strava") {
                    bearerAuth(token)
                }

                assertEquals(HttpStatusCode.BadRequest, sync.status)
                assertTrue(sync.bodyAsText().contains("real trainer"))
                assertEquals(0, strava.count("POST", "/api/v3/uploads"))
            }
        }
    }

    @Test
    fun stravaSyncCanUploadAgainAfterConnectingDifferentAthlete() {
        MockStravaServer().use { strava ->
            testApplication {
                application { module(strava.appConfig()) }

                val token = loginToken()
                connectStrava(token)
                val sessionId = completedTrainerSession(token)

                val firstSync = client.post("/history/$sessionId/sync/strava") {
                    bearerAuth(token)
                }
                assertEquals(HttpStatusCode.OK, firstSync.status)
                assertTrue(firstSync.bodyAsText().contains(""""status":"synced""""))
                assertEquals(1, strava.count("POST", "/api/v3/uploads"))

                val disconnect = client.post("/integrations/strava/disconnect") {
                    bearerAuth(token)
                }
                assertEquals(HttpStatusCode.OK, disconnect.status)

                strava.athleteId = 67890
                connectStrava(token)

                val statusAfterReconnect = client.get("/history/$sessionId/sync-status") {
                    bearerAuth(token)
                }
                assertEquals(HttpStatusCode.OK, statusAfterReconnect.status)
                assertTrue(statusAfterReconnect.bodyAsText().contains(""""status":"not_synced""""))

                val secondSync = client.post("/history/$sessionId/sync/strava") {
                    bearerAuth(token)
                }
                assertEquals(HttpStatusCode.OK, secondSync.status)
                assertTrue(secondSync.bodyAsText().contains(""""status":"synced""""))
                assertEquals(2, strava.count("POST", "/api/v3/uploads"))
            }
        }
    }

    @Test
    fun stravaTransportFailuresReturnBadGateway() = testApplication {
        val unreachableStravaBaseUrl = "http://127.0.0.1:${unusedLocalPort()}"
        application {
            module(
                testAppConfig(
                    stravaBaseUrl = unreachableStravaBaseUrl,
                    stravaClientId = "client",
                    stravaClientSecret = "secret",
                ),
            )
        }

        val token = loginToken()
        val connectUrl = client.get("/integrations/strava/connect-url") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, connectUrl.status)
        val state = connectUrl.bodyAsText().extractUrlQueryParam("state")

        val callback = client.get("/integrations/strava/callback?code=oauth-code&scope=read,activity:write&state=$state")

        assertEquals(HttpStatusCode.BadGateway, callback.status)
        val body = callback.bodyAsText()
        assertTrue(body.contains(""""code":"bad_gateway""""))
        assertTrue(body.contains("Strava request failed"))
    }
}

private fun String.extractToken(name: String): String {
    return """"$name":"([^"]+)"""".toRegex().find(this)!!.groupValues[1]
}

private suspend fun io.ktor.server.testing.ApplicationTestBuilder.loginToken(): String {
    val login = client.post("/auth/login") {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"marko@example.com","password":"password"}""")
    }
    assertEquals(HttpStatusCode.OK, login.status)
    return login.bodyAsText().extractToken("accessToken")
}

private suspend fun io.ktor.server.testing.ApplicationTestBuilder.connectStrava(token: String) {
    val connectUrl = client.get("/integrations/strava/connect-url") {
        bearerAuth(token)
    }
    assertEquals(HttpStatusCode.OK, connectUrl.status)
    val state = connectUrl.bodyAsText().extractUrlQueryParam("state")

    val callbackUrl = "/integrations/strava/callback?code=oauth-code&scope=read,activity:write&state=$state"
    val callback = client.get(callbackUrl)
    assertEquals(HttpStatusCode.OK, callback.status)
    assertTrue(callback.bodyAsText().contains("Strava is connected"))
}

private suspend fun io.ktor.server.testing.ApplicationTestBuilder.connectTrainerDevice(token: String) {
    val response = client.post("/devices/connect") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody("""{"name":"Test Smart Trainer","type":"smart_trainer","supportsErg":true}""")
    }
    assertEquals(HttpStatusCode.OK, response.status)
}

private suspend fun io.ktor.server.testing.ApplicationTestBuilder.completedTrainerSession(token: String): String {
    connectTrainerDevice(token)

    val started = client.post("/sessions/start") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody("""{"workoutId":"vo2-w1d1"}""")
    }
    assertEquals(HttpStatusCode.OK, started.status)
    val sessionId = started.bodyAsText().extractToken("id")

    val metrics = client.post("/sessions/$sessionId/metrics") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(
            """
            {"elapsedSeconds":30,"currentPower":215,"targetPower":220,"cadence":91,"heartRate":151,"speedKmh":32.5}
            """.trimIndent(),
        )
    }
    assertEquals(HttpStatusCode.OK, metrics.status)

    val completed = client.put("/sessions/$sessionId/complete") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody("""{"elapsedSeconds":1200,"hasRealTrainerData":false}""")
    }
    assertEquals(HttpStatusCode.OK, completed.status)
    assertTrue(completed.bodyAsText().contains(""""hasRealTrainerData":true"""))

    return sessionId
}

private fun String.extractUrlQueryParam(name: String): String {
    val url = extractToken("url")
    return URI.create(url).rawQuery
        .split("&")
        .first { it.startsWith("$name=") }
        .substringAfter("=")
}

private fun unusedLocalPort(): Int = ServerSocket(0).use { it.localPort }

private fun testAppConfig(
    stravaBaseUrl: String = "https://www.strava.com",
    stravaClientId: String? = null,
    stravaClientSecret: String? = null,
): AppConfig = AppConfig(
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
        clientId = stravaClientId,
        clientSecret = stravaClientSecret,
        redirectUri = "http://localhost/integrations/strava/callback",
        baseUrl = stravaBaseUrl,
    ),
    persistenceMode = PersistenceMode.IN_MEMORY,
)

private fun MockStravaServer.appConfig(): AppConfig = testAppConfig(
    stravaBaseUrl = baseUrl,
    stravaClientId = "client",
    stravaClientSecret = "secret",
)

private class MockStravaServer(
    private val uploadStatusCode: Int = 201,
    private val deauthorizeStatusCode: Int = 200,
    private val uploadDelayMillis: Long = 0,
    var athleteId: Long = 12345,
) : Closeable {
    private val requests = Collections.synchronizedList(mutableListOf<RecordedStravaRequest>())
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange -> handle(exchange) }
        start()
    }

    val baseUrl: String = "http://127.0.0.1:${server.address.port}"

    fun count(method: String, path: String): Int = synchronized(requests) {
        requests.count { it.method == method && it.path == path }
    }

    fun authorizationFor(method: String, path: String): String? = synchronized(requests) {
        requests.firstOrNull { it.method == method && it.path == path }?.authorization
    }

    fun bodyFor(method: String, path: String): String? = synchronized(requests) {
        requests.firstOrNull { it.method == method && it.path == path }?.body
    }

    suspend fun awaitCount(method: String, path: String, expected: Int, timeoutMillis: Long = 1_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (count(method, path) >= expected) return
            delay(10)
        }
        assertTrue(count(method, path) >= expected)
    }

    override fun close() {
        server.stop(0)
    }

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.use { it.readBytes() }.toString(StandardCharsets.UTF_8)
        requests += RecordedStravaRequest(
            method = exchange.requestMethod,
            path = exchange.requestURI.path,
            authorization = exchange.requestHeaders.getFirst("Authorization"),
            body = body,
        )

        when {
            exchange.requestMethod == "POST" && exchange.requestURI.path == "/oauth/token" -> {
                exchange.respond(
                    200,
                    """
                    {
                      "access_token":"test-access-token",
                      "refresh_token":"test-refresh-token",
                      "expires_at":4102444800,
                      "scope":"read,activity:write",
                      "athlete":{"id":$athleteId}
                    }
                    """.trimIndent(),
                )
            }

            exchange.requestMethod == "POST" && exchange.requestURI.path == "/oauth/deauthorize" -> {
                exchange.respond(deauthorizeStatusCode, """{}""")
            }

            exchange.requestMethod == "POST" && exchange.requestURI.path == "/api/v3/uploads" -> {
                if (uploadDelayMillis > 0) {
                    Thread.sleep(uploadDelayMillis)
                }
                exchange.respond(
                    uploadStatusCode,
                    """{"id":98765,"id_str":"98765","activity_id":55555,"status":"Your activity is ready."}""",
                )
            }

            else -> exchange.respond(404, """{}""")
        }
    }

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}

private data class RecordedStravaRequest(
    val method: String,
    val path: String,
    val authorization: String?,
    val body: String,
)
