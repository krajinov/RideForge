package com.delminiusapps.rideforge.core.network

import com.delminiusapps.rideforge.SERVER_PORT
import platform.Foundation.NSProcessInfo

actual fun defaultServerConfig(): ServerConfig {
    val host = if (isSimulator()) {
        ServerHosts.LOCALHOST
    } else {
        ServerHosts.DEVICE_LAN
    }
    return DefaultServerConfig(host = host, port = SERVER_PORT)
}

private fun isSimulator(): Boolean {
    return NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != null
}
