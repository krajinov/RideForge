package com.delminiusapps.rideforge.data.auth

import com.delminiusapps.rideforge.domain.usecase.LogoutUseCase
import com.delminiusapps.rideforge.domain.usecase.RestoreAuthSessionUseCase
import kotlinx.coroutines.flow.StateFlow

class AuthManager(
    private val authSessionStore: AuthSessionStore,
    private val restoreAuthSessionUseCase: RestoreAuthSessionUseCase,
    private val logoutUseCase: LogoutUseCase,
) {
    val isAuthenticated: StateFlow<Boolean> = authSessionStore.isAuthenticated

    suspend fun restoreSession(): Boolean = restoreAuthSessionUseCase()

    suspend fun logout() {
        logoutUseCase()
    }
}
