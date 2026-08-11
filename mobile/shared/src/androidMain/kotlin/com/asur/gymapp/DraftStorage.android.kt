package com.asur.gymapp

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

lateinit var appContext: Context

actual object DraftStorage {
    private const val PREFS_NAME = "gymapp_drafts"
    private const val KEY = "drafts_json"

    actual fun save(drafts: List<PersistedDraft>) {
        val json = Json.encodeToString(drafts)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY, json).apply()
    }

    actual fun load(): List<PersistedDraft> {
        val json = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
}