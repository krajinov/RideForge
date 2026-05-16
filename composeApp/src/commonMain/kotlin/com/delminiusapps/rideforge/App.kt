package com.delminiusapps.rideforge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.delminiusapps.rideforge.platform.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.delminiusapps.rideforge.core.network.DataSourceMonitor
import com.delminiusapps.rideforge.data.auth.AuthManager
import com.delminiusapps.rideforge.data.local.WorkoutLocalStorage
import com.delminiusapps.rideforge.domain.usecase.ObserveSessionSyncStatusUseCase
import com.delminiusapps.rideforge.domain.usecase.SyncPendingSessionsUseCase
import com.delminiusapps.rideforge.models.SyncStatus
import com.delminiusapps.rideforge.navigation.AppRoute
import com.delminiusapps.rideforge.navigation.bottomRoutes
import com.delminiusapps.rideforge.platform.rememberPlatformTokenStore
import com.delminiusapps.rideforge.presentation.components.AppScaffold
import com.delminiusapps.rideforge.features.auth.presentation.LoginScreen
import com.delminiusapps.rideforge.features.auth.presentation.RegisterScreen
import com.delminiusapps.rideforge.features.history.presentation.HistoryDetailScreen
import com.delminiusapps.rideforge.features.history.presentation.HistoryScreen
import com.delminiusapps.rideforge.features.home.presentation.HomeScreen
import com.delminiusapps.rideforge.presentation.onboarding.OnboardingScreen
import com.delminiusapps.rideforge.features.plans.presentation.PlansScreen
import com.delminiusapps.rideforge.features.plans.presentation.PlanWorkoutsScreen
import com.delminiusapps.rideforge.features.profile.presentation.ProfileScreen
import com.delminiusapps.rideforge.features.trainer.presentation.TrainerScreen
import com.delminiusapps.rideforge.features.workout.presentation.ActiveWorkoutScreen
import com.delminiusapps.rideforge.features.workout.presentation.WorkoutCompleteScreen
import com.delminiusapps.rideforge.features.workout.presentation.WorkoutDetailScreen
import com.delminiusapps.rideforge.features.workout.presentation.WorkoutListScreen
import com.delminiusapps.rideforge.theme.ForgeBackground
import com.delminiusapps.rideforge.theme.RideForgeTheme

import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.module
import com.delminiusapps.rideforge.data.auth.TokenStore
import com.delminiusapps.rideforge.core.di.rideForgeModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import rideforge.composeapp.generated.resources.Res
import rideforge.composeapp.generated.resources.common_offline_warning

