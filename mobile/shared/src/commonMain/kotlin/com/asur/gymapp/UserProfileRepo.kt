package com.asur.gymapp

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.hours

@Serializable
private data class WorkoutIdOnly(val workout_id: String)

/**
 * Of the given workout ids, which ones actually have at least one logged set.
 * Empty day-cards exist as `workouts` rows with no `workout_sets` — they must not
 * count toward streaks, heatmaps, workout counts, or "trained today" status.
 * Chunked so the request URL can't overflow as history grows.
 */
suspend fun workoutIdsWithSets(workoutIds: List<String>): Set<String> {
    if (workoutIds.isEmpty()) return emptySet()
    return workoutIds.chunked(40).flatMap { batch ->
        supabase.postgrest.from("workout_sets")
            .select(Columns.raw("workout_id")) {
                filter { isIn("workout_id", batch) }
            }
            .decodeList<WorkoutIdOnly>()
            .map { it.workout_id }
    }.toSet()
}

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
    supabase.postgrest.from("body_weight_logs").upsert(
        BodyWeightInsert(user_id = userId, weight_kg = weightKg, date = logDateForNow())
    ) { onConflict = "user_id, date" }
}

suspend fun fetchStreak(): StreakRow? {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return null
    return supabase.postgrest.from("streaks")
        .select { filter { eq("user_id", userId) } }
        .decodeSingleOrNull<StreakRow>()
}

suspend fun fetchWorkoutCount(): Int {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return 0
    return try {
        val workouts = supabase.postgrest.from("workouts")
            .select { filter { eq("user_id", userId) } }
            .decodeList<WorkoutRow>()
        workoutIdsWithSets(workouts.map { it.id }).size
    } catch (e: Exception) {
        0
    }
}

suspend fun updateNudgeSettings(cutoffTime: String, enabled: Boolean) {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return
    supabase.postgrest.from("users").update(
        buildJsonObject {
            put("nudge_cutoff_time", cutoffTime)
            put("nudge_enabled", enabled)
        }
    ) { filter { eq("id", userId) } }
}

suspend fun updateProfileDetails(weightKg: Double, heightCm: Double, activityLevel: String, goal: String) {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return
    supabase.postgrest.from("users").update(
        UserProfileUpdate(height_cm = heightCm, activity_level = activityLevel, goal = goal)
    ) { filter { eq("id", userId) } }
    supabase.postgrest.from("body_weight_logs").upsert(
        BodyWeightInsert(user_id = userId, weight_kg = weightKg, date = logDateForNow())
    ) { onConflict = "user_id, date" }
}

suspend fun fetchTemplates(): List<WorkoutTemplateRow> {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
    return supabase.postgrest.from("workout_templates")
        .select { filter { eq("user_id", userId) } }
        .decodeList<WorkoutTemplateRow>()
}

suspend fun fetchTemplateExercises(templateId: String): List<Exercise> {
    val rows = supabase.postgrest.from("workout_template_exercises")
        .select(Columns.raw("position, exercises(*)")) {
            filter { eq("template_id", templateId) }
            order("position", Order.ASCENDING)
        }
        .decodeList<TemplateExerciseWithDetails>()
    return rows.mapNotNull { it.exercises }
}

suspend fun saveAsTemplate(name: String, exerciseIds: List<String>) {
    val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
    val template = supabase.postgrest.from("workout_templates")
        .insert(WorkoutTemplateInsert(user_id = userId, name = name)) { select() }
        .decodeSingle<WorkoutTemplateRow>()
    val exerciseRows = exerciseIds.mapIndexed { index, exId ->
        TemplateExerciseInsert(template_id = template.id, exercise_id = exId, position = index)
    }
    if (exerciseRows.isNotEmpty()) {
        supabase.postgrest.from("workout_template_exercises").insert(exerciseRows)
    }
}

suspend fun fetchProgressPhotos(): List<ProgressPhotoRow> {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
    return supabase.postgrest.from("progress_photos")
        .select {
            filter { eq("user_id", userId) }
            order("date", Order.DESCENDING)
        }
        .decodeList<ProgressPhotoRow>()
}

suspend fun uploadProgressPhoto(bytes: ByteArray, fileExtension: String) {
    val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
    val path = "$userId/${Clock.System.now().toEpochMilliseconds()}.$fileExtension"
    supabase.storage.from("progress-photos").upload(path, bytes)
    supabase.postgrest.from("progress_photos").insert(
        ProgressPhotoInsert(user_id = userId, storage_path = path, date = logDateForNow())
    )
}

suspend fun getPhotoUrl(storagePath: String): String {
    return supabase.storage.from("progress-photos").createSignedUrl(storagePath, 1.hours)
}

suspend fun deleteProgressPhoto(photoId: String, storagePath: String) {
    supabase.storage.from("progress-photos").delete(storagePath)
    supabase.postgrest.from("progress_photos").delete { filter { eq("id", photoId) } }
}

suspend fun updateFeedVisibility(visible: Boolean) {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return
    supabase.postgrest.from("users").update(
        buildJsonObject { put("feed_visible", visible) }
    ) { filter { eq("id", userId) } }
}

suspend fun fetchWorkoutDatesLastNDays(days: Int): Set<String> {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return emptySet()
    val today = LocalDate.parse(logDateForNow())
    val cutoff = today.minus(DatePeriod(days = days - 1)).toString()

    val workouts = supabase.postgrest.from("workouts")
        .select {
            filter {
                eq("user_id", userId)
                gte("date", cutoff)
            }
        }
        .decodeList<WorkoutRow>()

    val withSets = workoutIdsWithSets(workouts.map { it.id })
    return workouts.filter { it.id in withSets }.map { it.date }.toSet()
}

suspend fun fetchGroupStatus(): List<Pair<UserProfileRow, Boolean>> {
    val today = logDateForNow()
    val users = supabase.postgrest.from("users")
        .select { filter { eq("feed_visible", true) } }
        .decodeList<UserProfileRow>()

    val todaysWorkouts = supabase.postgrest.from("workouts")
        .select { filter { eq("date", today) } }
        .decodeList<WorkoutRow>()

    val withSets = workoutIdsWithSets(todaysWorkouts.map { it.id })
    val activeIds = todaysWorkouts.filter { it.id in withSets }.map { it.user_id }.toSet()

    return users.map { it to (it.id in activeIds) }
}

suspend fun updateFeedVisibleWorkouts(visible: Boolean) {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return
    supabase.postgrest.from("users").update(
        buildJsonObject { put("feed_visible_workouts", visible) }
    ) { filter { eq("id", userId) } }
}

suspend fun updateFeedVisibleDiet(visible: Boolean) {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return
    supabase.postgrest.from("users").update(
        buildJsonObject { put("feed_visible_diet", visible) }
    ) { filter { eq("id", userId) } }
}

suspend fun updateShowInStatusRow(visible: Boolean) {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return
    supabase.postgrest.from("users").update(
        buildJsonObject { put("show_in_status_row", visible) }
    ) { filter { eq("id", userId) } }
}

suspend fun updateShowOnLeaderboard(visible: Boolean) {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return
    supabase.postgrest.from("users").update(
        buildJsonObject { put("show_on_leaderboard", visible) }
    ) { filter { eq("id", userId) } }
}