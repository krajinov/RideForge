package com.delminiusapps.rideforge.core.network

import com.delminiusapps.rideforge.SERVER_PORT

interface ServerConfig {
    val baseUrl: String
}

class DefaultServerConfig(
    val host: String,
    val port: Int = SERVER_PORT,
) : ServerConfig {
    override val baseUrl: String = "http://$host:$port"
}

class StaticServerConfig(
    baseUrl: String,
) : ServerConfig {
    override val baseUrl: String = normalizeApiBaseUrl(baseUrl)
}

private fun normalizeApiBaseUrl(url: String): String = url.trim().trimEnd('/')

object ServerHosts {
    const val ANDROID_EMULATOR = "10.0.2.2"
    const val LOCALHOST = "localhost"
    const val DEVICE_LAN = "192.168.168.75"
}

