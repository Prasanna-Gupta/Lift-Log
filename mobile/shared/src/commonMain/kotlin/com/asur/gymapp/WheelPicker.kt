package com.asur.gymapp

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun <T> WheelPicker(
    items: List<T>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier,
    visibleItemCount: Int = 5
) {
    val itemHeight = 44.dp
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(listState)

    LaunchedEffect(selectedIndex, items) {
        if (listState.firstVisibleItemIndex != selectedIndex) {
            listState.scrollToItem(selectedIndex)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    val itemHeightPx = with(density) { itemHeight.toPx() }
                    val offsetItems = (listState.firstVisibleItemScrollOffset / itemHeightPx).let {
                        if (it > 0.5f) 1 else 0
                    }
                    val idx = (listState.firstVisibleItemIndex + offsetItems).coerceIn(0, items.size - 1)
                    if (idx != selectedIndex) onSelectedIndexChange(idx)
                }
            }
    }

    Box(modifier = modifier.height(itemHeight * visibleItemCount), contentAlignment = Alignment.Center) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleItemCount / 2)),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items) { index, item ->
                Box(
                    modifier = Modifier.height(itemHeight).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        itemLabel(item),
                        style = if (index == selectedIndex) MaterialTheme.typography.headlineSmall
                        else MaterialTheme.typography.bodyLarge,
                        color = if (index == selectedIndex) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.align(Alignment.Center).offset(y = -itemHeight / 2))
        HorizontalDivider(modifier = Modifier.align(Alignment.Center).offset(y = itemHeight / 2))
    }
}