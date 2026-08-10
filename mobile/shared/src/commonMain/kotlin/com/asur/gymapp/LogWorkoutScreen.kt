package com.asur.gymapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

data class SetEntry(val weight: String = "", val reps: String = "")
data class ExerciseBlock(val exercise: Exercise, val sets: MutableList<SetEntry> = mutableListOf(SetEntry()))
data class WorkoutDraft(val id: String, val title: String, val blocks: List<ExerciseBlock> = emptyList(), val lastEditedDate: String)

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

// If the persisted draft is from a previous calendar day, keep the exercises/set-count
// (it's a routine template) but blank out the actual weight/reps values.
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

@Composable
fun LogWorkoutScreen() {
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
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Log Workout", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))

                loadError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(drafts, key = { it.id }) { draft ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                .clickable { screenState = LogScreenState.Builder(draft.id) }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(draft.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    if (drafts.size > 1) {
                                        IconButton(onClick = { drafts = drafts.filter { it.id != draft.id } }) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove")
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                val summary = if (draft.blocks.isEmpty()) "No exercises yet"
                                else draft.blocks.joinToString(", ") { it.exercise.name }
                                Text(
                                    summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { screenState = LogScreenState.Builder(draft.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (draft.blocks.isEmpty()) "Start Workout" else "Continue Workout")
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { drafts = drafts + WorkoutDraft(id = newDraftId(), title = "New Workout", lastEditedDate = todayDateString()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ New Day")
                }
            }
        }
    }
}

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
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            items(blocks, key = { it.exercise.id }) { block ->
                Spacer(Modifier.height(16.dp))
                ExerciseTableSection(
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
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onAddExercise, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Add Exercise")
                }
                Spacer(Modifier.height(80.dp))
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            statusMessage?.let {
                Text(it)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = onSave,
                enabled = blocks.isNotEmpty() && !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saving) "Saving..." else "Save Workout")
            }
        }
    }
}

@Composable
private fun ExerciseTableSection(block: ExerciseBlock, onChanged: (ExerciseBlock) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(block.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove exercise")
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Text("SET", modifier = Modifier.width(40.dp), style = MaterialTheme.typography.labelMedium)
            Text("KG", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text("REPS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(40.dp))
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        block.sets.forEachIndexed { index, set ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text("${index + 1}", modifier = Modifier.width(40.dp))
                OutlinedTextField(
                    value = set.weight,
                    onValueChange = { newVal ->
                        val updated = block.sets.toMutableList()
                        updated[index] = set.copy(weight = newVal)
                        onChanged(block.copy(sets = updated))
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                OutlinedTextField(
                    value = set.reps,
                    onValueChange = { newVal ->
                        val updated = block.sets.toMutableList()
                        updated[index] = set.copy(reps = newVal)
                        onChanged(block.copy(sets = updated))
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val updated = block.sets.toMutableList()
                        updated.removeAt(index)
                        if (updated.isEmpty()) updated.add(SetEntry())
                        onChanged(block.copy(sets = updated))
                    },
                    modifier = Modifier.width(40.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove set")
                }
            }
        }

        TextButton(onClick = {
            onChanged(block.copy(sets = (block.sets + SetEntry()).toMutableList()))
        }) {
            Text("+ Add Set")
        }
    }
}

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
    var showMuscleDialog by remember { mutableStateOf(false) }
    var showEquipmentDialog by remember { mutableStateOf(false) }

    val muscles = remember(exercises) { exercises.mapNotNull { it.muscle_group }.distinct().sorted() }
    val equipmentOptions = remember(exercises) { exercises.mapNotNull { it.equipment_type }.distinct().sorted() }

    val filtered = remember(query, muscleFilter, equipmentFilter, exercises) {
        exercises.filter { ex ->
            (query.isBlank() || ex.name.contains(query, ignoreCase = true)) &&
                    (muscleFilter == null || ex.muscle_group == muscleFilter) &&
                    (equipmentFilter == null || ex.equipment_type == equipmentFilter)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Add Exercise", style = MaterialTheme.typography.titleLarge)
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search exercises") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            OutlinedButton(onClick = { showEquipmentDialog = true }, modifier = Modifier.weight(1f)) {
                Text(equipmentFilter ?: "All Equipment")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { showMuscleDialog = true }, modifier = Modifier.weight(1f)) {
                Text(muscleFilter ?: "All Muscles")
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { exercise ->
                val alreadyAdded = exercise.id in alreadySelected
                ListItem(
                    headlineContent = { Text(exercise.name) },
                    supportingContent = {
                        val details = listOfNotNull(exercise.muscle_group, exercise.equipment_type).joinToString(" · ")
                        if (details.isNotBlank()) Text(details)
                    },
                    trailingContent = { if (alreadyAdded) Text("Added") },
                    modifier = if (!alreadyAdded) Modifier.clickable { onSelect(exercise) } else Modifier
                )
                HorizontalDivider()
            }
        }
    }

    if (showMuscleDialog) {
        FilterDialog(
            title = "Muscle Group",
            options = muscles,
            onSelect = { muscleFilter = it; showMuscleDialog = false },
            onDismiss = { showMuscleDialog = false }
        )
    }
    if (showEquipmentDialog) {
        FilterDialog(
            title = "Equipment",
            options = equipmentOptions,
            onSelect = { equipmentFilter = it; showEquipmentDialog = false },
            onDismiss = { showEquipmentDialog = false }
        )
    }
}

@Composable
private fun FilterDialog(
    title: String,
    options: List<String>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                item {
                    TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) { Text("All") }
                }
                items(options) { option ->
                    TextButton(onClick = { onSelect(option) }, modifier = Modifier.fillMaxWidth()) { Text(option) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

suspend fun saveWorkout(title: String, blocks: List<ExerciseBlock>) {
    val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")

    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val logDate = if (now.hour < 4) {
        now.date.minus(DatePeriod(days = 1)).toString()
    } else {
        now.date.toString()
    }

    val workout = supabase.postgrest.from("workouts")
        .insert(WorkoutInsert(user_id = userId, date = logDate, title = title)) { select() }
        .decodeSingle<WorkoutRow>()

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
}