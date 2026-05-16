package com.delminiusapps.rideforge

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
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
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("rideforge-api"))
    }

    @Test
    fun loginAndRecommendedWorkoutWork() = testApplication {
        application { module() }

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
        application { module() }

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
        application { module() }

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
}

private fun String.extractToken(name: String): String {
    return """"$name":"([^"]+)"""".toRegex().find(this)!!.groupValues[1]
}
