package com.asur.gymapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

    LaunchedEffect(showFoodSearch) {
        onNestedChange(showFoodSearch)
    }

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
                        errorMessage = "Error: ${e.message}"
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
        Spacer(Modifier.height(24.dp))
        Text("Log Diet", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Track today's macros",
            style = MaterialTheme.typography.bodyMedium,
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
                    Text("Today's Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(14.dp))
                    NutrientRow("Calories", totalCalories.toDouble(), t.targetCalories.toDouble(), "")
                    Spacer(Modifier.height(10.dp))
                    NutrientRow("Protein", totalProtein, t.targetProteinG.toDouble(), "g")
                    Spacer(Modifier.height(10.dp))
                    NutrientRow("Fat", totalFat, t.targetFatG.toDouble(), "g")
                    Spacer(Modifier.height(10.dp))
                    NutrientRow("Fiber", totalFiber, t.targetFiberG.toDouble(), "g")
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$totalCalories", style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp), fontWeight = FontWeight.Bold)
                            Text("calories today", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${totalProtein.toInt()}g", style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp), fontWeight = FontWeight.Bold)
                            Text("protein today", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    targetsError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { showFoodSearch = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text("+ Search Food")
        }

        errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(entry.meal_label ?: "Entry", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${entry.calories} cal · ${entry.protein_g.toInt()}g protein" +
                                        (entry.fat_g?.let { " · ${it.toInt()}g fat" } ?: "") +
                                        (entry.fiber_g?.let { " · ${it.toInt()}g fiber" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                supabase.postgrest.from("diet_logs").delete { filter { eq("id", entry.id) } }
                                refreshEntries()
                            }
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NutrientRow(label: String, current: Double, target: Double, unit: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${current.toInt()}$unit / ${target.toInt()}$unit",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (current / target).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}