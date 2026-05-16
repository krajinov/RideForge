package com.delminiusapps.rideforge.core.network

import com.delminiusapps.rideforge.data.auth.AuthTokens
import com.delminiusapps.rideforge.data.auth.AuthSessionStore
import com.delminiusapps.rideforge.data.auth.TokenStore
import com.delminiusapps.rideforge.data.dto.AuthResponseDto
import com.delminiusapps.rideforge.data.dto.LoginRequestDto
import com.delminiusapps.rideforge.data.dto.RefreshRequestDto
import com.delminiusapps.rideforge.data.dto.RegisterRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

class ApiClient(
    @PublishedApi internal val httpClient: HttpClient,
    @PublishedApi internal val config: AppConfig,
    @PublishedApi internal val tokenStore: TokenStore,
    @PublishedApi internal val authSessionStore: AuthSessionStore,
) {
    suspend fun register(name: String, email: String, password: String): AuthResponseDto {
        val response = httpClient.post("${config.baseUrl}/auth/register") {
            setBody(RegisterRequestDto(email = email, password = password, name = name))
        }
        val auth = response.decode<AuthResponseDto>()
        tokenStore.saveTokens(AuthTokens(auth.accessToken, auth.refreshToken))
        return auth
    }

    suspend fun login(email: String, password: String): AuthResponseDto {
        val response = httpClient.post("${config.baseUrl}/auth/login") {
            setBody(LoginRequestDto(email, password))
        }
        val auth = response.decode<AuthResponseDto>()
        tokenStore.saveTokens(AuthTokens(auth.accessToken, auth.refreshToken))
        return auth
    }

    suspend fun restoreTokens(): AuthTokens? {
        val tokens = tokenStore.getTokens() ?: return null
        return if (tokens.accessToken.isNotBlank() && tokens.refreshToken.isNotBlank()) tokens else null
    }

    suspend fun logout() {
        val tokens = tokenStore.getTokens()
        runCatching {
            if (tokens != null) {
                httpClient.post("${config.baseUrl}/auth/logout") {
                    bearerAuth(tokens.accessToken)
                    setBody(RefreshRequestDto(tokens.refreshToken))
                }
            }
        }
        tokenStore.clearTokens()
        authSessionStore.setAuthenticated(false)
    }

    suspend inline fun <reified T> get(path: String): T {
        val response = authedRequest<Unit>(path, "GET", Unit)
        return response.decode()
    }

    suspend inline fun <reified T, reified R> post(path: String, body: T): R {
        val response = authedRequest(path, "POST", body)
        return response.decode()
    }

    suspend inline fun <reified T, reified R> put(path: String, body: T): R {
        val response = authedRequest(path, "PUT", body)
        return response.decode()
    }

    suspend fun close() {
        httpClient.close()
    }

    @PublishedApi
    internal suspend inline fun <reified T> authedRequest(path: String, method: String, bodyObj: T? = null): HttpResponse {
        var token = tokenStore.getTokens()?.accessToken
            ?: throw ApiClientException(HttpStatusCode.Unauthorized.value, "No saved auth token")
        
        var response = makeRequest(path, method, bodyObj, token)
        
        if (response.status == HttpStatusCode.Unauthorized) {
            token = refreshTokensOrThrow().accessToken
            response = makeRequest(path, method, bodyObj, token)
        }
        
        return response
    }

    @PublishedApi
    internal suspend inline fun <reified T> makeRequest(path: String, method: String, bodyObj: T?, token: String): HttpResponse {
        return httpClient.request("${config.baseUrl}$path") {
            this.method = io.ktor.http.HttpMethod.parse(method)
            bearerAuth(token)
            if (bodyObj != null && bodyObj !is Unit) {
                setBody(bodyObj)
            }
        }
    }

    @PublishedApi
    internal suspend fun refreshTokensOrThrow(): AuthTokens {
        val refreshToken = tokenStore.getTokens()?.refreshToken
            ?: throw ApiClientException(HttpStatusCode.Unauthorized.value, "No saved refresh token")
        val response = httpClient.post("${config.baseUrl}/auth/refresh") {
            setBody(RefreshRequestDto(refreshToken))
        }
        if (!response.status.isSuccess()) {
            tokenStore.clearTokens()
            authSessionStore.setAuthenticated(false)
            throw ApiClientException(response.status.value, "Refresh failed with HTTP ${response.status.value}")
        }
        val auth = response.body<AuthResponseDto>()
        val tokens = AuthTokens(auth.accessToken, auth.refreshToken)
        tokenStore.saveTokens(tokens)
        return tokens
    }

    @PublishedApi
    internal suspend inline fun <reified T> HttpResponse.decode(): T {
        if (!status.isSuccess()) {
            throw ApiClientException(status.value, "Request failed with HTTP ${status.value}")
        }
        return body()
    }
}

class ApiClientException(val statusCode: Int, message: String) : RuntimeException(message)
