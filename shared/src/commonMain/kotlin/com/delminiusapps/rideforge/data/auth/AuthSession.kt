package com.delminiusapps.rideforge.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

typealias AuthTokens = com.delminiusapps.rideforge.models.AuthTokens
typealias AuthSession = com.delminiusapps.rideforge.models.AuthSession

interface TokenStorage {
    suspend fun getTokens(): AuthTokens?
    suspend fun saveTokens(tokens: AuthTokens)
    suspend fun clearTokens()
}

typealias TokenStore = TokenStorage

class InMemoryTokenStore : TokenStorage {
    private var tokens: AuthTokens? = null

    override suspend fun getTokens(): AuthTokens? = tokens

    override suspend fun saveTokens(tokens: AuthTokens) {
        this.tokens = tokens
    }

    override suspend fun clearTokens() {
        tokens = null
    }
}

class AuthSessionStore {
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    fun setAuthenticated(value: Boolean) {
        _isAuthenticated.value = value
    }
}
