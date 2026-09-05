package com.asur.gymapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.launch

@OptIn(ExperimentalTime::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf<List<NotificationRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    suspend fun refresh() {
        notifications = fetchNotifications()
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

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
                "Notifications",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (notifications.any { !it.is_read }) {
                Text(
                    "Mark all read",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        scope.launch {
                            markAllNotificationsRead()
                            refresh()
                        }
                    }
                )
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (notifications.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = AppColors.TextTertiary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Nothing yet", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Streak milestones, nudges, and group activity will show up here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(notifications, key = { _, n -> n.id }) { index, notification ->
                    NotificationRowItem(
                        notification = notification,
                        onClick = {
                            if (!notification.is_read) {
                                scope.launch {
                                    markNotificationRead(notification.id)
                                    refresh()
                                }
                            }
                        }
                    )
                    if (index < notifications.lastIndex) HorizontalDivider(color = AppColors.Divider)
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun NotificationRowItem(notification: NotificationRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.padding(top = 5.dp).size(7.dp).clip(CircleShape)
                .background(if (!notification.is_read) MaterialTheme.colorScheme.primary else AppColors.Divider)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                notification.body,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (!notification.is_read) FontWeight.Medium else FontWeight.Normal
            )
            Spacer(Modifier.height(3.dp))
            Text(relativeTimeAgo(notification.created_at), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun relativeTimeAgo(isoTimestamp: String): String {
    return try {
        val then = Instant.parse(isoTimestamp)
        val diff = (Clock.System.now() - then).inWholeSeconds.coerceAtLeast(0)
        when {
            diff < 60 -> "just now"
            diff < 3600 -> "${diff / 60}m ago"
            diff < 86400 -> "${diff / 3600}h ago"
            diff < 604800 -> "${diff / 86400}d ago"
            else -> "${diff / 604800}w ago"
        }
    } catch (e: Exception) {
        ""
    }
}