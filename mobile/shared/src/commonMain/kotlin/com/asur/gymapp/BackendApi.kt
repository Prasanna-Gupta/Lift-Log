package com.asur.gymapp

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.http.parameters
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val BACKEND_BASE_URL = "https://lift-log-9yxs.onrender.com"
private val httpClient = HttpClient()
private val json = Json { ignoreUnknownKeys = true }

suspend fun searchFood(query: String): List<FoodSearchResult> {
    val localResults = supabase.postgrest.rpc(
        "search_local_foods",
        buildJsonObject { put("p_query", query) }
    ).decodeList<LocalFoodRow>()

    if (localResults.isNotEmpty()) {
        return localResults.map {
            FoodSearchResult(
                fdcId = it.fdc_id,
                description = it.description,
                dataType = it.data_type,
                brandOwner = it.brand_owner,
                caloriesPer100g = it.calories_per_100g,
                proteinPer100g = it.protein_per_100g,
                fatPer100g = it.fat_per_100g,
                fiberPer100g = it.fiber_per_100g
            )
        }
    }

    val response: String = httpClient.get {
        url("$BACKEND_BASE_URL/food/search")
        parameters { append("q", query) }
    }.body()
    return json.decodeFromString(response)
}

suspend fun getFoodPortions(fdcId: Long): FoodDetail {
    val localRow = supabase.postgrest.from("local_foods")
        .select { filter { eq("fdc_id", fdcId) } }
        .decodeSingleOrNull<LocalFoodRow>()

    if (localRow != null) {
        val portions = supabase.postgrest.rpc(
            "get_local_food_portions",
            buildJsonObject { put("p_fdc_id", fdcId) }
        ).decodeList<LocalPortionRow>()

        return FoodDetail(
            fdcId = localRow.fdc_id,
            description = localRow.description,
            caloriesPer100g = localRow.calories_per_100g,
            proteinPer100g = localRow.protein_per_100g,
            fatPer100g = localRow.fat_per_100g,
            fiberPer100g = localRow.fiber_per_100g,
            portions = portions.map { FoodPortion(label = it.label, grams = it.grams) }
        )
    }

    val response: String = httpClient.get {
        url("$BACKEND_BASE_URL/food/$fdcId/portions")
    }.body()
    return json.decodeFromString(response)
}