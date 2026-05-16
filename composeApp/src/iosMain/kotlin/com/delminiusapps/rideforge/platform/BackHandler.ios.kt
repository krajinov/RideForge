package com.delminiusapps.rideforge.platform

import androidx.compose.runtime.Composable

/**
 * iOS implementation is a no-op as iOS handles back navigation via gestures/UI.
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op for iOS
}
