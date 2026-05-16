package com.delminiusapps.rideforge.routes

import com.delminiusapps.rideforge.dto.LoginRequest
import com.delminiusapps.rideforge.dto.RefreshRequest
import com.delminiusapps.rideforge.dto.RegisterRequest
import com.delminiusapps.rideforge.plugins.JwtAuthName
import com.delminiusapps.rideforge.plugins.ServiceRegistry
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(registry: ServiceRegistry) {
    route("/auth") {
        post("/register") {
            call.respond(registry.authService.register(call.receive<RegisterRequest>()))
        }
        post("/login") {
            call.respond(registry.authService.login(call.receive<LoginRequest>()))
        }
        post("/refresh") {
            call.respond(registry.authService.refresh(call.receive<RefreshRequest>().refreshToken))
        }
        authenticate(JwtAuthName) {
            get("/me") {
                call.respond(registry.authService.me(call.userId()))
            }
            post("/logout") {
                val request = runCatching { call.receive<RefreshRequest>() }.getOrNull()
                registry.authService.logout(call.userId(), request?.refreshToken)
                call.respond(io.ktor.http.HttpStatusCode.NoContent)
            }
        }
    }
}
