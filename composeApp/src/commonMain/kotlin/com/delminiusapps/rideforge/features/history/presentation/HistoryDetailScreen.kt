package com.delminiusapps.rideforge.features.history.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delminiusapps.rideforge.models.RideHistoryItem
import com.delminiusapps.rideforge.models.StravaSyncInfo
import com.delminiusapps.rideforge.models.StravaSyncState
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutSession
import com.delminiusapps.rideforge.navigation.AppRoute
import com.delminiusapps.rideforge.presentation.components.AppCard
import com.delminiusapps.rideforge.presentation.components.ErrorState
import com.delminiusapps.rideforge.presentation.components.LoadingState
import com.delminiusapps.rideforge.presentation.components.PrimaryButton
import com.delminiusapps.rideforge.presentation.components.ScreenHeader
import com.delminiusapps.rideforge.presentation.components.ScreenLazyColumn
import com.delminiusapps.rideforge.presentation.components.SecondaryButton
import com.delminiusapps.rideforge.presentation.components.SmallPill
import com.delminiusapps.rideforge.theme.ForgeBlue
import com.delminiusapps.rideforge.theme.ForgeBorder
import com.delminiusapps.rideforge.theme.ForgeCard
import com.delminiusapps.rideforge.theme.ForgeGreen
import com.delminiusapps.rideforge.theme.ForgeMuted
import com.delminiusapps.rideforge.theme.ForgeOrange
import com.delminiusapps.rideforge.theme.ForgePurple
import com.delminiusapps.rideforge.theme.ForgeRed
import com.delminiusapps.rideforge.theme.ForgeSurface
import com.delminiusapps.rideforge.theme.ForgeSurfaceHigh
import com.delminiusapps.rideforge.theme.ForgeStrava
import com.delminiusapps.rideforge.theme.ForgeText
import com.delminiusapps.rideforge.theme.ForgeYellow
import com.delminiusapps.rideforge.theme.ForgeZone1
import com.delminiusapps.rideforge.theme.ForgeZone2
import com.delminiusapps.rideforge.theme.ForgeZone3
import com.delminiusapps.rideforge.theme.ForgeZone4
import com.delminiusapps.rideforge.theme.ForgeZone5
import com.delminiusapps.rideforge.theme.ForgeZone6
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Instant

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryDetailScreen(
    sessionId: String,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
    viewModel: HistoryDetailViewModel = koinViewModel(key = sessionId) { parametersOf(sessionId) },
) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HistoryDetailEvent.OpenUrl -> runCatching { uriHandler.openUri(event.url) }
            }
        }
    }

    ScreenLazyColumn {
        when (val uiState = state) {
            is HistoryDetailUiState.Loading -> {
                item { ScreenHeader("Workout Details", "Loading completed workout...", onBack = onBack) }
                item { LoadingState("Loading workout details...") }
            }

            is HistoryDetailUiState.Error -> {
                item { ScreenHeader("Workout Details", "Completed workout", onBack = onBack) }
                item { ErrorState(message = "Workout details unavailable.", title = "Unable to load workout") }
            }

            is HistoryDetailUiState.Ready -> {
                val summary = uiState.summary
                val workout = uiState.workout
                val analysis = uiState.analysis
                val title = summary.workoutName.ifBlank { workout?.name ?: "Workout Details" }

                item { ScreenHeader(title, "Completed workout analysis", onBack = onBack) }

                stickyHeader {
                    StickySummaryHeader(
                        title = title,
                        summary = summary,
                        analysis = analysis,
                    )
                }

                item {
                    HeroSummaryCard(
                        title = title,
                        summary = summary,
                        workout = workout,
                        historyItem = uiState.historyItem,
                        analysis = analysis,
                    )
                }

                item {
                    StravaSyncDetailCard(
                        sync = uiState.stravaSync,
                        hasRealTrainerData = summary.hasRealTrainerData,
                        isSyncing = uiState.isStravaSyncing,
                        onSync = { viewModel.onAction(HistoryDetailAction.SyncToStrava) },
                        onView = { viewModel.onAction(HistoryDetailAction.ViewOnStrava) },
                    )
                }

                if (!analysis.hasRecordedMetrics) {
                    item { DataQualityCard() }
                }

                item {
                    WorkoutTimelineCard(analysis.intervals)
                }

                item {
                    PowerAnalyticsCard(analysis)
                }

                item {
                    CadenceAnalyticsCard(analysis)
                }

                item {
                    HeartRateAnalyticsCard(analysis)
                }

                item {
                    SpeedAnalyticsCard(analysis)
                }

                item {
                    ZoneDistributionCard(analysis)
                }

                item {
                    TrainerMetricsCard(analysis.trainerMetrics)
                }

                item {
                    InsightsCard(analysis.insights)
                }

                item {
                    AchievementsCard(analysis.achievements)
                }

                item {
                    CoachNotesCard(analysis.coachNotes)
                }

                item {
                    CompareCard(analysis.comparisons)
                }

                if (workout != null) {
                    item {
                        PrimaryButton(
                            "Repeat Workout",
                            { onNavigate(AppRoute.ActiveWorkout(workout.id)) },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StravaSyncDetailCard(
    sync: StravaSyncInfo?,
    hasRealTrainerData: Boolean,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onView: () -> Unit,
) {
    val state = sync?.state ?: StravaSyncState.NotSynced
    val connected = sync?.connected == true
    val canSync = sync?.canSync ?: hasRealTrainerData
    val isProcessing = isSyncing || state == StravaSyncState.Syncing
    val statusText = when {
        state == StravaSyncState.Synced -> "Synced to Strava"
        isProcessing -> "Strava is processing this upload."
        state == StravaSyncState.Failed -> sync?.error ?: "Strava sync failed."
        !hasRealTrainerData -> "Only real trainer workouts can be uploaded."
        !connected -> "Connect Strava in Profile before syncing."
        else -> "Ready to upload as an indoor virtual ride."
    }
    val buttonText = when {
        state == StravaSyncState.Synced && sync?.activityUrl != null -> "View on Strava"
        isProcessing -> "Syncing..."
        !hasRealTrainerData -> "Trainer data required"
        state == StravaSyncState.Failed -> "Retry Strava Sync"
        else -> "Sync to Strava"
    }
    val enabled = when {
        state == StravaSyncState.Synced -> sync?.activityUrl != null
        isProcessing -> false
        else -> connected && canSync
    }

    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Strava", fontWeight = FontWeight.Bold, color = ForgeStrava)
            Text(statusText, color = ForgeMuted)
            if (state == StravaSyncState.Synced && sync?.activityUrl != null) {
                PrimaryButton(buttonText, onView, Modifier.fillMaxWidth(), enabled = enabled)
            } else {
                SecondaryButton(buttonText, onSync, Modifier.fillMaxWidth(), enabled = enabled)
            }
        }
    }
}

@Composable
private fun StickySummaryHeader(
    title: String,
    summary: WorkoutSession,
    analysis: WorkoutAnalysis,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ForgeSurface.copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ForgeBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatDuration(summary.elapsedSeconds)} / ${summary.completionPercent}% complete",
                    color = ForgeMuted,
                    fontSize = 12.sp,
                )
            }
            CompactMetric("${analysis.averagePowerWatts} W", "Avg")
            CompactMetric("${analysis.normalizedPowerWatts} W", "NP")
            CompactMetric("${summary.tss}", "TSS")
        }
    }
}

