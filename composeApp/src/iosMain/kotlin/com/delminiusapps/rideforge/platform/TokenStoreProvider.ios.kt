package com.delminiusapps.rideforge.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.delminiusapps.rideforge.data.auth.AuthTokens
import com.delminiusapps.rideforge.data.auth.TokenStore
import platform.Foundation.NSUserDefaults

@Composable
actual fun rememberPlatformTokenStore(): TokenStore {
    return remember { IosTokenStore() }
}

private class IosTokenStore : TokenStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun getTokens(): AuthTokens? {
        val accessToken = defaults.stringForKey("rideforge_access_token")
        val refreshToken = defaults.stringForKey("rideforge_refresh_token")
        return if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
            AuthTokens(accessToken, refreshToken)
        } else {
            null
        }
    }

    override suspend fun saveTokens(tokens: AuthTokens) {
        defaults.setObject(tokens.accessToken, "rideforge_access_token")
        defaults.setObject(tokens.refreshToken, "rideforge_refresh_token")
    }

    override suspend fun clearTokens() {
        defaults.removeObjectForKey("rideforge_access_token")
        defaults.removeObjectForKey("rideforge_refresh_token")
    }
}
