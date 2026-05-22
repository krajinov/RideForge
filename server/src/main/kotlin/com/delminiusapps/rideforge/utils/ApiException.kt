package com.delminiusapps.rideforge.utils

import io.ktor.http.HttpStatusCode

class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val details: Map<String, String> = emptyMap(),
) : RuntimeException(message)

fun badRequest(message: String, details: Map<String, String> = emptyMap()): Nothing {
    throw ApiException(HttpStatusCode.BadRequest, "bad_request", message, details)
}

fun unauthorized(message: String = "Authentication is required"): Nothing {
    throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", message)
}

fun forbidden(message: String = "Access denied"): Nothing {
    throw ApiException(HttpStatusCode.Forbidden, "forbidden", message)
}

fun notFound(resource: String): Nothing {
    throw ApiException(HttpStatusCode.NotFound, "not_found", "$resource was not found")
}

fun conflict(message: String): Nothing {
    throw ApiException(HttpStatusCode.Conflict, "conflict", message)
}

fun serviceUnavailable(message: String): Nothing {
    throw ApiException(HttpStatusCode.ServiceUnavailable, "service_unavailable", message)
}

fun badGateway(message: String): Nothing {
    throw ApiException(HttpStatusCode.BadGateway, "bad_gateway", message)
}
