package com.delminiusapps.rideforge.plugins

import com.delminiusapps.rideforge.config.JwtConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.cors.routing.CORS

const val JwtAuthName = "auth-jwt"

fun Application.configureSecurity(registry: ServiceRegistry, jwtConfig: JwtConfig) {
    install(Authentication) {
        jwt(JwtAuthName) {
            realm = jwtConfig.realm
            verifier(registry.jwtService.verifier)
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                val type = credential.payload.getClaim("type").asString()
                if (!userId.isNullOrBlank() && type == "access") JWTPrincipal(credential.payload) else null
            }
        }
    }
}

fun Application.configureCors() {
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Requested-With")
    }
}
