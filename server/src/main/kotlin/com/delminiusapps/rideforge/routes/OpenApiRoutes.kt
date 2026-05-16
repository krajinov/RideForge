package com.delminiusapps.rideforge.routes

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.openApiRoutes() {
    get("/openapi.json") {
        call.respondText(openApiJson, ContentType.Application.Json)
    }
}

private val openApiJson = """
{
  "openapi": "3.0.3",
  "info": {
    "title": "RideForge API",
    "version": "1.0.0",
    "description": "Mock-backed Ktor API foundation for the RideForge indoor cycling app."
  },
  "servers": [{ "url": "http://localhost:8080" }],
  "security": [{ "bearerAuth": [] }],
  "paths": {
    "/auth/register": { "post": { "summary": "Register a user" } },
    "/auth/login": { "post": { "summary": "Login" } },
    "/auth/refresh": { "post": { "summary": "Refresh tokens" } },
    "/auth/logout": { "post": { "summary": "Logout and revoke refresh token" } },
    "/auth/me": { "get": { "summary": "Current user" } },
    "/profile": { "get": { "summary": "Get profile" }, "put": { "summary": "Update profile" } },
    "/profile/ftp": { "put": { "summary": "Update FTP" } },
    "/profile/weight": { "put": { "summary": "Update weight" } },
    "/plans": { "get": { "summary": "List training plans" } },
    "/plans/{id}": { "get": { "summary": "Get training plan" } },
    "/plans/{id}/workouts": { "get": { "summary": "Get plan workouts" } },
    "/plans/{id}/enroll": { "post": { "summary": "Enroll in plan" } },
    "/my-plan": { "get": { "summary": "Get enrolled plan" } },
    "/workouts": { "get": { "summary": "List workouts" } },
    "/workouts/recommended": { "get": { "summary": "Recommended workout" } },
    "/workouts/{id}": { "get": { "summary": "Get workout" } },
    "/workouts/{id}/intervals": { "get": { "summary": "Get FTP-adjusted intervals" } },
    "/sessions/start": { "post": { "summary": "Start workout session" } },
    "/sessions/{id}/pause": { "put": { "summary": "Pause session" } },
    "/sessions/{id}/resume": { "put": { "summary": "Resume session" } },
    "/sessions/{id}/complete": { "put": { "summary": "Complete session" } },
    "/sessions/{id}/metrics": { "post": { "summary": "Add metric sample" } },
    "/history": { "get": { "summary": "List workout history" } },
    "/history/{id}": { "get": { "summary": "Get history item" }, "delete": { "summary": "Delete history item" } },
    "/devices": { "get": { "summary": "List devices" } },
    "/devices/connect": { "post": { "summary": "Connect placeholder device" } },
    "/devices/disconnect": { "post": { "summary": "Disconnect placeholder device" } },
    "/devices/current": { "get": { "summary": "Current device" } }
  },
  "components": {
    "securitySchemes": {
      "bearerAuth": { "type": "http", "scheme": "bearer", "bearerFormat": "JWT" }
    }
  }
}
""".trimIndent()
