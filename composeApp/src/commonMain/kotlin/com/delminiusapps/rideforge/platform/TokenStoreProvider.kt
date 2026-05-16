package com.delminiusapps.rideforge.platform

import androidx.compose.runtime.Composable
import com.delminiusapps.rideforge.data.auth.TokenStore

@Composable
expect fun rememberPlatformTokenStore(): TokenStore
