package com.asur.gymapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen() {
    var streak by remember { mutableStateOf<StreakRow?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        streak = fetchStreak()
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val s = streak
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${s?.current_streak ?: 0}",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("day streak", style = MaterialTheme.typography.bodyMedium)

                    if (s?.warning_used == true) {
                        Spacer(Modifier.height(8.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text("⚠️ Saved after a missed day") }
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${s?.longest_streak ?: 0}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("longest streak", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(s?.last_logged_date ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("last logged", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}