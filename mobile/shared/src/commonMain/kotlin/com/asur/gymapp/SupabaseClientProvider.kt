package com.asur.gymapp

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin

val supabase = createSupabaseClient(
    supabaseUrl = "https://irtadvcvamniklshbztq.supabase.co", // your real project URL
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImlydGFkdmN2YW1uaWtsc2hienRxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYxNzg2ODksImV4cCI6MjEwMTc1NDY4OX0.Vru4YF_X9c4yubnmMpcxSKNk2E6ZbeXG3-yf83kZ3ng" // the ANON key — Settings > API in Supabase, NOT the service role key
) {
    install(Auth) {
        host = "login-callback"
        scheme = "com.asur.gymapp"
    }
    install(Postgrest)
    install(ComposeAuth) {
        googleNativeLogin(serverClientId = "850023867378-ksjlvql6br8giup84793mp3fa2424i0v.apps.googleusercontent.com")
        // ^ this is your WEB client ID specifically, not the Android one — that's intentional
    }
}