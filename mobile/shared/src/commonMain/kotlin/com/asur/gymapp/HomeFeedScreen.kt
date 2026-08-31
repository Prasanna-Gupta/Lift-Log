package com.asur.gymapp

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.Duration.Companion.days

private data class FeedGroup(val userId: String, val userName: String, val avatarUrl: String?, val entries: List<FeedItem>)

private fun groupConsecutive(items: List<FeedItem>): List<FeedGroup> {
    val groups = mutableListOf<FeedGroup>()
    for (item in items) {
        val last = groups.lastOrNull()
        if (last != null && last.userId == item.user_id) {
            groups[groups.lastIndex] = last.copy(entries = last.entries + item)
        } else {
            groups.add(FeedGroup(item.user_id, item.user_name, item.avatar_url, listOf(item)))
        }
    }
    return groups
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeFeedScreen() {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var groupStatus by remember { mutableStateOf<List<Pair<UserProfileRow, Boolean>>>(emptyList()) }

    suspend fun refresh() {
        try {
            val threeDaysAgo = (Clock.System.now() - 3.days).toString()
            items = supabase.postgrest.from("feed_items")
                .select {
                    filter { gte("created_at", threeDaysAgo) }
                    order("created_at", Order.DESCENDING)
                    limit(50)
                }
                .decodeList<FeedItem>()
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = "Couldn't load the feed. ${e.message}"
        } finally {
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        groupStatus = fetchGroupStatus()
    }

    val grouped = remember(items) { groupConsecutive(items) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Home",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "See what your crew's been up to",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        if (groupStatus.isNotEmpty()) {
            GroupStatusRow(groupStatus)
            Spacer(Modifier.height(20.dp))
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { refreshing = true; scope.launch { refresh() } },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (grouped.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(top = 64.dp), contentAlignment = Alignment.TopCenter) {
                            Text(
                                "No activity yet — log a workout or meal to get things going.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            items(grouped, key = { it.userId + it.entries.first().id }) { group ->
                                FeedGroupCard(
                                    group = group,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(durationMillis = 400),
                                        placementSpec = tween(durationMillis = 400),
                                        fadeOutSpec = tween(durationMillis = 200)
                                    )
                                )
                            }
                        }
                    }
                }
                BottomFadeOverlay()
            }
        }
    }
}

@Composable
private fun FeedGroupCard(group: FeedGroup, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            if (group.avatarUrl != null) {
                AsyncImage(
                    model = group.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(38.dp).clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(group.userName.take(1), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(group.userName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }

        group.entries.forEachIndexed { index, item ->
            FeedEntryRow(item)
            if (index < group.entries.lastIndex) {
                Spacer(Modifier.height(2.dp))
                HorizontalDivider(color = AppColors.Divider)
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun FeedEntryRow(item: FeedItem) {
    val isWorkout = item.activity_type == "workout"

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF3A2A26)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isWorkout) Icons.Filled.FitnessCenter else Icons.Filled.Restaurant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            when (item.activity_type) {
                "workout" -> {
                    Text(item.workout_title ?: "Workout", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    item.workout_exercise_count?.let {
                        Text(
                            if (it == 1) "1 exercise" else "$it exercises",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextTertiary
                        )
                    }
                }
                "diet" -> {
                    Text(item.meal_label ?: "Entry", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "${item.diet_calories ?: 0} cal · ${item.diet_protein_g?.toInt() ?: 0}g protein",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextTertiary
                    )
                }
                else -> Text("Logged activity", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(relativeTime(item.created_at), style = MaterialTheme.typography.labelSmall)
    }
}

private fun relativeTime(isoTimestamp: String): String {
    return try {
        val then = Instant.parse(isoTimestamp)
        val now = Clock.System.now()
        val diffSeconds = (now - then).inWholeSeconds.coerceAtLeast(0)
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

@Composable
private fun GroupStatusRow(members: List<Pair<UserProfileRow, Boolean>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        members.forEach { (user, active) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    val avatarModifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .then(
                            if (active) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier
                        )
                    if (user.avatar_url != null) {
                        AsyncImage(
                            model = user.avatar_url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = if (!active) avatarModifier.background(MaterialTheme.colorScheme.surfaceVariant) else avatarModifier
                        )
                    } else {
                        Box(
                            modifier = avatarModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(user.name?.take(1) ?: "?", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (active) {
                        Box(
                            modifier = Modifier.size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    user.name?.split(" ")?.firstOrNull() ?: "?",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}