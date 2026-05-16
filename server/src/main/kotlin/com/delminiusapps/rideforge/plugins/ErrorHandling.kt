package com.delminiusapps.rideforge.plugins

import com.auth0.jwt.exceptions.JWTVerificationException
import com.delminiusapps.rideforge.dto.ErrorResponse
import com.delminiusapps.rideforge.utils.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.code, cause.message, cause.details))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_payload", cause.message ?: "Invalid request payload"))
        }
        exception<JWTVerificationException> { call, _ ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Invalid token"))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled API error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error", "Unexpected server error"))
        }
    }
}
