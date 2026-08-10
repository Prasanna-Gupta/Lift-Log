package com.asur.gymapp

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class Tab { HOME, LOG, PROFILE }
private enum class LogSection { WORKOUT, DIET }

@Composable
fun AppShell() {
    var currentTab by remember { mutableStateOf(Tab.HOME) }
    var logSection by remember { mutableStateOf(LogSection.WORKOUT) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == Tab.HOME,
                    onClick = { currentTab = Tab.HOME },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentTab == Tab.LOG,
                    onClick = { currentTab = Tab.LOG },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Log") },
                    label = { Text("Log") }
                )
                NavigationBarItem(
                    selected = currentTab == Tab.PROFILE,
                    onClick = { currentTab = Tab.PROFILE },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (currentTab) {
                Tab.HOME -> Text("Home feed — coming soon")
                Tab.LOG -> {
                    Column {
                        SegmentedButtonRow(
                            options = listOf("Workout" to LogSection.WORKOUT, "Diet" to LogSection.DIET),
                            selected = logSection,
                            onSelect = { logSection = it }
                        )
                        when (logSection) {
                            LogSection.WORKOUT -> LogWorkoutScreen()
                            LogSection.DIET -> DietLogScreen()
                        }
                    }
                }
                Tab.PROFILE -> Text("Profile — coming soon")
            }
        }
    }
}

@Composable
private fun <T> SegmentedButtonRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        options.forEachIndexed { index, (label, value) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(label)
            }
        }
    }
}