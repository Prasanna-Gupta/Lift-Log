package com.asur.gymapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var hasSearched by remember { mutableStateOf(false) }
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
                hasSearched = true
            } catch (e: Exception) {
                errorMessage = "Search failed. ${e.message}"
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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(22.dp).clickable(onClick = onBack)
            )
            Spacer(Modifier.width(14.dp))
            Text("Search food", style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp), fontWeight = FontWeight.Medium)
        }

        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = AppColors.TextTertiary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search USDA food database", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextTertiary)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Clear",
                    tint = AppColors.TextTertiary,
                    modifier = Modifier.size(15.dp).clickable { query = ""; results = emptyList(); hasSearched = false }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
        }

        if (searching || loadingPortions) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (!hasSearched) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = AppColors.TextTertiary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Search a food and press enter or the search key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (results.isEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.SearchOff, contentDescription = null, tint = AppColors.TextTertiary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(12.dp))
                Text("No results for \"$query\"", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(results, key = { _, r -> r.fdcId }) { index, result ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    loadingPortions = true
                                    errorMessage = null
                                    try {
                                        selectedFood = getFoodPortions(result.fdcId)
                                    } catch (e: Exception) {
                                        errorMessage = "Couldn't load portion data. ${e.message}"
                                    } finally {
                                        loadingPortions = false
                                    }
                                }
                            }
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(result.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            val cal = result.caloriesPer100g?.toInt()
                            val prot = result.proteinPer100g
                            val details = buildString {
                                if (cal != null) append("$cal cal/100g")
                                if (prot != null) append(" · ${prot.toInt()}g protein")
                                result.brandOwner?.let { append(" · $it") }
                            }
                            if (details.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(details, style = MaterialTheme.typography.bodySmall, color = AppColors.TextTertiary)
                            }
                        }
                    }
                    if (index < results.lastIndex) HorizontalDivider(color = AppColors.Divider)
                }
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
    var quantityText by remember { mutableStateOf("1") }
    val quantity = quantityText.toDoubleOrNull()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(22.dp).clickable(onClick = onBack)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                food.description,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
        }

        if (food.portions.isEmpty()) {
            Text(
                "No portion data available for this food.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp)
            )
            return
        }

        Text("PORTION", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(food.portions) { portion ->
                val selected = portion == selectedPortion
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color(0xFF3A2A26) else MaterialTheme.colorScheme.surface)
                        .clickable { selectedPortion = portion }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(portion.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("${portion.grams.toInt()}g", style = MaterialTheme.typography.bodySmall, color = AppColors.TextTertiary)
                    }
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Quantity", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            BasicTextField(
                value = quantityText,
                onValueChange = { raw -> quantityText = raw.filter { it.isDigit() || it == '.' }.take(5) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(60.dp)
            )
        }
        if (quantity == null || quantity <= 0) {
            Spacer(Modifier.height(6.dp))
            Text("Enter a quantity greater than 0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (selectedPortion != null && quantity != null && quantity > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = selectedPortion != null && quantity != null && quantity > 0) {
                    val portion = selectedPortion ?: return@clickable
                    val qty = quantity ?: return@clickable
                    val totalGrams = portion.grams * qty
                    val scale = totalGrams / 100.0
                    val qtyLabel = if (qty == qty.toInt().toDouble()) qty.toInt().toString() else qty.toString()
                    onConfirm(
                        ((food.caloriesPer100g ?: 0.0) * scale).toInt(),
                        (food.proteinPer100g ?: 0.0) * scale,
                        (food.fatPer100g ?: 0.0) * scale,
                        (food.fiberPer100g ?: 0.0) * scale,
                        "${food.description} ($qtyLabel × ${portion.label})"
                    )
                }
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "Add to log",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Medium,
                color = if (selectedPortion != null && quantity != null && quantity > 0) Color(0xFF4A1B0C) else AppColors.TextTertiary
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
