package app.roadready.ui

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.roadready.core.AppState
import app.roadready.core.Drive
import app.roadready.core.Driver
import app.roadready.core.QuizAttempt
import app.roadready.core.QuizMode
import app.roadready.ui.screens.HomeScreen
import app.roadready.ui.screens.QuizRunScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
// Plain Application: the screens under test take their state as parameters.
@Config(sdk = [34], application = Application::class)
class ScreensTest {
    @get:Rule val rule = createComposeRule()

    private val maya = Driver("m", "Maya", LocalDate.of(2010, 3, 15), permitDate = LocalDate.of(2025, 4, 1))

    @Test fun homeShowsPermitProgressAndRules() {
        val state = AppState().upsertDriver(maya).saveDrive(Drive("d1", "m", 0L, 150, supervisor = "Dad"))
        rule.setContent {
            HomeScreen(
                state = state,
                driver = maya,
                onStartDrive = {},
                onCancelDrive = {},
                onFinishDrive = {},
                onOpenQuiz = {},
                onOpenSkills = {},
                onEditDriver = {},
                today = LocalDate.of(2025, 9, 1),
            )
        }
        rule.onNodeWithTag("stage").assertExists()
        rule.onNodeWithText("Age 15 · Instruction permit").assertExists()
        rule.onNodeWithText("2 h 30 m / 100 h").assertExists()
        rule.onNodeWithText("Log 100 supervised hours").assertExists()
        rule.onNodeWithText("Supervisor in the front seat").assertExists()
        rule.onNodeWithText("Start drive").assertExists()
    }

    @Test fun topicQuizRunsToTheEnd() {
        val answers = mutableListOf<Boolean>()
        var finished: QuizAttempt? = null
        rule.setContent {
            QuizRunScreen(
                driver = maya,
                mode = QuizMode.TOPIC,
                topicId = "parking",
                stats = emptyMap(),
                onAnswer = { _, correct -> answers += correct },
                onFinish = { finished = it },
                onClose = {},
            )
        }
        repeat(6) {
            // The explanation card can push Next below the small test screen: scroll before clicking.
            rule.onNodeWithTag("next").assertIsNotEnabled()
            rule.onNodeWithTag("choice0").performScrollTo().performClick()
            rule.onNodeWithTag("next").assertIsEnabled().performScrollTo().performClick()
        }
        rule.waitForIdle()
        rule.onNodeWithTag("score").assertExists()
        assertEquals(6, answers.size)
        assertEquals(6, finished?.total)
        assertEquals(answers.count { it }, finished?.correct)
    }
}
