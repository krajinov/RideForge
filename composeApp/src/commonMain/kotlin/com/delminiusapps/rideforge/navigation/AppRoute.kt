package com.delminiusapps.rideforge.navigation

sealed interface AppRoute {
    data object Onboarding : AppRoute
    data object Login : AppRoute
    data object Register : AppRoute
    data object Home : AppRoute
    data object Plans : AppRoute
    data class PlanWorkouts(val planId: String, val planName: String) : AppRoute
    data object Workouts : AppRoute
    data class Workout(val id: String) : AppRoute
    data object Trainer : AppRoute
    data class ActiveWorkout(val id: String = "") : AppRoute
    data class WorkoutComplete(val id: String = "") : AppRoute
    data object History : AppRoute
    data class HistoryItem(val id: String) : AppRoute
    data object Profile : AppRoute
}

val bottomRoutes = listOf(
    AppRoute.Home,
    AppRoute.Plans,
    AppRoute.Workouts,
    AppRoute.History,
    AppRoute.Profile,
)

fun AppRoute.label(): String = when (this) {
    AppRoute.Onboarding -> "Onboarding"
    AppRoute.Login -> "Login"
    AppRoute.Register -> "Register"
    AppRoute.Home -> "Home"
    AppRoute.Plans -> "Plans"
    is AppRoute.PlanWorkouts -> "Plan Workouts"
    AppRoute.Workouts -> "Workouts"
    is AppRoute.Workout -> "Workout"
    AppRoute.Trainer -> "Trainer"
    is AppRoute.ActiveWorkout -> "Active"
    is AppRoute.WorkoutComplete -> "Complete"
    AppRoute.History -> "History"
    is AppRoute.HistoryItem -> "History"
    AppRoute.Profile -> "Profile"
}
