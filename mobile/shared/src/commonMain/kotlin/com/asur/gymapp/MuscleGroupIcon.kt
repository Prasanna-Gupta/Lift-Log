package com.asur.gymapp

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import liftlog.shared.generated.resources.Res
import liftlog.shared.generated.resources.abdominals
import liftlog.shared.generated.resources.abductors
import liftlog.shared.generated.resources.adductors
import liftlog.shared.generated.resources.biceps
import liftlog.shared.generated.resources.calves
import liftlog.shared.generated.resources.chest
import liftlog.shared.generated.resources.forearms
import liftlog.shared.generated.resources.glutes
import liftlog.shared.generated.resources.hamstrings
import liftlog.shared.generated.resources.lats
import liftlog.shared.generated.resources.lower_back
import liftlog.shared.generated.resources.middle_back
import liftlog.shared.generated.resources.neck
import liftlog.shared.generated.resources.quadriceps
import liftlog.shared.generated.resources.shoulders
import liftlog.shared.generated.resources.traps
import liftlog.shared.generated.resources.triceps
import liftlog.shared.generated.resources.default_body
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private fun drawableFor(muscleGroup: String?): DrawableResource = when (muscleGroup?.lowercase()?.trim()) {
    "abdominals" -> Res.drawable.abdominals
    "abductors" -> Res.drawable.abductors
    "adductors" -> Res.drawable.adductors
    "biceps" -> Res.drawable.biceps
    "calves" -> Res.drawable.calves
    "chest" -> Res.drawable.chest
    "forearms" -> Res.drawable.forearms
    "glutes" -> Res.drawable.glutes
    "hamstrings" -> Res.drawable.hamstrings
    "lats" -> Res.drawable.lats
    "lower back" -> Res.drawable.lower_back
    "middle back" -> Res.drawable.middle_back
    "neck" -> Res.drawable.neck
    "quadriceps" -> Res.drawable.quadriceps
    "shoulders" -> Res.drawable.shoulders
    "traps" -> Res.drawable.traps
    "triceps" -> Res.drawable.triceps
    else -> Res.drawable.default_body
}

/**
 * Full-body silhouette with the exercise's primary muscle group highlighted in the
 * artwork itself — no runtime tinting, colors are baked into each SVG.
 */
@Composable
fun MuscleGroupIcon(muscleGroup: String?, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(drawableFor(muscleGroup)),
        contentDescription = muscleGroup,
        modifier = modifier
    )
}