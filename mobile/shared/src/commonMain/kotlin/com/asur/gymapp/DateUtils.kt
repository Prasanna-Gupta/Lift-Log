package com.asur.gymapp

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun logDateForNow(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return if (now.hour < 4) {
        now.date.minus(DatePeriod(days = 1)).toString()
    } else {
        now.date.toString()
    }
}

@OptIn(ExperimentalTime::class)
fun ageFromDob(dob: LocalDate): Int {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    var age = today.year - dob.year
    if (today.monthNumber < dob.monthNumber || (today.monthNumber == dob.monthNumber && today.dayOfMonth < dob.dayOfMonth)) {
        age--
    }
    return age
}