package com.delminiusapps.rideforge.services

import com.delminiusapps.rideforge.config.StravaConfig
import com.delminiusapps.rideforge.utils.badGateway
import com.delminiusapps.rideforge.utils.serviceUnavailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

class StravaApiClient(
    private val config: StravaConfig,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun exchangeCode(code: String): StravaTokenResult {
        val dto = postForm<StravaTokenResponse>(
            path = "/oauth/token",
            fields = mapOf(
                "client_id" to requireClientId(),
                "client_secret" to requireClientSecret(),
                "code" to code,
                "grant_type" to "authorization_code",
            ),
        )
        return dto.toResult()
    }

    suspend fun refreshToken(refreshToken: String): StravaTokenResult {
        val dto = postForm<StravaTokenResponse>(
            path = "/oauth/token",
            fields = mapOf(
                "client_id" to requireClientId(),
                "client_secret" to requireClientSecret(),
                "refresh_token" to refreshToken,
                "grant_type" to "refresh_token",
            ),
        )
        return dto.toResult()
    }

    suspend fun uploadTcx(
        accessToken: String,
        fileName: String,
        tcx: String,
        name: String,
        description: String,
        externalId: String,
    ): StravaUploadResult {
        val boundary = "RideForge-${UUID.randomUUID()}"
        val body = multipartBody(
            boundary = boundary,
            fields = mapOf(
                "data_type" to "tcx",
                "sport_type" to "VirtualRide",
                "trainer" to "1",
                "commute" to "0",
                "name" to name,
                "description" to description,
                "external_id" to externalId,
            ),
            fileName = fileName,
            fileContent = tcx.toByteArray(StandardCharsets.UTF_8),
        )
        val request = HttpRequest.newBuilder(apiUri("/api/v3/uploads"))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()
        return sendAndDecodeUpload(request)
    }

    suspend fun uploadStatus(accessToken: String, uploadId: String): StravaUploadResult {
        val request = HttpRequest.newBuilder(apiUri("/api/v3/uploads/$uploadId"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer $accessToken")
            .GET()
            .build()
        return sendAndDecodeUpload(request)
    }

    suspend fun deauthorize(accessToken: String) {
        val body = "access_token=${encode(accessToken)}"
        val request = HttpRequest.newBuilder(apiUri("/oauth/deauthorize"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = send(request)
        if (response.statusCode() !in 200..299) {
            badGateway("Strava deauthorization failed with HTTP ${response.statusCode()}")
        }
    }

    private suspend inline fun <reified T> postForm(path: String, fields: Map<String, String>): T {
        val body = fields.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val request = HttpRequest.newBuilder(apiUri(path))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = send(request)
        if (response.statusCode() !in 200..299) {
            badGateway("Strava request failed with HTTP ${response.statusCode()}")
        }
        return json.decodeFromString(response.body())
    }

    private suspend fun sendAndDecodeUpload(request: HttpRequest): StravaUploadResult {
        val response = send(request)
        if (response.statusCode() !in 200..299) {
            badGateway("Strava upload request failed with HTTP ${response.statusCode()}")
        }
        return json.decodeFromString<StravaUploadResponse>(response.body()).toResult()
    }

    private suspend fun send(request: HttpRequest): HttpResponse<String> = withContext(Dispatchers.IO) {
        try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (error: IOException) {
            badGateway("Strava request failed: ${error.message ?: error.javaClass.simpleName}")
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            badGateway("Strava request was interrupted")
        }
    }

    private fun apiUri(path: String): URI = URI.create("${config.baseUrl.trimEnd('/')}$path")

    private fun requireClientId(): String =
        config.clientId ?: serviceUnavailable("Strava client id is not configured")

    private fun requireClientSecret(): String =
        config.clientSecret ?: serviceUnavailable("Strava client secret is not configured")

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun multipartBody(
        boundary: String,
        fields: Map<String, String>,
        fileName: String,
        fileContent: ByteArray,
    ): ByteArray {
        val crlf = "\r\n"
        val output = ByteArrayOutputStream()
        fun write(value: String) {
            output.write(value.toByteArray(StandardCharsets.UTF_8))
        }

        fields.forEach { (name, value) ->
            write("--$boundary$crlf")
            write("Content-Disposition: form-data; name=\"$name\"$crlf$crlf")
            write(value)
            write(crlf)
        }
        write("--$boundary$crlf")
        write("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"$crlf")
        write("Content-Type: application/vnd.garmin.tcx+xml$crlf$crlf")
        output.write(fileContent)
        write(crlf)
        write("--$boundary--$crlf")
        return output.toByteArray()
    }
}

data class StravaTokenResult(
    val athleteId: String?,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val scope: String?,
)

data class StravaUploadResult(
    val uploadId: String?,
    val activityId: String?,
    val status: String?,
    val error: String?,
    val externalId: String?,
)

@Serializable
private data class StravaTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: Long,
    val scope: String? = null,
    val athlete: StravaAthleteResponse? = null,
)

@Serializable
private data class StravaAthleteResponse(val id: Long? = null)

@Serializable
private data class StravaUploadResponse(
    val id: Long? = null,
    @SerialName("id_str") val idString: String? = null,
    @SerialName("activity_id") val activityId: Long? = null,
    val status: String? = null,
    val error: String? = null,
    @SerialName("external_id") val externalId: String? = null,
)

private fun StravaTokenResponse.toResult(): StravaTokenResult = StravaTokenResult(
    athleteId = athlete?.id?.toString(),
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresAtEpochSeconds = expiresAt,
    scope = scope,
)

private fun StravaUploadResponse.toResult(): StravaUploadResult = StravaUploadResult(
    uploadId = idString ?: id?.toString(),
    activityId = activityId?.toString(),
    status = status,
    error = error,
    externalId = externalId,
)
