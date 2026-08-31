package com.asur.gymapp

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable

@Serializable
private data class DeviceTokenInsert(val user_id: String, val fcm_token: String)

actual suspend fun registerFcmToken() {
    Log.d("FCM", "registerFcmToken() called")
    val userId = supabase.auth.currentUserOrNull()?.id ?: run {
        Log.d("FCM", "No user session yet — skipping token registration")
        return
    }
    try {
        val token = FirebaseMessaging.getInstance().token.await()
        supabase.postgrest.from("device_tokens").upsert(
            DeviceTokenInsert(user_id = userId, fcm_token = token)
        ) {
            onConflict = "fcm_token"
        }
        Log.d("FCM", "Token registered successfully via Supabase")
    } catch (e: Exception) {
        Log.e("FCM", "Failed to register token: ${e.message}")
    }
}