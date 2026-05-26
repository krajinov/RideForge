package com.delminiusapps.rideforge.features.trends.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.delminiusapps.rideforge.models.DailyFatigue
import com.delminiusapps.rideforge.models.FtpHistoryRecord
import com.delminiusapps.rideforge.presentation.components.AppCard
import com.delminiusapps.rideforge.presentation.components.LoadingState
import com.delminiusapps.rideforge.presentation.components.ScreenLazyColumn
import com.delminiusapps.rideforge.theme.ForgeBackground
import com.delminiusapps.rideforge.theme.ForgeBlue
import com.delminiusapps.rideforge.theme.ForgeGreen
import com.delminiusapps.rideforge.theme.ForgeMuted
import com.delminiusapps.rideforge.theme.ForgeSurfaceHigh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    onBack: () -> Unit,
    viewModel: TrendsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = ForgeBackground) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = ForgeSurfaceHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Performance Trends",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            when (val uiState = state) {
                is TrendsUiState.Loading -> {
                    LoadingState("Loading performance trends...")
                }
                is TrendsUiState.Error -> {
                    com.delminiusapps.rideforge.presentation.components.ErrorState(
                        message = "Failed to load performance charts. Please try again.",
                        actionLabel = "Retry",
                        onAction = { viewModel.refresh() }
                    )
                }
                is TrendsUiState.Ready -> {
                    TrendsContent(
                        fatigueHistory = uiState.fatigueHistory,
                        ftpHistory = uiState.ftpHistory,
                        levels = uiState.progressionLevels
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendsContent(
    fatigueHistory: List<DailyFatigue>,
    ftpHistory: List<FtpHistoryRecord>,
    levels: Map<String, Double>
) {
    ScreenLazyColumn {
        // Section 1: Progression Levels
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Current Progression Levels",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = ForgeBlue
                    )
                    
                    if (levels.isEmpty()) {
                        Text(
                            text = "No progression data found yet. Start training to unlock levels!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ForgeMuted
                        )
                    } else {
                        levels.forEach { (type, level) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = type.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() },
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Level ${level}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ForgeGreen
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = (level / 10.0).toFloat().coerceIn(0f, 1f),
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                                    color = ForgeGreen,
                                    trackColor = ForgeSurfaceHigh
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Training Load Fatigue Chart (CTL & ATL)
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Training Load (ATL / CTL)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = ForgeBlue
                    )
                    
                    if (fatigueHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Complete a ride to start tracking training load.", color = ForgeMuted)
                        }
                    } else {
                        Column {
                            // Legend
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LegendItem("CTL (Fitness)", Color(0xFF3B82F6))
                                Spacer(Modifier.width(16.dp))
                                LegendItem("ATL (Fatigue)", Color(0xFFEF4444))
                            }
                            Spacer(Modifier.height(8.dp))
                            
                            FatigueLoadChart(history = fatigueHistory)
                        }
                    }
                }
            }
        }

        // Section 3: FTP History Chart
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "FTP Evolution",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = ForgeBlue
                    )
                    
                    val approvedHistory = ftpHistory.filter { it.status == "approved" }
                    if (approvedHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No approved FTP changes recorded yet.", color = ForgeMuted)
                        }
                    } else {
                        FtpHistoryChart(history = approvedHistory)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = ForgeMuted)
    }
}

@Composable
private fun FatigueLoadChart(history: List<DailyFatigue>) {
    val points = history.takeLast(30) // Show last 30 days
    val maxVal = points.flatMap { listOf(it.ctl, it.atl) }.maxOrNull()?.toFloat()?.coerceAtLeast(40f) ?: 40f
    
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        val width = size.width
        val height = size.height
        val stepX = width / (points.size - 1).coerceAtLeast(1)

        val ctlPath = Path()
        val atlPath = Path()

        points.forEachIndexed { idx, day ->
            val x = idx * stepX
            val yCtl = height - ((day.ctl.toFloat() / maxVal) * height)
            val yAtl = height - ((day.atl.toFloat() / maxVal) * height)

            if (idx == 0) {
                ctlPath.moveTo(x, yCtl)
                atlPath.moveTo(x, yAtl)
            } else {
                ctlPath.lineTo(x, yCtl)
                atlPath.lineTo(x, yAtl)
            }
        }

        // Draw grid lines
        drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, 0f), Offset(width, 0f), strokeWidth = 1f)
        drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, height / 2f), Offset(width, height / 2f), strokeWidth = 1f)
        drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, height), Offset(width, height), strokeWidth = 1f)

        // Draw lines
        drawPath(ctlPath, Color(0xFF3B82F6), style = Stroke(width = 3.dp.toPx()))
        drawPath(atlPath, Color(0xFFEF4444), style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun FtpHistoryChart(history: List<FtpHistoryRecord>) {
    val maxFtp = history.maxOf { it.estimatedFtp }.toFloat()
    val minFtp = history.minOf { it.estimatedFtp }.toFloat()
    val ftpRange = (maxFtp - minFtp).coerceAtLeast(10f)
    
    val paddingMin = minFtp - (ftpRange * 0.1f)
    val paddingMax = maxFtp + (ftpRange * 0.1f)
    val totalRange = paddingMax - paddingMin

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        val width = size.width
        val height = size.height
        val stepX = width / (history.size - 1).coerceAtLeast(1)

        val ftpPath = Path()

        history.forEachIndexed { idx, record ->
            val x = idx * stepX
            val y = height - (((record.estimatedFtp.toFloat() - paddingMin) / totalRange) * height)

            if (idx == 0) {
                ftpPath.moveTo(x, y)
            } else {
                ftpPath.lineTo(x, y)
            }

            // Draw a dot on each coordinate
            drawCircle(
                color = ForgeGreen,
                radius = 5.dp.toPx(),
                center = Offset(x, y)
            )
        }

        // Draw grid lines
        drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, 0f), Offset(width, 0f), strokeWidth = 1f)
        drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, height), Offset(width, height), strokeWidth = 1f)

        drawPath(ftpPath, ForgeGreen, style = Stroke(width = 3.dp.toPx()))
    }
}
