package com.asur.gymapp

import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate

private val monthAbbrev = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private fun parseIso(iso: String?): LocalDate? {
    if (iso.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()
}

fun formatDayMonth(iso: String?): String {
    val d = parseIso(iso) ?: return "—"
    return "${monthAbbrev[d.monthNumber - 1]} ${d.dayOfMonth}"
}

fun formatMonthYear(iso: String?): String {
    val d = parseIso(iso) ?: return "—"
    return "${monthAbbrev[d.monthNumber - 1]} ${d.year}"
}

fun Double.oneDecimal(): String {
    val scaled = (this * 10).roundToInt()
    return "${scaled / 10}.${scaled % 10}"
}

fun formatThousands(value: Double): String {
    val n = value.roundToInt()
    if (n < 1000) return n.toString()
    val s = n.toString()
    return s.reversed().chunked(3).joinToString(",").reversed()
}