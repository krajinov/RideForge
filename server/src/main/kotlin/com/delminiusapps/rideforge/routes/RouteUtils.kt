package com.delminiusapps.rideforge.routes

import com.delminiusapps.rideforge.utils.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal

fun ApplicationCall.userId(): String {
    return principal<JWTPrincipal>()
        ?.payload
        ?.getClaim("userId")
        ?.asString()
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Authentication is required")
}

fun ApplicationCall.paging(): Pair<Int, Int> {
    val limit = request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
    val offset = request.queryParameters["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    return limit to offset
}

fun ApplicationCall.requiredPath(name: String): String {
    return parameters[name] ?: throw ApiException(HttpStatusCode.BadRequest, "missing_path_parameter", "$name path parameter is required")
}
