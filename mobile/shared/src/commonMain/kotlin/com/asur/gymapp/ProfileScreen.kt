package com.asur.gymapp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt

@Composable
fun ProfileScreen(onNestedChange: (Boolean) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var streak by remember { mutableStateOf<StreakRow?>(null) }
    var profile by remember { mutableStateOf<UserProfileRow?>(null) }
    var weightKg by remember { mutableStateOf<Double?>(null) }
    var workoutCount by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf(false) }
    var showPhotos by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var unreadCount by remember { mutableStateOf(0) }
    var activeDates by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showLogWeight by remember { mutableStateOf(false) }

    LaunchedEffect(editing, showPhotos, showProgress, showNotifications, showLogWeight) {
        onNestedChange(editing || showPhotos || showProgress || showNotifications || showLogWeight)
    }

    suspend fun loadAll() {
        activeDates = fetchWorkoutDatesLastNDays(84)
        streak = fetchStreak()
        profile = fetchUserProfile()
        weightKg = fetchLatestWeightKg()
        workoutCount = fetchWorkoutCount()
        unreadCount = fetchUnreadNotificationCount()
        loading = false
    }
    LaunchedEffect(Unit) { loadAll() }

    if (showProgress) {
        ProgressScreen(onBack = { showProgress = false })
        return
    }
    if (showPhotos) {
        ProgressPhotosScreen(onBack = { showPhotos = false })
        return
    }
    if (showNotifications) {
        NotificationsScreen(onBack = {
            showNotifications = false
            scope.launch { unreadCount = fetchUnreadNotificationCount() }
        })
        return
    }
    if (editing) {
        val p = profile
        if (p != null) {
            EditProfileScreen(
                profile = p,
                currentWeightKg = weightKg ?: 70.0,
                onBack = { editing = false },
                onSaved = {
                    editing = false
                    scope.launch { loadAll() }
                }
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(28.dp))
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val p = profile
                val s = streak
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    if (p?.avatar_url != null) {
                        AsyncImage(
                            model = p.avatar_url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(64.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = AppColors.TextTertiary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f).padding(top = 6.dp)) {
                        Text(
                            p?.name ?: "You",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
                            fontWeight = FontWeight.Medium
                        )
                        p?.email?.let {
                            Spacer(Modifier.height(3.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.TextTertiary)
                        }
                    }
                    Box(
                        modifier = Modifier.size(34.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { editing = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit profile",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        val animatedStreak by animateIntAsState(
                            targetValue = s?.current_streak ?: 0,
                            animationSpec = tween(durationMillis = 800),
                            label = "streak"
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "$animatedStreak",
                                style = MaterialTheme.typography.displayLarge.copy(fontSize = 46.sp),
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "day streak",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        if (s?.warning_used == true) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Saved after a missed day",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = AppColors.Divider)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Last 12 weeks", style = MaterialTheme.typography.labelSmall)
                            Text("$workoutCount workouts", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(10.dp))
                        StreakHeatmap(activeDates = activeDates)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetaItem(Modifier.weight(1f), "${s?.longest_streak ?: 0} days", "longest")
                    MetaDivider()
                    MetaItem(Modifier.weight(1f), formatDayMonth(s?.last_logged_date), "last logged")
                    MetaDivider()
                    MetaItem(Modifier.weight(1f), formatMonthYear(p?.created_at), "member since")
                }
                Spacer(Modifier.height(24.dp))
                Text("DETAILS", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showLogWeight = true }.padding(vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Weight", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(weightKg?.let { "${it.oneDecimal()} kg" } ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Log weight", tint = AppColors.TextTertiary, modifier = Modifier.size(16.dp))
                            }
                        }
                        HorizontalDivider(color = AppColors.Divider)
                        DetailRow("Height", p?.height_cm?.let { "${it.toInt()} cm" } ?: "—")
                        DetailRow("Age", p?.date_of_birth?.let { "${ageFromDob(LocalDate.parse(it))}" } ?: "—")
                        DetailRow(
                            "Activity",
                            p?.activity_level?.let { runCatching { ActivityLevel.valueOf(it).label }.getOrNull() } ?: "—"
                        )
                        DetailRow(
                            "Goal",
                            p?.goal?.let { runCatching { Goal.valueOf(it).label }.getOrNull() } ?: "—",
                            showDivider = false
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show my activity", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Visible in the group feed",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Switch(
                            checked = p?.feed_visible ?: true,
                            onCheckedChange = { visible ->
                                scope.launch {
                                    updateFeedVisibility(visible)
                                    loadAll()
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ActionRow(Icons.Filled.Notifications, "Notifications", badgeCount = unreadCount) { showNotifications = true }
                        HorizontalDivider(color = AppColors.Divider)
                        ActionRow(Icons.Filled.ShowChart, "View progress") { showProgress = true }
                        ActionRow(Icons.Filled.PhotoCamera, "Progress photos", showDivider = false) { showPhotos = true }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { scope.launch { supabase.auth.signOut() } }) {
                        Text("Sign out", style = MaterialTheme.typography.bodyMedium, color = AppColors.Destructive)
                    }
                }
                Spacer(Modifier.height(120.dp))
            }
        }
        BottomFadeOverlay()
    }

    if (showLogWeight) {
        LogWeightSheet(
            currentWeightKg = weightKg,
            onDismiss = { showLogWeight = false },
            onSaved = {
                showLogWeight = false
                scope.launch { loadAll() }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    profile: UserProfileRow,
    currentWeightKg: Double,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var weightIsKg by remember { mutableStateOf(profile.unit_preference != "imperial") }
    var heightIsCm by remember { mutableStateOf(profile.unit_preference != "imperial") }
    var weightText by remember {
        mutableStateOf(
            if (weightIsKg) currentWeightKg.oneDecimal()
            else (currentWeightKg * 2.20462).oneDecimal()
        )
    }
    val startCm = profile.height_cm ?: 170.0
    var heightCmText by remember { mutableStateOf(startCm.roundToInt().toString()) }
    var heightFtText by remember { mutableStateOf((startCm / 30.48).toInt().toString()) }
    var heightInText by remember {
        mutableStateOf(((startCm / 2.54) - (startCm / 30.48).toInt() * 12).roundToInt().toString())
    }
    var activityLevel by remember { mutableStateOf(profile.activity_level?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() }) }
    var goal by remember { mutableStateOf(profile.goal?.let { runCatching { Goal.valueOf(it) }.getOrNull() }) }
    var showActivitySheet by remember { mutableStateOf(false) }
    var showGoalSheet by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }
    fun resolvedWeightKg(): Double? {
        val v = weightText.toDoubleOrNull() ?: return null
        return if (weightIsKg) v else v / 2.20462
    }
    fun resolvedHeightCm(): Double? {
        return if (heightIsCm) {
            heightCmText.toDoubleOrNull()
        } else {
            val ft = heightFtText.toIntOrNull() ?: return null
            val inch = heightInText.toIntOrNull() ?: 0
            (ft * 12 + inch) * 2.54
        }
    }
    fun attemptBack() {
        if (dirty) showDiscardDialog = true else onBack()
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 26.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(22.dp).clickable { attemptBack() }
            )
            Spacer(Modifier.width(14.dp))
            Text("Edit profile", style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp), fontWeight = FontWeight.Medium)
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("MEASUREMENTS", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Weight", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        InlineNumberField(
                            value = weightText,
                            onValueChange = { weightText = it; dirty = true },
                            widthDp = 64
                        )
                        Spacer(Modifier.width(12.dp))
                        UnitToggle(
                            options = listOf("kg", "lb"),
                            selectedIndex = if (weightIsKg) 0 else 1,
                            onSelect = { idx ->
                                val toKg = idx == 0
                                if (toKg != weightIsKg) {
                                    val v = weightText.toDoubleOrNull()
                                    if (v != null) {
                                        weightText = if (toKg) (v / 2.20462).oneDecimal() else (v * 2.20462).oneDecimal()
                                    }
                                    weightIsKg = toKg
                                }
                            }
                        )
                    }
                    HorizontalDivider(color = AppColors.Divider)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Height", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (heightIsCm) {
                            InlineNumberField(
                                value = heightCmText,
                                onValueChange = { heightCmText = it; dirty = true },
                                widthDp = 64
                            )
                        } else {
                            InlineNumberField(
                                value = heightFtText,
                                onValueChange = { heightFtText = it; dirty = true },
                                widthDp = 28
                            )
                            Text("'", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextTertiary, modifier = Modifier.padding(horizontal = 3.dp))
                            InlineNumberField(
                                value = heightInText,
                                onValueChange = { heightInText = it; dirty = true },
                                widthDp = 28
                            )
                            Text("\"", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextTertiary, modifier = Modifier.padding(start = 2.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        UnitToggle(
                            options = listOf("cm", "ft"),
                            selectedIndex = if (heightIsCm) 0 else 1,
                            onSelect = { idx ->
                                val toCm = idx == 0
                                if (toCm != heightIsCm) {
                                    if (toCm) {
                                        val ft = heightFtText.toIntOrNull() ?: 0
                                        val inch = heightInText.toIntOrNull() ?: 0
                                        heightCmText = ((ft * 12 + inch) * 2.54).roundToInt().toString()
                                    } else {
                                        val cm = heightCmText.toDoubleOrNull() ?: 0.0
                                        heightFtText = (cm / 30.48).toInt().toString()
                                        heightInText = ((cm / 2.54) - (cm / 30.48).toInt() * 12).roundToInt().toString()
                                    }
                                    heightIsCm = toCm
                                }
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("TRAINING", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    PickerRow("Activity", activityLevel?.label ?: "Choose") { showActivitySheet = true }
                    HorizontalDivider(color = AppColors.Divider)
                    PickerRow("Goal", goal?.label ?: "Choose") { showGoalSheet = true }
                }
            }
            errorMessage?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp))
            }
        }
        Button(
            onClick = {
                val kg = resolvedWeightKg()
                val cm = resolvedHeightCm()
                if (kg == null || cm == null || activityLevel == null || goal == null) {
                    errorMessage = "Fill in every field before saving"
                    return@Button
                }
                if (kg < 20 || kg > 400 || cm < 90 || cm > 250) {
                    errorMessage = "Those numbers look off — check weight and height"
                    return@Button
                }
                scope.launch {
                    saving = true
                    errorMessage = null
                    try {
                        updateProfileDetails(kg, cm, activityLevel!!.name, goal!!.name)
                        onSaved()
                    } catch (e: Exception) {
                        errorMessage = "Couldn't save. ${e.message}"
                    } finally {
                        saving = false
                    }
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color(0xFF4A1B0C)
            )
        ) {
            Text(if (saving) "Saving" else "Save changes", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(124.dp))
    }
    if (showActivitySheet) {
        OptionSheet(
            title = "Activity level",
            options = ActivityLevel.entries.map { it.label },
            selectedIndex = ActivityLevel.entries.indexOf(activityLevel),
            onDismiss = { showActivitySheet = false },
            onSelect = { activityLevel = ActivityLevel.entries[it]; dirty = true; showActivitySheet = false }
        )
    }
    if (showGoalSheet) {
        OptionSheet(
            title = "Goal",
            options = Goal.entries.map { it.label },
            selectedIndex = Goal.entries.indexOf(goal),
            onDismiss = { showGoalSheet = false },
            onSelect = { goal = Goal.entries[it]; dirty = true; showGoalSheet = false }
        )
    }
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            title = { Text("Discard changes?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Your edits won't be saved.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) {
                    Text("Discard", color = AppColors.Destructive)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun MetaItem(modifier: Modifier = Modifier, value: String, label: String) {
    Column(modifier = modifier.padding(horizontal = 12.dp)) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
@Composable
private fun MetaDivider() {
    Box(modifier = Modifier.width(1.dp).height(26.dp).background(AppColors.Divider))
}
@Composable
private fun DetailRow(label: String, value: String, showDivider: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
    if (showDivider) HorizontalDivider(color = AppColors.Divider)
}
@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    badgeCount: Int = 0,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(13.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    if (badgeCount > 9) "9+" else "$badgeCount",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp, fontSize = 11.sp),
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4A1B0C)
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.TextTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}
@Composable
private fun InlineNumberField(value: String, onValueChange: (String) -> Unit, widthDp: Int) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() || it == '.' }.take(6)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
            .width(widthDp.dp)
            .onFocusChanged { focused = it.isFocused }
            .drawBehind {
                drawLine(
                    color = if (focused) Color(0xFFFF7A5C) else Color(0xFF232323),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(bottom = 3.dp)
    )
}
@Composable
private fun UnitToggle(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.Divider)
            .padding(2.dp)
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    fontWeight = FontWeight.Medium,
                    color = if (active) Color(0xFF4A1B0C) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
@Composable
private fun PickerRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.TextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionSheet(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(14.dp))
            options.forEachIndexed { i, label ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(i) }.padding(vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (i == selectedIndex) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
                if (i < options.lastIndex) HorizontalDivider(color = AppColors.Divider)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogWeightSheet(currentWeightKg: Double?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var weightText by remember { mutableStateOf(currentWeightKg?.oneDecimal() ?: "") }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier.padding(top = 10.dp).width(36.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(AppColors.Divider)
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Log weight", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp), fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(
                "Today, ${formatDayMonth(logDateForNow())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = weightText,
                    onValueChange = { raw -> weightText = raw.filter { it.isDigit() || it == '.' }.take(6) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).padding(vertical = 14.dp)
                )
                Text("kg", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextTertiary)
            }
            errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            val weight = weightText.toDoubleOrNull()
            val valid = weight != null && weight in 20.0..400.0
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (valid && !saving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = valid && !saving) {
                        scope.launch {
                            saving = true
                            errorMessage = null
                            try {
                                logWeightQuick(weight!!)
                                onSaved()
                            } catch (e: Exception) {
                                errorMessage = "Couldn't save. ${e.message}"
                            } finally {
                                saving = false
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    if (saving) "Saving" else "Save",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (valid && !saving) Color(0xFF4A1B0C) else AppColors.TextTertiary
                )
            }
        }
    }
}