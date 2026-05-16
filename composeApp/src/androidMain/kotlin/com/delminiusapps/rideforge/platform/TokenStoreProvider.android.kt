package com.delminiusapps.rideforge.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.delminiusapps.rideforge.data.auth.AuthTokens
import com.delminiusapps.rideforge.data.auth.TokenStore

@Composable
actual fun rememberPlatformTokenStore(): TokenStore {
    val context = LocalContext.current.applicationContext
    return remember { AndroidTokenStore(context) }
}

private class AndroidTokenStore(context: Context) : TokenStore {
    private val preferences = context.getSharedPreferences("rideforge_auth", Context.MODE_PRIVATE)

    override suspend fun getTokens(): AuthTokens? {
        val accessToken = preferences.getString("access_token", null)
        val refreshToken = preferences.getString("refresh_token", null)
        return if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
            AuthTokens(accessToken, refreshToken)
        } else {
            null
        }
    }

    override suspend fun saveTokens(tokens: AuthTokens) {
        preferences.edit()
            .putString("access_token", tokens.accessToken)
            .putString("refresh_token", tokens.refreshToken)
            .apply()
    }

    override suspend fun clearTokens() {
        preferences.edit().clear().apply()
    }
}
