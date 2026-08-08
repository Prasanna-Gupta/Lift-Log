package com.asur.gymapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform