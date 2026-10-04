package app.roadready.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class AppStateTest {
    private val sun = SunClock(HomeCity.SALEM)
    private val maya = Driver("m", "Maya", LocalDate.of(2010, 3, 15), permitDate = LocalDate.of(2025, 4, 1))
    private val leo = Driver("l", "Leo", LocalDate.of(2011, 8, 2))

    private fun at(h: Int, m: Int = 0) =
        ZonedDateTime.of(LocalDate.of(2026, 1, 10), LocalTime.of(h, m), sun.zone).toInstant().toEpochMilli()

    @Test fun newKidIsSelectedAndKidsAreSeparate() {
        var s = AppState().upsertDriver(maya).upsertDriver(leo)
        assertEquals("l", s.selectedDriver?.id)
        s = s.select("m").upsertDriver(leo.copy(name = "Leon"))
        assertEquals("m", s.selectedDriver?.id)
        assertEquals("Leon", s.drivers.last().name)

        s = s.startDrive("m", at(10), "Dad").finishDrive("m", at(11), "d1", sun, setOf(Condition.HIGHWAY))
        assertEquals(1, s.drivesFor("m").size)
        assertTrue(s.drivesFor("l").isEmpty())
        assertEquals(60, s.drivesFor("m").single().minutes)
        assertEquals(0, s.drivesFor("m").single().nightMinutes)
        assertEquals(listOf("Dad"), s.supervisors)
        assertNull(s.activeDriveFor("m"))
    }

    @Test fun eveningDriveCountsNightMinutes() {
        val s = AppState().upsertDriver(maya).startDrive("m", at(17), "Mom").finishDrive("m", at(18, 30), "d1", sun)
        val drive = s.drives.single()
        assertEquals(90, drive.minutes)
        assertEquals(90, drive.nightMinutes) // January sunset in Salem is before 5 p.m.
    }

    @Test fun tooShortDrivesAreDropped() {
        val s = AppState().upsertDriver(maya).startDrive("m", at(10), "").finishDrive("m", at(10) + 30_000, "d1", sun)
        assertTrue(s.drives.isEmpty())
        assertTrue(s.activeDrives.isEmpty())
    }

    @Test fun removingAKidRemovesTheirData() {
        var s = AppState().upsertDriver(maya).upsertDriver(leo)
            .saveDrive(Drive("d1", "m", at(9), 30))
            .saveDrive(Drive("d2", "l", at(9), 30))
            .recordAnswer("m", "gdl1", true)
            .setSkill("m", "cockpit", SkillLevel.CONFIDENT)
        s = s.removeDriver("m")
        assertEquals(listOf("l"), s.drivers.map { it.id })
        assertEquals("l", s.selectedDriver?.id)
        assertEquals(listOf("d2"), s.drives.map { it.id })
        assertTrue(s.mastery.isEmpty() && s.skills.isEmpty())
    }

    @Test fun roundTripsThroughJson() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val s = AppState().upsertDriver(maya.copy(provisionalDate = LocalDate.of(2026, 5, 1)))
            .saveDrive(Drive("d1", "m", at(9), 30, conditions = setOf(Condition.RAIN), notes = "wet, \"slick\""))
            .recordAnswer("m", "gdl1", false)
            .recordAttempt(QuizAttempt("m", at(12), QuizMode.MOCK_EXAM, 30, 35))
            .setSkill("m", "merge", SkillLevel.PRACTICING)
        val text = json.encodeToString(AppState.serializer(), s)
        assertTrue(text.contains("\"2010-03-15\""))
        assertEquals(s, json.decodeFromString(AppState.serializer(), text))
    }

    @Test fun csvAndSummary() {
        val drives = listOf(
            Drive("d1", "m", at(9), 45, supervisor = "Dad", conditions = setOf(Condition.CITY), notes = "rain, then sun"),
            Drive("d2", "m", at(17), 75, nightMinutes = 75, supervisor = "Mom"),
        )
        val csv = DriveLog.csv(maya, drives, sun.zone).lines()
        assertEquals("Maya,2026-01-10,09:00,45,0,Dad,City traffic,\"rain, then sun\"", csv[1])
        val summary = DriveLog.summary(maya, drives)
        assertTrue(summary, summary.contains("Total: 2 h over 2 drives (required: 100 h)"))
        assertTrue(summary, summary.contains("Night: 1 h 15 m"))
        assertTrue(summary, summary.contains("Supervisors: Dad, Mom"))
    }
}
