package com.asur.gymapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import coil3.compose.AsyncImage
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeFeedScreen() {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        try {
            items = supabase.postgrest.from("feed_items")
                .select {
                    order("created_at", Order.DESCENDING)
                    limit(50)
                }
                .decodeList<FeedItem>()
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = "Failed to load feed: ${e.message}"
        } finally {
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Home", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "See what your crew's been up to",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    scope.launch { refresh() }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(top = 64.dp), contentAlignment = Alignment.TopCenter) {
                        Text(
                            "No activity yet — log a workout or meal to get things going.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            FeedItemCard(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedItemCard(item: FeedItem) {
    val isWorkout = item.activity_type == "workout"
    val accentColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            if (item.avatar_url != null) {
                AsyncImage(
                    model = item.avatar_url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier.size(44.dp).background(accentColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isWorkout) "🏋️" else "🍽️", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.user_name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        relativeTime(item.created_at),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                when (item.activity_type) {
                    "workout" -> {
                        Text(item.workout_title ?: "Workout", style = MaterialTheme.typography.bodyLarge)
                        item.workout_exercise_count?.let {
                            Text(
                                "$it exercise${if (it != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    "diet" -> {
                        Text(item.meal_label ?: "Entry", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${item.diet_calories ?: 0} cal · ${item.diet_protein_g?.toInt() ?: 0}g protein",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    else -> Text("Logged activity", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private fun relativeTime(isoTimestamp: String): String {
    return try {
        val then = Instant.parse(isoTimestamp)
        val now = Clock.System.now()
        val diffSeconds = (now - then).inWholeSeconds
        when {
            diffSeconds < 60 -> "now"
            diffSeconds < 3600 -> "${diffSeconds / 60}m"
            diffSeconds < 86400 -> "${diffSeconds / 3600}h"
            else -> "${diffSeconds / 86400}d"
        }
    } catch (e: Exception) {
        ""
    }
}