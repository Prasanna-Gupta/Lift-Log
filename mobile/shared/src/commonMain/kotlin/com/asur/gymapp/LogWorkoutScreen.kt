package com.asur.gymapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
data class SetEntry(val weight: String = "", val reps: String = "")
data class ExerciseBlock(val exercise: Exercise, val sets: MutableList<SetEntry> = mutableListOf(SetEntry()))
data class WorkoutDraft(val id: String, val title: String, val blocks: List<ExerciseBlock> = emptyList(), val lastEditedDate: String)

private val MILESTONES = setOf(7, 14, 30, 50, 100, 200, 365)

private sealed class LogScreenState {
    object Summary : LogScreenState()
    data class Builder(val draftId: String) : LogScreenState()
    data class Picker(val draftId: String) : LogScreenState()
}

private fun todayDateString(): String =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

private fun defaultTitleForToday(): String {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.dayOfWeek
    return when (today) {
        DayOfWeek.MONDAY -> "Monday"
        DayOfWeek.TUESDAY -> "Tuesday"
        DayOfWeek.WEDNESDAY -> "Wednesday"
        DayOfWeek.THURSDAY -> "Thursday"
        DayOfWeek.FRIDAY -> "Friday"
        DayOfWeek.SATURDAY -> "Saturday"
        DayOfWeek.SUNDAY -> "Sunday"
        else -> "Workout"
    }
}

private var draftIdCounter = 0
private fun newDraftId(): String = "draft_${draftIdCounter++}_${Clock.System.now().toEpochMilliseconds()}"

private fun WorkoutDraft.toPersisted(): PersistedDraft = PersistedDraft(
    id = id,
    title = title,
    lastEditedDate = lastEditedDate,
    blocks = blocks.map { block ->
        PersistedExerciseBlock(
            exerciseId = block.exercise.id,
            exerciseName = block.exercise.name,
            muscleGroup = block.exercise.muscle_group,
            equipmentType = block.exercise.equipment_type,
            sets = block.sets.map { PersistedSetEntry(it.weight, it.reps) }
        )
    }
)

