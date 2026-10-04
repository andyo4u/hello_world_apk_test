package app.roadready.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.roadready.core.AppState
import app.roadready.core.Driver
import app.roadready.core.QuestionBank
import app.roadready.core.QuestionStat
import app.roadready.core.QuizEngine
import app.roadready.core.RulesOfTheRoad

@Composable
fun StudyScreen(state: AppState, driver: Driver, onOpenTopic: (String) -> Unit) {
    val stats = state.masteryFor(driver.id)
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column {
                Text("Oregon rules of the road", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(RulesOfTheRoad.DISCLAIMER, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        items(RulesOfTheRoad.topics, key = { it.id }) { topic ->
            val mastered = topicMastery(topic.id, stats)
            Card(Modifier.fillMaxWidth().clickable { onOpenTopic(topic.id) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(topic.title, style = MaterialTheme.typography.titleMedium)
                        Text(topic.summary, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${topic.facts.size} key facts · ${driver.name} knows $mastered%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}

/** Share of a topic's questions the kid has moved to box 3+. */
internal fun topicMastery(topicId: String, stats: Map<String, QuestionStat>): Int =
    QuizEngine.readiness(stats, QuestionBank.byTopic(topicId))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(topicId: String, stats: Map<String, QuestionStat>, onBack: () -> Unit, onPractice: () -> Unit) {
    val topic = RulesOfTheRoad.topic(topicId)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(topic?.title ?: "Topic") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            if (topic != null) {
                ExtendedFloatingActionButton(
                    onClick = onPractice,
                    icon = { Icon(Icons.Default.Quiz, contentDescription = null) },
                    text = { Text("Practice ${QuestionBank.byTopic(topic.id).size} questions") },
                )
            }
        },
    ) { padding ->
        if (topic == null) {
            Text("Topic not found", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column {
                    Text(topic.summary, style = MaterialTheme.typography.bodyLarge)
                    Text("Mastery: ${topicMastery(topic.id, stats)}%", style = MaterialTheme.typography.labelMedium)
                }
            }
            items(topic.facts) { fact ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                ) {
                    Row(Modifier.padding(14.dp)) {
                        Text("•", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(fact.text)
                            if (fact.source.isNotEmpty()) {
                                Text(fact.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
