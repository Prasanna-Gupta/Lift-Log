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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@Composable
fun DietLogScreen() {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<DietLogRow>>(emptyList()) }
    var targets by remember { mutableStateOf<NutritionTargets?>(null) }
    var targetsError by remember { mutableStateOf<String?>(null) }

    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }
    var mealLabel by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

    val totalCalories = entries.sumOf { it.calories }
    val totalProtein = entries.sumOf { it.protein_g }
    val totalFat = entries.sumOf { it.fat_g ?: 0.0 }
    val totalFiber = entries.sumOf { it.fiber_g ?: 0.0 }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Log Diet", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                val t = targets
                if (t != null) {
                    Text("Today's Progress", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    NutrientRow("Calories", totalCalories.toDouble(), t.targetCalories.toDouble(), "")
                    NutrientRow("Protein", totalProtein, t.targetProteinG.toDouble(), "g")
                    NutrientRow("Fat", totalFat, t.targetFatG.toDouble(), "g")
                    NutrientRow("Fiber", totalFiber, t.targetFiberG.toDouble(), "g")
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$totalCalories", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("calories today", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${totalProtein.toInt()}g", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Add entry", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = mealLabel,
                    onValueChange = { mealLabel = it },
                    label = { Text("Meal (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = calories,
                        onValueChange = { calories = it },
                        label = { Text("Calories") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it },
                        label = { Text("Protein (g)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it },
                        label = { Text("Fat (g)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    OutlinedTextField(
                        value = fiber,
                        onValueChange = { fiber = it },
                        label = { Text("Fiber (g)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        val cal = calories.toIntOrNull()
                        val prot = protein.toDoubleOrNull()
                        if (cal == null || prot == null) {
                            errorMessage = "Enter valid calories and protein"
                            return@Button
                        }
                        scope.launch {
                            saving = true
                            errorMessage = null
                            try {
                                val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
                                supabase.postgrest.from("diet_logs").insert(
                                    DietLogInsert(
                                        user_id = userId,
                                        date = logDateForNow(),
                                        calories = cal,
                                        protein_g = prot,
                                        fat_g = fat.toDoubleOrNull(),
                                        fiber_g = fiber.toDoubleOrNull(),
                                        meal_label = mealLabel.ifBlank { null }
                                    )
                                )
                                calories = ""; protein = ""; fat = ""; fiber = ""; mealLabel = ""
                                refreshEntries()
                            } catch (e: Exception) {
                                errorMessage = "Error: ${e.message}"
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (saving) "Adding..." else "Add Entry")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(entries, key = { it.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(entry.meal_label ?: "Entry", fontWeight = FontWeight.Bold)
                            Text(
                                "${entry.calories} cal · ${entry.protein_g.toInt()}g protein" +
                                        (entry.fat_g?.let { " · ${it.toInt()}g fat" } ?: "") +
                                        (entry.fiber_g?.let { " · ${it.toInt()}g fiber" } ?: ""),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                supabase.postgrest.from("diet_logs").delete { filter { eq("id", entry.id) } }
                                refreshEntries()
                            }
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NutrientRow(label: String, current: Double, target: Double, unit: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${current.toInt()}$unit / ${target.toInt()}$unit", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (current / target).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}