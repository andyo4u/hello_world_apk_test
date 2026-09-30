package app.quarterhour.core.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class DailyBudgetTest {
    private val signer = object : Signer {
        override fun sign(payload: String) = "sig:" + payload.hashCode()
    }
    private val budget = DailyBudget(signer, ZoneOffset.UTC)
    private val min = 60_000L

    private fun wall(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 9, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val installedLongAgo = wall(1, 0)

    /** Simulates [minutes] of foreground use in 1-second ticks. */
    private fun use(state: BudgetState, start: Clocks, minutes: Int): Pair<BudgetState, Clocks> {
        var s = state
        var c = start
        repeat(minutes * 60) {
            c = c.copy(wallMillis = c.wallMillis + 1000, elapsedMillis = c.elapsedMillis + 1000)
            s = budget.addUsage(s, c)
        }
        return s to c
    }

    private fun fresh(clocks: Clocks): BudgetState {
        val s = budget.decode(null, clocks, firstInstallMillis = clocks.wallMillis)
        return budget.refresh(s, clocks)
    }

    @Test fun firstInstallGetsFullBudget() {
        val c = Clocks(wall(29, 9), 1_000_000, 3)
        assertEquals(15 * min, budget.remainingMillis(fresh(c)))
    }

    @Test fun usageCountsAndStopsAtLimit() {
        val c = Clocks(wall(29, 9), 1_000_000, 3)
        val (s, _) = use(fresh(c), c, 14)
        assertEquals(1 * min, budget.remainingMillis(s))
        assertFalse(budget.isExhausted(s))
        val (s2, _) = use(s, c.copy(wallMillis = c.wallMillis + 14 * min, elapsedMillis = c.elapsedMillis + 14 * min), 5)
        assertTrue(budget.isExhausted(s2))
        assertEquals(15 * min, s2.usedMillis)
    }

    @Test fun pausesAreNotCounted() {
        val c = Clocks(wall(29, 9), 1_000_000, 3)
        var s = fresh(c)
        // App backgrounded for an hour: the next tick only counts up to MAX_TICK.
        s = budget.addUsage(s, c.copy(wallMillis = c.wallMillis + 60 * min, elapsedMillis = c.elapsedMillis + 60 * min))
        assertEquals(DailyBudget.MAX_TICK_MS, s.usedMillis)
    }

    @Test fun resetsAtLocalMidnight() {
        val c = Clocks(wall(29, 23, 30), 1_000_000, 3)
        val (s, end) = use(fresh(c), c, 15)
        assertTrue(budget.isExhausted(s))
        // Real time passes (monotonic clock agrees): new day.
        val next = end.copy(wallMillis = wall(30, 8), elapsedMillis = end.elapsedMillis + (wall(30, 8) - end.wallMillis))
        val s2 = budget.refresh(s, next)
        assertEquals("2026-09-30", s2.day)
        assertEquals(15 * min, budget.remainingMillis(s2))
    }

    @Test fun movingClockForwardDoesNotReset() {
        val c = Clocks(wall(29, 10), 1_000_000, 3)
        val (s, end) = use(fresh(c), c, 15)
        // User sets the date to tomorrow; only a minute actually elapsed.
        val cheat = end.copy(wallMillis = wall(30, 10), elapsedMillis = end.elapsedMillis + min)
        val s2 = budget.refresh(s, cheat)
        assertEquals("2026-09-29", s2.day)
        assertTrue(budget.isExhausted(s2))
    }

    @Test fun movingClockBackwardDoesNotReset() {
        val c = Clocks(wall(29, 10), 1_000_000, 3)
        val (s, end) = use(fresh(c), c, 15)
        val back = end.copy(wallMillis = wall(28, 10), elapsedMillis = end.elapsedMillis + min)
        assertTrue(budget.isExhausted(budget.refresh(s, back)))
    }

    @Test fun trustedServerTimeWinsOverDeviceClock() {
        val c = Clocks(wall(29, 10), 1_000_000, 3)
        val (s, end) = use(fresh(c), c, 15)
        // Reboot + clock moved forward, but the server says it's still the 29th.
        val rebooted = Clocks(wall(30, 10), 5_000, 4, trustedWallMillis = wall(29, 10, 30))
        assertTrue(budget.isExhausted(budget.refresh(s, rebooted)))
        // Server says it's really the 30th.
        val real = rebooted.copy(trustedWallMillis = wall(30, 10))
        assertFalse(budget.isExhausted(budget.refresh(s, real)))
    }

    @Test fun roundTripsThroughSignature() {
        val c = Clocks(wall(29, 9), 1_000_000, 3)
        val (s, end) = use(fresh(c), c, 5)
        val decoded = budget.decode(budget.encode(s), end, installedLongAgo)
        assertEquals(s, decoded)
    }

    @Test fun editedStateCountsAsUsedUp() {
        val c = Clocks(wall(29, 9), 1_000_000, 3)
        val (s, end) = use(fresh(c), c, 5)
        val stored = budget.encode(s)
        val tampered = stored.copy(payload = stored.payload.replace(Regex("\"usedMillis\":\\d+"), "\"usedMillis\":0"))
        assertTrue(budget.isExhausted(budget.decode(tampered, end, installedLongAgo)))
    }

    @Test fun clearedDataOnLaterDayCountsAsUsedUp() {
        val c = Clocks(wall(29, 9), 1_000_000, 3)
        assertTrue(budget.isExhausted(budget.decode(null, c, installedLongAgo)))
    }

    @Test fun timeUntilReset() {
        val c = Clocks(wall(29, 23, 0), 1_000_000, 3)
        assertEquals(60 * min, budget.millisUntilReset(fresh(c), c))
    }
}
