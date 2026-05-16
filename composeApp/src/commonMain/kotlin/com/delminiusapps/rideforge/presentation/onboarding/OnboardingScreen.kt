package com.delminiusapps.rideforge.presentation.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delminiusapps.rideforge.presentation.components.PrimaryButton
import com.delminiusapps.rideforge.presentation.components.SecondaryButton
import com.delminiusapps.rideforge.theme.ForgeBackground
import com.delminiusapps.rideforge.theme.ForgeBlue
import com.delminiusapps.rideforge.theme.ForgeGreen
import com.delminiusapps.rideforge.theme.ForgeMuted

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(ForgeBackground)
            .padding(24.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.36f)
            drawCircle(ForgeBlue.copy(alpha = 0.20f), radius = size.minDimension * 0.34f, center = center)
            drawCircle(ForgeGreen.copy(alpha = 0.14f), radius = size.minDimension * 0.22f, center = center)
            repeat(8) { index ->
                val y = size.height * (0.18f + index * 0.055f)
                drawLine(
                    color = Color.White.copy(alpha = 0.06f),
                    start = Offset(size.width * 0.16f, y),
                    end = Offset(size.width * 0.84f, y + if (index % 2 == 0) 26f else -18f),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("RideForge", fontSize = 54.sp, lineHeight = 56.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Text("Smart ERG cycling workouts", color = ForgeMuted, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(42.dp))
            PrimaryButton("Get Started", onContinue, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Connect Trainer Later", onContinue, Modifier.fillMaxWidth())
        }
    }
}
