package com.asur.gymapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private enum class Step { WEIGHT, HEIGHT, DOB, GENDER, ACTIVITY, GOAL, REVIEW }

private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.WEIGHT) }

    // Canonical values, always stored in metric internally
    var weightKg by remember { mutableStateOf(70.0) }
    var heightCm by remember { mutableStateOf(170.0) }

    val currentYear = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.year }
    var dobDay by remember { mutableStateOf(1) }
    var dobMonthIndex by remember { mutableStateOf(0) }
    var dobYear by remember { mutableStateOf(currentYear - 25) }

    var isMale by remember { mutableStateOf(true) }
    var activityLevel by remember { mutableStateOf<ActivityLevel?>(null) }
    var goal by remember { mutableStateOf<Goal?>(null) }

    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun next(target: Step) { step = target }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        when (step) {

            Step.WEIGHT -> {
                Text("Your weight", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(24.dp))
                WeightPicker(weightKg = weightKg, onWeightKgChange = { weightKg = it })
                Spacer(Modifier.weight(1f))
                Button(onClick = { next(Step.HEIGHT) }, modifier = Modifier.fillMaxWidth()) { Text("Next") }
            }

            Step.HEIGHT -> {
                Text("Your height", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(24.dp))
                HeightPicker(heightCm = heightCm, onHeightCmChange = { heightCm = it })
                Spacer(Modifier.weight(1f))
                Row {
                    OutlinedButton(onClick = { next(Step.WEIGHT) }, modifier = Modifier.weight(1f)) { Text("Back") }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { next(Step.DOB) }, modifier = Modifier.weight(1f)) { Text("Next") }
                }
            }

            Step.DOB -> {
                Text("Date of birth", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(32.dp))
                val dayOptions = (1..31).toList()
                val yearOptions = ((currentYear - 100)..(currentYear - 10)).toList().reversed()
                Row(modifier = Modifier.fillMaxWidth()) {
                    WheelPicker(
                        items = dayOptions,
                        selectedIndex = dobDay - 1,
                        onSelectedIndexChange = { dobDay = dayOptions[it] },
                        itemLabel = { "$it" },
                        modifier = Modifier.weight(1f)
                    )
                    WheelPicker(
                        items = months,
                        selectedIndex = dobMonthIndex,
                        onSelectedIndexChange = { dobMonthIndex = it },
                        itemLabel = { it },
                        modifier = Modifier.weight(1f)
                    )
                    WheelPicker(
                        items = yearOptions,
                        selectedIndex = yearOptions.indexOf(dobYear).coerceAtLeast(0),
                        onSelectedIndexChange = { dobYear = yearOptions[it] },
                        itemLabel = { "$it" },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.weight(1f))
                Row {
                    OutlinedButton(onClick = { next(Step.HEIGHT) }, modifier = Modifier.weight(1f)) { Text("Back") }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { next(Step.GENDER) }, modifier = Modifier.weight(1f)) { Text("Next") }
                }
            }

            Step.GENDER -> {
                Text("Gender", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(24.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = isMale,
                        onClick = { isMale = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Male") }
                    SegmentedButton(
                        selected = !isMale,
                        onClick = { isMale = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Female") }
                }
                Spacer(Modifier.weight(1f))
                Row {
                    OutlinedButton(onClick = { next(Step.DOB) }, modifier = Modifier.weight(1f)) { Text("Back") }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { next(Step.ACTIVITY) }, modifier = Modifier.weight(1f)) { Text("Next") }
                }
            }

            Step.ACTIVITY -> {
                Text("Activity level", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                ActivityLevel.entries.forEach { level ->
                    OutlinedButton(
                        onClick = { activityLevel = level },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = if (activityLevel == level) ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) else ButtonDefaults.outlinedButtonColors()
                    ) { Text(level.label) }
                }
                Spacer(Modifier.weight(1f))
                Row {
                    OutlinedButton(onClick = { next(Step.GENDER) }, modifier = Modifier.weight(1f)) { Text("Back") }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { next(Step.GOAL) },
                        enabled = activityLevel != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("Next") }
                }
            }

            Step.GOAL -> {
                Text("Goal", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Goal.entries.forEach { g ->
                    OutlinedButton(
                        onClick = { goal = g },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = if (goal == g) ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) else ButtonDefaults.outlinedButtonColors()
                    ) { Text(g.label) }
                }
                Spacer(Modifier.weight(1f))
                Row {
                    OutlinedButton(onClick = { next(Step.ACTIVITY) }, modifier = Modifier.weight(1f)) { Text("Back") }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { next(Step.REVIEW) },
                        enabled = goal != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("Next") }
                }
            }

            Step.REVIEW -> {
                Text("Review", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                val dob = LocalDate(dobYear, dobMonthIndex + 1, dobDay)
                Text("Weight: ${"%.2f".format(weightKg)} kg")
                Text("Height: ${heightCm.toInt()} cm")
                Text("Date of birth: $dob")
                Text("Age: ${ageFromDob(dob)}")
                Text("Gender: ${if (isMale) "Male" else "Female"}")
                Text("Activity: ${activityLevel?.label}")
                Text("Goal: ${goal?.label}")

                Spacer(Modifier.height(16.dp))
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                }

                Spacer(Modifier.weight(1f))
                Row {
                    OutlinedButton(onClick = { next(Step.GOAL) }, modifier = Modifier.weight(1f)) { Text("Back") }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                saving = true
                                errorMessage = null
                                try {
                                    completeOnboarding(
                                        weightKg = weightKg,
                                        heightCm = heightCm,
                                        dateOfBirth = dob.toString(),
                                        gender = if (isMale) "male" else "female",
                                        activityLevel = activityLevel!!.name,
                                        goal = goal!!.name
                                    )
                                    onComplete()
                                } catch (e: Exception) {
                                    errorMessage = "Error: ${e.message}"
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (saving) "Saving..." else "Finish") }
                }
            }
        }
    }
}