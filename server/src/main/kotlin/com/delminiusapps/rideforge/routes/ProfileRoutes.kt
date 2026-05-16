package com.delminiusapps.rideforge.routes

import com.delminiusapps.rideforge.dto.UpdateFtpRequest
import com.delminiusapps.rideforge.dto.UpdateProfileRequest
import com.delminiusapps.rideforge.dto.UpdateWeightRequest
import com.delminiusapps.rideforge.plugins.ServiceRegistry
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.profileRoutes(registry: ServiceRegistry) {
    route("/profile") {
        get {
            call.respond(registry.profileService.getProfile(call.userId()))
        }
        put {
            call.respond(registry.profileService.updateProfile(call.userId(), call.receive<UpdateProfileRequest>()))
        }
        put("/ftp") {
            call.respond(registry.profileService.updateFtp(call.userId(), call.receive<UpdateFtpRequest>()))
        }
        put("/weight") {
            call.respond(registry.profileService.updateWeight(call.userId(), call.receive<UpdateWeightRequest>()))
        }
    }
}
