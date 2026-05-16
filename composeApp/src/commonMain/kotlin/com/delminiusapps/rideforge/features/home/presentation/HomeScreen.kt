package com.delminiusapps.rideforge.features.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.delminiusapps.rideforge.navigation.AppRoute
import com.delminiusapps.rideforge.presentation.components.AppCard
import com.delminiusapps.rideforge.presentation.components.ConnectionStatusCard
import com.delminiusapps.rideforge.presentation.components.LoadingState
import com.delminiusapps.rideforge.presentation.components.MetricCard
import com.delminiusapps.rideforge.presentation.components.PrimaryButton
import com.delminiusapps.rideforge.presentation.components.ScreenLazyColumn
import com.delminiusapps.rideforge.presentation.components.SecondaryButton
import com.delminiusapps.rideforge.presentation.components.WeeklyProgressCard
import com.delminiusapps.rideforge.presentation.components.WorkoutCard
import com.delminiusapps.rideforge.theme.ForgeBlue
import com.delminiusapps.rideforge.theme.ForgeGreen
import com.delminiusapps.rideforge.theme.ForgeMuted
import com.delminiusapps.rideforge.theme.ForgeSurfaceHigh

@Composable
fun HomeScreen(
    onNavigate: (AppRoute) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ready = state as? HomeUiState.Ready

    ScreenLazyColumn {
        item { DashboardHeader(ready?.dashboard?.user?.name ?: "Rider") }

        when (val uiState = state) {
            is HomeUiState.Loading -> {
                item { LoadingState("Loading dashboard...") }
            }
            is HomeUiState.Error -> {
                item {
                    com.delminiusapps.rideforge.presentation.components.ErrorState(
                        message = "We couldn't load your dashboard. Please check your connection and try again.",
                        actionLabel = "Retry",
                        onAction = { viewModel.onAction(HomeAction.Refresh) }
                    )
                }
            }
            is HomeUiState.Ready -> {
                val dashboard = uiState.dashboard
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricCard("FTP", "${dashboard.user.ftpWatts} W", modifier = Modifier.weight(1f))
                        MetricCard("Status", "Ready", ForgeGreen, Modifier.weight(1f))
                    }
                }
                item {
                    ConnectionStatusCard(
                        status = dashboard.trainerStatus,
                        deviceName = dashboard.trainerDevice?.name ?: "No trainer selected",
                        onClick = { onNavigate(AppRoute.Trainer) },
                    )
                }
                item {
                    WorkoutCard(
                        workout = dashboard.workout,
                        onClick = { onNavigate(AppRoute.Workout(dashboard.workout.id)) },
                    )
                }
                item { WeeklyProgressCard(dashboard.progress) }
                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = ForgeBlue, modifier = Modifier.size(18.dp))
                                Text("Quick actions", fontWeight = FontWeight.Bold)
                            }
                            PrimaryButton("Start Workout", { onNavigate(AppRoute.ActiveWorkout(dashboard.workout.id)) }, Modifier.fillMaxWidth(), Icons.Rounded.PlayArrow)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SecondaryButton("Plans", { onNavigate(AppRoute.Plans) }, Modifier.weight(1f), Icons.AutoMirrored.Rounded.ListAlt)
                                SecondaryButton("History", { onNavigate(AppRoute.History) }, Modifier.weight(1f), Icons.Rounded.History)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(name: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(color = ForgeSurfaceHigh, contentColor = ForgeBlue, shape = CircleShape) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.DirectionsBike, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text("RideForge", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text("Good morning, $name", color = ForgeMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Surface(color = ForgeGreen.copy(alpha = 0.16f), contentColor = ForgeGreen, shape = CircleShape) {
            Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.padding(9.dp).size(18.dp))
        }
    }
}
