package com.asur.gymapp

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.auth
import coil3.compose.AsyncImage
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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

private enum class HomeTab { FEED, LEADERBOARD }
private enum class GroupSheetStep { MENU, CREATE, JOIN, CREATED }

@OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen() {
    val scope = rememberCoroutineScope()

    var groups by remember { mutableStateOf<List<GroupRow>>(emptyList()) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var loadingGroups by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(HomeTab.FEED) }
    var showGroupSheet by remember { mutableStateOf(false) }

    var items by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var loadingFeed by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var groupStatus by remember { mutableStateOf<List<Pair<UserProfileRow, Boolean>>>(emptyList()) }

    var leaderboard by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var loadingLeaderboard by remember { mutableStateOf(false) }

    var showInviteSheet by remember { mutableStateOf(false) }
    var groupPendingDelete by remember { mutableStateOf<GroupRow?>(null) }

    var selectedWorkoutId by remember { mutableStateOf<String?>(null) }
    var selectedDietLogId by remember { mutableStateOf<String?>(null) }

    suspend fun loadGroups() {
        loadingGroups = true
        val fetched = fetchMyGroups()
        groups = fetched
        val savedId = GroupPrefs.getLastSelectedGroupId()
        selectedGroupId = when {
            fetched.any { it.id == selectedGroupId } -> selectedGroupId
            fetched.any { it.id == savedId } -> savedId
            else -> fetched.firstOrNull()?.id
        }
        loadingGroups = false
    }

    suspend fun refreshFeed(groupId: String) {
        try {
            val memberIds = fetchGroupMemberIds(groupId)
            val threeDaysAgo = (Clock.System.now() - (72 * 3600).seconds).toString()
            items = if (memberIds.isEmpty()) emptyList() else {
                supabase.postgrest.from("feed_items")
                    .select {
                        filter {
                            isIn("user_id", memberIds)
                            gte("created_at", threeDaysAgo)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(50)
                    }
                    .decodeList<FeedItem>()
            }
            groupStatus = fetchGroupStatus(groupId)
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = "Couldn't load the feed. ${e.message}"
        } finally {
            loadingFeed = false
            refreshing = false
        }
    }

    suspend fun loadLeaderboard(groupId: String) {
        loadingLeaderboard = true
        try {
            leaderboard = fetchLeaderboard(groupId)
        } catch (e: Exception) {
            errorMessage = "Couldn't load the leaderboard. ${e.message}"
        } finally {
            loadingLeaderboard = false
        }
    }

    LaunchedEffect(Unit) { loadGroups() }

    LaunchedEffect(selectedGroupId, tab) {
        val gid = selectedGroupId ?: return@LaunchedEffect
        when (tab) {
            HomeTab.FEED -> { loadingFeed = true; refreshFeed(gid) }
            HomeTab.LEADERBOARD -> loadLeaderboard(gid)
        }
    }

    selectedWorkoutId?.let { id ->
        WorkoutDetailScreen(workoutId = id, onBack = { selectedWorkoutId = null })
        return
    }
    selectedDietLogId?.let { id ->
        DietDetailScreen(dietLogId = id, onBack = { selectedDietLogId = null })
        return
    }

    val grouped = remember(items) { groupConsecutive(items) }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Home",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (selectedGroupId != null) {
                Icon(
                    Icons.Filled.PersonAdd,
                    contentDescription = "Invite to group",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).clickable { showInviteSheet = true }
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            "See what your crew's been up to",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(16.dp))

        if (!loadingGroups) {
            GroupSwitcherRow(
                groups = groups,
                selectedGroupId = selectedGroupId,
                onSelect = { id ->
                    selectedGroupId = id
                    GroupPrefs.setLastSelectedGroupId(id)
                },
                onAddClick = { showGroupSheet = true },
                onLongPress = { groupPendingDelete = it }
            )
            Spacer(Modifier.height(16.dp))
        }

        if (loadingGroups) {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (groups.isEmpty()) {
            NoGroupsEmptyState(onCreateOrJoin = { showGroupSheet = true })
        } else {
            HomeTabRow(tab = tab, onTabChange = { tab = it })
            Spacer(Modifier.height(16.dp))

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
            }

            when (tab) {
                HomeTab.FEED -> {
                    if (groupStatus.isNotEmpty()) {
                        GroupStatusRow(groupStatus, modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(Modifier.height(20.dp))
                    }

                    if (loadingFeed) {
                        Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            PullToRefreshBox(
                                isRefreshing = refreshing,
                                onRefresh = {
                                    refreshing = true
                                    scope.launch { selectedGroupId?.let { refreshFeed(it) } }
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (grouped.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize().padding(top = 64.dp), contentAlignment = Alignment.TopCenter) {
                                        Text(
                                            "No activity yet — log a workout or meal to get things going.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 20.dp)
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(bottom = 120.dp)
                                    ) {
                                        items(grouped, key = { it.userId + it.entries.first().id }) { group ->
                                            FeedGroupCard(
                                                group = group,
                                                onOpenWorkout = { selectedWorkoutId = it },
                                                onOpenDiet = { selectedDietLogId = it },
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
                        }
                    }
                }
                HomeTab.LEADERBOARD -> {
                    if (loadingLeaderboard) {
                        Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            PullToRefreshBox(
                                isRefreshing = refreshing,
                                onRefresh = {
                                    refreshing = true
                                    scope.launch {
                                        selectedGroupId?.let { loadLeaderboard(it) }
                                        refreshing = false
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                LeaderboardList(entries = leaderboard, modifier = Modifier.padding(horizontal = 20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGroupSheet) {
        GroupSheet(
            onDismiss = { showGroupSheet = false },
            onGroupReady = { group ->
                scope.launch {
                    loadGroups()
                    selectedGroupId = group.id
                    GroupPrefs.setLastSelectedGroupId(group.id)
                    showGroupSheet = false
                }
            }
        )
    }

    if (showInviteSheet) {
        val currentGroup = groups.find { it.id == selectedGroupId }
        if (currentGroup != null) {
            InviteSheet(group = currentGroup, onDismiss = { showInviteSheet = false })
        }
    }

    groupPendingDelete?.let { group ->
        val userId = supabase.auth.currentUserOrNull()?.id
        val isCreator = group.created_by == userId

        AlertDialog(
            onDismissRequest = { groupPendingDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            title = {
                Text(
                    if (isCreator) "Delete \"${group.name}\"?" else "Leave \"${group.name}\"?",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    if (isCreator) "This removes the group for everyone in it. This can't be undone."
                    else "You'll need a new invite code to rejoin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = group
                    groupPendingDelete = null
                    scope.launch {
                        try {
                            if (isCreator) deleteGroup(target.id) else leaveGroup(target.id)
                            loadGroups()
                        } catch (e: Exception) {
                            errorMessage = "Couldn't ${if (isCreator) "delete" else "leave"} the group. ${e.message}"
                        }
                    }
                }) {
                    Text(if (isCreator) "Delete" else "Leave", color = AppColors.Destructive)
                }
            },
            dismissButton = {
                TextButton(onClick = { groupPendingDelete = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupSwitcherRow(
    groups: List<GroupRow>,
    selectedGroupId: String?,
    onSelect: (String) -> Unit,
    onAddClick: () -> Unit,
    onLongPress: (GroupRow) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(groups, key = { it.id }) { group ->
            val selected = group.id == selectedGroupId
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(if (selected) Color(0xFF3A2A26) else MaterialTheme.colorScheme.surface)
                        .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                        .combinedClickable(
                            onClick = { onSelect(group.id) },
                            onLongClick = { onLongPress(group) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        group.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    group.name,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    maxLines = 1,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else AppColors.TextTertiary
                )
            }
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .border(1.dp, AppColors.Divider, CircleShape)
                        .clickable { onAddClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add group", tint = AppColors.TextTertiary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text("New", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = AppColors.TextTertiary)
            }
        }
    }
}

@Composable
private fun HomeTabRow(tab: HomeTab, onTabChange: (HomeTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(3.dp)
    ) {
        listOf(HomeTab.FEED to "Feed", HomeTab.LEADERBOARD to "Leaderboard").forEach { (value, label) ->
            val active = tab == value
            Box(
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .clickable { onTabChange(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NoGroupsEmptyState(onCreateOrJoin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Groups, contentDescription = null, tint = AppColors.TextTertiary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("No groups yet", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(7.dp))
        Text(
            "Create a group or join one with a code to see your crew's activity and leaderboard.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onCreateOrJoin)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text("Create or join a group", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun LeaderboardList(entries: List<LeaderboardEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) {
        Column(
            modifier = modifier.fillMaxWidth().padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = AppColors.TextTertiary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(12.dp))
            Text("No members yet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)
    ) {
        itemsIndexed(entries) { index, entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = when (index) {
                            0 -> MaterialTheme.colorScheme.primary
                            else -> AppColors.TextTertiary
                        }
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (entry.avatar_url != null) {
                    AsyncImage(
                        model = entry.avatar_url,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(entry.name.take(1), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(
                    if (entry.workouts_this_week == 1L) "1 workout" else "${entry.workouts_this_week} workouts",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (index == 0) MaterialTheme.colorScheme.primary else AppColors.TextTertiary,
                    fontWeight = if (index == 0) FontWeight.Medium else FontWeight.Normal
                )
            }
            if (index < entries.lastIndex) HorizontalDivider(color = AppColors.Divider)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSheet(onDismiss: () -> Unit, onGroupReady: (GroupRow) -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(GroupSheetStep.MENU) }
    var nameText by remember { mutableStateOf("") }
    var codeText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var createdGroup by remember { mutableStateOf<GroupRow?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier.padding(top = 10.dp).width(36.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(AppColors.Divider)
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Spacer(Modifier.height(4.dp))

            when (step) {
                GroupSheetStep.MENU -> {
                    Text("Groups", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Create a new group or join one with a code",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(18.dp))

                    SheetActionRow(icon = Icons.Filled.Add, label = "Create a group") {
                        error = null; step = GroupSheetStep.CREATE
                    }
                    Spacer(Modifier.height(10.dp))
                    SheetActionRow(icon = Icons.Filled.Groups, label = "Join with a code") {
                        error = null; step = GroupSheetStep.JOIN
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text("Cancel", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                GroupSheetStep.CREATE -> {
                    Text("Create a group", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(18.dp))
                    SheetTextField(value = nameText, onValueChange = { nameText = it }, placeholder = "Group name")
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(18.dp))
                    SheetPrimaryButton(
                        label = if (loading) "Creating" else "Create",
                        enabled = nameText.isNotBlank() && !loading
                    ) {
                        scope.launch {
                            loading = true; error = null
                            try {
                                val group = createGroup(nameText.trim())
                                createdGroup = group
                                step = GroupSheetStep.CREATED
                            } catch (e: Exception) {
                                error = "Couldn't create the group. ${e.message}"
                            } finally {
                                loading = false
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth().clickable { step = GroupSheetStep.MENU }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text("Back", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                GroupSheetStep.CREATED -> {
                    val clipboard = LocalClipboardManager.current
                    var copied by remember { mutableStateOf(false) }
                    val group = createdGroup!!

                    Text("Group created", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Share this code to invite people to \"${group.name}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            group.invite_code,
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, letterSpacing = 4.sp),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (copied) Color(0xFF3A2A26) else MaterialTheme.colorScheme.primary)
                            .clickable {
                                clipboard.setText(AnnotatedString(group.invite_code))
                                copied = true
                            }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = if (copied) MaterialTheme.colorScheme.primary else Color(0xFF4A1B0C),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (copied) "Copied" else "Copy code",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (copied) MaterialTheme.colorScheme.primary else Color(0xFF4A1B0C)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().clickable { onGroupReady(group) }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Done", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                GroupSheetStep.JOIN -> {
                    Text("Join with a code", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(18.dp))
                    SheetTextField(
                        value = codeText,
                        onValueChange = { codeText = it.uppercase().take(6) },
                        placeholder = "6-character code"
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(18.dp))
                    SheetPrimaryButton(
                        label = if (loading) "Joining" else "Join",
                        enabled = codeText.length == 6 && !loading
                    ) {
                        scope.launch {
                            loading = true; error = null
                            try {
                                val group = joinGroupViaCode(codeText)
                                onGroupReady(group)
                            } catch (e: Exception) {
                                error = "Invalid code, or something went wrong."
                            } finally {
                                loading = false
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth().clickable { step = GroupSheetStep.MENU }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text("Back", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF3A2A26)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SheetTextField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextTertiary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SheetPrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Color(0xFF4A1B0C) else AppColors.TextTertiary
        )
    }
}

@Composable
private fun FeedGroupCard(
    group: FeedGroup,
    onOpenWorkout: (String) -> Unit,
    onOpenDiet: (String) -> Unit,
    modifier: Modifier = Modifier
) {
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
            FeedEntryRow(item) {
                val refId = item.reference_id ?: return@FeedEntryRow
                when (item.activity_type) {
                    "workout" -> onOpenWorkout(refId)
                    "diet" -> onOpenDiet(refId)
                }
            }
            if (index < group.entries.lastIndex) {
                Spacer(Modifier.height(2.dp))
                HorizontalDivider(color = AppColors.Divider)
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun FeedEntryRow(item: FeedItem, onClick: () -> Unit) {
    val isWorkout = item.activity_type == "workout"
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

@OptIn(ExperimentalTime::class)
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
private fun GroupStatusRow(members: List<Pair<UserProfileRow, Boolean>>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteSheet(group: GroupRow, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier.padding(top = 10.dp).width(36.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(AppColors.Divider)
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Invite to ${group.name}", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp), fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(
                "Share this code — anyone with it can join",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    group.invite_code,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, letterSpacing = 4.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (copied) Color(0xFF3A2A26) else MaterialTheme.colorScheme.primary)
                    .clickable {
                        clipboard.setText(AnnotatedString(group.invite_code))
                        copied = true
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    contentDescription = null,
                    tint = if (copied) MaterialTheme.colorScheme.primary else Color(0xFF4A1B0C),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (copied) "Copied" else "Copy code",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (copied) MaterialTheme.colorScheme.primary else Color(0xFF4A1B0C)
                )
            }

            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text("Done", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}