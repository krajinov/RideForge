package com.delminiusapps.rideforge.core.network

import android.os.Build
import com.delminiusapps.rideforge.SERVER_PORT

actual fun defaultServerConfig(): ServerConfig {
    val host = if (isAndroidEmulator()) {
        ServerHosts.ANDROID_EMULATOR
    } else {
        ServerHosts.DEVICE_LAN
    }
    return DefaultServerConfig(host = host, port = SERVER_PORT)
}

private fun isAndroidEmulator(): Boolean {
    return Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.startsWith("unknown") ||
        Build.MODEL.contains("google_sdk") ||
        Build.MODEL.contains("Emulator") ||
        Build.MODEL.contains("Android SDK built for x86") ||
        Build.MANUFACTURER.contains("Genymotion") ||
        Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
        Build.PRODUCT == "google_sdk" ||
        Build.HARDWARE.contains("goldfish") ||
        Build.HARDWARE.contains("ranchu")
}
