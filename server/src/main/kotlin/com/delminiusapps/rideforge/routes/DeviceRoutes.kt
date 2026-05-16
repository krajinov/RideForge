package com.delminiusapps.rideforge.routes

import com.delminiusapps.rideforge.dto.ConnectDeviceRequest
import com.delminiusapps.rideforge.dto.DeviceResponse
import com.delminiusapps.rideforge.plugins.ServiceRegistry
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.deviceRoutes(registry: ServiceRegistry) {
    route("/devices") {
        get {
            call.respond(registry.deviceService.list(call.userId()))
        }
        post("/connect") {
            call.respond(registry.deviceService.connect(call.userId(), call.receive<ConnectDeviceRequest>()))
        }
        post("/disconnect") {
            call.respond(DeviceResponse(registry.deviceService.disconnect(call.userId())))
        }
        get("/current") {
            call.respond(DeviceResponse(registry.deviceService.current(call.userId())))
        }
    }
}
