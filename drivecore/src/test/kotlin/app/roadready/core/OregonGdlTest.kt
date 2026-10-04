package app.roadready.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OregonGdlTest {
    private val maya = Driver("m", "Maya", birthDate = LocalDate.of(2010, 3, 15))

    @Test fun permitAtFifteen() {
        assertEquals(LocalDate.of(2025, 3, 15), OregonGdl.permitEligibleOn(maya))
        assertEquals(Stage.PRE_PERMIT, OregonGdl.stage(maya, LocalDate.of(2025, 4, 1)))
    }

    @Test fun provisionalNeedsSixteenAndSixMonthsOfPermit() {
        // Permit right at 15: the 16th birthday is the later date.
        assertEquals(LocalDate.of(2026, 3, 15), OregonGdl.provisionalEligibleOn(maya.copy(permitDate = LocalDate.of(2025, 3, 15))))
        // Permit at 15¾: six months of holding is the later date.
        assertEquals(LocalDate.of(2026, 6, 1), OregonGdl.provisionalEligibleOn(maya.copy(permitDate = LocalDate.of(2025, 12, 1))))
        assertNull(OregonGdl.provisionalEligibleOn(maya))
    }

    @Test fun driverEdHalvesHours() {
        assertEquals(100, OregonGdl.requiredHours(maya))
        assertEquals(50, OregonGdl.requiredHours(maya.copy(driverEd = true)))
    }

    @Test fun permitMilestonesTrackHours() {
        val permit = maya.copy(permitDate = LocalDate.of(2025, 4, 1))
        val milestones = OregonGdl.milestones(permit, LocalDate.of(2025, 9, 1), loggedMinutes = 100 * 60)
        val hours = milestones.first { it.label.startsWith("Log") }
        assertTrue(hours.done)
        assertFalse(milestones.first { it.label.startsWith("Hold") }.done)
        assertTrue(OregonGdl.restrictions(permit, LocalDate.of(2025, 9, 1)).any { it.title.startsWith("Supervisor") })
    }

    @Test fun provisionalRestrictionsChangeAtSixMonths() {
        val licensed = maya.copy(permitDate = LocalDate.of(2025, 3, 15), provisionalDate = LocalDate.of(2026, 4, 1))
        val early = OregonGdl.restrictions(licensed, LocalDate.of(2026, 5, 1)).map { it.title }
        assertTrue("No teen passengers" in early)
        assertTrue("No driving midnight–5 a.m." in early)
        assertTrue("No phone while driving" in early)

        val later = OregonGdl.restrictions(licensed, LocalDate.of(2026, 11, 1))
        assertTrue(later.any { it.title == "At most 3 teen passengers" && it.endsOn == LocalDate.of(2027, 4, 1) })
        assertFalse(later.any { it.title == "No teen passengers" })

        // After a year only the under-18 phone rule is left.
        assertEquals(listOf("No phone while driving"), OregonGdl.restrictions(licensed, LocalDate.of(2027, 4, 2)).map { it.title })
    }

    @Test fun limitsEndAtEighteenIfThatComesFirst() {
        val late = maya.copy(permitDate = LocalDate.of(2027, 1, 1), provisionalDate = LocalDate.of(2027, 9, 1))
        val restrictions = OregonGdl.restrictions(late, LocalDate.of(2027, 10, 1))
        assertTrue(restrictions.all { it.endsOn == LocalDate.of(2028, 3, 1) || it.endsOn == LocalDate.of(2028, 3, 15) })
        assertEquals(Stage.FULL, OregonGdl.stage(late, LocalDate.of(2028, 3, 15)))
        assertTrue(OregonGdl.restrictions(late, LocalDate.of(2028, 3, 15)).isEmpty())
    }
}
