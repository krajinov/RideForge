package com.delminiusapps.rideforge.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.core.network.AppConfig
import com.delminiusapps.rideforge.domain.usecase.RegisterUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val registerUseCase: RegisterUseCase,
    appConfig: AppConfig,
) : ViewModel() {
    private val _state = MutableStateFlow(
        RegisterState(
            email = appConfig.devEmail,
            password = appConfig.devPassword,
        ),
    )
    val state: StateFlow<RegisterState> = _state.asStateFlow()

    fun onAction(action: RegisterAction) {
        when (action) {
            is RegisterAction.NameChanged -> _state.update { it.copy(name = action.name) }
            is RegisterAction.EmailChanged -> _state.update { it.copy(email = action.email) }
            is RegisterAction.PasswordChanged -> _state.update { it.copy(password = action.password) }
            RegisterAction.Submit -> register()
            RegisterAction.ClearError -> _state.update { it.copy(error = null) }
        }
    }

    private fun register() {
        if (_state.value.isLoading) return
        val name = _state.value.name.trim()
        val email = _state.value.email.trim()
        val password = _state.value.password
        val validationError = when {
            name.isBlank() -> AuthFormError.NameRequired
            email.isBlank() -> AuthFormError.EmailRequired
            password.isBlank() -> AuthFormError.PasswordRequired
            password.length < 8 -> AuthFormError.PasswordTooShort
            else -> null
        }
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return
        }

        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                registerUseCase(name, email, password)
            }.onSuccess {
                _state.update { it.copy(isLoading = false, isSuccess = true) }
            }.onFailure {
                _state.update { it.copy(isLoading = false, error = AuthFormError.RequestFailed) }
            }
        }
    }
}

data class RegisterState(
    val name: String = "Marko",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: AuthFormError? = null,
    val isSuccess: Boolean = false,
)

sealed interface RegisterAction {
    data class NameChanged(val name: String) : RegisterAction
    data class EmailChanged(val email: String) : RegisterAction
    data class PasswordChanged(val password: String) : RegisterAction
    data object Submit : RegisterAction
    data object ClearError : RegisterAction
}
