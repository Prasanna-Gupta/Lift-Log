package com.asur.gymapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.*
import kotlin.time.Clock

private const val CELL = 16
private const val GAP = 4

@Composable
fun StreakHeatmap(activeDates: Set<String>, weeks: Int = 12) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val gridEnd = today.plus(7 - today.dayOfWeek.isoDayNumber, DateTimeUnit.DAY)
    val gridStart = gridEnd.minus(weeks * 7 - 1, DateTimeUnit.DAY)

    Row(horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(GAP.dp)) {
            listOf("M", "", "W", "", "F", "", "").forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(12.dp).height(CELL.dp)
                )
            }
        }
        repeat(weeks) { col ->
            Column(verticalArrangement = Arrangement.spacedBy(GAP.dp)) {
                repeat(7) { row ->
                    val date = gridStart.plus(col * 7 + row, DateTimeUnit.DAY)
                    val isFuture = date > today
                    val isActive = activeDates.contains(date.toString())
                    Box(
                        modifier = Modifier
                            .size(CELL.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    isActive -> AppColors.HeatFull
                                    isFuture -> AppColors.HeatEmpty.copy(alpha = 0.4f)
                                    else -> AppColors.HeatEmpty
                                }
                            )
                    )
                }
            }
        }
    }
}