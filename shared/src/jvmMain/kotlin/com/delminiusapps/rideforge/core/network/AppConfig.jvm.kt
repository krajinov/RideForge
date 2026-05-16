package com.delminiusapps.rideforge.core.network

import com.delminiusapps.rideforge.SERVER_PORT

actual fun defaultServerConfig(): ServerConfig {
    return DefaultServerConfig(host = ServerHosts.LOCALHOST, port = SERVER_PORT)
}
