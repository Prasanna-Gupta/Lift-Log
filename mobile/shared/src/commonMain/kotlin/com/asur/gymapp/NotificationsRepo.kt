package com.asur.gymapp

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable

@Serializable
data class NotificationRow(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val reference_type: String? = null,
    val reference_id: String? = null,
    val is_read: Boolean,
    val created_at: String
)

suspend fun fetchNotifications(): List<NotificationRow> {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
    return supabase.postgrest.from("notifications")
        .select {
            filter { eq("user_id", userId) }
            order("created_at", Order.DESCENDING)
            limit(100)
        }
        .decodeList<NotificationRow>()
}

suspend fun markNotificationRead(id: String) {
    supabase.postgrest.from("notifications")
        .update(mapOf("is_read" to true)) { filter { eq("id", id) } }
}

suspend fun markAllNotificationsRead() {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return
    supabase.postgrest.from("notifications")
        .update(mapOf("is_read" to true)) {
            filter {
                eq("user_id", userId)
                eq("is_read", false)
            }
        }
}

suspend fun fetchUnreadNotificationCount(): Int {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return 0
    return supabase.postgrest.from("notifications")
        .select { filter { eq("user_id", userId); eq("is_read", false) } }
        .decodeList<NotificationRow>()
        .size
}