package com.delminiusapps.rideforge.features.profile.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import com.delminiusapps.rideforge.models.UserProfile
import com.delminiusapps.rideforge.presentation.components.AppCard
import com.delminiusapps.rideforge.presentation.components.LoadingState
import com.delminiusapps.rideforge.presentation.components.PowerZoneRow
import com.delminiusapps.rideforge.presentation.components.PrimaryButton
import com.delminiusapps.rideforge.presentation.components.ScreenHeader
import com.delminiusapps.rideforge.presentation.components.ScreenLazyColumn
import com.delminiusapps.rideforge.presentation.components.SecondaryButton
import com.delminiusapps.rideforge.presentation.components.AppButton
import com.delminiusapps.rideforge.presentation.components.AppButtonVariant
import com.delminiusapps.rideforge.theme.ForgeBorder
import com.delminiusapps.rideforge.theme.ForgeCard
import com.delminiusapps.rideforge.theme.ForgeGreen
import com.delminiusapps.rideforge.theme.ForgeSurface
import com.delminiusapps.rideforge.theme.ForgeMuted
import com.delminiusapps.rideforge.theme.ForgeStrava
import com.delminiusapps.rideforge.theme.ForgeText
import com.delminiusapps.rideforge.theme.RideForgeRadius

import androidx.compose.material.icons.rounded.ChevronRight
import com.delminiusapps.rideforge.models.FtpEstimate
import com.delminiusapps.rideforge.models.FtpHistoryRecord
import com.delminiusapps.rideforge.theme.ForgeRed
import com.delminiusapps.rideforge.theme.ForgeYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val readyState = state as? ProfileUiState.Ready
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileEvent.OpenUrl -> runCatching { uriHandler.openUri(event.url) }
            }
        }
    }

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
                
                uiState.pendingFtpEstimate?.let { estimate ->
                    item {
                        FtpEstimateBanner(
                            estimate = estimate,
                            onApprove = { viewModel.onAction(ProfileAction.ApproveFtp(estimate.id)) },
                            onDismiss = { viewModel.onAction(ProfileAction.DismissFtp(estimate.id)) },
                            isApplying = uiState.isApplyingFtp
                        )
                    }
                }
                
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
                    FtpHistoryCard(uiState.ftpHistory)
                }
                item {
                    StravaIntegrationCard(
                        isConnected = uiState.isStravaConnected,
                        isBusy = uiState.isStravaBusy,
                        hasPendingAuthorization = uiState.hasPendingStravaAuthorization,
                        error = uiState.stravaError,
                        onToggleConnection = { viewModel.onAction(ProfileAction.ToggleStravaConnection) },
                        onSyncSettings = { viewModel.onAction(ProfileAction.StravaSync) },
                    )
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

@Composable
private fun FtpEstimateBanner(
    estimate: FtpEstimate,
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
    isApplying: Boolean
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(ForgeGreen, CircleShape)
                )
                Text(
                    text = "New FTP Estimate Detected",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = ForgeGreen
                )
            }
            Text(
                text = estimate.message,
                fontSize = 14.sp,
                color = ForgeMuted
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${estimate.previousFtp} W",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ForgeMuted
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = ForgeMuted,
                    modifier = Modifier.padding(horizontal = 16.dp).size(24.dp)
                )
                Text(
                    text = "${estimate.estimatedFtp} W",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = ForgeGreen
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppButton(
                    text = if (isApplying) "Applying..." else "Apply",
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    enabled = !isApplying,
                    variant = AppButtonVariant.Primary
                )
                AppButton(
                    text = "Dismiss",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !isApplying,
                    variant = AppButtonVariant.Secondary
                )
            }
        }
    }
}

@Composable
private fun FtpHistoryCard(history: List<FtpHistoryRecord>) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("FTP History", fontWeight = FontWeight.Bold)
            if (history.isEmpty()) {
                Text("No FTP changes recorded yet.", color = ForgeMuted, fontSize = 14.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    history.forEach { record ->
                        FtpHistoryRow(record)
                    }
                }
            }
        }
    }
}

