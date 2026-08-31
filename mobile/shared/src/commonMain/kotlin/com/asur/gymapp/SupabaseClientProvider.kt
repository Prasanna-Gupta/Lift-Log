package com.asur.gymapp

import com.russhwolf.settings.Settings
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin

val supabase = createSupabaseClient(
    supabaseUrl = "https://irtadvcvamniklshbztq.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImlydGFkdmN2YW1uaWtsc2hienRxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYxNzg2ODksImV4cCI6MjEwMTc1NDY4OX0.Vru4YF_X9c4yubnmMpcxSKNk2E6ZbeXG3-yf83kZ3ng"
) {
    install(io.github.jan.supabase.storage.Storage)
    install(Auth) {
        host = "login-callback"
        scheme = "com.asur.gymapp"
        sessionManager = SettingsSessionManager(Settings())
        alwaysAutoRefresh = true
    }
    install(Postgrest)
    install(ComposeAuth) {
        googleNativeLogin(serverClientId = "850023867378-ksjlvql6br8giup84793mp3fa2424i0v.apps.googleusercontent.com")
    }
}