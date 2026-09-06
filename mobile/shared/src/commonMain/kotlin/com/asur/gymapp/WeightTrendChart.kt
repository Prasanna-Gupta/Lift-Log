package com.asur.gymapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * Custom-drawn line chart for weight trend over time. Points are (date, kg) pairs,
 * expected pre-sorted ascending by date. X-position is proportional to actual
 * elapsed days, not array index — so irregular logging gaps show correctly
 * rather than implying evenly-spaced readings.
 */
@Composable
fun WeightTrendChart(points: List<Pair<LocalDate, Double>>) {
    if (points.size < 2) {
        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (points.isEmpty()) "No weight logged in this range" else "Need at least 2 entries to show a trend",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextTertiary
            )
        }
        return
    }

    val minWeight = points.minOf { it.second }
    val maxWeight = points.maxOf { it.second }
    val range = (maxWeight - minWeight).coerceAtLeast(0.5)
    val paddedMin = minWeight - range * 0.15
    val paddedMax = maxWeight + range * 0.15

    val firstDate = points.first().first
    val lastDate = points.last().first
    val totalDays = firstDate.daysUntil(lastDate).coerceAtLeast(1)

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = AppColors.Divider

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val w = size.width
            val h = size.height

            for (i in 0..2) {
                val y = h * i / 2f
                drawLine(color = gridColor, start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1.dp.toPx())
            }

            fun xFor(date: LocalDate): Float {
                val elapsed = firstDate.daysUntil(date)
                return w * (elapsed.toFloat() / totalDays.toFloat())
            }
            fun yFor(weight: Double) = h - ((weight - paddedMin) / (paddedMax - paddedMin) * h).toFloat()

            val path = androidx.compose.ui.graphics.Path().apply {
                points.forEachIndexed { i, (date, weight) ->
                    val x = xFor(date)
                    val y = yFor(weight)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }

            drawPath(path = path, color = lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

            points.forEachIndexed { i, (date, weight) ->
                val isLast = i == points.lastIndex
                drawCircle(
                    color = lineColor,
                    radius = if (isLast) 4.dp.toPx() else 2.5.dp.toPx(),
                    center = Offset(xFor(date), yFor(weight))
                )
            }
        }

        Spacer(Modifier.height(7.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                formatDayMonth(firstDate.toString()),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                color = AppColors.TextTertiary,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatDayMonth(lastDate.toString()),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                color = AppColors.TextTertiary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "${maxWeight.oneDecimal()} – ${minWeight.oneDecimal()} kg",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextTertiary
            )
        }
    }
}