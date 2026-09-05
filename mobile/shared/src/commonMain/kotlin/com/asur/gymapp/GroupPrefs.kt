package com.asur.gymapp

import com.russhwolf.settings.Settings

private const val KEY_LAST_GROUP_ID = "last_selected_group_id"

object GroupPrefs {
    private val settings: Settings by lazy { Settings() }

    fun getLastSelectedGroupId(): String? =
        settings.getStringOrNull(KEY_LAST_GROUP_ID)

    fun setLastSelectedGroupId(groupId: String) {
        settings.putString(KEY_LAST_GROUP_ID, groupId)
    }
}