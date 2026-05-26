package com.delminiusapps.rideforge.plugins

import com.delminiusapps.rideforge.routes.authRoutes
import com.delminiusapps.rideforge.routes.deviceRoutes
import com.delminiusapps.rideforge.routes.historyRoutes
import com.delminiusapps.rideforge.routes.openApiRoutes
import com.delminiusapps.rideforge.routes.planRoutes
import com.delminiusapps.rideforge.routes.profileRoutes
import com.delminiusapps.rideforge.routes.sessionRoutes
import com.delminiusapps.rideforge.routes.stravaCallbackRoutes
import com.delminiusapps.rideforge.routes.stravaIntegrationRoutes
import com.delminiusapps.rideforge.routes.workoutRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
private data class HealthResponse(val status: String, val service: String)

fun Application.configureRouting(registry: ServiceRegistry) {
    routing {
        get("/") {
            call.respond(HealthResponse("ok", "rideforge-api"))
        }
        get("/health") {
            call.respond(HealthResponse("ok", "rideforge-api"))
        }
        openApiRoutes()
        authRoutes(registry)
        stravaCallbackRoutes(registry)
        authenticate(JwtAuthName) {
            profileRoutes(registry)
            planRoutes(registry)
            workoutRoutes(registry)
            sessionRoutes(registry)
            historyRoutes(registry)
            deviceRoutes(registry)
            stravaIntegrationRoutes(registry)
        }
    }
}
