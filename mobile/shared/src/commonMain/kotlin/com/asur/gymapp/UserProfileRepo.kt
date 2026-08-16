package com.asur.gymapp

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

suspend fun fetchUserProfile(): UserProfileRow? {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return null
    return supabase.postgrest.from("users")
        .select { filter { eq("id", userId) } }
        .decodeSingleOrNull<UserProfileRow>()
}

suspend fun fetchLatestWeightKg(): Double? {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return null
    val row = supabase.postgrest.from("body_weight_logs")
        .select {
            filter { eq("user_id", userId) }
            order("date", Order.DESCENDING)
            limit(1)
        }
        .decodeSingleOrNull<BodyWeightRow>()
    return row?.weight_kg
}

suspend fun completeOnboarding(
    weightKg: Double,
    heightCm: Double,
    dateOfBirth: String,
    gender: String,
    activityLevel: String,
    goal: String
) {
    val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")

    supabase.postgrest.from("users").update(
        UserProfileUpdate(
            height_cm = heightCm,
            date_of_birth = dateOfBirth,
            gender = gender,
            activity_level = activityLevel,
            goal = goal,
            onboarding_completed = true
        )
    ) { filter { eq("id", userId) } }

    supabase.postgrest.from("body_weight_logs").insert(
        BodyWeightInsert(user_id = userId, weight_kg = weightKg, date = logDateForNow())
    )
}

suspend fun fetchStreak(): StreakRow? {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return null
    return supabase.postgrest.from("streaks")
        .select { filter { eq("user_id", userId) } }
        .decodeSingleOrNull<StreakRow>()
}