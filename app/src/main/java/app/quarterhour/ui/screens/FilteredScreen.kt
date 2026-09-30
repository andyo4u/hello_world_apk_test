package app.quarterhour.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.quarterhour.core.filter.FilterReason
import app.quarterhour.core.news.FilteredItem

/**
 * What was hidden and why. Only headlines are shown — opening the originals
 * would mean paywalls and ads — but mistakes can be corrected here.
 */
@Composable
fun FilteredScreen(
    items: List<FilteredItem>,
    onNotClickbait: (FilteredItem) -> Unit,
    onMarkAi: (FilteredItem) -> Unit,
    onDismiss: (FilteredItem) -> Unit,
) {
    val grouped = items.groupBy { it.reason }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        if (items.isEmpty()) item { Text("Nothing filtered in the current feed.") }
        FilterReason.entries.forEach { reason ->
            val list = grouped[reason].orEmpty()
            if (list.isEmpty()) return@forEach
            item {
                Text("${reason.label} (${list.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            }
            items(list) { f ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(f.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOf(f.sourceName).plus(f.details.map { it.replace('-', ' ') }).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        if (reason == FilterReason.CLICKBAIT) TextButton(onClick = { onNotClickbait(f) }) { Text("Not clickbait") }
                        if (f.sourceDomain != null && reason != FilterReason.AI_CONTENT && reason != FilterReason.BLOCKED_SOURCE) {
                            TextButton(onClick = { onMarkAi(f) }) { Text("Source is AI-generated") }
                        }
                        TextButton(onClick = { onDismiss(f) }) { Text("Dismiss") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
