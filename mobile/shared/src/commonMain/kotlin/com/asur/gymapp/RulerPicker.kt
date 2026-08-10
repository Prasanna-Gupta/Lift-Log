package com.asur.gymapp

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun RulerPicker(
    range: IntRange,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    majorStep: Int = 10,
    tickSpacing: Dp = 12.dp,
    height: Dp = 90.dp,
    majorTickHeight: Dp = 36.dp,
    minorTickHeight: Dp = 18.dp,
    showLabels: Boolean = true
) {
    val values = remember(range) { range.toList() }
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(listState)
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.height(height)) {
        val sidePadding = (maxWidth / 2) - (tickSpacing / 2)

        LaunchedEffect(value, range) {
            val idx = values.indexOf(value).coerceAtLeast(0)
            if (listState.firstVisibleItemIndex != idx) {
                listState.scrollToItem(idx)
            }
        }

        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }
                .collect { scrolling ->
                    if (!scrolling) {
                        val tickPx = with(density) { tickSpacing.toPx() }
                        val offsetItems = if (listState.firstVisibleItemScrollOffset / tickPx > 0.5f) 1 else 0
                        val idx = (listState.firstVisibleItemIndex + offsetItems).coerceIn(0, values.size - 1)
                        val newValue = values[idx]
                        if (newValue != value) onValueChange(newValue)
                    }
                }
        }

        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(horizontal = sidePadding),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(values) { _, v ->
                val isMajor = v % majorStep == 0
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.width(tickSpacing).fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(if (isMajor) majorTickHeight else minorTickHeight)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    if (showLabels) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isMajor) "$v" else "",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(3.dp)
                .height(majorTickHeight + 4.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}