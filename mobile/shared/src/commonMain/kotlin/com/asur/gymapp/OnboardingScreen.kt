package com.asur.gymapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock

private enum class Step { WEIGHT, HEIGHT, DOB, GENDER, ACTIVITY, GOAL, REVIEW }
private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun daysInMonth(monthIndex: Int, year: Int): Int = when (monthIndex) {
    1 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    3, 5, 8, 10 -> 30
    else -> 31
}

private fun formatTwoDecimal(value: Double): String {
    val scaled = (value * 100).roundToInt()
    val whole = scaled / 100
    val frac = (scaled % 100).let { if (it < 0) -it else it }
    return "$whole.${frac.toString().padStart(2, '0')}"
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.WEIGHT) }
    var weightKg by remember { mutableStateOf(70.0) }
    var heightCm by remember { mutableStateOf(170.0) }
    val currentYear = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.year }
    var dobDay by remember { mutableStateOf(1) }
    var dobMonthIndex by remember { mutableStateOf(0) }
    var dobYear by remember { mutableStateOf(currentYear - 25) }
    var gender by remember { mutableStateOf<String?>(null) }
    var activityLevel by remember { mutableStateOf<ActivityLevel?>(null) }
    var goal by remember { mutableStateOf<Goal?>(null) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Clamp the selected day whenever month/year changes so an invalid
    // date (e.g. Feb 31) can never reach LocalDate construction on Review.
    LaunchedEffect(dobMonthIndex, dobYear) {
        val maxDay = daysInMonth(dobMonthIndex, dobYear)
        if (dobDay > maxDay) dobDay = maxDay
    }

    fun next(target: Step) { step = target }
    val stepIndex = Step.entries.indexOf(step)
    val progress = (stepIndex + 1).toFloat() / Step.entries.size

    Column(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = AppColors.Divider,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        Spacer(Modifier.height(28.dp))

        StepHeader(when (step) {
            Step.WEIGHT -> "Your weight"
            Step.HEIGHT -> "Your height"
            Step.DOB -> "Your date of birth"
            Step.GENDER -> "Gender"
            Step.ACTIVITY -> "Activity level"
            Step.GOAL -> "Goal"
            Step.REVIEW -> "Review"
        })

        Spacer(Modifier.height(40.dp))

        when (step) {
            Step.WEIGHT -> {
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    WeightPicker(weightKg = weightKg, onWeightKgChange = { weightKg = it })
                }
                PrimaryButton(label = "Next", onClick = { next(Step.HEIGHT) })
            }
            Step.HEIGHT -> {
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    HeightPicker(heightCm = heightCm, onHeightCmChange = { heightCm = it })
                }
                NavRow(onBack = { next(Step.WEIGHT) }, onNext = { next(Step.DOB) })
            }
            Step.DOB -> {
                val dayOptions = (1..daysInMonth(dobMonthIndex, dobYear)).toList()
                val yearOptions = ((currentYear - 100)..(currentYear - 10)).toList().reversed()
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        WheelPicker(
                            items = dayOptions,
                            selectedIndex = (dobDay - 1).coerceIn(0, dayOptions.lastIndex),
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
                }
                NavRow(onBack = { next(Step.HEIGHT) }, onNext = { next(Step.GENDER) })
            }
            Step.GENDER -> {
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GenderCard("♂", "Male", gender == "male", { gender = "male" }, Modifier.weight(1f))
                        GenderCard("♀", "Female", gender == "female", { gender = "female" }, Modifier.weight(1f))
                        GenderCard("⚧", "Other", gender == "other", { gender = "other" }, Modifier.weight(1f))
                    }
                }
                NavRow(onBack = { next(Step.DOB) }, onNext = { next(Step.ACTIVITY) }, nextEnabled = gender != null)
            }
            Step.ACTIVITY -> {
                Column(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActivityLevel.entries.forEach { level ->
                        SelectableCard(level.label, activityLevel == level, { activityLevel = level })
                    }
                }
                NavRow(onBack = { next(Step.GENDER) }, onNext = { next(Step.GOAL) }, nextEnabled = activityLevel != null)
            }
            Step.GOAL -> {
                Column(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Goal.entries.forEach { g ->
                        SelectableCard(g.label, goal == g, { goal = g })
                    }
                }
                NavRow(onBack = { next(Step.ACTIVITY) }, onNext = { next(Step.REVIEW) }, nextEnabled = goal != null)
            }
            Step.REVIEW -> {
                val dob = LocalDate(dobYear, dobMonthIndex + 1, dobDay)
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp)
                    ) {
                        ReviewRow("Weight", "${formatTwoDecimal(weightKg)} kg")
                        ReviewRow("Height", "${heightCm.roundToInt()} cm")
                        ReviewRow("Date of birth", "$dob")
                        ReviewRow("Age", "${ageFromDob(dob)}")
                        ReviewRow("Gender", gender?.replaceFirstChar { it.uppercase() } ?: "—")
                        ReviewRow("Activity", activityLevel?.label ?: "—")
                        ReviewRow("Goal", goal?.label ?: "—", showDivider = false)
                    }
                    errorMessage?.let {
                        Spacer(Modifier.height(16.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row {
                    OutlinedButton(
                        onClick = { next(Step.GOAL) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) { Text("Back") }
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
                                        gender = gender!!,
                                        activityLevel = activityLevel!!.name,
                                        goal = goal!!.name
                                    )
                                    onComplete()
                                } catch (e: Exception) {
                                    errorMessage = "Couldn't save your details. ${e.message}"
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.medium,
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color(0xFF4A1B0C))
                    ) { Text(if (saving) "Saving" else "Finish", fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
}

@Composable
private fun StepHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color(0xFF4A1B0C))
    ) { Text(label, fontWeight = FontWeight.Medium) }
}

@Composable
private fun NavRow(onBack: () -> Unit, onNext: () -> Unit, nextEnabled: Boolean = true) {
    Row {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) { Text("Back") }
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = MaterialTheme.shapes.medium,
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color(0xFF4A1B0C))
        ) { Text("Next", fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun ReviewRow(label: String, value: String, showDivider: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
    if (showDivider) HorizontalDivider(color = AppColors.Divider)
}

@Composable
private fun GenderCard(symbol: String, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFF3A2A26) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(symbol, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SelectableCard(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFF3A2A26) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp, horizontal = 16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}