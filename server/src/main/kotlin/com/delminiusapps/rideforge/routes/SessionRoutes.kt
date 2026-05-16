package com.delminiusapps.rideforge.routes

import com.delminiusapps.rideforge.dto.CompleteSessionRequest
import com.delminiusapps.rideforge.dto.MetricSampleRequest
import com.delminiusapps.rideforge.dto.StartSessionRequest
import com.delminiusapps.rideforge.plugins.ServiceRegistry
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.sessionRoutes(registry: ServiceRegistry) {
    route("/sessions") {
        post("/start") {
            call.respond(registry.sessionService.start(call.userId(), call.receive<StartSessionRequest>()))
        }
        put("/{id}/pause") {
            call.respond(registry.sessionService.pause(call.userId(), call.requiredPath("id")))
        }
        put("/{id}/resume") {
            call.respond(registry.sessionService.resume(call.userId(), call.requiredPath("id")))
        }
        put("/{id}/complete") {
            val request = runCatching { call.receive<CompleteSessionRequest>() }.getOrElse { CompleteSessionRequest() }
            call.respond(registry.sessionService.complete(call.userId(), call.requiredPath("id"), request))
        }
        post("/{id}/metrics") {
            call.respond(registry.sessionService.addMetric(call.userId(), call.requiredPath("id"), call.receive<MetricSampleRequest>()))
        }
        get("/{id}/metrics") {
            call.respond(registry.sessionService.getMetrics(call.userId(), call.requiredPath("id")))
        }
    }
}