@Composable
@Preview
fun App() {
    RideForgeTheme {
        val tokenStore = rememberPlatformTokenStore()
        
        KoinApplication(application = {
            modules(
                module {
                    single<TokenStore> { tokenStore }
                },
                rideForgeModule,
                com.delminiusapps.rideforge.features.auth.di.authModule,
                com.delminiusapps.rideforge.features.home.di.homeModule,
                com.delminiusapps.rideforge.features.plans.di.plansModule,
                com.delminiusapps.rideforge.features.trainer.di.trainerModule,
                com.delminiusapps.rideforge.features.profile.di.profileModule,
                com.delminiusapps.rideforge.features.history.di.historyModule,
                com.delminiusapps.rideforge.features.workout.di.workoutModule
            )
        }) {
            val authManager = koinInject<AuthManager>()
            val dataSourceMonitor = koinInject<DataSourceMonitor>()
            val workoutLocalStorage = koinInject<WorkoutLocalStorage>()
            val syncPendingSessionsUseCase = koinInject<SyncPendingSessionsUseCase>()
            val observeSessionSyncStatusUseCase = koinInject<ObserveSessionSyncStatusUseCase>()
            val dataSourceState by dataSourceMonitor.state.collectAsState()
            val isAuthenticated by authManager.isAuthenticated.collectAsState()
            val sessionSyncStatus by observeSessionSyncStatusUseCase().collectAsState(SyncStatus.Synced)
            val lifecycleOwner = LocalLifecycleOwner.current
            val foregroundSyncScope = rememberCoroutineScope()
            val latestIsAuthenticated by rememberUpdatedState(isAuthenticated)
            val latestSyncPendingSessionsUseCase by rememberUpdatedState(syncPendingSessionsUseCase)
            val latestSessionSyncStatus by rememberUpdatedState(sessionSyncStatus)
            var showForegroundSyncSuccess by remember { mutableStateOf(false) }
            var foregroundSyncSequence by remember { mutableStateOf(0) }
            var route by remember { mutableStateOf<AppRoute>(AppRoute.Onboarding) }
            var routeBackStack by remember { mutableStateOf<List<AppRoute>>(emptyList()) }
            var checkedForUnfinishedWorkout by remember { mutableStateOf(false) }
            val saveableStateHolder = rememberSaveableStateHolder()
            val suppressGlobalDataBanners = route is AppRoute.ActiveWorkout || route == AppRoute.Trainer
            val bottomNavRoute = when (route) {
                is AppRoute.PlanWorkouts -> AppRoute.Plans
                is AppRoute.HistoryItem -> AppRoute.History
                else -> route
            }
            val showBottomNav = bottomRoutes.any { it::class == bottomNavRoute::class }
            val authenticatedRoutesClasses = bottomRoutes.map { it::class } + listOf(
                AppRoute.Trainer::class,
                AppRoute.Workout::class,
                AppRoute.ActiveWorkout::class,
                AppRoute.WorkoutComplete::class,
                AppRoute.PlanWorkouts::class,
                AppRoute.HistoryItem::class,
            )
            val navigateTo: (AppRoute) -> Unit = { nextRoute ->
                if (nextRoute != route) {
                    routeBackStack = routeBackStack + route
                    route = nextRoute
                }
            }
            val setRootRoute: (AppRoute) -> Unit = { nextRoute ->
                routeBackStack = emptyList()
                route = nextRoute
            }
            val navigateBackOr: (AppRoute) -> Unit = { fallbackRoute ->
                val previousRoute = routeBackStack.lastOrNull()
                if (previousRoute != null) {
                    routeBackStack = routeBackStack.dropLast(1)
                    route = previousRoute
                } else {
                    route = fallbackRoute
                }
            }

            LaunchedEffect(Unit) {
                if (authManager.restoreSession()) {
                    setRootRoute(AppRoute.Home)
                }
            }

            LaunchedEffect(isAuthenticated) {
                if (!isAuthenticated) {
                    checkedForUnfinishedWorkout = false
                    return@LaunchedEffect
                }
                // Only redirect to unfinished workout if we are at the root (Home) 
                // and haven't already checked during this session.
                if (!checkedForUnfinishedWorkout) {
                    checkedForUnfinishedWorkout = true
                    if (route == AppRoute.Home || route == AppRoute.Onboarding) {
                        workoutLocalStorage.getActiveWorkout()?.let { unfinishedWorkout ->
                            setRootRoute(AppRoute.ActiveWorkout(id = unfinishedWorkout.workoutId))
                        }
                    }
                }
            }

            LaunchedEffect(isAuthenticated) {
                if (!isAuthenticated) return@LaunchedEffect
                while (true) {
                    runCatching { syncPendingSessionsUseCase() }
                    delay(PendingSyncRetryIntervalMillis)
                }
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME && latestIsAuthenticated) {
                        foregroundSyncScope.launch {
                            foregroundSyncSequence += 1
                            val sequence = foregroundSyncSequence
                            showForegroundSyncSuccess = false
                            val hadPendingWorkoutSync = runCatching {
                                workoutLocalStorage.getPendingSyncQueue().events.isNotEmpty()
                            }.getOrDefault(false)
                            runCatching { latestSyncPendingSessionsUseCase() }
                            if (hadPendingWorkoutSync && latestSessionSyncStatus == SyncStatus.Synced) {
                                showForegroundSyncSuccess = true
                                delay(ForegroundSyncSuccessVisibleMillis)
                                if (foregroundSyncSequence == sequence) {
                                    showForegroundSyncSuccess = false
                                }
                            }
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(isAuthenticated, route) {
                if (!isAuthenticated && route::class in authenticatedRoutesClasses) {
                    setRootRoute(AppRoute.Login)
                }
            }

            BackHandler(enabled = routeBackStack.isNotEmpty()) {
                navigateBackOr(AppRoute.Home)
            }

            AppScaffold(
                modifier = Modifier.fillMaxSize().background(ForgeBackground),
                currentRoute = bottomNavRoute,
                showBottomNavigation = showBottomNav,
                isOffline = dataSourceState.isUsingMockData && !suppressGlobalDataBanners,
                offlineMessage = stringResource(Res.string.common_offline_warning),
                syncStatus = sessionSyncStatus.takeIf { isAuthenticated && !suppressGlobalDataBanners },
                showSyncSuccess = isAuthenticated && showForegroundSyncSuccess && !suppressGlobalDataBanners,
                onRouteSelected = setRootRoute,
            ) {
                Box(Modifier.fillMaxSize()) {
                    saveableStateHolder.SaveableStateProvider(route.toString()) {
                        when (val currentRoute = route) {
                            AppRoute.Onboarding -> OnboardingScreen { setRootRoute(AppRoute.Login) }
                            AppRoute.Login -> LoginScreen(
                                onLoginSuccess = { setRootRoute(AppRoute.Home) },
                                onRegister = { navigateTo(AppRoute.Register) },
                                onBack = { navigateBackOr(AppRoute.Onboarding) },
                            )
                            AppRoute.Register -> RegisterScreen(
                                onRegisterSuccess = { setRootRoute(AppRoute.Home) },
                                onLogin = { navigateBackOr(AppRoute.Login) },
                                onBack = { navigateBackOr(AppRoute.Onboarding) },
                            )
                            AppRoute.Home -> HomeScreen(onNavigate = navigateTo)
                            AppRoute.Plans -> PlansScreen(onNavigate = navigateTo)
                            is AppRoute.PlanWorkouts -> PlanWorkoutsScreen(
                                planId = currentRoute.planId,
                                planName = currentRoute.planName,
                                onNavigate = navigateTo,
                                onBack = { navigateBackOr(AppRoute.Plans) },
                            )
                            AppRoute.Workouts -> WorkoutListScreen(onNavigate = navigateTo)
                            is AppRoute.Workout -> WorkoutDetailScreen(
                                workoutId = currentRoute.id,
                                onNavigate = navigateTo,
                                onBack = { navigateBackOr(AppRoute.Workouts) },
                            )
                            AppRoute.Trainer -> TrainerScreen(onNavigate = navigateTo, onBack = { navigateBackOr(AppRoute.Home) })
                            is AppRoute.ActiveWorkout -> ActiveWorkoutScreen(
                                workoutId = currentRoute.id,
                                onNavigate = navigateTo,
                            )
                            is AppRoute.WorkoutComplete -> WorkoutCompleteScreen(
                                sessionId = currentRoute.id,
                                onNavigate = navigateTo,
                            )
                            AppRoute.History -> HistoryScreen(onNavigate = navigateTo)
                            is AppRoute.HistoryItem -> HistoryDetailScreen(
                                sessionId = currentRoute.id,
                                onNavigate = navigateTo,
                                onBack = { navigateBackOr(AppRoute.History) },
                            )
                            AppRoute.Profile -> ProfileScreen(
                                onLogout = { setRootRoute(AppRoute.Login) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val PendingSyncRetryIntervalMillis = 30_000L
private const val ForegroundSyncSuccessVisibleMillis = 2_500L
