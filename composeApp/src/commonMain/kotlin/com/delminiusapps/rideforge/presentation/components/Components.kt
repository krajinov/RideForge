package com.delminiusapps.rideforge.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.PowerZone
import com.delminiusapps.rideforge.domain.trainer.ConnectionState
import com.delminiusapps.rideforge.models.SyncStatus
import com.delminiusapps.rideforge.models.TrainingPlan
import com.delminiusapps.rideforge.models.WeeklyProgress
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutInterval
import com.delminiusapps.rideforge.models.WorkoutType
import com.delminiusapps.rideforge.navigation.AppRoute
import com.delminiusapps.rideforge.navigation.bottomRoutes
import com.delminiusapps.rideforge.navigation.label
import com.delminiusapps.rideforge.theme.ForgeBackground
import com.delminiusapps.rideforge.theme.ForgeBlue
import com.delminiusapps.rideforge.theme.ForgeBlueDark
import com.delminiusapps.rideforge.theme.ForgeBorder
import com.delminiusapps.rideforge.theme.ForgeCard
import com.delminiusapps.rideforge.theme.ForgeGreen
import com.delminiusapps.rideforge.theme.ForgeMuted
import com.delminiusapps.rideforge.theme.ForgeOrange
import com.delminiusapps.rideforge.theme.ForgeRed
import com.delminiusapps.rideforge.theme.ForgeSurface
import com.delminiusapps.rideforge.theme.ForgeSurfaceHigh
import com.delminiusapps.rideforge.theme.ForgeTabInactive
import com.delminiusapps.rideforge.theme.ForgeText
import com.delminiusapps.rideforge.theme.ForgeYellow
import com.delminiusapps.rideforge.theme.ForgeZone1
import com.delminiusapps.rideforge.theme.ForgeZone2
import com.delminiusapps.rideforge.theme.ForgeZone3
import com.delminiusapps.rideforge.theme.ForgeZone4
import com.delminiusapps.rideforge.theme.ForgeZone5
import com.delminiusapps.rideforge.theme.ForgeZone6
import com.delminiusapps.rideforge.theme.ForgeZone7
import com.delminiusapps.rideforge.theme.RideForgeRadius
import com.delminiusapps.rideforge.theme.RideForgeSpacing

enum class AppButtonVariant {
    Primary,
    Secondary,
    Quiet,
}

