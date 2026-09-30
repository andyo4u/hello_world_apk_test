package app.quarterhour.core.budget

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Signs stored state so edits to the file are detected. The app backs this with an Android Keystore HMAC key. */
interface Signer {
    fun sign(payload: String): String
    fun verify(payload: String, signature: String): Boolean = sign(payload) == signature
}

/** Clock readings taken at one moment. */
data class Clocks(
    /** System.currentTimeMillis(): user-adjustable. */
    val wallMillis: Long,
    /** SystemClock.elapsedRealtime(): monotonic since boot, not adjustable. */
    val elapsedMillis: Long,
    /** Settings.Global.BOOT_COUNT, changes on every reboot. */
    val bootCount: Int,
    /** Wall time derived from a recent server `Date` header, if known this boot. */
    val trustedWallMillis: Long? = null,
)

@Serializable
data class BudgetState(
    /** ISO local date the usage belongs to. */
    val day: String,
    val usedMillis: Long,
    val lastWallMillis: Long,
    val lastElapsedMillis: Long,
    val bootCount: Int,
)

@Serializable
data class SignedBudget(val payload: String, val signature: String)

/**
 * One shared 15-minute allowance per day across news and social.
 *
 * Hardening:
 * - Moving the clock forward to reach "tomorrow" doesn't work while the device
 *   stays on: time is measured with the monotonic clock, and a server `Date`
 *   is preferred when one has been seen.
 * - Moving the clock backward never resets anything; the day only advances.
 * - A missing or edited state file counts as a used-up day, unless the app
 *   was installed today (first run). Clearing app data doesn't change the
 *   install time, so it doesn't earn a fresh 15 minutes.
 */
class DailyBudget(
    private val signer: Signer,
    private val zone: ZoneId = ZoneId.systemDefault(),
    val limitMillis: Long = DEFAULT_LIMIT_MS,
) {
    fun decode(stored: SignedBudget?, clocks: Clocks, firstInstallMillis: Long): BudgetState {
        val today = dayOf(effectiveNow(null, clocks))
        if (stored == null) {
            val installedToday = dayOf(firstInstallMillis) == today
            return BudgetState(today, if (installedToday) 0 else limitMillis, clocks.wallMillis, clocks.elapsedMillis, clocks.bootCount)
        }
        val state = if (signer.verify(stored.payload, stored.signature)) {
            runCatching { json.decodeFromString(BudgetState.serializer(), stored.payload) }.getOrNull()
        } else {
            null
        }
        return state ?: BudgetState(today, limitMillis, clocks.wallMillis, clocks.elapsedMillis, clocks.bootCount)
    }

    fun encode(state: BudgetState): SignedBudget {
        val payload = json.encodeToString(BudgetState.serializer(), state)
        return SignedBudget(payload, signer.sign(payload))
    }

    /** Rolls the day over if it's genuinely a new day, and refreshes the clock anchors. */
    fun refresh(state: BudgetState, clocks: Clocks): BudgetState {
        val now = effectiveNow(state, clocks)
        val nowDay = dayOf(now)
        val newDay = LocalDate.parse(nowDay).isAfter(LocalDate.parse(state.day))
        return state.copy(
            day = if (newDay) nowDay else state.day,
            usedMillis = if (newDay) 0 else state.usedMillis,
            lastWallMillis = maxOf(now, state.lastWallMillis),
            lastElapsedMillis = clocks.elapsedMillis,
            bootCount = clocks.bootCount,
        )
    }

    /** Adds time spent on a counted screen since the last anchor. */
    fun addUsage(state: BudgetState, clocks: Clocks): BudgetState {
        val sameBoot = clocks.bootCount == state.bootCount && clocks.elapsedMillis >= state.lastElapsedMillis
        val delta = if (sameBoot) (clocks.elapsedMillis - state.lastElapsedMillis) else 0L
        // A gap longer than a tick means we were paused; callers refresh anchors on resume.
        val used = (state.usedMillis + delta.coerceIn(0, MAX_TICK_MS)).coerceAtMost(limitMillis)
        return refresh(state.copy(usedMillis = used), clocks)
    }

    fun remainingMillis(state: BudgetState): Long = (limitMillis - state.usedMillis).coerceAtLeast(0)

    fun isExhausted(state: BudgetState): Boolean = remainingMillis(state) == 0L

    /** Millis until the next local midnight after the state's day, per the effective clock. */
    fun millisUntilReset(state: BudgetState, clocks: Clocks): Long {
        val next = LocalDate.parse(state.day).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return (next - effectiveNow(state, clocks)).coerceAtLeast(0)
    }

    /**
     * Best estimate of real wall time. Prefers a server-derived time; otherwise,
     * within the same boot, trusts the monotonic clock over a wall clock that
     * jumped forward.
     */
    internal fun effectiveNow(state: BudgetState?, clocks: Clocks): Long {
        clocks.trustedWallMillis?.let { return it }
        if (state == null) return clocks.wallMillis
        val sameBoot = clocks.bootCount == state.bootCount && clocks.elapsedMillis >= state.lastElapsedMillis
        if (!sameBoot) return maxOf(clocks.wallMillis, state.lastWallMillis)
        val expected = state.lastWallMillis + (clocks.elapsedMillis - state.lastElapsedMillis)
        return if (clocks.wallMillis > expected + CLOCK_TOLERANCE_MS) expected else maxOf(clocks.wallMillis, state.lastWallMillis)
    }

    private fun dayOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()

    companion object {
        const val DEFAULT_LIMIT_MS = 15L * 60 * 1000
        const val WARNING_MS = 2L * 60 * 1000
        const val MAX_TICK_MS = 5_000L
        const val CLOCK_TOLERANCE_MS = 2L * 60 * 1000
        private val json = Json { ignoreUnknownKeys = true }
    }
}
