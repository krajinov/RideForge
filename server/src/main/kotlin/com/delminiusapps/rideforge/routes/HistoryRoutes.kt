package com.delminiusapps.rideforge.routes

import com.delminiusapps.rideforge.plugins.ServiceRegistry
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.historyRoutes(registry: ServiceRegistry) {
    route("/history") {
        get {
            val (limit, offset) = call.paging()
            call.respond(registry.sessionService.history(call.userId(), limit, offset))
        }
        get("/{id}") {
            call.respond(registry.sessionService.historyItem(call.userId(), call.requiredPath("id")))
        }
        delete("/{id}") {
            registry.sessionService.deleteHistory(call.userId(), call.requiredPath("id"))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
