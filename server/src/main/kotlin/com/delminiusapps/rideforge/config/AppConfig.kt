package com.delminiusapps.rideforge.config

data class AppConfig(
    val port: Int,
    val databaseUrl: String,
    val jwt: JwtConfig,
)

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val accessTokenMinutes: Long,
    val refreshTokenDays: Long,
)

fun loadAppConfig(): AppConfig {
    return AppConfig(
        port = env("PORT")?.toIntOrNull() ?: 8080,
        databaseUrl = env("DATABASE_URL") ?: "postgresql://localhost:5432/rideforge",
        jwt = JwtConfig(
            secret = env("JWT_SECRET") ?: "dev-rideforge-change-me",
            issuer = env("JWT_ISSUER") ?: "rideforge-api",
            audience = env("JWT_AUDIENCE") ?: "rideforge-mobile",
            realm = env("JWT_REALM") ?: "rideforge",
            accessTokenMinutes = env("JWT_ACCESS_MINUTES")?.toLongOrNull() ?: 60,
            refreshTokenDays = env("JWT_REFRESH_DAYS")?.toLongOrNull() ?: 30,
        ),
    )
}

private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
