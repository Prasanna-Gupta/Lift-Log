package com.asur.gymapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FoodSearchScreen(
    onFoodSelected: (calories: Int, proteinG: Double, fatG: Double, fiberG: Double, label: String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FoodSearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFood by remember { mutableStateOf<FoodDetail?>(null) }
    var loadingPortions by remember { mutableStateOf(false) }

    fun runSearch() {
        if (query.isBlank()) return
        scope.launch {
            searching = true
            errorMessage = null
            try {
                results = searchFood(query)
            } catch (e: Exception) {
                errorMessage = "Search failed: ${e.message}"
            } finally {
                searching = false
            }
        }
    }

    val food = selectedFood
    if (food != null) {
        PortionPickerScreen(
            food = food,
            onConfirm = { calories, protein, fat, fiber, label -> onFoodSelected(calories, protein, fat, fiber, label) },
            onBack = { selectedFood = null }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Search Food", style = MaterialTheme.typography.titleLarge)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search USDA food database") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { runSearch() }, enabled = !searching, shape = MaterialTheme.shapes.medium) {
                Text(if (searching) "..." else "Go")
            }
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
        }

        if (loadingPortions) {
            Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            items(results, key = { it.fdcId }) { result ->
                ListItem(
                    headlineContent = { Text(result.description) },
                    supportingContent = {
                        val cal = result.caloriesPer100g?.toInt()
                        val prot = result.proteinPer100g
                        Text(
                            buildString {
                                if (cal != null) append("$cal cal/100g")
                                if (prot != null) append(" · ${prot.toInt()}g protein")
                                result.brandOwner?.let { append(" · $it") }
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clickable {
                        scope.launch {
                            loadingPortions = true
                            errorMessage = null
                            try {
                                selectedFood = getFoodPortions(result.fdcId)
                            } catch (e: Exception) {
                                errorMessage = "Failed to load portions: ${e.message}"
                            } finally {
                                loadingPortions = false
                            }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable
private fun PortionPickerScreen(
    food: FoodDetail,
    onConfirm: (calories: Int, proteinG: Double, fatG: Double, fiberG: Double, label: String) -> Unit,
    onBack: () -> Unit
) {
    var selectedPortion by remember { mutableStateOf(food.portions.firstOrNull()) }
    var quantity by remember { mutableStateOf("1") }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(food.description, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))

        Text("Portion", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(food.portions) { portion ->
                val selected = portion == selectedPortion
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .clickable { selectedPortion = portion },
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(portion.label, fontWeight = FontWeight.Medium)
                            Text("${portion.grams.toInt()}g", style = MaterialTheme.typography.bodySmall)
                        }
                        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = it },
            label = { Text("Quantity") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val portion = selectedPortion ?: return@Button
                val qty = quantity.toDoubleOrNull() ?: 1.0
                val totalGrams = portion.grams * qty
                val scale = totalGrams / 100.0
                onConfirm(
                    ((food.caloriesPer100g ?: 0.0) * scale).toInt(),
                    (food.proteinPer100g ?: 0.0) * scale,
                    (food.fatPer100g ?: 0.0) * scale,
                    (food.fiberPer100g ?: 0.0) * scale,
                    "${food.description} (${qty.let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() }} × ${portion.label})"
                )
            },
            enabled = selectedPortion != null,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text("Add to Log")
        }
        Spacer(Modifier.height(16.dp))
    }
}