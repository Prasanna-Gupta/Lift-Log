package com.asur.gymapp

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.http.parameters
import kotlinx.serialization.json.Json

// Replace with your machine's LAN IP for real device testing, or 10.0.2.2 for the Android emulator.
// localhost from the PHONE means the phone itself, not your laptop.
const val BACKEND_BASE_URL = "https://lift-log-production-9885.up.railway.app"

private val httpClient = HttpClient()
private val json = Json { ignoreUnknownKeys = true }

suspend fun searchFood(query: String): List<FoodSearchResult> {
    val response: String = httpClient.get {
        url("$BACKEND_BASE_URL/food/search")
        parameters { append("q", query) }
    }.body()
    return json.decodeFromString(response)
}

suspend fun getFoodPortions(fdcId: Long): FoodDetail {
    val response: String = httpClient.get {
        url("$BACKEND_BASE_URL/food/$fdcId/portions")
    }.body()
    return json.decodeFromString(response)
}