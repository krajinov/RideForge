package com.delminiusapps.rideforge.features.plans.presentation

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel
import com.delminiusapps.rideforge.navigation.AppRoute
import com.delminiusapps.rideforge.presentation.components.EmptyState
import com.delminiusapps.rideforge.presentation.components.LoadingState
import com.delminiusapps.rideforge.presentation.components.PlanCard
import com.delminiusapps.rideforge.presentation.components.ScreenLazyColumn
import com.delminiusapps.rideforge.presentation.components.ScreenHeader

@Composable
fun PlansScreen(
    onNavigate: (AppRoute) -> Unit,
    viewModel: PlansViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    ScreenLazyColumn {
        item { ScreenHeader("Training Plans", "Choose an ERG progression") }
        when (val uiState = state) {
            is PlansUiState.Loading -> item { LoadingState("Loading plans...") }
            is PlansUiState.Ready -> {
                if (uiState.plans.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No plans available",
                            message = "Training plans will appear here when they are available.",
                        )
                    }
                }
                items(uiState.plans, key = { it.id }) { plan ->
                    PlanCard(plan = plan, onClick = { onNavigate(AppRoute.PlanWorkouts(plan.id, plan.name)) })
                }
            }
        }
    }
}
