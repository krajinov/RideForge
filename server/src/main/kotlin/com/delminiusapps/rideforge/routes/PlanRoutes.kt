package com.delminiusapps.rideforge.routes

import com.delminiusapps.rideforge.plugins.ServiceRegistry
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.planRoutes(registry: ServiceRegistry) {
    route("/plans") {
        get {
            val (limit, offset) = call.paging()
            call.respond(registry.trainingPlanService.list(limit, offset))
        }
        get("/{id}") {
            call.respond(registry.trainingPlanService.get(call.requiredPath("id")))
        }
        get("/{id}/workouts") {
            call.respond(registry.trainingPlanService.workoutsForPlan(call.userId(), call.requiredPath("id")))
        }
        post("/{id}/enroll") {
            call.respond(registry.trainingPlanService.enroll(call.userId(), call.requiredPath("id")))
        }
        post("/{id}/join") {
            call.respond(registry.trainingPlanService.enroll(call.userId(), call.requiredPath("id")))
        }
        post("/{id}/leave") {
            registry.trainingPlanService.leave(call.userId(), call.requiredPath("id"))
            call.respond(mapOf("status" to "left"))
        }
        get("/joined") {
            call.respond(registry.trainingPlanService.getJoinedPlans(call.userId()))
        }
        get("/{id}/completed-workouts") {
            call.respond(registry.trainingPlanService.getCompletedWorkouts(call.userId(), call.requiredPath("id")))
        }
    }
    get("/my-plan") {
        val plan = registry.trainingPlanService.myPlan(call.userId())
        if (plan == null) call.respond(HttpStatusCode.NoContent) else call.respond(plan)
    }
}
