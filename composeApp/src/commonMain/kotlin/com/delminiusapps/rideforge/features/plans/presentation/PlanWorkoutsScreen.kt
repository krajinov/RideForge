package com.delminiusapps.rideforge.features.plans.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.delminiusapps.rideforge.theme.ForgeGreen
import com.delminiusapps.rideforge.theme.ForgeMuted
import com.delminiusapps.rideforge.theme.ForgeRed
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
                onJoin = { viewModel.joinPlan() },
                onLeave = { viewModel.leavePlan() }
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
    onJoin: () -> Unit,
    onLeave: () -> Unit,
) {
    val plan = state.plan
    val selectedPlanName = plan?.name ?: planName
    val workoutsByWeek = state.workoutsByWeek
    val sortedWorkouts = workoutsByWeek.values.flatten()
        .sortedWith(compareBy<Workout> { it.weekNumber }.thenBy { it.dayNumber })

    val isJoined = state.isJoined
    val completedWorkoutIds = state.completedWorkoutIds
    val nextWorkout = sortedWorkouts.firstOrNull { it.id !in completedWorkoutIds }
    val allCompleted = sortedWorkouts.isNotEmpty() && nextWorkout == null

    var showLeaveConfirmation by remember { mutableStateOf(false) }

    if (showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmation = false },
            title = { Text("Leave Plan") },
            text = { Text("Are you sure you want to leave this plan? Your progress for this plan will reset.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirmation = false
                        onLeave()
                    },
                ) {
                    Text("Leave", color = ForgeRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

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

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isJoined) {
                    PrimaryButton(
                        "Join Plan",
                        onJoin,
                        Modifier.fillMaxWidth(),
                    )
                } else {
                    if (allCompleted) {
                        PrimaryButton(
                            "Plan Completed",
                            {},
                            Modifier.fillMaxWidth(),
                            enabled = false
                        )
                    } else if (nextWorkout != null) {
                        PrimaryButton(
                            "Start Next Workout",
                            { onNavigate(AppRoute.ActiveWorkout(nextWorkout.id)) },
                            Modifier.fillMaxWidth(),
                            Icons.Rounded.PlayArrow,
                        )
                    }

                    SecondaryButton(
                        "Leave Plan",
                        { showLeaveConfirmation = true },
                        Modifier.fillMaxWidth(),
                    )
                }
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
                        isCompleted = workout.id in completedWorkoutIds,
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
    isCompleted: Boolean,
    onView: () -> Unit,
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallPill("Day ${workout.dayNumber}")
                    PowerZoneChip(
                        label = workoutTypeLabel(workout.workoutType),
                        color = workoutTypeColor(workout.workoutType),
                    )
                }
                if (isCompleted) {
                    Box(
                        modifier = Modifier
                            .background(ForgeGreen.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Done",
                            color = ForgeGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
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
