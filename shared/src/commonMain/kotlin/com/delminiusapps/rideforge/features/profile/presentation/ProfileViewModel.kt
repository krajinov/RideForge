package com.delminiusapps.rideforge.features.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delminiusapps.rideforge.domain.usecase.DisconnectStravaUseCase
import com.delminiusapps.rideforge.domain.usecase.GetCurrentUserUseCase
import com.delminiusapps.rideforge.domain.usecase.GetStravaConnectUrlUseCase
import com.delminiusapps.rideforge.domain.usecase.GetStravaStatusUseCase
import com.delminiusapps.rideforge.domain.usecase.LogoutUseCase
import com.delminiusapps.rideforge.domain.usecase.UpdateProfileUseCase
import com.delminiusapps.rideforge.models.UserProfile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val getStravaStatusUseCase: GetStravaStatusUseCase,
    private val getStravaConnectUrlUseCase: GetStravaConnectUrlUseCase,
    private val disconnectStravaUseCase: DisconnectStravaUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEvent>()
    val events: SharedFlow<ProfileEvent> = _events.asSharedFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            runCatching {
                val profile = getCurrentUserUseCase()
                val strava = runCatching { getStravaStatusUseCase() }.getOrNull()
                profile to strava
            }.onSuccess { (profile, strava) ->
                _state.update {
                    ProfileUiState.Ready(
                        profile = profile,
                        isStravaConnected = strava?.connected == true,
                    )
                }
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
            ProfileAction.ToggleStravaConnection -> toggleStravaConnection()
            ProfileAction.StravaSync -> refreshStravaStatus()
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
                _state.update { state ->
                    when (state) {
                        is ProfileUiState.Ready -> state.copy(
                            profile = profile,
                            isEditorOpen = false,
                            isSaving = false,
                            editError = null,
                        )
                        else -> ProfileUiState.Ready(profile = profile)
                    }
                }
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

    private fun toggleStravaConnection() {
        val ready = _state.value as? ProfileUiState.Ready ?: return
        if (ready.isStravaBusy) return
        viewModelScope.launch {
            _state.update { state ->
                when (state) {
                    is ProfileUiState.Ready -> state.copy(isStravaBusy = true, stravaError = null)
                    else -> state
                }
            }
            if (ready.isStravaConnected) {
                runCatching { disconnectStravaUseCase() }
                    .onSuccess { status ->
                        _state.update { state ->
                            when (state) {
                                is ProfileUiState.Ready -> state.copy(
                                    isStravaConnected = status.connected,
                                    isStravaBusy = false,
                                    hasPendingStravaAuthorization = false,
                                    stravaError = null,
                                )
                                else -> state
                            }
                        }
                    }
                    .onFailure {
                        updateStravaFailure("Could not disconnect Strava. Try again.")
                    }
            } else {
                runCatching { getStravaConnectUrlUseCase() }
                    .onSuccess { url ->
                        _state.update { state ->
                            when (state) {
                                is ProfileUiState.Ready -> state.copy(
                                    isStravaBusy = false,
                                    hasPendingStravaAuthorization = true,
                                    stravaError = null,
                                )
                                else -> state
                            }
                        }
                        _events.emit(ProfileEvent.OpenUrl(url))
                    }
                    .onFailure {
                        updateStravaFailure("Could not start Strava connection. Check backend Strava configuration.")
                    }
            }
        }
    }

    private fun refreshStravaStatus() {
        viewModelScope.launch {
            _state.update { state ->
                when (state) {
                    is ProfileUiState.Ready -> state.copy(isStravaBusy = true, stravaError = null)
                    else -> state
                }
            }
            runCatching { getStravaStatusUseCase() }
                .onSuccess { status ->
                    _state.update { state ->
                        when (state) {
                            is ProfileUiState.Ready -> state.copy(
                                isStravaConnected = status.connected,
                                isStravaBusy = false,
                                hasPendingStravaAuthorization = if (status.connected) false else state.hasPendingStravaAuthorization,
                                stravaError = null,
                            )
                            else -> state
                        }
                    }
                }
                .onFailure {
                    updateStravaFailure("Could not refresh Strava status. Try again.")
                }
        }
    }

    private fun updateStravaFailure(message: String) {
        _state.update { state ->
            when (state) {
                is ProfileUiState.Ready -> state.copy(isStravaBusy = false, stravaError = message)
                else -> state
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
        val isStravaConnected: Boolean = false,
        val isStravaBusy: Boolean = false,
        val hasPendingStravaAuthorization: Boolean = false,
        val stravaError: String? = null,
    ) : ProfileUiState
    data object LoggedOut : ProfileUiState
}

sealed interface ProfileAction {
    data object EditProfile : ProfileAction
    data object DismissEditor : ProfileAction
    data class SaveProfile(val ftpWatts: Int, val weightKg: Double, val units: String) : ProfileAction
    data object Logout : ProfileAction
    data object ToggleStravaConnection : ProfileAction
    data object StravaSync : ProfileAction
}

sealed interface ProfileEvent {
    data class OpenUrl(val url: String) : ProfileEvent
}
