package com.delminiusapps.rideforge.config

data class AppConfig(
    val port: Int,
    val databaseUrl: String,
    val jwt: JwtConfig,
    val strava: StravaConfig,
    val databaseUser: String? = null,
    val databasePassword: String? = null,
    val databaseMaxPoolSize: Int = 10,
    val migrateDatabaseOnStart: Boolean = true,
    val seedDatabaseOnStart: Boolean = true,
    val persistenceMode: PersistenceMode = PersistenceMode.POSTGRES,
)

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val accessTokenMinutes: Long,
    val refreshTokenDays: Long,
)

data class StravaConfig(
    val clientId: String?,
    val clientSecret: String?,
    val redirectUri: String,
    val baseUrl: String = "https://www.strava.com",
)

enum class PersistenceMode {
    POSTGRES,
    IN_MEMORY,
}

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
        strava = StravaConfig(
            clientId = env("STRAVA_CLIENT_ID"),
            clientSecret = env("STRAVA_CLIENT_SECRET"),
            redirectUri = env("STRAVA_REDIRECT_URI") ?: "http://localhost:8080/integrations/strava/callback",
            baseUrl = env("STRAVA_BASE_URL") ?: "https://www.strava.com",
        ),
        databaseUser = env("DATABASE_USER"),
        databasePassword = env("DATABASE_PASSWORD"),
        databaseMaxPoolSize = env("DATABASE_MAX_POOL_SIZE")?.toIntOrNull() ?: 10,
        migrateDatabaseOnStart = envFlag("DATABASE_MIGRATE_ON_START", default = true),
        seedDatabaseOnStart = envFlag("DATABASE_SEED_ON_START", default = true),
        persistenceMode = when (env("PERSISTENCE_MODE")?.lowercase()) {
            "memory", "in_memory", "in-memory", "test" -> PersistenceMode.IN_MEMORY
            else -> PersistenceMode.POSTGRES
        },
    )
}

private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

private fun envFlag(name: String, default: Boolean): Boolean =
    when (env(name)?.lowercase()) {
        "1", "true", "yes", "y", "on" -> true
        "0", "false", "no", "n", "off" -> false
        null -> default
        else -> default
    }
