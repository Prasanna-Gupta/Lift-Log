package com.asur.gymapp

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

data class ExercisePR(
    val exerciseName: String,
    val weight: Double,
    val reps: Int,
    val estimated1RM: Double,
    val achievedOn: LocalDate?
)

data class ProgressSummary(
    val prs: List<ExercisePR>,
    val totalVolumeAllTime: Double,
    val volumeThisWeek: Double,
    val volumeLastWeek: Double,
    val volumeThisMonth: Double,
    val workoutsThisWeek: Int,
    val workoutsThisMonth: Int,
    val dailyVolumeLast7: Map<LocalDate, Double>,
    val weeklyVolumeThisMonth: List<Pair<LocalDate, Double>>,
    val monthlyVolumeAllTime: List<Pair<LocalDate, Double>>,
    val prsSetThisWeek: Int
)

private val emptySummary = ProgressSummary(
    prs = emptyList(),
    totalVolumeAllTime = 0.0,
    volumeThisWeek = 0.0,
    volumeLastWeek = 0.0,
    volumeThisMonth = 0.0,
    workoutsThisWeek = 0,
    workoutsThisMonth = 0,
    dailyVolumeLast7 = emptyMap(),
    weeklyVolumeThisMonth = emptyList(),
    monthlyVolumeAllTime = emptyList(),
    prsSetThisWeek = 0
)

// Epley formula — standard, simple estimated 1RM calculation
private fun estimated1RM(weight: Double, reps: Int): Double =
    if (reps <= 1) weight else weight * (1 + reps / 30.0)

private fun wt(s: SetWithExercise): Double = s.weight ?: 0.0
private fun rp(s: SetWithExercise): Int = s.reps ?: 0

/** Monday-start week. For Sunday-start, use `day.dayOfWeek.value % 7`. */
private fun startOfWeek(day: LocalDate): LocalDate =
    day.minus(DatePeriod(days = day.dayOfWeek.isoDayNumber - 1))

private fun startOfMonth(day: LocalDate): LocalDate =
    LocalDate(day.year, day.monthNumber, 1)

suspend fun fetchProgressSummary(): ProgressSummary {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return emptySummary

    val workouts = supabase.postgrest.from("workouts")
        .select { filter { eq("user_id", userId) } }
        .decodeList<WorkoutRow>()

    if (workouts.isEmpty()) return emptySummary

    val workoutDateById = workouts.mapNotNull { w ->
        runCatching { LocalDate.parse(w.date) }.getOrNull()?.let { w.id to it }
    }.toMap()

    // Chunked so the request URL can't overflow as workout history grows.
    val sets = workouts.map { it.id }.chunked(40).flatMap { batch ->
        supabase.postgrest.from("workout_sets")
            .select(Columns.raw("*, exercises(name)")) {
                filter { isIn("workout_id", batch) }
            }
            .decodeList<SetWithExercise>()
    }

    // Inherits the 4 AM day-boundary rule from logDateForNow().
    val today = LocalDate.parse(logDateForNow())
    val weekStart = startOfWeek(today)
    val lastWeekStart = weekStart.minus(DatePeriod(days = 7))
    val monthStart = startOfMonth(today)
    val chartStart = weekStart

    fun dateOf(s: SetWithExercise): LocalDate? = workoutDateById[s.workout_id]

    val prs = sets
        .filter { wt(it) > 0.0 && rp(it) > 0 }
        .groupBy { it.exercise_id }
        .mapNotNull { (_, exerciseSets) ->
            val best = exerciseSets.maxByOrNull { wt(it) }
                ?: return@mapNotNull null
            val name = best.exercises?.name ?: return@mapNotNull null
            ExercisePR(
                exerciseName = name,
                weight = wt(best),
                reps = rp(best),
                estimated1RM = estimated1RM(wt(best), rp(best)),
                achievedOn = dateOf(best)
            )
        }
        .sortedByDescending { it.weight }

    val dailyVolume = mutableMapOf<LocalDate, Double>()
    val weekBuckets = mutableMapOf<LocalDate, Double>()
    val monthBuckets = mutableMapOf<LocalDate, Double>()
    var totalVolume = 0.0
    var volumeThisWeek = 0.0
    var volumeLastWeek = 0.0
    var volumeThisMonth = 0.0

    for (s in sets) {
        val v = wt(s) * rp(s)
        totalVolume += v
        val d = dateOf(s) ?: continue

        if (d >= weekStart) volumeThisWeek += v
        if (d >= lastWeekStart && d < weekStart) volumeLastWeek += v
        if (d >= monthStart) volumeThisMonth += v
        if (d >= chartStart && d <= today) dailyVolume[d] = (dailyVolume[d] ?: 0.0) + v

        if (d >= monthStart) {
            val wk = startOfWeek(d)
            weekBuckets[wk] = (weekBuckets[wk] ?: 0.0) + v
        }
        val mo = startOfMonth(d)
        monthBuckets[mo] = (monthBuckets[mo] ?: 0.0) + v
    }

    // Gaps filled so chart axes stay continuous instead of collapsing.
    val weeklyVolumeThisMonth = buildList {
        var cursor = startOfWeek(monthStart)
        while (cursor <= today) {
            add(cursor to (weekBuckets[cursor] ?: 0.0))
            cursor = cursor.plus(DatePeriod(days = 7))
        }
    }

    val monthlyVolumeAllTime = buildList {
        var cursor = monthBuckets.keys.minOrNull() ?: monthStart
        while (cursor <= monthStart) {
            add(cursor to (monthBuckets[cursor] ?: 0.0))
            cursor = cursor.plus(DatePeriod(months = 1))
        }
    }.takeLast(12)

    // Empty day-cards (workouts rows with no sets) must not count as workouts.
    val workoutIdsWithSets = sets.map { it.workout_id }.toSet()
    val loggedDates = workoutDateById.filterKeys { it in workoutIdsWithSets }.values

    return ProgressSummary(
        prs = prs,
        totalVolumeAllTime = totalVolume,
        volumeThisWeek = volumeThisWeek,
        volumeLastWeek = volumeLastWeek,
        volumeThisMonth = volumeThisMonth,
        workoutsThisWeek = loggedDates.count { it >= weekStart },
        workoutsThisMonth = loggedDates.count { it >= monthStart },
        dailyVolumeLast7 = dailyVolume,
        weeklyVolumeThisMonth = weeklyVolumeThisMonth,
        monthlyVolumeAllTime = monthlyVolumeAllTime,
        prsSetThisWeek = prs.count { it.achievedOn != null && it.achievedOn >= weekStart }
    )
}