package com.asur.gymapp

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

@Serializable
private data class TitledWorkoutDate(val title: String? = null, val date: String)

/**
 * Most recent date each workout title was actually trained — i.e. has at least
 * one set — for the current user. Matches on title, same as the template upsert
 * uses for matching, since day-cards aren't otherwise linked to a stable id.
 */
suspend fun fetchLastLoggedDates(): Map<String, String> {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return emptyMap()

    val rows = supabase.postgrest.from("workout_sets")
        .select(Columns.raw("workouts!inner(title, date, user_id)")) {
            filter { eq("workouts.user_id", userId) }
        }
        .decodeList<TitledWorkoutSetRow>()

    return rows
        .mapNotNull { it.workouts?.title?.let { t -> t to it.workouts.date } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, dates) -> dates.max() }
}

@Serializable
private data class TitledWorkoutSetRow(val workouts: TitledWorkoutInner? = null) {
    @Serializable
    data class TitledWorkoutInner(val title: String? = null, val date: String, val user_id: String)
}

data class TodayLogStatus(val hasLoggedToday: Boolean, val exerciseCount: Int)

/** Exercises actually logged today — i.e. with at least one saved set, not just an open draft. */
suspend fun fetchTodayLogStatus(): TodayLogStatus {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return TodayLogStatus(false, 0)
    val today = logDateForNow()

    val rows = supabase.postgrest.from("workout_sets")
        .select(Columns.raw("exercise_id, workouts!inner(date, user_id)")) {
            filter {
                eq("workouts.user_id", userId)
                eq("workouts.date", today)
            }
        }
        .decodeList<ExerciseIdWithWorkout>()

    val distinctExercises = rows.map { it.exercise_id }.toSet()
    return TodayLogStatus(hasLoggedToday = distinctExercises.isNotEmpty(), exerciseCount = distinctExercises.size)
}

@Serializable
private data class ExerciseIdWithWorkout(val exercise_id: String, val workouts: WorkoutUserDate? = null) {
    @Serializable
    data class WorkoutUserDate(val date: String, val user_id: String)
}