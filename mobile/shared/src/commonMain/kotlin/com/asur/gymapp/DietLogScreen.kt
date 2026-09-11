package com.asur.gymapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@Composable
fun DietLogScreen(onNestedChange: (Boolean) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<DietLogRow>>(emptyList()) }
    var targets by remember { mutableStateOf<NutritionTargets?>(null) }
    var targetsError by remember { mutableStateOf<String?>(null) }
    var showFoodSearch by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var entryPendingDelete by remember { mutableStateOf<DietLogRow?>(null) }

    LaunchedEffect(showFoodSearch) { onNestedChange(showFoodSearch) }

    suspend fun refreshEntries() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        entries = supabase.postgrest.from("diet_logs")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("date", logDateForNow())
                }
                order("date", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }
            .decodeList<DietLogRow>()
    }

    suspend fun loadTargets() {
        val profile = fetchUserProfile()
        val weightKg = fetchLatestWeightKg()
        if (profile == null || weightKg == null || profile.height_cm == null ||
            profile.date_of_birth == null || profile.gender == null ||
            profile.activity_level == null || profile.goal == null) {
            targetsError = "Complete your profile to see targets."
            return
        }
        try {
            val dob = LocalDate.parse(profile.date_of_birth)
            val age = ageFromDob(dob)
            val activityLevel = ActivityLevel.valueOf(profile.activity_level)
            val goal = Goal.valueOf(profile.goal)
            targets = calculateTargets(
                weightKg = weightKg,
                heightCm = profile.height_cm,
                age = age,
                isMale = profile.gender == "male",
                activityLevel = activityLevel,
                goal = goal
            )
            targetsError = null
        } catch (e: Exception) {
            targetsError = "Could not calculate targets: ${e.message}"
        }
    }

    LaunchedEffect(Unit) {
        refreshEntries()
        loadTargets()
    }

    if (showFoodSearch) {
        FoodSearchScreen(
            onFoodSelected = { calories, proteinG, fatG, fiberG, label ->
                scope.launch {
                    errorMessage = null
                    try {
                        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
                        supabase.postgrest.from("diet_logs").insert(
                            DietLogInsert(
                                user_id = userId,
                                date = logDateForNow(),
                                calories = calories,
                                protein_g = proteinG,
                                fat_g = fatG,
                                fiber_g = fiberG,
                                meal_label = label
                            )
                        )
                        refreshEntries()
                        showFoodSearch = false
                    } catch (e: Exception) {
                        errorMessage = "Couldn't save that entry. ${e.message}"
                        showFoodSearch = false
                    }
                }
            },
            onBack = { showFoodSearch = false }
        )
        return
    }

    val totalCalories = entries.sumOf { it.calories }
    val totalProtein = entries.sumOf { it.protein_g }
    val totalFat = entries.sumOf { it.fat_g ?: 0.0 }
    val totalFiber = entries.sumOf { it.fiber_g ?: 0.0 }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Today, ${formatDayMonth(logDateForNow())}",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(3.dp))
        Text(
            if (entries.isEmpty()) "Nothing logged yet" else "${entries.size} entries logged",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                val t = targets
                if (t != null) {
                    Text("Today's progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    NutrientRow("Calories", totalCalories.toDouble(), t.targetCalories.toDouble(), "")
                    Spacer(Modifier.height(14.dp))
                    NutrientRow("Protein", totalProtein, t.targetProteinG.toDouble(), "g")
                    Spacer(Modifier.height(14.dp))
                    NutrientRow("Fat", totalFat, t.targetFatG.toDouble(), "g")
                    Spacer(Modifier.height(14.dp))
                    NutrientRow("Fiber", totalFiber, t.targetFiberG.toDouble(), "g")
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$totalCalories", style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp), fontWeight = FontWeight.Medium)
                            Text("calories today", style = MaterialTheme.typography.bodySmall, color = AppColors.TextTertiary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${totalProtein.toInt()}g", style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp), fontWeight = FontWeight.Medium)
                            Text("protein today", style = MaterialTheme.typography.bodySmall, color = AppColors.TextTertiary)
                        }
                    }
                    targetsError?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { showFoodSearch = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF3A2A26)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("Search food", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }

        errorMessage?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(22.dp))

        if (entries.isNotEmpty()) {
            Text("TODAY'S ENTRIES", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
        }

        Box(modifier = Modifier.weight(1f)) {
            if (entries.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    itemsIndexed(entries, key = { _, e -> e.id }) { index, entry ->
                        var expanded by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).clickable { expanded = !expanded }
                            ) {
                                Text(
                                    entry.meal_label ?: "Entry",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    buildString {
                                        append("${entry.calories} cal · ${entry.protein_g.toInt()}g protein")
                                        entry.fat_g?.let { append(" · ${it.toInt()}g fat") }
                                        entry.fiber_g?.let { append(" · ${it.toInt()}g fiber") }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextTertiary
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Delete entry",
                                tint = AppColors.TextTertiary,
                                modifier = Modifier.size(16.dp).clickable { entryPendingDelete = entry }
                            )
                        }
                        if (index < entries.lastIndex) HorizontalDivider(color = AppColors.Divider)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Restaurant, contentDescription = null, tint = AppColors.TextTertiary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Nothing logged yet", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Search for a food to start tracking today.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    entryPendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryPendingDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            title = { Text("Delete this entry?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("This can't be undone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    entryPendingDelete = null
                    scope.launch {
                        supabase.postgrest.from("diet_logs").delete { filter { eq("id", entry.id) } }
                        refreshEntries()
                    }
                }) { Text("Delete", color = AppColors.Destructive) }
            },
            dismissButton = {
                TextButton(onClick = { entryPendingDelete = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }
}

@Composable
private fun NutrientRow(label: String, current: Double, target: Double, unit: String) {
    val fraction = if (target > 0) (current / target).toFloat().coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${current.toInt()}$unit / ${target.toInt()}$unit",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppColors.Divider)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(fraction.coerceAtLeast(if (current > 0) 0.02f else 0f)).fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}