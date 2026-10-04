package app.roadready.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.roadready.core.AppState
import app.roadready.core.Driver
import app.roadready.core.OregonGdl
import app.roadready.core.QuestionBank
import app.roadready.core.QuestionStat
import app.roadready.core.QuizAttempt
import app.roadready.core.QuizEngine
import app.roadready.core.QuizMode
import app.roadready.core.RulesOfTheRoad
import app.roadready.ui.LabeledProgress
import app.roadready.ui.SectionCard
import app.roadready.ui.theme.Good
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val attemptFormat = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuizScreen(state: AppState, driver: Driver, onStart: (QuizMode, String?) -> Unit) {
    val stats = state.masteryFor(driver.id)
    val attempts = state.attemptsFor(driver.id)
    val zone = ZoneId.of(state.homeCity.zoneId)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("${driver.name}'s test readiness") {
                LabeledProgress("Ready score", "${QuizEngine.readiness(stats)}%", QuizEngine.readiness(stats) / 100f)
                Text(
                    "The DMV knowledge test is ${OregonGdl.KNOWLEDGE_TEST_QUESTIONS} questions; you need " +
                        "${OregonGdl.KNOWLEDGE_TEST_PASS} right. Questions you miss come back more often.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Button(onClick = { onStart(QuizMode.MOCK_EXAM, null) }, modifier = Modifier.fillMaxWidth()) {
                Text("Mock knowledge test (${OregonGdl.KNOWLEDGE_TEST_QUESTIONS} questions)")
            }
        }
        item {
            FilledTonalButton(onClick = { onStart(QuizMode.PRACTICE, null) }, modifier = Modifier.fillMaxWidth()) {
                Text("Quick practice (${QuizEngine.PRACTICE_SIZE} questions)")
            }
        }
        val weak = QuizEngine.weakTopics(stats)
        if (weak.isNotEmpty()) {
            item {
                Column {
                    Text("Needs work", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        weak.forEach { t -> AssistChip(onClick = { onStart(QuizMode.TOPIC, t.id) }, label = { Text(t.title) }) }
                    }
                }
            }
        }
        item {
            Column {
                Text("Practice a topic", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RulesOfTheRoad.topics.forEach { t ->
                        AssistChip(onClick = { onStart(QuizMode.TOPIC, t.id) }, label = { Text(t.title) })
                    }
                }
            }
        }
        if (attempts.isNotEmpty()) {
            item { Text("History", style = MaterialTheme.typography.titleSmall) }
            items(attempts.take(30)) { a ->
                val passed = QuizEngine.passed(a.mode, a.correct, a.total)
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            a.mode.label + (a.topicId?.let { id -> " · " + (RulesOfTheRoad.topic(id)?.title ?: id) } ?: ""),
                            Modifier.weight(1f),
                        )
                        Text(
                            "${a.correct}/${a.total}",
                            fontWeight = FontWeight.SemiBold,
                            color = if (passed) Good else MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        Instant.ofEpochMilli(a.atMillis).atZone(zone).format(attemptFormat),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/**
 * Runs one quiz. Practice and topic quizzes show the answer right away; the mock
 * test, like the real one, only shows results at the end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizRunScreen(
    driver: Driver,
    mode: QuizMode,
    topicId: String?,
    stats: Map<String, QuestionStat>,
    onAnswer: (questionId: String, correct: Boolean) -> Unit,
    onFinish: (QuizAttempt) -> Unit,
    onClose: () -> Unit,
) {
    val ids = rememberSaveable {
        val questions = when (mode) {
            QuizMode.MOCK_EXAM -> QuizEngine.mockExam()
            QuizMode.TOPIC -> QuizEngine.topic(topicId.orEmpty())
            QuizMode.PRACTICE -> QuizEngine.practice(stats)
        }
        ArrayList(questions.map { it.id })
    }
    val questions = ids.mapNotNull { QuestionBank.byId(it) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var chosen by rememberSaveable { mutableStateOf<Int?>(null) }
    var correct by rememberSaveable { mutableIntStateOf(0) }
    val missed = rememberSaveable { ArrayList<String>() }
    var recorded by rememberSaveable { mutableStateOf(false) }
    val reveal = mode != QuizMode.MOCK_EXAM && chosen != null
    val done = index >= questions.size

    if (done && !recorded) {
        LaunchedEffect(Unit) {
            recorded = true
            onFinish(QuizAttempt(driver.id, System.currentTimeMillis(), mode, correct, questions.size, topicId))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (mode) {
                            QuizMode.MOCK_EXAM -> "Mock knowledge test"
                            QuizMode.TOPIC -> RulesOfTheRoad.topic(topicId.orEmpty())?.title ?: "Topic quiz"
                            QuizMode.PRACTICE -> "Quick practice"
                        },
                    )
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close quiz") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (questions.isEmpty()) {
                Text("No questions here yet.")
                return@Column
            }
            if (done) {
                val passed = QuizEngine.passed(mode, correct, questions.size)
                Text(
                    "$correct / ${questions.size}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("score"),
                )
                Text(
                    when {
                        mode == QuizMode.MOCK_EXAM && passed -> "Passed! You'd need ${OregonGdl.KNOWLEDGE_TEST_PASS} on the real test."
                        mode == QuizMode.MOCK_EXAM -> "Not yet — ${OregonGdl.KNOWLEDGE_TEST_PASS} needed to pass. Review the misses below."
                        passed -> "Nice work, ${driver.name}!"
                        else -> "Keep practicing — these questions will come back."
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (passed) Good else MaterialTheme.colorScheme.error,
                )
                missed.mapNotNull { QuestionBank.byId(it) }.forEach { q ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(q.prompt, fontWeight = FontWeight.Medium)
                            Text("Answer: ${q.choices[q.answer]}", color = Good)
                            Text(q.explanation, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                return@Column
            }

            val q = questions[index]
            LinearProgressIndicator(progress = { index / questions.size.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text("Question ${index + 1} of ${questions.size}", style = MaterialTheme.typography.labelMedium)
            Text(q.prompt, style = MaterialTheme.typography.titleLarge)
            q.choices.forEachIndexed { i, choice ->
                val isChosen = chosen == i
                val colors = when {
                    reveal && i == q.answer -> ButtonDefaults.outlinedButtonColors(containerColor = Good.copy(alpha = 0.18f))
                    reveal && isChosen -> ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    isChosen -> ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    else -> ButtonDefaults.outlinedButtonColors()
                }
                OutlinedButton(
                    onClick = {
                        if (!reveal) {
                            chosen = i
                            // Practice records right away; the mock test records on Next so answers can be changed.
                            if (mode != QuizMode.MOCK_EXAM) onAnswer(q.id, i == q.answer)
                        }
                    },
                    colors = colors,
                    border = BorderStroke(1.dp, if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().testTag("choice$i"),
                ) { Text(choice, Modifier.fillMaxWidth()) }
            }
            if (reveal) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            if (chosen == q.answer) "Correct" else "Not quite",
                            fontWeight = FontWeight.Bold,
                            color = if (chosen == q.answer) Good else MaterialTheme.colorScheme.error,
                        )
                        Text(q.explanation)
                    }
                }
            }
            Button(
                onClick = {
                    val pick = chosen ?: return@Button
                    val right = pick == q.answer
                    if (mode == QuizMode.MOCK_EXAM) onAnswer(q.id, right)
                    if (right) correct++ else missed.add(q.id)
                    chosen = null
                    index++
                },
                enabled = chosen != null,
                modifier = Modifier.fillMaxWidth().testTag("next"),
            ) { Text(if (index == questions.lastIndex) "Finish" else "Next") }
        }
    }
}
