package com.delminiusapps.rideforge.core.network

data class AppConfig(
    val baseUrl: String = defaultServerConfig().baseUrl,
    val devEmail: String = "marko@example.com",
    val devPassword: String = "password",
)

expect fun defaultServerConfig(): ServerConfig
