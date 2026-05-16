package com.delminiusapps.rideforge.routes

import com.delminiusapps.rideforge.plugins.ServiceRegistry
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.workoutRoutes(registry: ServiceRegistry) {
    route("/workouts") {
        get {
            val (limit, offset) = call.paging()
            call.respond(registry.workoutService.list(call.userId(), limit, offset))
        }
        get("/recommended") {
            call.respond(registry.workoutService.recommended(call.userId()))
        }
        get("/{id}") {
            call.respond(registry.workoutService.get(call.userId(), call.requiredPath("id")))
        }
        get("/{id}/intervals") {
            call.respond(registry.workoutService.intervals(call.userId(), call.requiredPath("id")))
        }
    }
}
