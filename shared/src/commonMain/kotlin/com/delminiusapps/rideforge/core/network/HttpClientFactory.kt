package com.delminiusapps.rideforge.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun platformHttpClientEngine(): HttpClientEngineFactory<*>

fun createRideForgeHttpClient(): HttpClient {
    return HttpClient(platformHttpClientEngine()) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    explicitNulls = false
                },
            )
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }
}
