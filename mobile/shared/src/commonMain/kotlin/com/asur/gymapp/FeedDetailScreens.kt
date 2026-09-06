package com.asur.gymapp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.jan.supabase.auth.auth

@Composable
fun WorkoutDetailScreen(workoutId: String, onBack: () -> Unit) {
    var header by remember { mutableStateOf<Pair<WorkoutDetailHeader, UserProfileRow?>?>(null) }
    var sets by remember { mutableStateOf<List<SetWithExercise>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(workoutId) {
        header = fetchWorkoutDetail(workoutId)
        sets = fetchWorkoutSetsDetail(workoutId)
        loading = false
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(22.dp).clickable(onClick = onBack)
            )
        }
        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return
        }
        if (header == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                Text("Couldn't load this workout", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }
        val (workout, owner) = header!!
        val isOwnWorkout = workout.user_id == supabase.auth.currentUserOrNull()?.id
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isOwnWorkout && owner?.avatar_url != null) {
                AsyncImage(
                    model = owner.avatar_url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(10.dp))
            }
            Column {
                Text(
                    workout.title ?: "Workout",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (isOwnWorkout) formatDayMonth(workout.date) else "${owner?.name ?: "Someone"} · ${formatDayMonth(workout.date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        val grouped = sets.groupBy { it.exercise_id }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(grouped.entries.toList(), key = { it.key }) { (_, exerciseSets) ->
                val exerciseName = exerciseSets.firstOrNull()?.exercises?.name ?: "Exercise"
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    Text(exerciseName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Spacer(Modifier.width(26.dp))
                        Text("KG", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp))
                        Text("REPS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp))
                    }
                    exerciseSets.sortedBy { it.set_number }.forEach { set ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Box(
                                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${set.set_number}", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${set.weight ?: 0.0}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("${set.reps ?: 0}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DietDetailScreen(dietLogId: String, onBack: () -> Unit) {
    var result by remember { mutableStateOf<Pair<DietLogRow, UserProfileRow?>?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(dietLogId) {
        result = fetchDietLogDetail(dietLogId)
        loading = false
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(22.dp).clickable(onClick = onBack)
            )
        }
        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return
        }
        if (result == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                Text("Couldn't load this entry", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }
        val (log, owner) = result!!
        val isOwnLog = log.user_id == supabase.auth.currentUserOrNull()?.id
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isOwnLog && owner?.avatar_url != null) {
                    AsyncImage(
                        model = owner.avatar_url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column {
                    Text(
                        log.meal_label ?: "Entry",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (isOwnLog) formatDayMonth(log.date) else "${owner?.name ?: "Someone"} · ${formatDayMonth(log.date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextTertiary
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(18.dp)
            ) {
                MacroRow("Calories", "${log.calories}")
                MacroRow("Protein", "${log.protein_g.oneDecimal()} g")
                log.fat_g?.let { MacroRow("Fat", "${it.oneDecimal()} g") }
                log.fiber_g?.let { MacroRow("Fiber", "${it.oneDecimal()} g", showDivider = false) }
            }
        }
    }
}

@Composable
private fun MacroRow(label: String, value: String, showDivider: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
    if (showDivider) HorizontalDivider(color = AppColors.Divider)
}