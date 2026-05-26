package com.delminiusapps.rideforge.features.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontStyle
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

    LaunchedEffect(Unit) {
        viewModel.onAction(HomeAction.ScreenVisible)
    }

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
                val adaptive = uiState.adaptive

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricCard("FTP", "${dashboard.user.ftpWatts} W", modifier = Modifier.weight(1f))
                        
                        val tsbVal = adaptive?.fatigue?.tsb ?: 0.0
                        val balanceColor = when {
                            tsbVal < -20.0 -> ForgeMuted
                            tsbVal > 5.0 -> ForgeGreen
                            else -> ForgeBlue
                        }
                        MetricCard("Fatigue Balance", "${tsbVal.toInt()} TSB", balanceColor, Modifier.weight(1f))
                    }
                }

                val pending = adaptive?.pendingFtpEstimate
                if (pending != null) {
                    item {
                        AppCard {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("FTP Change Detected", fontWeight = FontWeight.Bold, color = ForgeGreen, style = MaterialTheme.typography.titleMedium)
                                Text(pending.message, style = MaterialTheme.typography.bodyMedium, color = ForgeMuted)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PrimaryButton("Approve", { viewModel.onAction(HomeAction.ApproveFtp(pending.id)) }, Modifier.weight(1f))
                                    SecondaryButton("Dismiss", { viewModel.onAction(HomeAction.DismissFtp(pending.id)) }, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                item {
                    ConnectionStatusCard(
                        status = dashboard.trainerStatus,
                        deviceName = dashboard.trainerDevice?.name ?: "No trainer selected",
                        onClick = { onNavigate(AppRoute.Trainer) },
                    )
                }

                if (adaptive?.recommendation != null) {
                    val rec = adaptive.recommendation
                    item {
                        AppCard {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = ForgeBlue, modifier = Modifier.size(18.dp))
                                    Text("Coach Recommendation", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(rec.title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyLarge)
                                Text(rec.description, color = ForgeMuted, style = MaterialTheme.typography.bodyMedium)
                                Text("Reason: ${rec.reason}", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                                val recWorkoutId = rec.workoutId
                                if (recWorkoutId != null) {
                                    PrimaryButton("Start Recommended Ride", { onNavigate(AppRoute.ActiveWorkout(recWorkoutId)) }, Modifier.fillMaxWidth(), Icons.Rounded.PlayArrow)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        WorkoutCard(
                            workout = dashboard.workout,
                            onClick = { onNavigate(AppRoute.Workout(dashboard.workout.id)) },
                        )
                    }
                }

                if (adaptive?.insights?.isNotEmpty() == true) {
                    item {
                        AppCard {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = ForgeGreen, modifier = Modifier.size(18.dp))
                                    Text("AI Coach Insights", fontWeight = FontWeight.Bold)
                                }
                                adaptive.insights.forEach { insight ->
                                    Text("• $insight", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                item { WeeklyProgressCard(dashboard.progress) }
                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = ForgeBlue, modifier = Modifier.size(18.dp))
                                Text("Quick actions", fontWeight = FontWeight.Bold)
                            }
                            val startWorkoutId = adaptive?.recommendation?.workoutId ?: dashboard.workout.id
                            PrimaryButton("Start Workout", { onNavigate(AppRoute.ActiveWorkout(startWorkoutId)) }, Modifier.fillMaxWidth(), Icons.Rounded.PlayArrow)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SecondaryButton("Plans", { onNavigate(AppRoute.Plans) }, Modifier.weight(1f), Icons.AutoMirrored.Rounded.ListAlt)
                                SecondaryButton("History", { onNavigate(AppRoute.History) }, Modifier.weight(1f), Icons.Rounded.History)
                            }
                            Spacer(Modifier.height(4.dp))
                            SecondaryButton("Performance Trends", { onNavigate(AppRoute.Trends) }, Modifier.fillMaxWidth(), Icons.Rounded.TrendingUp)
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
