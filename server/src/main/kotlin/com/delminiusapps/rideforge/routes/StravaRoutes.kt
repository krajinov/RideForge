package com.delminiusapps.rideforge.routes

import com.delminiusapps.rideforge.plugins.ServiceRegistry
import io.ktor.http.ContentType
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.stravaCallbackRoutes(registry: ServiceRegistry) {
    get("/integrations/strava/callback") {
        call.respondText(
            registry.stravaService.completeOAuthCallback(
                code = call.request.queryParameters["code"],
                state = call.request.queryParameters["state"],
                scope = call.request.queryParameters["scope"],
                error = call.request.queryParameters["error"],
            ),
            ContentType.Text.Html,
        )
    }
}

fun Route.stravaIntegrationRoutes(registry: ServiceRegistry) {
    route("/integrations/strava") {
        get("/status") {
            call.respond(registry.stravaService.status(call.userId()))
        }
        get("/connect-url") {
            call.respond(registry.stravaService.connectUrl(call.userId()))
        }
        post("/disconnect") {
            call.respond(registry.stravaService.disconnect(call.userId()))
        }
    }
}