@Composable
fun AppScaffold(
    currentRoute: AppRoute,
    showBottomNavigation: Boolean,
    isOffline: Boolean,
    offlineMessage: String,
    syncStatus: SyncStatus? = null,
    showSyncSuccess: Boolean = false,
    onRouteSelected: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(ForgeBackground),
        containerColor = ForgeBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (showBottomNavigation) {
                AppBottomNavigation(
                    currentRoute = currentRoute,
                    onRouteSelected = onRouteSelected,
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            content()
            SyncDataSnackbar(
                syncStatus = syncStatus,
                showSuccess = showSyncSuccess,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        horizontal = RideForgeSpacing.ScreenHorizontal,
                        vertical = if (isOffline) 76.dp else 10.dp,
                    ),
            )
            if (isOffline) {
                OfflineDataSnackbar(
                    message = offlineMessage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = RideForgeSpacing.ScreenHorizontal, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
fun ScreenLazyColumn(
    modifier: Modifier = Modifier,
    state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    horizontalPadding: androidx.compose.ui.unit.Dp = RideForgeSpacing.ScreenHorizontal,
    topPadding: androidx.compose.ui.unit.Dp = RideForgeSpacing.ScreenTop,
    bottomPadding: androidx.compose.ui.unit.Dp = RideForgeSpacing.ScreenBottom,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(RideForgeSpacing.ItemGap),
    content: LazyListScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val sidePadding = if (maxWidth > 600.dp) {
            ((maxWidth - 560.dp) / 2f).coerceAtLeast(horizontalPadding)
        } else {
            horizontalPadding
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(
                start = sidePadding,
                top = topPadding,
                end = sidePadding,
                bottom = bottomPadding,
            ),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(RideForgeSpacing.Card),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(RideForgeRadius.Card),
        colors = CardDefaults.cardColors(
            containerColor = ForgeCard,
            contentColor = ForgeText,
        ),
        border = BorderStroke(1.dp, ForgeBorder),
    ) {
        Box(Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = ForgeBlue,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = ForgeMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    variant: AppButtonVariant = AppButtonVariant.Primary,
) {
    val shape = RoundedCornerShape(RideForgeRadius.Button)
    val contentColor = when {
        !enabled -> ForgeMuted
        variant == AppButtonVariant.Primary -> ForgeBackground
        else -> ForgeText
    }
    val backgroundBrush = when {
        !enabled -> SolidColor(ForgeSurfaceHigh)
        variant == AppButtonVariant.Primary -> Brush.horizontalGradient(listOf(ForgeBlue, ForgeBlueDark))
        variant == AppButtonVariant.Secondary -> SolidColor(ForgeCard)
        else -> SolidColor(Color.Transparent)
    }
    val border = when (variant) {
        AppButtonVariant.Primary -> null
        AppButtonVariant.Secondary -> BorderStroke(1.5.dp, ForgeBorder)
        AppButtonVariant.Quiet -> BorderStroke(1.dp, ForgeBorder.copy(alpha = 0.75f))
    }

    Box(
        modifier = modifier
            .heightIn(min = 54.dp)
            .clip(shape)
            .background(backgroundBrush, shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    AppButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        variant = AppButtonVariant.Primary,
    )
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    AppButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        variant = AppButtonVariant.Secondary,
    )
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    accent: Color = ForgeBlue,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = label.uppercase(),
                color = ForgeMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                color = ForgeText,
                fontSize = 30.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .height(3.dp)
                    .fillMaxWidth()
                    .background(accent, RoundedCornerShape(99.dp)),
            )
        }
    }
}

@Composable
fun ConnectionStatusCard(
    status: ConnectionState,
    deviceName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier.clickable(role = Role.Button, onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = if (status == ConnectionState.CONNECTED) ForgeGreen.copy(alpha = 0.16f) else ForgeBlueDark,
                contentColor = if (status == ConnectionState.CONNECTED) ForgeGreen else ForgeBlue,
                shape = CircleShape,
            ) {
                Icon(
                    Icons.Rounded.BluetoothConnected,
                    contentDescription = null,
                    modifier = Modifier.padding(11.dp).size(22.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Smart Trainer", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(status.label(), color = ForgeMuted, fontSize = 13.sp, maxLines = 1)
                Text(deviceName, color = ForgeMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ForgeMuted, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun WorkoutCard(workout: Workout, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier.clickable(role = Role.Button, onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        workout.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallPill("${workout.durationMinutes} min")
                        DifficultyBadge(workout.difficulty)
                    }
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ForgeMuted, modifier = Modifier.size(24.dp))
            }
            if (workout.intervals.isNotEmpty()) {
                IntervalTimeline(workout.intervals, Modifier.height(52.dp))
            }
            Text(
                workout.description,
                color = ForgeMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            PowerZoneChip(workoutTypeLabel(workout.workoutType), color = workoutTypeColor(workout.workoutType))
        }
    }
}

@Composable
fun PlanCard(plan: TrainingPlan, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier, contentPadding = PaddingValues(RideForgeSpacing.CardLarge)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    plan.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                SmallPill("${plan.workoutCount}")
            }
            Text(
                plan.description,
                color = ForgeMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${plan.durationWeeks} weeks", color = ForgeMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("${plan.workoutCount} workouts", color = ForgeMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                DifficultyBadge(plan.difficulty)
            }
            SecondaryButton("View Plan", onClick, Modifier.fillMaxWidth(), Icons.AutoMirrored.Rounded.ListAlt)
        }
    }
}

@Composable
fun DifficultyBadge(difficulty: String, modifier: Modifier = Modifier) {
    SmallPill(difficulty, difficultyColor(difficulty), modifier)
}

@Composable
fun PowerZoneChip(
    label: String,
    color: Color = zoneColor(label),
    range: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = ForgeCard,
        contentColor = ForgeText,
        shape = RoundedCornerShape(RideForgeRadius.Chip),
        border = BorderStroke(1.dp, ForgeBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
            if (range != null) {
                Text(range, color = ForgeMuted, fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun PowerZoneRow(zone: PowerZone) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PowerZoneChip(label = zone.name, range = zone.rangeLabel, color = zoneColor(zone.name))
    }
}

@Composable
fun LoadingState(message: String, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ForgeBlue)
            Text(message, color = ForgeMuted, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    AppCard(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ListAlt, contentDescription = null, tint = ForgeBlue, modifier = Modifier.size(30.dp))
            Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(message, color = ForgeMuted, textAlign = TextAlign.Center, lineHeight = 20.sp)
            if (actionLabel != null && onAction != null) {
                SecondaryButton(actionLabel, onAction, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "Something went wrong",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    AppCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(RideForgeSpacing.CardLarge)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = ForgeRed, modifier = Modifier.size(30.dp))
            Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(message, color = ForgeMuted, textAlign = TextAlign.Center, lineHeight = 20.sp)
            if (actionLabel != null && onAction != null) {
                SecondaryButton(actionLabel, onAction, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun ProgressGraph(samples: List<MetricSample>, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(ForgeSurfaceHigh, RoundedCornerShape(RideForgeRadius.Card))
            .border(1.dp, ForgeBorder, RoundedCornerShape(RideForgeRadius.Card))
            .padding(12.dp),
    ) {
        val data = if (samples.size >= 2) samples else listOf(
            MetricSample(0, 118, 120, 88, 116),
            MetricSample(1, 125, 120, 89, 118),
            MetricSample(2, 286, 290, 92, 136),
            MetricSample(3, 294, 290, 93, 148),
            MetricSample(4, 121, 120, 88, 140),
        )
        val maxPower = maxOf(320f, data.maxOf { it.currentPowerWatts }.toFloat())
        val step = size.width / (data.lastIndex.coerceAtLeast(1))
        for (line in 1..3) {
            val y = size.height * line / 4f
            drawLine(ForgeBorder.copy(alpha = 0.55f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        fun yFor(power: Int) = size.height - (power / maxPower) * size.height
        data.zipWithNext().forEachIndexed { index, pair ->
            val x1 = index * step
            val x2 = (index + 1) * step
            drawLine(
                color = ForgeMuted.copy(alpha = 0.55f),
                start = Offset(x1, yFor(pair.first.targetPowerWatts)),
                end = Offset(x2, yFor(pair.second.targetPowerWatts)),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = ForgeBlue,
                start = Offset(x1, yFor(pair.first.currentPowerWatts)),
                end = Offset(x2, yFor(pair.second.currentPowerWatts)),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun IntervalTimeline(intervals: List<WorkoutInterval>, modifier: Modifier = Modifier, progress: Float = 0f) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(ForgeSurfaceHigh, RoundedCornerShape(RideForgeRadius.Card))
            .border(1.dp, ForgeBorder, RoundedCornerShape(RideForgeRadius.Card))
            .padding(12.dp),
    ) {
        val totalSeconds = intervals.sumOf { it.durationSeconds }.toFloat()
        if (totalSeconds <= 0f) return@Canvas

        var x = 0f
        intervals.forEach { interval ->
            val width = size.width * interval.durationSeconds / totalSeconds
            val barHeight = (size.height * (interval.targetFtpPercent.toFloat() / 150f)).coerceIn(14f, size.height)
            val top = size.height - barHeight
            drawRoundRect(
                color = zoneColor(interval.zone),
                topLeft = Offset(x + 2.dp.toPx(), top),
                size = Size((width - 4.dp.toPx()).coerceAtLeast(2.dp.toPx()), barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx(), 7.dp.toPx()),
            )
            x += width
        }
        if (progress > 0f) {
            val markerX = size.width * progress.coerceIn(0f, 1f)
            drawLine(Color.White, Offset(markerX, 0f), Offset(markerX, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

@Composable
fun ErgBadge() {
    Surface(
        color = ForgeGreen.copy(alpha = 0.16f),
        contentColor = ForgeGreen,
        shape = RoundedCornerShape(99.dp),
        border = BorderStroke(1.dp, ForgeGreen.copy(alpha = 0.45f)),
    ) {
        Text("ERG ON", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
    }
}

@Composable
fun AppBottomNavigation(currentRoute: AppRoute, onRouteSelected: (AppRoute) -> Unit) {
    Surface(color = ForgeSurface, shadowElevation = 8.dp) {
        Column(Modifier.navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(ForgeBorder.copy(alpha = 0.85f)))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                bottomRoutes.forEach { route ->
                    val selected = route::class == currentRoute::class
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 54.dp)
                            .clickable(role = Role.Tab, onClick = { onRouteSelected(route) })
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = route.icon(),
                            contentDescription = route.label(),
                            tint = if (selected) ForgeBlue else ForgeTabInactive,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            route.label().uppercase(),
                            color = if (selected) ForgeBlue else ForgeTabInactive,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(currentRoute: AppRoute, onRouteSelected: (AppRoute) -> Unit) {
    AppBottomNavigation(currentRoute = currentRoute, onRouteSelected = onRouteSelected)
}

@Composable
fun WeeklyProgressCard(progress: WeeklyProgress, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        val planned = progress.plannedWorkouts.coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = ForgeGreen, modifier = Modifier.size(18.dp))
                    Text("Weekly progress", fontWeight = FontWeight.Bold)
                }
                Text("${progress.completedWorkouts} of ${progress.plannedWorkouts}", color = ForgeBlue, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { progress.completedWorkouts.toFloat() / planned.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = ForgeGreen,
                trackColor = ForgeSurfaceHigh,
            )
            Text("${progress.completedWorkouts} of ${progress.plannedWorkouts} workouts completed", color = ForgeMuted, fontSize = 14.sp)
        }
    }
}

@Composable
fun SmallPill(text: String, color: Color = ForgeBlue, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(99.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SyncDataSnackbar(
    syncStatus: SyncStatus?,
    showSuccess: Boolean,
    modifier: Modifier = Modifier,
) {
    val notice = syncNotice(syncStatus, showSuccess) ?: return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ForgeCard,
        contentColor = notice.color,
        shape = RoundedCornerShape(RideForgeRadius.Card),
        border = BorderStroke(1.dp, notice.color.copy(alpha = 0.45f)),
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (notice.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = notice.color,
                    trackColor = ForgeSurfaceHigh,
                )
            } else if (notice.isError) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(notice.color),
                )
            }
            Text(notice.message, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OfflineDataSnackbar(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ForgeCard,
        contentColor = ForgeOrange,
        shape = RoundedCornerShape(RideForgeRadius.Card),
        border = BorderStroke(1.dp, ForgeOrange.copy(alpha = 0.45f)),
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(message, fontWeight = FontWeight.Bold)
        }
    }
}

private data class SyncNotice(
    val message: String,
    val color: Color,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
)

private fun syncNotice(syncStatus: SyncStatus?, showSuccess: Boolean): SyncNotice? {
    return when {
        syncStatus == SyncStatus.Syncing -> SyncNotice("Syncing workout data...", ForgeBlue, isLoading = true)
        syncStatus == SyncStatus.PendingSync -> SyncNotice("Workout sync pending. Saved locally.", ForgeOrange)
        syncStatus == SyncStatus.SyncFailed -> SyncNotice("Workout sync failed. Will retry.", ForgeRed, isError = true)
        showSuccess -> SyncNotice("Workout sync complete.", ForgeGreen)
        else -> null
    }
}

fun AppRoute.icon(): ImageVector = when (this) {
    AppRoute.Splash -> Icons.Rounded.PlayCircle
    AppRoute.Home -> Icons.Rounded.Home
    AppRoute.Plans -> Icons.Rounded.CalendarMonth
    is AppRoute.PlanWorkouts -> Icons.Rounded.CalendarMonth
    AppRoute.Workouts -> Icons.Rounded.FitnessCenter
    is AppRoute.Workout -> Icons.AutoMirrored.Rounded.DirectionsBike
    AppRoute.History -> Icons.Rounded.History
    AppRoute.Profile -> Icons.Rounded.Person
    AppRoute.Onboarding -> Icons.Rounded.PlayCircle
    AppRoute.Login -> Icons.Rounded.Person
    AppRoute.Register -> Icons.Rounded.Person
    AppRoute.Trainer -> Icons.Rounded.BluetoothConnected
    is AppRoute.ActiveWorkout -> Icons.Rounded.FitnessCenter
    is AppRoute.WorkoutComplete -> Icons.Rounded.History
    is AppRoute.HistoryItem -> Icons.Rounded.History
    AppRoute.Trends -> Icons.Rounded.ShowChart
}

fun workoutTypeLabel(type: WorkoutType): String = type.name
    .lowercase()
    .split("_")
    .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }

fun workoutTypeColor(type: WorkoutType): Color = when (type) {
    WorkoutType.RECOVERY -> ForgeGreen
    WorkoutType.ENDURANCE -> ForgeBlue
    WorkoutType.TEMPO -> ForgeYellow
    WorkoutType.SWEET_SPOT -> ForgeOrange
    WorkoutType.THRESHOLD -> ForgeOrange
    WorkoutType.VO2_MAX -> ForgeRed
    WorkoutType.OVER_UNDER -> ForgeRed
    WorkoutType.RACE_SIMULATION -> ForgeRed
}

private fun ConnectionState.label(): String = name
    .lowercase()
    .replaceFirstChar { it.titlecase() }

fun zoneColor(label: String): Color = when {
    label.contains("Z7", ignoreCase = true) || label.contains("Neuromuscular", ignoreCase = true) -> ForgeZone7
    label.contains("Z6", ignoreCase = true) || label.contains("Anaerobic", ignoreCase = true) -> ForgeZone6
    label.contains("Z5", ignoreCase = true) || label.contains("VO2", ignoreCase = true) -> ForgeZone5
    label.contains("Z4", ignoreCase = true) || label.contains("Threshold", ignoreCase = true) -> ForgeZone4
    label.contains("Z3", ignoreCase = true) || label.contains("Tempo", ignoreCase = true) || label.contains("Sweet", ignoreCase = true) -> ForgeZone3
    label.contains("Z2", ignoreCase = true) || label.contains("Endurance", ignoreCase = true) -> ForgeZone2
    label.contains("Z1", ignoreCase = true) || label.contains("Recovery", ignoreCase = true) -> ForgeZone1
    else -> ForgeBlue
}

private fun difficultyColor(difficulty: String): Color = when (difficulty) {
    "Hard", "Advanced" -> ForgeRed
    "Intermediate" -> ForgeOrange
    else -> ForgeGreen
}