@Composable
private fun HeroSummaryCard(
    title: String,
    summary: WorkoutSession,
    workout: Workout?,
    historyItem: RideHistoryItem?,
    analysis: WorkoutAnalysis,
) {
    AppCard(
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            ForgeBlue.copy(alpha = 0.18f),
                            ForgeCard,
                            ForgeCard,
                        ),
                    ),
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        completionDateLine(summary, historyItem),
                        color = ForgeMuted,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallPill(workout?.workoutType?.name?.replace("_", " ") ?: "Workout", ForgeBlue)
                    SmallPill(workout?.difficulty ?: "Completed", difficultyColor(workout?.difficulty))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HeroMetric("Duration", formatDuration(summary.elapsedSeconds), ForgeBlue, Modifier.weight(1f))
                HeroMetric("Distance", analysis.distanceKm?.let { "${oneDecimal(it)} km" } ?: "Not recorded", ForgeGreen, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HeroMetric("Calories", "${summary.calories}", ForgeOrange, Modifier.weight(1f))
                HeroMetric("TSS", "${summary.tss}", ForgePurple, Modifier.weight(1f))
                HeroMetric("Complete", "${summary.completionPercent}%", ForgeGreen, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HeroMetric("Avg Power", "${analysis.averagePowerWatts} W", ForgeBlue, Modifier.weight(1f))
                HeroMetric("NP", "${analysis.normalizedPowerWatts} W", ForgeBlue, Modifier.weight(1f))
                HeroMetric("FTP Impact", analysis.ftpImpactLabel, ForgePurple, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HeroMetric("Cadence", analysis.averageCadenceRpm?.let { "$it rpm" } ?: "Not recorded", ForgeGreen, Modifier.weight(1f))
                HeroMetric("Speed", analysis.averageSpeedKmh?.let { "${oneDecimal(it)} km/h" } ?: "Not recorded", ForgeBlue, Modifier.weight(1f))
                HeroMetric("Heart Rate", analysis.averageHeartRateBpm?.let { "$it bpm" } ?: "Not recorded", ForgeRed, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DataQualityCard() {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recorded Metrics Unavailable", fontWeight = FontWeight.Bold)
            Text(
                "This workout has a saved summary, but no power/cadence/heart-rate sample rows were found. Analytics that require samples are shown as unavailable instead of estimated.",
                color = ForgeMuted,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun WorkoutTimelineCard(intervals: List<IntervalAnalysis>) {
    ExpandableAnalyticsCard(
        title = "Workout Timeline",
        subtitle = "Interval execution, target zone, power, and cadence trend",
        accent = ForgeBlue,
        initiallyExpanded = true,
    ) {
        if (intervals.isEmpty()) {
            EmptySection("No interval structure was saved for this workout.")
        } else {
            var selectedIndex by rememberSaveable { mutableStateOf(0) }
            val selected = intervals.getOrNull(selectedIndex) ?: intervals.first()
            TimelineSegments(
                intervals = intervals,
                selectedIndex = selectedIndex,
                onSelected = { selectedIndex = it },
            )
            DetailPanel {
                Text(selected.name, fontWeight = FontWeight.Bold)
                DetailRow("Type", selected.typeLabel)
                DetailRow("Duration", formatDuration(selected.durationSeconds))
                DetailRow("Target", "${selected.targetZone} / ${selected.targetPowerWatts} W")
                DetailRow("Avg Power", selected.averagePowerWatts?.let { "$it W" } ?: "Not recorded")
                DetailRow("Cadence", selected.averageCadenceRpm?.let { "$it rpm" } ?: "Not recorded")
                DetailRow("Cadence Trend", selected.cadenceTrendLabel)
            }
        }
    }
}

@Composable
private fun PowerAnalyticsCard(analysis: WorkoutAnalysis) {
    ExpandableAnalyticsCard(
        title = "Power Analytics",
        subtitle = "Power graph, curve, zones, NP, and intensity",
        accent = ForgeBlue,
        initiallyExpanded = true,
    ) {
        LineChart(
            title = "Power vs Target",
            series = analysis.powerSeries,
            color = ForgeBlue,
            targetSeries = analysis.targetPowerSeries,
            targetColor = ForgeMuted,
            emptyLabel = "No recorded power samples.",
        )
        MetricRow(
            listOf(
                MetricValue("Avg", "${analysis.averagePowerWatts} W", ForgeBlue),
                MetricValue("NP", "${analysis.normalizedPowerWatts} W", ForgeBlue),
                MetricValue("IF", analysis.intensityFactor?.let { twoDecimals(it) } ?: "N/A", ForgePurple),
            ),
        )
        DetailPanel {
            DetailRow("NP vs Avg Power", "${analysis.normalizedPowerWatts} W vs ${analysis.averagePowerWatts} W")
            DetailRow("Left/right balance", "Not recorded")
        }
        PowerCurveChips(analysis.powerCurve)
        ZoneBars(analysis.powerZones, zoneColors(), showTitle = false)
    }
}

@Composable
private fun CadenceAnalyticsCard(analysis: WorkoutAnalysis) {
    ExpandableAnalyticsCard(
        title = "Cadence Analytics",
        subtitle = "Cadence trend, consistency, zones, and drops",
        accent = ForgeGreen,
        initiallyExpanded = false,
    ) {
        LineChart(
            title = "Cadence",
            series = analysis.cadenceSeries,
            color = ForgeGreen,
            emptyLabel = "No recorded cadence samples.",
        )
        MetricRow(
            listOf(
                MetricValue("Avg", analysis.averageCadenceRpm?.let { "$it rpm" } ?: "N/A", ForgeGreen),
                MetricValue("Max", analysis.maxCadenceRpm?.let { "$it rpm" } ?: "N/A", ForgeGreen),
                MetricValue("Consistency", analysis.cadenceConsistencyScore?.let { "$it/100" } ?: "N/A", ForgePurple),
            ),
        )
        ZoneBars(analysis.cadenceZones, listOf(ForgeZone1, ForgeZone2, ForgeGreen, ForgeYellow, ForgeOrange), showTitle = false)
        val drops = analysis.intervals.filter { "Dropped" in it.cadenceTrendLabel }
        if (drops.isNotEmpty()) {
            DetailPanel {
                Text("Cadence Drops", fontWeight = FontWeight.Bold)
                drops.take(3).forEach { Text("${it.name}: ${it.cadenceTrendLabel}", color = ForgeMuted) }
            }
        }
    }
}

@Composable
private fun HeartRateAnalyticsCard(analysis: WorkoutAnalysis) {
    ExpandableAnalyticsCard(
        title = "Heart Rate Analytics",
        subtitle = "HR graph, zones, recovery trend, and drift",
        accent = ForgeRed,
        initiallyExpanded = false,
    ) {
        LineChart(
            title = "Heart Rate",
            series = analysis.heartRateSeries,
            color = ForgeRed,
            emptyLabel = "No recorded heart-rate samples.",
        )
        MetricRow(
            listOf(
                MetricValue("Avg", analysis.averageHeartRateBpm?.let { "$it bpm" } ?: "N/A", ForgeRed),
                MetricValue("Max", analysis.maxHeartRateBpm?.let { "$it bpm" } ?: "N/A", ForgeRed),
                MetricValue("Drift", analysis.heartRateDriftPercent?.let { "${oneDecimal(it)}%" } ?: "N/A", ForgeOrange),
            ),
        )
        DetailPanel {
            DetailRow("Recovery Trend", analysis.recoveryTrendBpm?.let { signed(it, " bpm") } ?: "Not recorded")
            DetailRow("HR Drift", analysis.heartRateDriftPercent?.let { "${oneDecimal(it)}%" } ?: "Not enough HR data")
        }
        ZoneBars(analysis.heartRateZones, listOf(ForgeZone2, ForgeGreen, ForgeYellow, ForgeOrange, ForgeRed), showTitle = false)
    }
}

@Composable
private fun SpeedAnalyticsCard(analysis: WorkoutAnalysis) {
    ExpandableAnalyticsCard(
        title = "Speed Analytics",
        subtitle = "Trainer speed, distance, max speed, and consistency",
        accent = ForgeBlue,
        initiallyExpanded = false,
    ) {
        LineChart(
            title = "Speed",
            series = analysis.speedSeries,
            color = ForgeBlue,
            emptyLabel = "Speed was not recorded for this session.",
        )
        MetricRow(
            listOf(
                MetricValue("Avg", analysis.averageSpeedKmh?.let { "${oneDecimal(it)} km/h" } ?: "N/A", ForgeBlue),
                MetricValue("Max", analysis.maxSpeedKmh?.let { "${oneDecimal(it)} km/h" } ?: "N/A", ForgeBlue),
                MetricValue("Consistency", analysis.speedConsistencyScore?.let { "$it/100" } ?: "N/A", ForgePurple),
            ),
        )
    }
}

@Composable
private fun ZoneDistributionCard(analysis: WorkoutAnalysis) {
    ExpandableAnalyticsCard(
        title = "Zone Distribution",
        subtitle = "Time in Z1-Z6 by recorded power and FTP",
        accent = ForgeOrange,
        initiallyExpanded = true,
    ) {
        ZoneBars(analysis.powerZones, zoneColors(), showTitle = false)
    }
}

@Composable
private fun TrainerMetricsCard(metrics: TrainerAnalytics?) {
    ExpandableAnalyticsCard(
        title = "Trainer Metrics",
        subtitle = "ERG compliance and target power accuracy",
        accent = ForgePurple,
        initiallyExpanded = false,
    ) {
        if (metrics == null) {
            EmptySection("Trainer target data was not recorded for this workout.")
        } else {
            MetricRow(
                listOf(
                    MetricValue("ERG Compliance", "${metrics.ergComplianceScore}/100", ForgePurple),
                    MetricValue("Accuracy", "${metrics.targetAccuracyPercent}%", ForgeBlue),
                    MetricValue("Avg Deviation", "${metrics.averageDeviationWatts} W", ForgeOrange),
                ),
            )
            DetailPanel {
                DetailRow("Resistance Changes", "${metrics.resistanceChanges}")
                DetailRow("Power Target Accuracy", "${metrics.targetAccuracyPercent}%")
            }
        }
    }
}

@Composable
private fun InsightsCard(insights: List<String>) {
    ExpandableAnalyticsCard(
        title = "Ride Insights",
        subtitle = "Execution notes from recorded workout data",
        accent = ForgeGreen,
        initiallyExpanded = true,
    ) {
        insights.forEach { insight ->
            InsightRow(insight, ForgeGreen)
        }
    }
}

@Composable
private fun AchievementsCard(achievements: List<AchievementInsight>) {
    ExpandableAnalyticsCard(
        title = "Achievements and PRs",
        subtitle = "Verified highlights from saved ride data",
        accent = ForgeYellow,
        initiallyExpanded = false,
    ) {
        achievements.forEach { achievement ->
            DetailPanel {
                Text(achievement.title, fontWeight = FontWeight.Bold)
                Text(achievement.subtitle, color = ForgeMuted)
            }
        }
    }
}

@Composable
private fun CoachNotesCard(notes: CoachNotes) {
    ExpandableAnalyticsCard(
        title = "Coach Notes",
        subtitle = "Recovery, recommendation, and next workout",
        accent = ForgeBlue,
        initiallyExpanded = true,
    ) {
        DetailPanel {
            Text(
                notes.summary,
                fontWeight = FontWeight.Bold,
                lineHeight = 21.sp,
            )
            CoachNoteItem("Recommendation", notes.recommendation)
            CoachNoteItem("Recovery", notes.recovery)
            CoachNoteItem("Next Workout", notes.nextWorkout)
        }
    }
}

@Composable
private fun CompareCard(comparisons: List<ComparisonInsight>) {
    ExpandableAnalyticsCard(
        title = "Compare",
        subtitle = "Previous ride and recent-history context",
        accent = ForgePurple,
        initiallyExpanded = false,
    ) {
        comparisons.forEach { comparison ->
            DetailPanel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(comparison.title, fontWeight = FontWeight.Bold)
                    Text(comparison.value, color = comparisonColor(comparison.value), fontWeight = FontWeight.Bold)
                }
                Text(comparison.detail, color = ForgeMuted)
            }
        }
    }
}

@Composable
private fun ExpandableAnalyticsCard(
    title: String,
    subtitle: String,
    accent: Color,
    initiallyExpanded: Boolean,
    content: ColumnScopeContent,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent),
                )
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(subtitle, color = ForgeMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = ForgeMuted,
                )
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    content()
                }
            }
        }
    }
}