@Composable
private fun FtpHistoryRow(record: FtpHistoryRecord) {
    val diff = record.estimatedFtp - record.previousFtp
    val diffText = if (diff >= 0) "+$diff W" else "$diff W"
    val diffColor = if (diff >= 0) ForgeGreen else ForgeRed
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${record.previousFtp} W → ${record.estimatedFtp} W",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = record.message,
                color = ForgeMuted,
                fontSize = 12.sp
            )
        }
        
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = diffText,
                color = diffColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            val badgeColor = when (record.status.lowercase()) {
                "approved" -> ForgeGreen
                "dismissed" -> ForgeMuted
                else -> ForgeYellow
            }
            Text(
                text = record.status.uppercase(),
                color = badgeColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun StravaIntegrationCard(
    isConnected: Boolean,
    isBusy: Boolean,
    hasPendingAuthorization: Boolean,
    error: String?,
    onToggleConnection: () -> Unit,
    onSyncSettings: () -> Unit,
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Integrations", fontWeight = FontWeight.Bold)

            // Strava row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Strava icon
                Surface(
                    modifier = Modifier.size(40.dp),
                    color = ForgeStrava.copy(alpha = 0.14f),
                    contentColor = ForgeStrava,
                    shape = RoundedCornerShape(RideForgeRadius.Control),
                    border = BorderStroke(1.dp, ForgeStrava.copy(alpha = 0.25f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = StravaIcon,
                            contentDescription = "Strava",
                            modifier = Modifier.size(22.dp),
                            tint = ForgeStrava,
                        )
                    }
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Strava", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    StravaStatusBadge(isConnected)
                }
            }

            // Action buttons
            if (isConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppButton(
                        text = "Disconnect",
                        onClick = onToggleConnection,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.LinkOff,
                        enabled = !isBusy,
                        variant = AppButtonVariant.Secondary,
                    )
                    AppButton(
                        text = "Refresh",
                        onClick = onSyncSettings,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Sync,
                        enabled = !isBusy,
                        variant = AppButtonVariant.Quiet,
                    )
                }
            } else {
                StravaConnectButton(
                    isBusy = isBusy,
                    onClick = { if (!isBusy) onToggleConnection() },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (hasPendingAuthorization) {
                    Text("After authorizing in Chrome, check the connection status here.", color = ForgeMuted)
                }
                AppButton(
                    text = if (isBusy) "Checking..." else "Check connection",
                    onClick = onSyncSettings,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Sync,
                    enabled = !isBusy,
                    variant = AppButtonVariant.Quiet,
                )
            }

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StravaStatusBadge(isConnected: Boolean) {
    val color = if (isConnected) ForgeGreen else ForgeMuted
    val label = if (isConnected) "Connected" else "Not connected"
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(99.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(color, CircleShape),
            )
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StravaConnectButton(isBusy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        color = ForgeStrava,
        contentColor = ForgeText,
        shape = RoundedCornerShape(RideForgeRadius.Button),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = StravaIcon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isBusy) "Opening Strava..." else "Connect Strava",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
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

private val StravaIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Strava",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 16f,
        viewportHeight = 16f,
    ).apply {
        // Large upward chevron (left-ish)
        path(fill = SolidColor(Color.Black)) {
            moveTo(6.731f, 0f)
            lineTo(2f, 9.125f)
            lineTo(4.788f, 9.125f)
            lineTo(6.73f, 5.497f)
            lineTo(8.66f, 9.125f)
            lineTo(11.426f, 9.125f)
            close()
        }
        // Small downward chevron (right-ish)
        path(fill = SolidColor(Color.Black)) {
            moveTo(11.425f, 9.125f)
            lineTo(10.053f, 11.881f)
            lineTo(8.66f, 9.125f)
            lineTo(6.547f, 9.125f)
            lineTo(10.053f, 16f)
            lineTo(13.537f, 9.125f)
            close()
        }
    }.build()
}
