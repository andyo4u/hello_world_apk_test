package app.roadready.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class SunClockTest {
    private val portland = SunClock(HomeCity.PORTLAND)

    private fun assertNear(expected: LocalTime, actual: java.time.Instant) {
        val local = actual.atZone(portland.zone).toLocalTime()
        val diff = abs(ChronoUnit.MINUTES.between(expected, local))
        assertTrue("expected ~$expected, got $local", diff <= 5)
    }

    @Test fun summerSolsticeInPortland() {
        val (rise, set) = portland.sunriseSunset(LocalDate.of(2026, 6, 21))
        assertNear(LocalTime.of(5, 22), rise)
        assertNear(LocalTime.of(21, 3), set)
    }

    @Test fun winterSolsticeInPortland() {
        val (rise, set) = portland.sunriseSunset(LocalDate.of(2026, 12, 21))
        assertNear(LocalTime.of(7, 51), rise)
        assertNear(LocalTime.of(16, 30), set)
    }

    @Test fun splitsADriveAcrossSunset() {
        val date = LocalDate.of(2026, 12, 21)
        // 4:00–5:00 p.m., sunset about 4:30.
        val start = ZonedDateTime.of(date, LocalTime.of(16, 0), portland.zone).toInstant().toEpochMilli()
        val night = portland.nightMinutes(start, 60)
        assertTrue("night=$night", night in 25..35)
        val noon = ZonedDateTime.of(date, LocalTime.NOON, portland.zone).toInstant().toEpochMilli()
        assertEquals(0, portland.nightMinutes(noon, 45))
    }

    @Test fun ontarioUsesMountainTime() {
        val ontario = SunClock(HomeCity.ONTARIO)
        val (_, set) = ontario.sunriseSunset(LocalDate.of(2026, 6, 21))
        val local = set.atZone(ontario.zone).toLocalTime()
        assertTrue("sunset $local", local.hour == 21)
    }
}
