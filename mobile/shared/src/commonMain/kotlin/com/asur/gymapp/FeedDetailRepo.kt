package com.asur.gymapp

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

@Serializable
data class WorkoutDetailHeader(
    val id: String,
    val title: String? = null,
    val date: String,
    val user_id: String
)

suspend fun fetchWorkoutDetail(workoutId: String): Pair<WorkoutDetailHeader, UserProfileRow?>? {
    val workout = supabase.postgrest.from("workouts")
        .select { filter { eq("id", workoutId) } }
        .decodeSingleOrNull<WorkoutDetailHeader>() ?: return null
    val owner = supabase.postgrest.from("users")
        .select { filter { eq("id", workout.user_id) } }
        .decodeSingleOrNull<UserProfileRow>()
    return workout to owner
}

suspend fun fetchWorkoutSetsDetail(workoutId: String): List<SetWithExercise> {
    return supabase.postgrest.from("workout_sets")
        .select(Columns.raw("*, exercises(name, muscle_group)")) {
            filter { eq("workout_id", workoutId) }
        }
        .decodeList<SetWithExercise>()
}

suspend fun fetchDietLogDetail(dietLogId: String): Pair<DietLogRow, UserProfileRow?>? {
    val log = supabase.postgrest.from("diet_logs")
        .select { filter { eq("id", dietLogId) } }
        .decodeSingleOrNull<DietLogRow>() ?: return null
    val owner = supabase.postgrest.from("users")
        .select { filter { eq("id", log.user_id) } }
        .decodeSingleOrNull<UserProfileRow>()
    return log to owner
}