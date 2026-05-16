package com.delminiusapps.rideforge.features.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.domain.usecase.GetCurrentUserUseCase
import com.delminiusapps.rideforge.domain.usecase.LogoutUseCase
import com.delminiusapps.rideforge.domain.usecase.UpdateProfileUseCase
import com.delminiusapps.rideforge.models.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            runCatching {
                getCurrentUserUseCase()
            }.onSuccess { profile ->
                _state.update { ProfileUiState.Ready(profile) }
            }.onFailure {
                // handle error
            }
        }
    }

    fun onAction(action: ProfileAction) {
        when (action) {
            ProfileAction.DismissEditor -> dismissEditor()
            ProfileAction.EditProfile -> showEditor()
            ProfileAction.Logout -> logout()
            is ProfileAction.SaveProfile -> saveProfile(action)
        }
    }

    private fun showEditor() {
        _state.update { state ->
            when (state) {
                is ProfileUiState.Ready -> state.copy(isEditorOpen = true, editError = null)
                else -> state
            }
        }
    }

    private fun dismissEditor() {
        _state.update { state ->
            when (state) {
                is ProfileUiState.Ready -> if (state.isSaving) state else state.copy(isEditorOpen = false, editError = null)
                else -> state
            }
        }
    }

    private fun saveProfile(action: ProfileAction.SaveProfile) {
        viewModelScope.launch {
            _state.update { state ->
                when (state) {
                    is ProfileUiState.Ready -> state.copy(isSaving = true, editError = null)
                    else -> state
                }
            }
            runCatching {
                updateProfileUseCase(action.ftpWatts, action.weightKg, action.units)
            }.onSuccess { profile ->
                _state.update { ProfileUiState.Ready(profile = profile, isEditorOpen = false) }
            }.onFailure {
                _state.update { state ->
                    when (state) {
                        is ProfileUiState.Ready -> state.copy(
                            isSaving = false,
                            editError = "Could not save profile. Check the values and try again.",
                        )
                        else -> state
                    }
                }
            }
        }
    }

    private fun logout() {
        viewModelScope.launch {
            runCatching {
                logoutUseCase()
            }.onSuccess {
                _state.update { ProfileUiState.LoggedOut }
            }.onFailure {
                _state.update { ProfileUiState.LoggedOut }
            }
        }
    }
}

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Ready(
        val profile: UserProfile,
        val isEditorOpen: Boolean = false,
        val isSaving: Boolean = false,
        val editError: String? = null,
    ) : ProfileUiState
    data object LoggedOut : ProfileUiState
}

sealed interface ProfileAction {
    data object EditProfile : ProfileAction
    data object DismissEditor : ProfileAction
    data class SaveProfile(val ftpWatts: Int, val weightKg: Double, val units: String) : ProfileAction
    data object Logout : ProfileAction
}
