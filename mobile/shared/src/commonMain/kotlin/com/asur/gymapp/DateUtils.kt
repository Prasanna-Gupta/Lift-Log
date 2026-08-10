package com.asur.gymapp

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

fun logDateForNow(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return if (now.hour < 4) {
        now.date.minus(DatePeriod(days = 1)).toString()
    } else {
        now.date.toString()
    }
}

fun ageFromDob(dob: LocalDate): Int {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    var age = today.year - dob.year
    if (today.monthNumber < dob.monthNumber || (today.monthNumber == dob.monthNumber && today.dayOfMonth < dob.dayOfMonth)) {
        age--
    }
    return age
}