package com.asur.gymapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WeightPicker(weightKg: Double, onWeightKgChange: (Double) -> Unit) {
    var unit by remember { mutableStateOf("kg") }

    // Independent, stable state per ruler — never re-derived from weightKg mid-interaction.
    // Only re-seeded when the unit toggle changes.
    var displayInt by remember { mutableStateOf(weightKg.toInt().coerceIn(30, 200)) }
    var displayDec by remember { mutableStateOf((((weightKg - weightKg.toInt()) * 100).toInt()).coerceIn(0, 99)) }

    fun emit() {
        val display = displayInt + displayDec / 100.0
        onWeightKgChange(if (unit == "kg") display else lbToKg(display))
    }

    fun switchUnit(newUnit: String) {
        if (newUnit == unit) return
        val currentDisplay = displayInt + displayDec / 100.0
        val currentKg = if (unit == "kg") currentDisplay else lbToKg(currentDisplay)
        val converted = if (newUnit == "kg") currentKg else kgToLb(currentKg)
        val bounds = if (newUnit == "kg") 30..200 else 66..440
        displayInt = converted.toInt().coerceIn(bounds.first, bounds.last)
        displayDec = (((converted - converted.toInt()) * 100).toInt()).coerceIn(0, 99)
        unit = newUnit
        emit()
    }

    val range = if (unit == "kg") 30..200 else 66..440

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.width(160.dp)) {
            SegmentedButton(selected = unit == "lb", onClick = { switchUnit("lb") }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("lb") }
            SegmentedButton(selected = unit == "kg", onClick = { switchUnit("kg") }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("kg") }
        }
        Spacer(Modifier.height(16.dp))
        Text("$displayInt.${displayDec.toString().padStart(2, '0')} $unit", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        RulerPicker(
            range = range,
            value = displayInt,
            onValueChange = { newInt -> displayInt = newInt; emit() },
            majorStep = 10,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        RulerPicker(
            range = 0..99,
            value = displayDec,
            onValueChange = { newDec -> displayDec = newDec; emit() },
            majorStep = 10,
            height = 60.dp,
            majorTickHeight = 22.dp,
            minorTickHeight = 10.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun HeightPicker(heightCm: Double, onHeightCmChange: (Double) -> Unit) {
    var unit by remember { mutableStateOf("cm") }

    var cmValue by remember { mutableStateOf(heightCm.toInt().coerceIn(100, 220)) }
    var feetValue by remember { mutableStateOf((cmToIn(heightCm).toInt() / 12).coerceIn(3, 8)) }
    var inchValue by remember { mutableStateOf((cmToIn(heightCm).toInt() % 12).coerceIn(0, 11)) }

    fun switchUnit(newUnit: String) {
        if (newUnit == unit) return
        if (newUnit == "cm") {
            val totalIn = feetValue * 12 + inchValue
            cmValue = inToCm(totalIn.toDouble()).toInt().coerceIn(100, 220)
            onHeightCmChange(cmValue.toDouble())
        } else {
            val totalIn = cmToIn(cmValue.toDouble()).toInt()
            feetValue = (totalIn / 12).coerceIn(3, 8)
            inchValue = (totalIn % 12).coerceIn(0, 11)
            onHeightCmChange(inToCm((feetValue * 12 + inchValue).toDouble()))
        }
        unit = newUnit
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.width(200.dp)) {
            SegmentedButton(selected = unit == "ftin", onClick = { switchUnit("ftin") }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("ft/in") }
            SegmentedButton(selected = unit == "cm", onClick = { switchUnit("cm") }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("cm") }
        }
        Spacer(Modifier.height(16.dp))

        if (unit == "cm") {
            Text("$cmValue cm", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(16.dp))
            RulerPicker(
                range = 100..220,
                value = cmValue,
                onValueChange = { cmValue = it; onHeightCmChange(it.toDouble()) },
                majorStep = 10,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text("$feetValue' $inchValue\"", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                RulerPicker(
                    range = 3..8,
                    value = feetValue,
                    onValueChange = { newFeet ->
                        feetValue = newFeet
                        onHeightCmChange(inToCm((newFeet * 12 + inchValue).toDouble()))
                    },
                    majorStep = 1,
                    modifier = Modifier.weight(1f)
                )
                RulerPicker(
                    range = 0..11,
                    value = inchValue,
                    onValueChange = { newIn ->
                        inchValue = newIn
                        onHeightCmChange(inToCm((feetValue * 12 + newIn).toDouble()))
                    },
                    majorStep = 3,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}