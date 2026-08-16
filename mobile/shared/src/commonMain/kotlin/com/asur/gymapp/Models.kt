package com.asur.gymapp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val muscle_group: String? = null,
    val equipment_type: String? = null
)

@Serializable
data class WorkoutInsert(
    val user_id: String,
    val date: String,
    val title: String? = null,
    val notes: String? = null
)

@Serializable
data class WorkoutRow(
    val id: String,
    val user_id: String,
    val date: String,
    val title: String? = null,
    val notes: String? = null
)

@Serializable
data class WorkoutSetInsert(
    val workout_id: String,
    val exercise_id: String,
    val set_number: Int,
    val weight: Double,
    val reps: Int
)

@Serializable
data class ExerciseNameOnly(val name: String)

@Serializable
data class WorkoutSetWithExercise(
    val set_number: Int,
    val weight: Double,
    val reps: Int,
    val exercises: ExerciseNameOnly? = null
)

@Serializable
data class WorkoutWithSets(
    val id: String,
    val title: String? = null,
    val date: String,
    val workout_sets: List<WorkoutSetWithExercise> = emptyList()
)

@Serializable
data class DietLogInsert(
    val user_id: String,
    val date: String,
    val calories: Int,
    val protein_g: Double,
    val fat_g: Double? = null,
    val fiber_g: Double? = null,
    val meal_label: String? = null,
    val nutrients: JsonObject? = null
)

@Serializable
data class DietLogRow(
    val id: String,
    val user_id: String,
    val date: String,
    val calories: Int,
    val protein_g: Double,
    val fat_g: Double? = null,
    val fiber_g: Double? = null,
    val meal_label: String? = null,
    val nutrients: JsonObject? = null
)

@Serializable
data class UserProfileUpdate(
    val height_cm: Double? = null,
    val date_of_birth: String? = null,
    val gender: String? = null,
    val activity_level: String? = null,
    val goal: String? = null,
    val unit_preference: String? = null,
    val onboarding_completed: Boolean? = null
)

@Serializable
data class UserProfileRow(
    val id: String,
    val height_cm: Double? = null,
    val date_of_birth: String? = null,
    val gender: String? = null,
    val activity_level: String? = null,
    val goal: String? = null,
    val unit_preference: String = "metric",
    val onboarding_completed: Boolean = false
)

@Serializable
data class BodyWeightInsert(val user_id: String, val weight_kg: Double, val date: String)

@Serializable
data class BodyWeightRow(val id: String, val weight_kg: Double, val date: String)

@Serializable
data class FoodSearchResult(
    val fdcId: Long,
    val description: String,
    val dataType: String? = null,
    val brandOwner: String? = null,
    val caloriesPer100g: Double? = null,
    val proteinPer100g: Double? = null,
    val fatPer100g: Double? = null,
    val fiberPer100g: Double? = null
)

@Serializable
data class FoodPortion(val label: String, val grams: Double)

@Serializable
data class FoodDetail(
    val fdcId: Long,
    val description: String,
    val caloriesPer100g: Double? = null,
    val proteinPer100g: Double? = null,
    val fatPer100g: Double? = null,
    val fiberPer100g: Double? = null,
    val portions: List<FoodPortion> = emptyList()
)

@Serializable
data class FeedItem(
    val id: String,
    val user_id: String,
    val user_name: String,
    val activity_type: String,
    val created_at: String,
    val workout_title: String? = null,
    val workout_exercise_count: Int? = null,
    val meal_label: String? = null,
    val diet_calories: Int? = null,
    val diet_protein_g: Double? = null,
    val avatar_url: String? = null
)

@Serializable
data class StreakRow(
    val user_id: String,
    val current_streak: Int,
    val longest_streak: Int,
    val last_logged_date: String? = null,
    val warning_used: Boolean = false
)