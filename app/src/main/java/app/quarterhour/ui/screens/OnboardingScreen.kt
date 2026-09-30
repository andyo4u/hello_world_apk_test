package app.quarterhour.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.quarterhour.core.news.Topic

/** Topic and keyword picker, used for onboarding and in settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestPicker(
    topics: Set<String>,
    keywords: Set<String>,
    onChange: (Set<String>, Set<String>) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    Text("Topics", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Topic.entries.forEach { t ->
            FilterChip(
                selected = t.id in topics,
                onClick = { onChange(if (t.id in topics) topics - t.id else topics + t.id, keywords) },
                label = { Text(t.label) },
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("Follow specific things", style = MaterialTheme.typography.titleMedium)
    Text("A team, a company, a place, a hobby…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true, modifier = Modifier.weight(1f), placeholder = { Text("e.g. electric cars") })
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = {
            if (draft.isNotBlank()) onChange(topics, keywords + draft.trim())
            draft = ""
        }) { Text("Add") }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        keywords.forEach { k ->
            InputChip(selected = true, onClick = { onChange(topics, keywords - k) }, label = { Text("$k  ✕") })
        }
    }
}

@Composable
fun OnboardingScreen(onDone: (Set<String>, Set<String>) -> Unit) {
    var topics by remember { mutableStateOf(setOf("WORLD", "TECHNOLOGY", "SCIENCE")) }
    var keywords by remember { mutableStateOf(emptySet<String>()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(32.dp))
        Text("QuarterHour", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            "The best 15 minutes of your day. Clean news without ads, paywalls or clickbait, plus 15 photos and videos from people you follow. Then it's done until tomorrow.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        InterestPicker(topics, keywords) { t, k -> topics = t; keywords = k }
        Spacer(Modifier.height(24.dp))
        Text(
            "Everything QuarterHour learns about you stays on this phone, in a file you can read: user_profile.md.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onDone(topics, keywords) }, enabled = topics.isNotEmpty() || keywords.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("Start reading")
        }
    }
}
