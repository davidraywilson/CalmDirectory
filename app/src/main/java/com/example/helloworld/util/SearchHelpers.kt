package com.example.helloworld.util

import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class StarBreakdown(val full: Int, val half: Boolean, val empty: Int) {
    init { require(full + (if (half) 1 else 0) + empty == 5) }
}

fun decomposeStars(rating: Double): StarBreakdown {
    val clamped = rating.coerceIn(0.0, 5.0)
    val halves = (clamped * 2.0).roundToInt()
    val full = halves / 2
    val half = halves % 2 == 1
    val empty = 5 - full - if (half) 1 else 0
    return StarBreakdown(full, half, empty)
}

data class TimePeriod(
    val openDay: DayOfWeek,
    val openHour: Int,
    val openMinute: Int,
    val closeDay: DayOfWeek?,
    val closeHour: Int,
    val closeMinute: Int,
)

fun isOpenThroughout(periods: List<TimePeriod>?, now: LocalDateTime, target: LocalDateTime): Boolean {
    if (periods == null) return true
    if (periods.size == 1 && periods[0].closeDay == null) return true

    val ref = now.toLocalDate()
    for (p in periods) {
        if (p.closeDay == null) continue
        val openAt = onSameWeekAs(ref, p.openDay, p.openHour, p.openMinute)
        var closeAt = onSameWeekAs(ref, p.closeDay, p.closeHour, p.closeMinute)
        if (!closeAt.isAfter(openAt)) closeAt = closeAt.plusDays(7)
        for (shift in listOf(0L, -7L, 7L)) {
            val o = openAt.plusDays(shift)
            val c = closeAt.plusDays(shift)
            if (!now.isBefore(o) && now.isBefore(c) && !target.isAfter(c)) return true
        }
    }
    return false
}

private fun onSameWeekAs(refDate: java.time.LocalDate, day: DayOfWeek, hour: Int, minute: Int): LocalDateTime {
    val refMonday = refDate.minusDays((refDate.dayOfWeek.value - 1).toLong())
    val targetDate = refMonday.plusDays((day.value - 1).toLong())
    return LocalDateTime.of(targetDate, java.time.LocalTime.of(hour, minute))
}

fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val sinDLat = sin(dLat / 2)
    val sinDLon = sin(dLon / 2)
    val a = sinDLat * sinDLat +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sinDLon * sinDLon
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

fun formatMiles(meters: Double): String {
    val miles = meters / 1609.344
    return if (miles < 10.0) "%.1f mi".format(miles) else "${miles.roundToInt()} mi"
}

fun formatKilometers(meters: Double): String {
    val km = meters / 1000.0
    return if (km < 10.0) "%.1f km".format(km) else "${km.roundToInt()} km"
}
