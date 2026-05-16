package com.delminiusapps.rideforge.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.core.network.AppConfig
import com.delminiusapps.rideforge.domain.usecase.LoginUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val appConfig: AppConfig,
) : ViewModel() {
    private val _state = MutableStateFlow(
        LoginState(
            email = appConfig.devEmail,
            password = appConfig.devPassword,
        ),
    )
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.EmailChanged -> _state.update { it.copy(email = action.email) }
            is LoginAction.PasswordChanged -> _state.update { it.copy(password = action.password) }
            LoginAction.Submit -> login()
            LoginAction.ClearError -> _state.update { it.copy(error = null) }
            LoginAction.UseDemoCredentials -> _state.update {
                it.copy(email = appConfig.devEmail, password = appConfig.devPassword, error = null)
            }
        }
    }

    private fun login() {
        if (_state.value.isLoading) return
        val email = _state.value.email.trim()
        val password = _state.value.password
        val validationError = when {
            email.isBlank() -> AuthFormError.EmailRequired
            password.isBlank() -> AuthFormError.PasswordRequired
            else -> null
        }
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch {
            runCatching {
                loginUseCase(email, password)
            }.onSuccess {
                _state.update { it.copy(isLoading = false, isSuccess = true) }
            }.onFailure {
                _state.update { it.copy(isLoading = false, error = AuthFormError.RequestFailed) }
            }
        }
    }
}

data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: AuthFormError? = null,
    val isSuccess: Boolean = false,
)

enum class AuthFormError {
    NameRequired,
    EmailRequired,
    PasswordRequired,
    PasswordTooShort,
    RequestFailed,
}

sealed interface LoginAction {
    data class EmailChanged(val email: String) : LoginAction
    data class PasswordChanged(val password: String) : LoginAction
    data object Submit : LoginAction
    data object ClearError : LoginAction
    data object UseDemoCredentials : LoginAction
}
