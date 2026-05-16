package com.delminiusapps.rideforge.features.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.delminiusapps.rideforge.models.UserProfile
import com.delminiusapps.rideforge.presentation.components.AppCard
import com.delminiusapps.rideforge.presentation.components.LoadingState
import com.delminiusapps.rideforge.presentation.components.PowerZoneRow
import com.delminiusapps.rideforge.presentation.components.PrimaryButton
import com.delminiusapps.rideforge.presentation.components.ScreenHeader
import com.delminiusapps.rideforge.presentation.components.ScreenLazyColumn
import com.delminiusapps.rideforge.presentation.components.SecondaryButton
import com.delminiusapps.rideforge.theme.ForgeSurface
import com.delminiusapps.rideforge.theme.ForgeMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val readyState = state as? ProfileUiState.Ready

    if (readyState?.isEditorOpen == true) {
        EditProfileSheet(
            profile = readyState.profile,
            isSaving = readyState.isSaving,
            error = readyState.editError,
            onDismiss = { viewModel.onAction(ProfileAction.DismissEditor) },
            onSave = { ftpWatts, weightKg, units ->
                viewModel.onAction(ProfileAction.SaveProfile(ftpWatts, weightKg, units))
            },
        )
    }

    ScreenLazyColumn {
        item { ScreenHeader("Profile", "Settings and rider data") }
        
        when (val uiState = state) {
            is ProfileUiState.Loading -> item { LoadingState("Loading profile...") }
            is ProfileUiState.Ready -> {
                val loadedProfile = uiState.profile
                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProfileRow("Name", loadedProfile.name)
                            ProfileRow("FTP", "${loadedProfile.ftpWatts} W")
                            ProfileRow("Weight", "${formatWeight(loadedProfile.weightKg)} kg")
                            ProfileRow("Units", loadedProfile.units)
                            ProfileRow("Connected device", loadedProfile.connectedDevice)
                            ProfileRow("Subscription", loadedProfile.subscription)
                            PrimaryButton(
                                text = "Edit profile",
                                onClick = { viewModel.onAction(ProfileAction.EditProfile) },
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Rounded.Edit,
                            )
                        }
                    }
                }
                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Power zones", fontWeight = FontWeight.Bold)
                            loadedProfile.powerZones.forEach { PowerZoneRow(it) }
                        }
                    }
                }
                item {
                    SecondaryButton(
                        text = "Log out",
                        onClick = {
                            viewModel.onAction(ProfileAction.Logout)
                            onLogout()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            is ProfileUiState.LoggedOut -> {
                // handled by the callback side effect typically, but we call onLogout synchronously
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(
    profile: UserProfile,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (ftpWatts: Int, weightKg: Double, units: String) -> Unit,
) {
    var ftpText by remember(profile.ftpWatts) { mutableStateOf(profile.ftpWatts.toString()) }
    var weightText by remember(profile.weightKg) { mutableStateOf(formatWeight(profile.weightKg)) }
    var selectedUnits by remember(profile.units) { mutableStateOf(profile.units.lowercase()) }
    val ftpWatts = ftpText.toIntOrNull()
    val weightKg = weightText.toDoubleOrNull()
    val canSave = ftpWatts in 80..600 && weightKg != null && weightKg in 35.0..180.0 && !isSaving

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = ForgeSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Edit profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = ftpText,
                onValueChange = { ftpText = it },
                label = { Text("FTP") },
                suffix = { Text("W") },
                singleLine = true,
                enabled = !isSaving,
                isError = ftpText.isNotBlank() && ftpWatts !in 80..600,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it },
                label = { Text("Weight") },
                suffix = { Text("kg") },
                singleLine = true,
                enabled = !isSaving,
                isError = weightText.isNotBlank() && (weightKg == null || weightKg !in 35.0..180.0),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Units", color = ForgeMuted, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UnitChip(
                        text = "Metric",
                        selected = selectedUnits == "metric",
                        enabled = !isSaving,
                        onClick = { selectedUnits = "metric" },
                    )
                    UnitChip(
                        text = "Imperial",
                        selected = selectedUnits == "imperial",
                        enabled = !isSaving,
                        onClick = { selectedUnits = "imperial" },
                    )
                }
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving,
                )
                PrimaryButton(
                    text = if (isSaving) "Saving..." else "Save",
                    onClick = {
                        val parsedFtp = ftpWatts
                        val parsedWeight = weightKg
                        if (parsedFtp != null && parsedWeight != null) {
                            onSave(parsedFtp, parsedWeight, selectedUnits)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Save,
                    enabled = canSave,
                )
            }
        }
    }
}

@Composable
private fun UnitChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(text, fontWeight = FontWeight.SemiBold) },
    )
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = ForgeMuted)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatWeight(weightKg: Double): String {
    return if (weightKg % 1.0 == 0.0) {
        weightKg.toInt().toString()
    } else {
        weightKg.toString()
    }
}
