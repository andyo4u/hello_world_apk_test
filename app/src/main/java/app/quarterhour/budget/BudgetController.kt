package app.quarterhour.budget

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.quarterhour.core.budget.BudgetState
import app.quarterhour.core.budget.Clocks
import app.quarterhour.core.budget.DailyBudget
import app.quarterhour.core.budget.SignedBudget
import app.quarterhour.core.net.ServerTimeListener
import app.quarterhour.data.JsonStore
import app.quarterhour.data.KeystoreSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.io.File

data class BudgetUi(
    val remainingMillis: Long,
    val limitMillis: Long,
    val exhausted: Boolean,
    /** True once, when the warning threshold is first crossed today. */
    val showWarning: Boolean,
    val millisUntilReset: Long,
    val counting: Boolean,
)

@Serializable
private data class StoredBudget(val signed: SignedBudget? = null)

/**
 * Owns the shared 15-minute daily allowance. Time counts only while at least
 * one counted screen (news, social, reader) is visible *and* the app is in the
 * foreground; turning the screen off stops the activity and pauses counting.
 * All state changes happen on the main thread ([scope] must use Dispatchers.Main).
 */
class BudgetController(
    private val context: Context,
    private val scope: CoroutineScope,
    /** Makes any request whose response `Date` reaches [onServerTime]. */
    private val checkServerTime: suspend () -> Unit,
    private val budget: DailyBudget = DailyBudget(KeystoreSigner()),
) : ServerTimeListener {

    private val store = JsonStore(File(context.filesDir, "budget.json"), StoredBudget.serializer()) { StoredBudget() }
    private var state: BudgetState
    private var countedScreens = 0
    private var foreground = false
    private var ticker: Job? = null
    private var warnedDay: String? = null

    // Last server Date header seen, anchored to the monotonic clock of this boot.
    @Volatile private var serverAnchor: Triple<Long, Long, Int>? = null

    private val _ui = MutableStateFlow(BudgetUi(0, budget.limitMillis, false, false, 0, false))
    val ui: StateFlow<BudgetUi> = _ui.asStateFlow()

    init {
        val clocks = clocks()
        val decoded = budget.decode(store.value.signed, clocks, firstInstallMillis())
        if (decoded.bootCount == clocks.bootCount) {
            state = budget.refresh(decoded, clocks)
        } else {
            // After a reboot the device clock can't be cross-checked against the
            // monotonic clock, so confirm the date with a server before rolling over.
            state = decoded
            scope.launch {
                runCatching { withTimeout(SERVER_CHECK_TIMEOUT_MS) { checkServerTime() } }
                state = budget.refresh(state, clocks())
                publish()
                updateTicking()
            }
        }
        publish()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = setForeground(true)
            override fun onStop(owner: LifecycleOwner) = setForeground(false)
        })
    }

    /** Called by a counted screen when it becomes visible. */
    fun enterCounted() {
        countedScreens++
        updateTicking()
    }

    fun exitCounted() {
        countedScreens = (countedScreens - 1).coerceAtLeast(0)
        updateTicking()
    }

    fun warningShown() {
        warnedDay = state.day
        publish()
    }

    override fun onServerTime(epochMillis: Long) {
        serverAnchor = Triple(epochMillis, SystemClock.elapsedRealtime(), bootCount())
    }

    private fun setForeground(value: Boolean) {
        foreground = value
        if (value) {
            // Returning from the background: re-anchor so the paused gap isn't counted.
            state = budget.refresh(state, clocks())
            publish()
        }
        updateTicking()
    }

    private fun updateTicking() {
        val shouldTick = foreground && countedScreens > 0 && !budget.isExhausted(state)
        if (shouldTick && ticker?.isActive != true) {
            state = budget.refresh(state, clocks())
            ticker = scope.launch {
                var sinceSave = 0
                while (isActive) {
                    delay(TICK_MS)
                    state = budget.addUsage(state, clocks())
                    publish()
                    if (++sinceSave >= SAVE_EVERY_TICKS || budget.isExhausted(state)) {
                        save(); sinceSave = 0
                    }
                    if (budget.isExhausted(state)) break
                }
            }
        } else if (!shouldTick && ticker != null) {
            ticker?.cancel()
            ticker = null
            state = budget.addUsage(state, clocks())
            scope.launch { save() }
        }
        publish()
    }

    private suspend fun save() = store.set(StoredBudget(budget.encode(state)))

    private fun publish() {
        val clocks = clocks()
        val remaining = budget.remainingMillis(state)
        _ui.value = BudgetUi(
            remainingMillis = remaining,
            limitMillis = budget.limitMillis,
            exhausted = remaining == 0L,
            showWarning = remaining in 1..DailyBudget.WARNING_MS && warnedDay != state.day,
            millisUntilReset = budget.millisUntilReset(state, clocks),
            counting = ticker?.isActive == true,
        )
    }

    private fun clocks(): Clocks {
        val boot = bootCount()
        val elapsed = SystemClock.elapsedRealtime()
        val trusted = serverAnchor?.takeIf { it.third == boot }?.let { (server, at, _) -> server + (elapsed - at) }
        return Clocks(System.currentTimeMillis(), elapsed, boot, trusted)
    }

    private fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    private fun firstInstallMillis(): Long =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime }
            .getOrDefault(System.currentTimeMillis())

    companion object {
        private const val TICK_MS = 1_000L
        private const val SAVE_EVERY_TICKS = 5
        private const val SERVER_CHECK_TIMEOUT_MS = 4_000L
    }
}