private fun PersistedDraft.toWorkoutDraft(): WorkoutDraft {
    val today = todayDateString()
    val isStale = lastEditedDate != today
    return WorkoutDraft(
        id = id,
        title = title,
        lastEditedDate = today,
        blocks = blocks.map { pb ->
            ExerciseBlock(
                exercise = Exercise(id = pb.exerciseId, name = pb.exerciseName, muscle_group = pb.muscleGroup, equipment_type = pb.equipmentType),
                sets = pb.sets.map {
                    if (isStale) SetEntry() else SetEntry(it.weight, it.reps)
                }.toMutableList()
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWorkoutScreen(onNestedChange: (Boolean) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var exercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    var drafts by remember {
        mutableStateOf(
            DraftStorage.load().map { it.toWorkoutDraft() }.ifEmpty {
                listOf(WorkoutDraft(id = newDraftId(), title = defaultTitleForToday(), lastEditedDate = todayDateString()))
            }
        )
    }
    var screenState by remember { mutableStateOf<LogScreenState>(LogScreenState.Summary) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showNewDayPicker by remember { mutableStateOf(false) }

    LaunchedEffect(screenState) {
        onNestedChange(screenState !is LogScreenState.Summary)
    }

    LaunchedEffect(Unit) {
        try {
            exercises = supabase.postgrest.from("exercises").select().decodeList<Exercise>()
        } catch (e: Exception) {
            loadError = "Failed to load exercises: ${e.message}"
        }
    }

    LaunchedEffect(drafts) {
        DraftStorage.save(drafts.map { it.toPersisted() })
    }

    fun updateDraft(id: String, update: (WorkoutDraft) -> WorkoutDraft) {
        drafts = drafts.map { if (it.id == id) update(it).copy(lastEditedDate = todayDateString()) else it }
    }

    when (val state = screenState) {
        is LogScreenState.Picker -> {
            val draft = drafts.first { it.id == state.draftId }
            ExercisePickerScreen(
                exercises = exercises,
                alreadySelected = draft.blocks.map { it.exercise.id }.toSet(),
                onSelect = { exercise ->
                    updateDraft(state.draftId) { it.copy(blocks = it.blocks + ExerciseBlock(exercise)) }
                    screenState = LogScreenState.Builder(state.draftId)
                },
                onBack = { screenState = LogScreenState.Builder(state.draftId) }
            )
        }
        is LogScreenState.Builder -> {
            val draft = drafts.first { it.id == state.draftId }
            WorkoutBuilderScreen(
                title = draft.title,
                onTitleChange = { newTitle -> updateDraft(state.draftId) { it.copy(title = newTitle) } },
                blocks = draft.blocks,
                onBlocksChange = { newBlocks -> updateDraft(state.draftId) { it.copy(blocks = newBlocks) } },
                onAddExercise = { screenState = LogScreenState.Picker(state.draftId) },
                onBack = { screenState = LogScreenState.Summary },
                saving = saving,
                statusMessage = statusMessage,
                onSave = {
                    scope.launch {
                        saving = true
                        statusMessage = null
                        try {
                            saveWorkout(draft.title, draft.blocks)
                            statusMessage = "Saved!"
                        } catch (e: Exception) {
                            statusMessage = "Error: ${e.message}"
                        } finally {
                            saving = false
                        }
                    }
                }
            )
        }
        is LogScreenState.Summary -> {
            var lastLoggedDates by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
            var todayStatus by remember { mutableStateOf(TodayLogStatus(false, 0)) }

            LaunchedEffect(Unit) {
                lastLoggedDates = fetchLastLoggedDates()
                todayStatus = fetchTodayLogStatus()
            }

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Today, ${formatDayMonth(todayDateString())}",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (todayStatus.hasLoggedToday) "${todayStatus.exerciseCount} exercises logged"
                    else "Nothing logged yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))

                loadError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(18.dp))
                }

                Text(
                    "YOUR TEMPLATES",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                )

                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
                    ) {
                        items(drafts, key = { it.id }) { draft ->
                            DayCard(
                                draft = draft,
                                lastLoggedDate = lastLoggedDates[draft.title],
                                showRemove = drafts.size > 1,
                                onOpen = { screenState = LogScreenState.Builder(draft.id) },
                                onRemove = { drafts = drafts.filter { it.id != draft.id } }
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, AppColors.Divider, RoundedCornerShape(16.dp))
                                    .clickable { showNewDayPicker = true }
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("New day", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (showNewDayPicker) {
                var templates by remember { mutableStateOf<List<WorkoutTemplateRow>>(emptyList()) }
                var templateCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
                var loadingTemplates by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    val fetched = fetchTemplates()
                    templates = fetched
                    templateCounts = fetched.associate { t ->
                        t.id to runCatching { fetchTemplateExercises(t.id).size }.getOrDefault(0)
                    }
                    loadingTemplates = false
                }

                ModalBottomSheet(
                    onDismissRequest = { showNewDayPicker = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { Box(
                        modifier = Modifier.padding(top = 10.dp).width(36.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp)).background(AppColors.Divider)
                    ) }
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(4.dp))
                        Text("New day", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Start blank or reuse a template",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    drafts = drafts + WorkoutDraft(id = newDraftId(), title = "New workout", lastEditedDate = todayDateString())
                                    showNewDayPicker = false
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(34.dp).clip(CircleShape)
                                    .background(Color(0xFF3A2A26)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("Blank workout", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }

                        if (loadingTemplates) {
                            Spacer(Modifier.height(20.dp))
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.height(20.dp))
                        } else if (templates.isNotEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            Text(
                                "FROM A TEMPLATE",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, AppColors.Divider, RoundedCornerShape(14.dp))
                            ) {
                                templates.forEachIndexed { index, template ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable {
                                                scope.launch {
                                                    val exs = fetchTemplateExercises(template.id)
                                                    drafts = drafts + WorkoutDraft(
                                                        id = newDraftId(),
                                                        title = template.name,
                                                        blocks = exs.map { ExerciseBlock(it) },
                                                        lastEditedDate = todayDateString()
                                                    )
                                                    showNewDayPicker = false
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(template.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Spacer(Modifier.height(2.dp))
                                            val count = templateCounts[template.id] ?: 0
                                            Text(
                                                if (count == 1) "1 exercise" else "$count exercises",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AppColors.TextTertiary
                                            )
                                        }
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = AppColors.TextTertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    if (index < templates.lastIndex) HorizontalDivider(color = AppColors.Divider)
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().clickable { showNewDayPicker = false }.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCard(
    draft: WorkoutDraft,
    lastLoggedDate: String?,
    showRemove: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    val muscleGroups = remember(draft.blocks) {
        draft.blocks.mapNotNull { it.exercise.muscle_group }.distinct()
    }

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpen)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(draft.title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), fontWeight = FontWeight.Medium)
                if (muscleGroups.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        muscleGroups.take(3).forEach { group ->
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF3A2A26))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(group, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (muscleGroups.size > 3) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF2A2320))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "+${muscleGroups.size - 3}",
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                                    color = AppColors.TextTertiary
                                )
                            }
                        }
                    }
                }
            }
            if (showRemove) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove day",
                    tint = AppColors.TextTertiary,
                    modifier = Modifier.size(16.dp).clickable(onClick = onRemove)
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = AppColors.TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            when {
                draft.blocks.isEmpty() -> "No exercises yet"
                lastLoggedDate != null -> "last done ${formatDayMonth(lastLoggedDate)}"
                else -> "never logged"
            },
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextTertiary
        )

        if (draft.blocks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                draft.blocks.take(3).forEach { block ->
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            block.exercise.name,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = Color(0xFFD4D4D4),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (draft.blocks.size > 3) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "+${draft.blocks.size - 3}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = AppColors.TextTertiary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutBuilderScreen(
    title: String,
    onTitleChange: (String) -> Unit,
    blocks: List<ExerciseBlock>,
    onBlocksChange: (List<ExerciseBlock>) -> Unit,
    onAddExercise: () -> Unit,
    onBack: () -> Unit,
    saving: Boolean,
    statusMessage: String?,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(22.dp).clickable(onClick = onBack)
            )
            Spacer(Modifier.width(14.dp))

            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = blocks.isNotEmpty() && !saving, onClick = onSave)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    if (saving) "Saving" else "Save",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (blocks.isNotEmpty() && !saving) MaterialTheme.colorScheme.primary else AppColors.TextTertiary
                )
            }
        }

        statusMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(blocks, key = { it.exercise.id }) { block ->
                ExerciseTableCard(
                    block = block,
                    onChanged = { updated ->
                        onBlocksChange(blocks.map { if (it.exercise.id == block.exercise.id) updated else it })
                    },
                    onRemove = {
                        onBlocksChange(blocks.filter { it.exercise.id != block.exercise.id })
                    }
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, AppColors.Divider, RoundedCornerShape(16.dp))
                        .clickable(onClick = onAddExercise)
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add exercise", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ExerciseTableCard(block: ExerciseBlock, onChanged: (ExerciseBlock) -> Unit, onRemove: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF3A2A26)),
                contentAlignment = Alignment.Center
            ) {
                MuscleGroupIcon(muscleGroup = block.exercise.muscle_group, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                block.exercise.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Remove exercise",
                tint = AppColors.TextTertiary,
                modifier = Modifier.size(16.dp).clickable { confirmDelete = true }
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Spacer(Modifier.width(26.dp))
            Text("KG", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp))
            Text("REPS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp))
            Spacer(Modifier.width(26.dp))
        }

        block.sets.forEachIndexed { index, set ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(
                    modifier = Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                SetField(
                    value = set.weight,
                    onValueChange = { v ->
                        val updated = block.sets.toMutableList()
                        updated[index] = set.copy(weight = v)
                        onChanged(block.copy(sets = updated))
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                SetField(
                    value = set.reps,
                    onValueChange = { v ->
                        val updated = block.sets.toMutableList()
                        updated[index] = set.copy(reps = v)
                        onChanged(block.copy(sets = updated))
                    },
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove set",
                    tint = AppColors.TextTertiary,
                    modifier = Modifier.width(26.dp).clickable {
                        val updated = block.sets.toMutableList()
                        updated.removeAt(index)
                        if (updated.isEmpty()) updated.add(SetEntry())
                        onChanged(block.copy(sets = updated))
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable {
                onChanged(block.copy(sets = (block.sets + SetEntry()).toMutableList()))
            }
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            title = { Text("Remove ${block.exercise.name}?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("This clears any sets you've logged for it in this card.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onRemove() }) { Text("Remove", color = AppColors.Destructive) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }
}

@Composable
private fun SetField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() || it == '.' }.take(6)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerScreen(
    exercises: List<Exercise>,
    alreadySelected: Set<String>,
    onSelect: (Exercise) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var muscleFilter by remember { mutableStateOf<String?>(null) }
    var equipmentFilter by remember { mutableStateOf<String?>(null) }
    var showMuscleSheet by remember { mutableStateOf(false) }
    var showEquipmentSheet by remember { mutableStateOf(false) }

    val muscles = remember(exercises) { exercises.mapNotNull { it.muscle_group }.distinct().sorted() }
    val equipmentOptions = remember(exercises) { exercises.mapNotNull { it.equipment_type }.distinct().sorted() }
    val filtered = remember(query, muscleFilter, equipmentFilter, exercises) {
        exercises.filter { ex ->
            (query.isBlank() || ex.name.contains(query, ignoreCase = true)) &&
                    (muscleFilter == null || ex.muscle_group == muscleFilter) &&
                    (equipmentFilter == null || ex.equipment_type == equipmentFilter)
        }
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
            Text(
                "Add exercise",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                fontWeight = FontWeight.Medium
            )
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
                    Text("Search exercises", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextTertiary)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Clear search",
                    tint = AppColors.TextTertiary,
                    modifier = Modifier.size(15.dp).clickable { query = "" }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill(
                label = equipmentFilter ?: "Equipment",
                active = equipmentFilter != null,
                modifier = Modifier.weight(1f),
                onClick = { showEquipmentSheet = true }
            )
            FilterPill(
                label = muscleFilter?.replaceFirstChar { it.uppercase() } ?: "Muscle group",
                active = muscleFilter != null,
                modifier = Modifier.weight(1f),
                onClick = { showMuscleSheet = true }
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            if (filtered.size == 1) "1 exercise" else "${filtered.size} exercises",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.SearchOff, contentDescription = null, tint = AppColors.TextTertiary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(12.dp))
                Text("No matches", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Try a different search or clear a filter",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(filtered, key = { _, e -> e.id }) { index, exercise ->
                    val alreadyAdded = exercise.id in alreadySelected
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable(enabled = !alreadyAdded) { onSelect(exercise) }
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(exercise.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            val details = listOfNotNull(exercise.muscle_group, exercise.equipment_type).joinToString(" · ")
                            if (details.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(details, style = MaterialTheme.typography.bodySmall, color = AppColors.TextTertiary)
                            }
                        }
                        if (alreadyAdded) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF3A2A26))
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text("Added", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (index < filtered.lastIndex) HorizontalDivider(color = AppColors.Divider)
                }
            }
        }
    }

    if (showMuscleSheet) {
        FilterSheet(
            title = "Muscle group",
            options = muscles,
            selected = muscleFilter,
            onSelect = { muscleFilter = it; showMuscleSheet = false },
            onDismiss = { showMuscleSheet = false }
        )
    }
    if (showEquipmentSheet) {
        FilterSheet(
            title = "Equipment",
            options = equipmentOptions,
            selected = equipmentFilter,
            onSelect = { equipmentFilter = it; showEquipmentSheet = false },
            onDismiss = { showEquipmentSheet = false }
        )
    }
}

@Composable
private fun FilterPill(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Color(0xFF3A2A26) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    title: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { Box(
            modifier = Modifier.padding(top = 10.dp).width(36.dp).height(4.dp)
                .clip(RoundedCornerShape(2.dp)).background(AppColors.Divider)
        ) }
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp), fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterOptionChip(label = "All", active = selected == null, onClick = { onSelect(null) })
                options.forEach { option ->
                    FilterOptionChip(
                        label = option.replaceFirstChar { it.uppercase() },
                        active = selected == option,
                        onClick = { onSelect(option) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterOptionChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

data class SaveResult(val newStreak: Int, val wasComeback: Boolean)

suspend fun saveWorkout(title: String, blocks: List<ExerciseBlock>) {
    val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
    val logDate = logDateForNow()

    val workout = supabase.postgrest.from("workouts")
        .upsert(WorkoutInsert(user_id = userId, date = logDate, title = title)) {
            onConflict = "user_id, date, title"
            select()
        }
        .decodeSingle<WorkoutRow>()

    supabase.postgrest.from("workout_sets").delete {
        filter { eq("workout_id", workout.id) }
    }

    val setInserts = blocks.flatMap { block ->
        block.sets.mapIndexedNotNull { index, set ->
            val weight = set.weight.toDoubleOrNull()
            val reps = set.reps.toIntOrNull()
            if (weight != null && reps != null) {
                WorkoutSetInsert(
                    workout_id = workout.id,
                    exercise_id = block.exercise.id,
                    set_number = index + 1,
                    weight = weight,
                    reps = reps
                )
            } else null
        }
    }
    if (setInserts.isNotEmpty()) {
        supabase.postgrest.from("workout_sets").insert(setInserts)
    }

    if (blocks.isNotEmpty()) {
        val template = supabase.postgrest.from("workout_templates")
            .upsert(WorkoutTemplateInsert(user_id = userId, name = title)) {
                onConflict = "user_id, name"
                select()
            }
            .decodeSingle<WorkoutTemplateRow>()
        supabase.postgrest.from("workout_template_exercises").delete {
            filter { eq("template_id", template.id) }
        }
        val exerciseRows = blocks.mapIndexed { index, block ->
            TemplateExerciseInsert(template_id = template.id, exercise_id = block.exercise.id, position = index)
        }
        supabase.postgrest.from("workout_template_exercises").insert(exerciseRows)
    }
}