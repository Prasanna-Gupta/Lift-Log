package com.asur.gymapp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private enum class Range(val label: String) {
    WEEK("Week"), MONTH("Month"), ALL("All time")
}

@Composable
fun ProgressScreen(onBack: () -> Unit) {
    var summary by remember { mutableStateOf<ProgressSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var range by remember { mutableStateOf(Range.WEEK) }

    LaunchedEffect(Unit) {
        summary = fetchProgressSummary()
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(22.dp).clickable(onClick = onBack)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                "Progress",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                fontWeight = FontWeight.Medium
            )
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }

        val s = summary ?: return@Column
        val maxRM = s.prs.maxOfOrNull { it.weight } ?: 1.0

        val rangeVolume = when (range) {
            Range.WEEK -> s.volumeThisWeek
            Range.MONTH -> s.volumeThisMonth
            Range.ALL -> s.totalVolumeAllTime
        }
        val rangeWorkouts = when (range) {
            Range.WEEK -> s.workoutsThisWeek
            Range.MONTH -> s.workoutsThisMonth
            Range.ALL -> null
        }
        val deltaPercent = if (range == Range.WEEK && s.volumeLastWeek > 0.0) {
            ((s.volumeThisWeek - s.volumeLastWeek) / s.volumeLastWeek * 100).toInt()
        } else null

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(3.dp)
                ) {
                    Range.entries.forEach { r ->
                        val active = r == range
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (active) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                .clickable { range = r }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                r.label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                                color = if (active) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                when (range) {
                                    Range.WEEK -> "Volume this week"
                                    Range.MONTH -> "Volume this month"
                                    Range.ALL -> "Volume all time"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                when (range) {
                                    Range.WEEK -> "by day"
                                    Range.MONTH -> "by week"
                                    Range.ALL -> "by month"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                formatThousands(rangeVolume),
                                style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "kg",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Spacer(Modifier.weight(1f))
                            if (deltaPercent != null) {
                                Text(
                                    (if (deltaPercent >= 0) "+" else "") + "$deltaPercent% vs last week",
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                                    color = if (deltaPercent >= 0) MaterialTheme.colorScheme.primary
                                    else AppColors.TextTertiary,
                                    modifier = Modifier.padding(bottom = 5.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        when (range) {
                            Range.WEEK -> {
                                val today = LocalDate.parse(logDateForNow())
                                val monday = today.minus(DatePeriod(days = today.dayOfWeek.isoDayNumber - 1))
                                VolumeBarChart(
                                    bars = (0..6).map { off ->
                                        val d = monday.plus(DatePeriod(days = off))
                                        d to (s.dailyVolumeLast7[d] ?: 0.0)
                                    },
                                    labelFor = { it.dayOfWeek.name.take(1) },
                                    highlightIndex = today.dayOfWeek.isoDayNumber - 1
                                )
                            }
                            Range.MONTH -> VolumeBarChart(
                                bars = s.weeklyVolumeThisMonth,
                                labelFor = { d -> "W${s.weeklyVolumeThisMonth.indexOfFirst { it.first == d } + 1}" },
                                highlightIndex = s.weeklyVolumeThisMonth.lastIndex
                            )
                            Range.ALL -> VolumeBarChart(
                                bars = s.monthlyVolumeAllTime,
                                labelFor = { monthInitial(it.monthNumber) },
                                highlightIndex = s.monthlyVolumeAllTime.lastIndex
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                            Text("Body weight", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    s.weightHistory.lastOrNull()?.second?.oneDecimal() ?: "—",
                                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp),
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    "kg",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                            val filteredWeights = remember(s.weightHistory, range) {
                                val cutoff = when (range) {
                                    Range.WEEK -> LocalDate.parse(logDateForNow()).minus(DatePeriod(days = 6))
                                    Range.MONTH -> LocalDate.parse(logDateForNow()).minus(DatePeriod(days = 29))
                                    Range.ALL -> null
                                }
                                if (cutoff == null) s.weightHistory else s.weightHistory.filter { it.first >= cutoff }
                            }
                            WeightTrendChart(points = filteredWeights)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricItem(
                        Modifier.weight(1f),
                        rangeWorkouts?.toString() ?: "${s.prs.size}",
                        if (rangeWorkouts != null) "workouts" else "exercises"
                    )
                    MetricDivider()
                    MetricItem(Modifier.weight(1f), "${s.prsSetThisWeek}", "PRs this week")
                    MetricDivider()
                    MetricItem(
                        Modifier.weight(1f),
                        s.prs.firstOrNull()?.let { "${it.weight.oneDecimal()} kg" } ?: "—",
                        "best lift"
                    )
                }

                Spacer(Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text("PERSONAL RECORDS", style = MaterialTheme.typography.labelSmall)
                    if (s.prs.isNotEmpty()) {
                        Text("max lift", style = MaterialTheme.typography.bodySmall, color = AppColors.TextTertiary)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (s.prs.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(56.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.FitnessCenter,
                                contentDescription = null,
                                tint = AppColors.TextTertiary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No records yet",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Log a few weighted sets and your best lifts show up here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(s.prs) { index, pr ->
                    val target = (pr.weight / maxRM).toFloat().coerceIn(0.04f, 1f)
                    val fraction by animateFloatAsState(
                        targetValue = target,
                        animationSpec = tween(durationMillis = 600),
                        label = "prBar"
                    )
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 15.dp, bottom = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    pr.exerciseName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    buildString {
                                        append(if (pr.reps == 1) "1 rep" else "${pr.reps} reps")
                                        pr.achievedOn?.let { append("  ·  ${formatDayMonth(it.toString())}") }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextTertiary
                                )
                            }
                            Text(
                                "${pr.weight.oneDecimal()} kg",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth().height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(AppColors.Divider)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight()
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                            )
                        }
                        if (index < s.prs.lastIndex) Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(modifier: Modifier = Modifier, value: String, label: String) {
    Column(modifier = modifier.padding(horizontal = 12.dp)) {
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MetricDivider() {
    Box(modifier = Modifier.width(1.dp).height(26.dp).background(AppColors.Divider))
}

@Composable
private fun VolumeBarChart(
    bars: List<Pair<LocalDate, Double>>,
    labelFor: (LocalDate) -> String,
    highlightIndex: Int
) {
    if (bars.isEmpty()) return
    val maxVolume = bars.maxOf { it.second }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEachIndexed { i, (_, v) ->
                val target = if (maxVolume > 0) (v / maxVolume).toFloat() else 0f
                val animated by animateFloatAsState(
                    targetValue = target.coerceAtLeast(if (v > 0) 0.08f else 0.03f),
                    animationSpec = tween(durationMillis = 500),
                    label = "bar$i"
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .fillMaxHeight(animated)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    v <= 0.0 -> AppColors.HeatEmpty
                                    i == highlightIndex -> MaterialTheme.colorScheme.primary
                                    else -> AppColors.HeatLow
                                }
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            bars.forEachIndexed { i, (d, _) ->
                Text(
                    labelFor(d),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    color = if (i == highlightIndex) MaterialTheme.colorScheme.primary
                    else AppColors.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun monthInitial(monthNumber: Int): String =
    listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")[monthNumber - 1]