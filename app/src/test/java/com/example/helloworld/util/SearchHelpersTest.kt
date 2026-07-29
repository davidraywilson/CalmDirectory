package com.example.helloworld.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class SearchHelpersTest {

    @Test fun decomposeStars_exactInteger() =
        assertEquals(StarBreakdown(full = 4, half = false, empty = 1), decomposeStars(4.0))

    @Test fun decomposeStars_roundsDown() =
        assertEquals(StarBreakdown(full = 4, half = false, empty = 1), decomposeStars(4.24))

    @Test fun decomposeStars_roundsToHalf() =
        assertEquals(StarBreakdown(full = 4, half = true, empty = 0), decomposeStars(4.25))

    @Test fun decomposeStars_roundsToHalfHigh() =
        assertEquals(StarBreakdown(full = 4, half = true, empty = 0), decomposeStars(4.74))

    @Test fun decomposeStars_roundsUp() =
        assertEquals(StarBreakdown(full = 5, half = false, empty = 0), decomposeStars(4.75))

    @Test fun decomposeStars_clampsLow() =
        assertEquals(StarBreakdown(full = 0, half = false, empty = 5), decomposeStars(-1.0))

    @Test fun decomposeStars_clampsHigh() =
        assertEquals(StarBreakdown(full = 5, half = false, empty = 0), decomposeStars(7.0))

    private fun mondayAt(hour: Int, minute: Int = 0): LocalDateTime =
        LocalDateTime.of(2026, 4, 27, hour, minute)

    @Test fun isOpenThroughout_nullPeriodsLenient() =
        assertTrue(isOpenThroughout(null, mondayAt(10), mondayAt(11)))

    @Test fun isOpenThroughout_currentlyOpenAndStaysOpen() {
        val periods = listOf(period(DayOfWeek.MONDAY, 9, 0, DayOfWeek.MONDAY, 17, 0))
        assertTrue(isOpenThroughout(periods, mondayAt(10), mondayAt(11)))
    }

    @Test fun isOpenThroughout_closesBeforeTarget() {
        val periods = listOf(period(DayOfWeek.MONDAY, 9, 0, DayOfWeek.MONDAY, 10, 30))
        assertFalse(isOpenThroughout(periods, mondayAt(10), mondayAt(11)))
    }

    @Test fun isOpenThroughout_currentlyClosed() {
        val periods = listOf(period(DayOfWeek.MONDAY, 14, 0, DayOfWeek.MONDAY, 17, 0))
        assertFalse(isOpenThroughout(periods, mondayAt(10), mondayAt(11)))
    }

    @Test fun isOpenThroughout_periodCrossesMidnight() {
        val periods = listOf(period(DayOfWeek.MONDAY, 22, 0, DayOfWeek.TUESDAY, 2, 0))
        assertTrue(isOpenThroughout(periods, mondayAt(23, 30), mondayAt(23, 30).plusHours(1)))
    }

    @Test fun isOpenThroughout_twentyFourSeven() {
        val periods = listOf(period(DayOfWeek.MONDAY, 0, 0, null))
        assertTrue(isOpenThroughout(periods, mondayAt(3), mondayAt(4)))
    }

    @Test fun isOpenThroughout_disjointPeriods_inGap() {
        val periods = listOf(
            period(DayOfWeek.MONDAY, 11, 0, DayOfWeek.MONDAY, 14, 0),
            period(DayOfWeek.MONDAY, 17, 0, DayOfWeek.MONDAY, 22, 0),
        )
        assertFalse(isOpenThroughout(periods, mondayAt(15), mondayAt(16)))
    }

    private fun period(
        openDay: DayOfWeek, openHour: Int, openMinute: Int,
        closeDay: DayOfWeek?, closeHour: Int = 0, closeMinute: Int = 0,
    ): TimePeriod = TimePeriod(
        openDay = openDay, openHour = openHour, openMinute = openMinute,
        closeDay = closeDay, closeHour = closeHour, closeMinute = closeMinute,
    )
}
