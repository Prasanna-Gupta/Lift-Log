package com.asur.gymapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

@Composable
@Preview
fun App() {
    MaterialTheme {
        val sessionStatus by supabase.auth.sessionStatus.collectAsState()
        val status = sessionStatus

        if (status is SessionStatus.Authenticated) {
            var profile by remember { mutableStateOf<UserProfileRow?>(null) }
            var loading by remember { mutableStateOf(true) }

            LaunchedEffect(status) {
                profile = fetchUserProfile()
                loading = false
            }

            when {
                loading -> {}
                profile?.onboarding_completed == true -> AppShell()
                else -> OnboardingScreen(onComplete = {
                    profile = profile?.copy(onboarding_completed = true)
                })
            }
        } else {
            LoginScreen(onLoggedIn = { })
        }
    }
}