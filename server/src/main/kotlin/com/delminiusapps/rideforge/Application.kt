package com.delminiusapps.rideforge

import com.delminiusapps.rideforge.config.loadAppConfig
import com.delminiusapps.rideforge.plugins.ServiceRegistry
import com.delminiusapps.rideforge.plugins.configureCors
import com.delminiusapps.rideforge.plugins.configureErrorHandling
import com.delminiusapps.rideforge.plugins.configureMonitoring
import com.delminiusapps.rideforge.plugins.configureRouting
import com.delminiusapps.rideforge.plugins.configureSecurity
import com.delminiusapps.rideforge.plugins.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val config = loadAppConfig()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config)
    }.start(wait = true)
}

fun Application.module() {
    module(loadAppConfig())
}

fun Application.module(config: com.delminiusapps.rideforge.config.AppConfig) {
    val registry = ServiceRegistry(config)
    configureMonitoring()
    configureCors()
    configureSerialization()
    configureErrorHandling()
    configureSecurity(registry, config.jwt)
    configureRouting(registry)
}
