package com.delminiusapps.rideforge.features.trainer.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delminiusapps.rideforge.domain.trainer.ConnectionState
import com.delminiusapps.rideforge.domain.trainer.SmartTrainerDevice
import com.delminiusapps.rideforge.navigation.AppRoute
import com.delminiusapps.rideforge.presentation.components.AppCard
import com.delminiusapps.rideforge.presentation.components.ErrorState
import com.delminiusapps.rideforge.presentation.components.MetricCard
import com.delminiusapps.rideforge.presentation.components.PrimaryButton
import com.delminiusapps.rideforge.presentation.components.ScreenHeader
import com.delminiusapps.rideforge.presentation.components.ScreenLazyColumn
import com.delminiusapps.rideforge.presentation.components.SecondaryButton
import com.delminiusapps.rideforge.presentation.components.SmallPill
import com.delminiusapps.rideforge.presentation.trainer.TrainerAction
import com.delminiusapps.rideforge.presentation.trainer.TrainerUiState
import com.delminiusapps.rideforge.presentation.trainer.TrainerViewModel
import com.delminiusapps.rideforge.theme.ForgeBlue
import com.delminiusapps.rideforge.theme.ForgeGreen
import com.delminiusapps.rideforge.theme.ForgeMuted
import com.delminiusapps.rideforge.theme.ForgeOrange
import com.delminiusapps.rideforge.theme.ForgeRed
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TrainerScreen(
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
    viewModel: TrainerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    ScreenLazyColumn {
        item {
            ScreenHeader(
                "Smart Trainer",
                "FTMS Bluetooth trainer control",
                onBack,
            )
        }
        item {
            TrainerStatusCard(
                state = state,
                onScan = { viewModel.onAction(TrainerAction.Scan) },
                onDisconnect = { viewModel.onAction(TrainerAction.Disconnect) },
            )
        }
        state.error?.let { error ->
            item {
                ErrorState(
                    title = error.type.name.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() },
                    message = error.message,
                )
            }
        }
        item {
            DeviceListCard(
                state = state,
                onConnect = { viewModel.onAction(TrainerAction.Connect(it)) },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Power", "${state.metrics.powerWatts} W", modifier = Modifier.weight(1f))
                MetricCard("Cadence", "${state.metrics.cadence} rpm", ForgeGreen, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Speed", "${state.metrics.speedKmh.toString().take(4)} km/h", ForgeBlue, Modifier.weight(1f))
                MetricCard("Heart", "${state.metrics.heartRate}", ForgeRed, Modifier.weight(1f))
            }
        }
        item {
            ErgControlCard(
                state = state,
                onErgChanged = { enabled ->
                    viewModel.onAction(if (enabled) TrainerAction.EnableErg else TrainerAction.DisableErg)
                },
                onTargetPowerChanged = { viewModel.onAction(TrainerAction.SetTargetPower(it)) },
                onResistanceChanged = { viewModel.onAction(TrainerAction.SetResistance(it)) },
            )
        }
        item {
            PrimaryButton(
                "Start ERG Test",
                { onNavigate(AppRoute.Workout(id = "free-ride")) },
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TrainerStatusCard(
    state: TrainerUiState,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ConnectionDot(state.connectionState)
                Column(Modifier.weight(1f)) {
                    Text(connectionLabel(state.connectionState), fontWeight = FontWeight.Bold)
                    Text(state.connectedDevice?.name ?: "No trainer connected", color = ForgeMuted, fontSize = 13.sp)
                }
                state.connectedDevice?.let {
                    SmallPill(if (it.supportsErg) "ERG" else "FTMS", if (it.supportsErg) ForgeGreen else ForgeOrange)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PrimaryButton(
                    text = if (state.connectionState == ConnectionState.SCANNING) "Scanning" else "Scan",
                    onClick = onScan,
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Rounded.BluetoothSearching,
                    enabled = state.connectionState != ConnectionState.SCANNING,
                )
                SecondaryButton(
                    text = "Disconnect",
                    onClick = onDisconnect,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.LinkOff,
                    enabled = state.connectedDevice != null,
                )
            }
        }
    }
}

@Composable
private fun DeviceListCard(
    state: TrainerUiState,
    onConnect: (String) -> Unit,
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Detected devices", fontWeight = FontWeight.Bold)
            if (state.devices.isEmpty()) {
                Text("No trainers found. Start a scan and keep the trainer awake nearby.", color = ForgeMuted)
            } else {
                state.devices.forEach { device ->
                    DeviceRow(
                        device = device,
                        selected = state.connectedDevice?.id == device.id,
                        onClick = { onConnect(device.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: SmartTrainerDevice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConnectionDot(device.connectionState)
        Column(Modifier.weight(1f)) {
            Text(device.name, fontWeight = FontWeight.Bold)
            Text("${device.rssi} dBm", color = ForgeMuted, fontSize = 13.sp)
        }
        if (selected) {
            SmallPill("Connected", ForgeGreen)
        } else if (device.supportsErg) {
            SmallPill("ERG", ForgeBlue)
        }
    }
}

@Composable
private fun ErgControlCard(
    state: TrainerUiState,
    onErgChanged: (Boolean) -> Unit,
    onTargetPowerChanged: (Int) -> Unit,
    onResistanceChanged: (Int) -> Unit,
) {
    val canControl = state.connectedDevice?.supportsErg == true && state.connectionState == ConnectionState.CONNECTED
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ERG mode", fontWeight = FontWeight.Bold)
                    Text("FTMS target power control", color = ForgeMuted, fontSize = 13.sp)
                }
                Switch(
                    checked = state.controlState.ergEnabled,
                    onCheckedChange = onErgChanged,
                    enabled = canControl,
                )
            }
            Text("Target power ${state.controlState.targetPower}W (Req: ${state.selectedTargetPower}W)", fontWeight = FontWeight.SemiBold)
            Slider(
                value = state.selectedTargetPower.toFloat(),
                onValueChange = { onTargetPowerChanged(it.toInt()) },
                valueRange = 80f..450f,
                steps = 36,
                enabled = canControl,
            )
            Text("Resistance ${state.controlState.currentResistance}", fontWeight = FontWeight.SemiBold)
            Slider(
                value = state.controlState.currentResistance.toFloat(),
                onValueChange = { onResistanceChanged(it.toInt()) },
                valueRange = 0f..100f,
                steps = 19,
                enabled = state.connectionState == ConnectionState.CONNECTED,
            )
        }
    }
}

@Composable
private fun ConnectionDot(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.CONNECTED -> ForgeGreen
        ConnectionState.CONNECTING, ConnectionState.SCANNING -> ForgeOrange
        ConnectionState.DISCONNECTED -> ForgeRed
    }
    Box(Modifier.size(12.dp).background(color, CircleShape))
}

private fun connectionLabel(state: ConnectionState): String = when (state) {
    ConnectionState.DISCONNECTED -> "Disconnected"
    ConnectionState.SCANNING -> "Scanning for FTMS trainers"
    ConnectionState.CONNECTING -> "Connecting"
    ConnectionState.CONNECTED -> "Connected"
}
