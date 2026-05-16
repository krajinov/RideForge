package com.delminiusapps.rideforge.platform

import androidx.compose.runtime.Composable

/**
 * Multiplatform abstraction for handling system back button presses.
 */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
