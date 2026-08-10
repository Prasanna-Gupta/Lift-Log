package com.asur.gymapp

enum class ActivityLevel(val multiplier: Double, val label: String) {
    SEDENTARY(1.2, "Sedentary (little/no exercise)"),
    LIGHT(1.375, "Light (1-3 days/week)"),
    MODERATE(1.55, "Moderate (3-5 days/week)"),
    ACTIVE(1.725, "Active (6-7 days/week)"),
    VERY_ACTIVE(1.9, "Very active (physical job + training)")
}

enum class Goal(val calorieAdjustment: Double, val proteinPerKg: Double, val label: String) {
    CUT(-0.20, 2.2, "Cut (fat loss)"),
    MAINTAIN(0.0, 1.8, "Maintain"),
    BULK(0.15, 1.8, "Bulk (muscle gain)")
}

data class NutritionTargets(
    val maintenanceCalories: Int,
    val targetCalories: Int,
    val targetProteinG: Int,
    val targetFatG: Int,
    val targetFiberG: Int
)

fun calculateTargets(
    weightKg: Double,
    heightCm: Double,
    age: Int,
    isMale: Boolean,
    activityLevel: ActivityLevel,
    goal: Goal
): NutritionTargets {
    val bmr = if (isMale) {
        10 * weightKg + 6.25 * heightCm - 5 * age + 5
    } else {
        10 * weightKg + 6.25 * heightCm - 5 * age - 161
    }
    val maintenance = bmr * activityLevel.multiplier
    val target = maintenance * (1 + goal.calorieAdjustment)
    val protein = weightKg * goal.proteinPerKg

    // Fat: 25% of total calories is a standard, reasonable default
    val fat = (target * 0.25) / 9.0 // 9 calories per gram of fat

    // Fiber: standard guideline is ~14g per 1000 calories consumed
    val fiber = (target / 1000.0) * 14.0

    return NutritionTargets(
        maintenanceCalories = maintenance.toInt(),
        targetCalories = target.toInt(),
        targetProteinG = protein.toInt(),
        targetFatG = fat.toInt(),
        targetFiberG = fiber.toInt()
    )
}

// kg <-> lb, cm <-> in, for the per-user unit preference
fun kgToLb(kg: Double) = kg * 2.20462
fun lbToKg(lb: Double) = lb / 2.20462
fun cmToIn(cm: Double) = cm / 2.54
fun inToCm(inches: Double) = inches * 2.54