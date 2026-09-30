package app.quarterhour.ui

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.quarterhour.budget.BudgetUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Plain Application: the composables under test don't need the app container or WorkManager.
@Config(sdk = [34], application = Application::class)
class CountedLockoutTest {
    @get:Rule val rule = createComposeRule()

    private fun ui(remaining: Long) = BudgetUi(
        remainingMillis = remaining,
        limitMillis = 15 * 60_000L,
        exhausted = remaining == 0L,
        showWarning = false,
        millisUntilReset = 3 * 60 * 60_000L,
        counting = false,
    )

    @Test fun showsContentAndCountsWhileTimeRemains() {
        var entered = 0
        rule.setContent {
            Counted(ui(5 * 60_000L), onEnter = { entered++ }, onExit = {}, readToday = emptyList(), onOpenProfile = {}) {
                Text("Top stories")
            }
        }
        rule.onNodeWithText("Top stories").assertIsDisplayed()
        assertEquals(1, entered)
    }

    @Test fun locksOutWhenBudgetIsUsedUp() {
        var entered = 0
        rule.setContent {
            Counted(ui(0), onEnter = { entered++ }, onExit = {}, readToday = listOf("Park expansion approved"), onOpenProfile = {}) {
                Text("Top stories")
            }
        }
        rule.onNodeWithTag("lockout").assertExists()
        rule.onNodeWithText("See you tomorrow").assertIsDisplayed()
        rule.onNodeWithText("• Park expansion approved").assertExists()
        rule.onNodeWithText("Top stories").assertDoesNotExist()
        assertEquals(0, entered)
    }

    @Test fun clockFormatting() {
        assertEquals("15:00", formatClock(15 * 60_000L))
        assertEquals("0:01", formatClock(1))
        assertEquals("3h 0m", formatDuration(3 * 60 * 60_000L))
    }
}
