package com.asur.gymapp

import kotlinx.serialization.Serializable

@Serializable
data class PersistedSetEntry(val weight: String, val reps: String)

@Serializable
data class PersistedExerciseBlock(
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String?,
    val equipmentType: String?,
    val sets: List<PersistedSetEntry>
)

@Serializable
data class PersistedDraft(
    val id: String,
    val title: String,
    val blocks: List<PersistedExerciseBlock>,
    val lastEditedDate: String
)

expect object DraftStorage {
    fun save(drafts: List<PersistedDraft>)
    fun load(): List<PersistedDraft>
}