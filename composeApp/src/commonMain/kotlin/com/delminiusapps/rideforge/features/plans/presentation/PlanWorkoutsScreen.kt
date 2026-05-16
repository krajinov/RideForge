package com.delminiusapps.rideforge.features.plans.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delminiusapps.rideforge.models.TrainingPlan
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.navigation.AppRoute
import com.delminiusapps.rideforge.presentation.components.AppCard
import com.delminiusapps.rideforge.presentation.components.DifficultyBadge
import com.delminiusapps.rideforge.presentation.components.EmptyState
import com.delminiusapps.rideforge.presentation.components.LoadingState
import com.delminiusapps.rideforge.presentation.components.PowerZoneChip
import com.delminiusapps.rideforge.presentation.components.PrimaryButton
import com.delminiusapps.rideforge.presentation.components.ScreenHeader
import com.delminiusapps.rideforge.presentation.components.ScreenLazyColumn
import com.delminiusapps.rideforge.presentation.components.SecondaryButton
import com.delminiusapps.rideforge.presentation.components.SmallPill
import com.delminiusapps.rideforge.presentation.components.workoutTypeColor
import com.delminiusapps.rideforge.presentation.components.workoutTypeLabel
import com.delminiusapps.rideforge.theme.ForgeMuted
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PlanWorkoutsScreen(
    planId: String,
    planName: String,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
    viewModel: PlanWorkoutsViewModel = koinViewModel(parameters = { parametersOf(planId) }),
) {
    val state by viewModel.state.collectAsState()

    when (val uiState = state) {
        is PlanWorkoutsUiState.Loading -> {
            ScreenLazyColumn {
                item { ScreenHeader(planName, "Plan details", onBack = onBack) }
                item { LoadingState("Loading workouts...") }
            }
        }

        is PlanWorkoutsUiState.Ready -> {
            PlanWorkoutsContent(
                planName = planName,
                state = uiState,
                onNavigate = onNavigate,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun PlanWorkoutsContent(
    planName: String,
    state: PlanWorkoutsUiState.Ready,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val plan = state.plan
    val selectedPlanName = plan?.name ?: planName
    val workoutsByWeek = state.workoutsByWeek
    val sortedWorkouts = workoutsByWeek.values.flatten()
        .sortedWith(compareBy<Workout> { it.weekNumber }.thenBy { it.dayNumber })
    val nextWorkout = sortedWorkouts.firstOrNull()

    ScreenLazyColumn {
        item {
            ScreenHeader(
                selectedPlanName,
                plan?.let { "${it.durationWeeks} weeks • ${it.workoutCount} workouts • ${it.difficulty}" } ?: "Plan details",
                onBack = onBack,
            )
        }
        item {
            PlanSummaryCard(
                plan = plan,
                fallbackWorkoutCount = sortedWorkouts.size,
            )
        }

        if (nextWorkout != null) {
            item {
                PrimaryButton(
                    "Start Next Workout",
                    { onNavigate(AppRoute.ActiveWorkout(nextWorkout.id)) },
                    Modifier.fillMaxWidth(),
                    Icons.Rounded.PlayArrow,
                )
            }
        }

        if (workoutsByWeek.isEmpty()) {
            item {
                EmptyState(
                    title = "No workouts available",
                    message = "No workouts available for this plan yet.",
                )
            }
        } else {
            workoutsByWeek.keys.sorted().forEach { week ->
                item(key = "week-$week") {
                    Text(
                        "Week $week",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                }
                items(
                    workoutsByWeek.getValue(week).sortedBy { it.dayNumber },
                    key = { it.id },
                ) { workout ->
                    PlanWorkoutCard(
                        workout = workout,
                        onView = { onNavigate(AppRoute.Workout(workout.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanSummaryCard(
    plan: TrainingPlan?,
    fallbackWorkoutCount: Int,
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                plan?.description ?: "Structured progression over planned ERG workouts.",
                color = ForgeMuted,
                lineHeight = 22.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallPill("${plan?.workoutCount ?: fallbackWorkoutCount} workouts")
                if (plan != null) DifficultyBadge(plan.difficulty)
            }
        }
    }
}

@Composable
private fun PlanWorkoutCard(
    workout: Workout,
    onView: () -> Unit,
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallPill("Day ${workout.dayNumber}")
                PowerZoneChip(
                    label = workoutTypeLabel(workout.workoutType),
                    color = workoutTypeColor(workout.workoutType),
                )
            }
            Text(
                workout.name,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                workout.description,
                color = ForgeMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 22.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallPill("${workout.durationMinutes} min")
                DifficultyBadge(workout.difficulty)
            }
            SecondaryButton("View Workout", onView, Modifier.fillMaxWidth())
        }
    }
}
