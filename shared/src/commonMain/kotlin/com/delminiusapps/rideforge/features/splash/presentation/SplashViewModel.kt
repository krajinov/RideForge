package com.delminiusapps.rideforge.features.splash.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.data.auth.AuthManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

class SplashViewModel(
    private val authManager: AuthManager,
) : ViewModel() {
    private val _state = MutableStateFlow<SplashState>(SplashState.Loading)
    val state: StateFlow<SplashState> = _state.asStateFlow()

    private var hasCheckedSession = false

    fun checkSession() {
        if (hasCheckedSession) return
        hasCheckedSession = true

        viewModelScope.launch {
            val startedAt = TimeSource.Monotonic.markNow()
            val result = runCatching { authManager.restoreSession() }
            val remainingMillis = MinimumSplashDurationMillis - startedAt.elapsedNow().inWholeMilliseconds
            if (remainingMillis > 0) {
                delay(remainingMillis)
            }

            _state.value = result.fold(
                onSuccess = { isAuthenticated ->
                    if (isAuthenticated) SplashState.Authenticated else SplashState.Unauthenticated
                },
                onFailure = { error ->
                    runCatching { authManager.logout() }
                    SplashState.Error(error.message)
                },
            )
        }
    }
}

sealed interface SplashState {
    data object Loading : SplashState
    data object Authenticated : SplashState
    data object Unauthenticated : SplashState
    data class Error(val message: String? = null) : SplashState
}

private const val MinimumSplashDurationMillis = 900L
