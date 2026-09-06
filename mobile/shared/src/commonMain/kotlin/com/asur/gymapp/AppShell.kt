package com.asur.gymapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class Tab { HOME, LOG, PROFILE }
private enum class LogSection { WORKOUT, DIET }

@Composable
fun AppShell() {
    var currentTab by remember { mutableStateOf(Tab.HOME) }
    var logSection by remember { mutableStateOf(LogSection.WORKOUT) }
    var logNested by remember { mutableStateOf(false) }
    var profileNested by remember { mutableStateOf(false) }
    var homeNested by remember { mutableStateOf(false) }

    val navVisible = when (currentTab) {
        Tab.HOME -> !homeNested
        Tab.LOG -> !logNested
        Tab.PROFILE -> !profileNested
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        when (currentTab) {
            Tab.HOME -> HomeFeedScreen(onNestedChange = { homeNested = it })
            Tab.LOG -> {
                Column {
                    if (!logNested) {
                        PillSegmentedRow(
                            options = listOf("Workout" to LogSection.WORKOUT, "Diet" to LogSection.DIET),
                            selected = logSection,
                            onSelect = { logSection = it }
                        )
                    }
                    when (logSection) {
                        LogSection.WORKOUT -> LogWorkoutScreen(onNestedChange = { logNested = it })
                        LogSection.DIET -> DietLogScreen(onNestedChange = { logNested = it })
                    }
                }
            }
            Tab.PROFILE -> ProfileScreen(onNestedChange = { profileNested = it })
        }

        if (navVisible) {
            FloatingNavBar(
                selected = currentTab,
                onSelect = { currentTab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun FloatingNavBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp).height(64.dp),
        shape = RoundedCornerShape(28.dp),
        color = AppColors.NavBar,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, AppColors.Divider)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(Icons.Filled.Home, "Home", selected == Tab.HOME) { onSelect(Tab.HOME) }
            NavItem(Icons.Filled.Add, "Log", selected == Tab.LOG) { onSelect(Tab.LOG) }
            NavItem(Icons.Filled.Person, "Profile", selected == Tab.PROFILE) { onSelect(Tab.PROFILE) }
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = if (isSelected) MaterialTheme.colorScheme.primary else AppColors.TextTertiary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .sizeIn(minWidth = 64.dp, minHeight = 48.dp)
            .padding(vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, letterSpacing = 0.sp),
            color = color
        )
    }
}

@Composable
private fun <T> PillSegmentedRow(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(3.dp)
    ) {
        options.forEach { (label, value) ->
            val active = selected == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}