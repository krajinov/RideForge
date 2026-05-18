package com.delminiusapps.rideforge.features.splash.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.delminiusapps.rideforge.theme.ForgeBackground
import com.delminiusapps.rideforge.theme.ForgeBlue
import com.delminiusapps.rideforge.theme.ForgeGreen
import com.delminiusapps.rideforge.theme.ForgeSurfaceHigh
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import rideforge.composeapp.generated.resources.Res
import rideforge.composeapp.generated.resources.ride_forge_logo

@Composable
fun SplashScreen(
    onAuthenticated: () -> Unit,
    onUnauthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkSession()
    }

    LaunchedEffect(state) {
        when (state) {
            SplashState.Authenticated -> onAuthenticated()
            is SplashState.Error, SplashState.Unauthenticated -> onUnauthenticated()
            SplashState.Loading -> Unit
        }
    }

    val transition = rememberInfiniteTransition(label = "Splash glow")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Glow alpha",
    )
    val glowScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Glow scale",
    )
    val visibleState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ForgeBackground),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = tween(durationMillis = 650)) +
                scaleIn(
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                    initialScale = 0.96f,
                ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Box(
                    modifier = Modifier.size(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val center = this.center
                        drawCircle(
                            color = ForgeBlue.copy(alpha = glowAlpha),
                            radius = size.minDimension * 0.38f * glowScale,
                            center = center,
                        )
                        drawCircle(
                            color = ForgeGreen.copy(alpha = glowAlpha * 0.42f),
                            radius = size.minDimension * 0.27f * glowScale,
                            center = center,
                        )
                    }
                    Image(
                        painter = painterResource(Res.drawable.ride_forge_logo),
                        contentDescription = "RideForge logo",
                        modifier = Modifier
                            .size(172.dp)
                            .clip(RoundedCornerShape(34.dp)),
                    )
                }
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = ForgeBlue,
                    trackColor = ForgeSurfaceHigh.copy(alpha = 0.5f),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}
