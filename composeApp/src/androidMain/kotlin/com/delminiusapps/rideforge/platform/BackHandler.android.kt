package com.delminiusapps.rideforge.platform

import androidx.compose.runtime.Composable

/**
 * Android implementation uses the standard Activity-based BackHandler.
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled, onBack)
}
