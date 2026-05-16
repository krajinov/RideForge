package com.delminiusapps.rideforge.features.history.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.delminiusapps.rideforge.models.RideHistoryItem
import com.delminiusapps.rideforge.navigation.AppRoute
import com.delminiusapps.rideforge.presentation.components.AppButton
import com.delminiusapps.rideforge.presentation.components.AppButtonVariant
import com.delminiusapps.rideforge.presentation.components.AppCard
import com.delminiusapps.rideforge.presentation.components.EmptyState
import com.delminiusapps.rideforge.presentation.components.LoadingState
import com.delminiusapps.rideforge.presentation.components.ScreenLazyColumn
import com.delminiusapps.rideforge.presentation.components.ScreenHeader
import com.delminiusapps.rideforge.theme.ForgeMuted

@Composable
fun HistoryScreen(
    onNavigate: (AppRoute) -> Unit,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    var selectedFilter by remember { mutableStateOf("Week") }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onAction(HistoryAction.Refresh)
    }

    ScreenLazyColumn {
        item { ScreenHeader("Ride History", "Completed workouts") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Week", "Month", "All").forEach { filter ->
                    AppButton(
                        text = filter,
                        onClick = { selectedFilter = filter },
                        modifier = Modifier.weight(1f),
                        variant = if (filter == selectedFilter) AppButtonVariant.Primary else AppButtonVariant.Secondary,
                    )
                }
            }
        }
        
        when (val uiState = state) {
            is HistoryUiState.Loading -> item { LoadingState("Loading history...") }
            is HistoryUiState.Error -> {
                item {
                    com.delminiusapps.rideforge.presentation.components.ErrorState(
                        message = "We couldn't load your ride history. Please try again.",
                        actionLabel = "Retry",
                        onAction = { viewModel.onAction(HistoryAction.Refresh) }
                    )
                }
            }
            is HistoryUiState.Ready -> {
                if (uiState.history.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No completed workouts yet",
                            message = "Finished rides will appear here after you save a workout.",
                        )
                    }
                } else {
                    items(uiState.history, key = { it.id }) { ride ->
                        HistoryRideCard(
                            ride = ride,
                            onClick = { onNavigate(AppRoute.HistoryItem(ride.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRideCard(ride: RideHistoryItem, onClick: () -> Unit) {
    AppCard(modifier = Modifier.clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(ride.workoutName, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 2)
                Text("${ride.completionPercent}%", color = ForgeMuted, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "${formatHistoryDate(ride)} • ${ride.durationMinutes} min • ${ride.averagePowerWatts} W avg",
                color = ForgeMuted,
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun formatHistoryDate(ride: RideHistoryItem): String {
    val monthNumber = ride.date.monthNumber
    val month = when (monthNumber) {
        5 -> "May"
        else -> monthNumber.toString().padStart(2, '0')
    }
    return "$month ${ride.date.dayOfMonth}, ${ride.date.year}"
}
