package com.asur.gymapp

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class GroupRow(
    val id: String,
    val name: String,
    val invite_code: String,
    val created_by: String,
    val created_at: String
)

@Serializable
data class LeaderboardEntry(
    val user_id: String,
    val name: String,
    val avatar_url: String? = null,
    val workouts_this_week: Long
)

@Serializable
data class GroupMemberRow(val group_id: String, val user_id: String)

suspend fun createGroup(name: String): GroupRow {
    return supabase.postgrest.rpc(
        "create_group",
        buildJsonObject { put("p_name", name) }
    ).decodeAs<GroupRow>()
}

suspend fun joinGroupViaCode(code: String): GroupRow {
    return supabase.postgrest.rpc(
        "join_group_via_code",
        buildJsonObject { put("p_invite_code", code.trim().uppercase()) }
    ).decodeAs<GroupRow>()
}

suspend fun fetchMyGroups(): List<GroupRow> {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
    val memberships = supabase.postgrest.from("group_members")
        .select { filter { eq("user_id", userId) } }
        .decodeList<GroupMemberRow>()
    if (memberships.isEmpty()) return emptyList()

    return supabase.postgrest.from("groups")
        .select { filter { isIn("id", memberships.map { it.group_id }) } }
        .decodeList<GroupRow>()
}

suspend fun fetchGroupMemberIds(groupId: String): List<String> {
    return supabase.postgrest.from("group_members")
        .select { filter { eq("group_id", groupId) } }
        .decodeList<GroupMemberRow>()
        .map { it.user_id }
}

suspend fun fetchGroupStatus(groupId: String): List<Pair<UserProfileRow, Boolean>> {
    val memberIds = fetchGroupMemberIds(groupId)
    if (memberIds.isEmpty()) return emptyList()
    val today = logDateForNow()

    val users = supabase.postgrest.from("users")
        .select {
            filter {
                isIn("id", memberIds)
                eq("feed_visible", true)
            }
        }
        .decodeList<UserProfileRow>()
    if (users.isEmpty()) return emptyList()

    val visibleIds = users.map { it.id }
    val todaysWorkouts = supabase.postgrest.from("workouts")
        .select {
            filter {
                isIn("user_id", visibleIds)
                eq("date", today)
            }
        }
        .decodeList<WorkoutRow>()

    val withSets = workoutIdsWithSets(todaysWorkouts.map { it.id })
    val activeIds = todaysWorkouts.filter { it.id in withSets }.map { it.user_id }.toSet()

    return users.map { it to (it.id in activeIds) }
}

suspend fun fetchLeaderboard(groupId: String): List<LeaderboardEntry> {
    return supabase.postgrest.rpc(
        "get_group_leaderboard",
        buildJsonObject { put("p_group_id", groupId) }
    ).decodeList<LeaderboardEntry>()
}

suspend fun deleteGroup(groupId: String) {
    supabase.postgrest.rpc(
        "delete_group",
        buildJsonObject { put("p_group_id", groupId) }
    )
}

suspend fun leaveGroup(groupId: String) {
    supabase.postgrest.rpc(
        "leave_group",
        buildJsonObject { put("p_group_id", groupId) }
    )
}