private typealias ColumnScopeContent = @Composable ColumnScope.() -> Unit

@Composable
private fun TimelineSegments(
    intervals: List<IntervalAnalysis>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ForgeSurfaceHigh),
        ) {
            intervals.forEachIndexed { index, interval ->
                Box(
                    modifier = Modifier
                        .weight(interval.durationSeconds.coerceAtLeast(1).toFloat())
                        .fillMaxHeight()
                        .background(intervalColor(interval.typeLabel).copy(alpha = if (index == selectedIndex) 1f else 0.54f))
                        .clickable { onSelected(index) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Warmup" to intervalColor("Warmup"),
                "Work" to intervalColor("Work"),
                "Recovery" to intervalColor("Recovery"),
                "Cooldown" to intervalColor("Cooldown"),
            ).forEach { (label, color) -> Legend(label, color) }
        }
    }
}

@Composable
private fun LineChart(
    title: String,
    series: List<ChartPoint>,
    color: Color,
    emptyLabel: String,
    targetSeries: List<ChartPoint> = emptyList(),
    targetColor: Color = ForgeMuted,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        if (series.isEmpty()) {
            EmptySection(emptyLabel)
            return@Column
        }
        val points = remember(series) { series.sortedBy { it.elapsedSeconds } }
        val targetPoints = remember(targetSeries) { targetSeries.sortedBy { it.elapsedSeconds } }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(188.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ForgeSurfaceHigh)
                .border(1.dp, ForgeBorder, RoundedCornerShape(14.dp))
                .padding(10.dp),
        ) {
            val drawableWidth = size.width
            val drawableHeight = size.height
            val allValues = (points + targetPoints).map { it.value }.filter { it > 0.0 }
            val minValue = 0.0
            val maxValue = (allValues.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
            val firstSecond = points.first().elapsedSeconds
            val lastSecond = points.last().elapsedSeconds.coerceAtLeast(firstSecond + 1)

            repeat(4) { line ->
                val y = drawableHeight * (line + 1) / 5f
                drawLine(
                    color = ForgeBorder.copy(alpha = 0.45f),
                    start = Offset(0f, y),
                    end = Offset(drawableWidth, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            fun x(point: ChartPoint): Float {
                return ((point.elapsedSeconds - firstSecond).toFloat() / (lastSecond - firstSecond).toFloat()) * drawableWidth
            }

            fun y(point: ChartPoint): Float {
                val normalized = ((point.value - minValue) / (maxValue - minValue)).toFloat().coerceIn(0f, 1f)
                return drawableHeight - normalized * drawableHeight
            }

            fun drawPathFor(chartPoints: List<ChartPoint>, pathColor: Color, strokeWidth: Float) {
                if (chartPoints.size < 2) return
                val path = Path()
                chartPoints.forEachIndexed { index, point ->
                    val offset = Offset(x(point), y(point))
                    if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
                }
                drawPath(
                    path = path,
                    color = pathColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            drawPathFor(targetPoints, targetColor.copy(alpha = 0.55f), 2.dp.toPx())
            drawPathFor(points, color, 3.dp.toPx())
        }
    }
}

@Composable
private fun PowerCurveChips(curve: List<PeakPower>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Peak Powers", fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            curve.forEach { peak ->
                Column(
                    modifier = Modifier
                        .width(94.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ForgeSurfaceHigh)
                        .border(1.dp, ForgeBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(peak.label, color = ForgeMuted, fontSize = 12.sp)
                    Text(peak.watts?.let { "$it W" } ?: "N/A", fontWeight = FontWeight.Bold, color = ForgeBlue)
                }
            }
        }
    }
}

@Composable
private fun ZoneBars(
    zones: List<ZoneDistribution>,
    colors: List<Color>,
    showTitle: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showTitle) {
            Text("Distribution", fontWeight = FontWeight.SemiBold)
        }
        if (zones.isEmpty() || zones.all { it.seconds == 0 }) {
            EmptySection("No zone data was recorded.")
        } else {
            zones.forEachIndexed { index, zone ->
                val color = colors.getOrElse(index) { ForgeBlue }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(zone.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(zone.rangeLabel, color = ForgeMuted, fontSize = 12.sp)
                        }
                        Text("${formatDuration(zone.seconds)} / ${zone.percent}%", color = ForgeMuted, fontSize = 12.sp)
                    }
                    LinearProgressIndicator(
                        progress = { zone.percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = color,
                        trackColor = ForgeSurfaceHigh,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(metrics: List<MetricValue>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        metrics.forEach { metric ->
            HeroMetric(metric.label, metric.value, metric.color, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ForgeSurfaceHigh)
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = ForgeMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CompactMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        Text(label, color = ForgeMuted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun DetailPanel(content: ColumnScopeContent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ForgeSurfaceHigh.copy(alpha = 0.72f))
            .border(1.dp, ForgeBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = ForgeMuted, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Spacer(Modifier.width(10.dp))
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun CoachNoteItem(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            label,
            color = ForgeBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            color = ForgeText,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun InsightRow(text: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(accent),
        )
        Text(text, color = ForgeText, lineHeight = 18.sp)
    }
}

@Composable
private fun EmptySection(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ForgeSurfaceHigh.copy(alpha = 0.58f))
            .border(1.dp, ForgeBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(text, color = ForgeMuted, lineHeight = 18.sp)
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(color),
        )
        Text(label, color = ForgeMuted, fontSize = 11.sp)
    }
}

private data class MetricValue(
    val label: String,
    val value: String,
    val color: Color,
)

private fun zoneColors(): List<Color> = listOf(
    ForgeZone1,
    ForgeZone2,
    ForgeZone3,
    ForgeZone4,
    ForgeZone5,
    ForgeZone6,
)

private fun intervalColor(type: String): Color = when (type) {
    "Warmup" -> ForgeBlue
    "Recovery" -> ForgeGreen
    "Cooldown" -> ForgePurple
    else -> ForgeOrange
}

private fun difficultyColor(difficulty: String?): Color {
    return when (difficulty?.lowercase()) {
        "easy" -> ForgeGreen
        "moderate" -> ForgeBlue
        "hard" -> ForgeOrange
        "expert" -> ForgeRed
        else -> ForgePurple
    }
}

private fun comparisonColor(value: String): Color {
    return when {
        value.startsWith("+") -> ForgeGreen
        value.startsWith("-") -> ForgeOrange
        else -> ForgeMuted
    }
}

private fun formatDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val remainingSeconds = safeSeconds % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "${remainingSeconds}s"
    }
}

private fun oneDecimal(value: Double): String {
    val rounded = (value * 10.0).roundToInt()
    val sign = if (rounded < 0) "-" else ""
    val absolute = abs(rounded)
    return "$sign${absolute / 10}.${absolute % 10}"
}

private fun twoDecimals(value: Double): String {
    val rounded = (value * 100.0).roundToInt()
    val sign = if (rounded < 0) "-" else ""
    val absolute = abs(rounded)
    val cents = absolute % 100
    return "$sign${absolute / 100}.${if (cents < 10) "0" else ""}$cents"
}

private fun signed(value: Int, suffix: String): String {
    return when {
        value > 0 -> "+$value$suffix"
        value < 0 -> "-${abs(value)}$suffix"
        else -> "0$suffix"
    }
}

private fun formatDate(date: LocalDate): String {
    val month = when (date.month) {
        Month.JANUARY -> "Jan"
        Month.FEBRUARY -> "Feb"
        Month.MARCH -> "Mar"
        Month.APRIL -> "Apr"
        Month.MAY -> "May"
        Month.JUNE -> "Jun"
        Month.JULY -> "Jul"
        Month.AUGUST -> "Aug"
        Month.SEPTEMBER -> "Sep"
        Month.OCTOBER -> "Oct"
        Month.NOVEMBER -> "Nov"
        Month.DECEMBER -> "Dec"
    }
    return "$month ${date.day}, ${date.year}"
}

private fun completionDateLine(
    summary: WorkoutSession,
    historyItem: RideHistoryItem?,
): String {
    return summary.completedAtEpochMillis?.let(::formatCompletionTimestamp)
        ?: historyItem?.date?.let { "${formatDate(it)} - time not recorded" }
        ?: "Completion time not recorded"
}

private fun formatCompletionTimestamp(epochMillis: Long): String {
    val local = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val date = LocalDate(local.year, local.month, local.day)
    return "${formatDate(date)} at ${twoDigit(local.hour)}:${twoDigit(local.minute)}"
}

private fun twoDigit(value: Int): String {
    return if (value < 10) "0$value" else value.toString()
}
