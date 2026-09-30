package app.quarterhour.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.quarterhour.core.news.FeedItem
import app.quarterhour.core.reader.Block
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    items: List<FeedItem>,
    refreshing: Boolean,
    error: String?,
    filteredCount: Int,
    onRefresh: () -> Unit,
    onOpen: (FeedItem) -> Unit,
    onThumb: (FeedItem, Boolean) -> Unit,
    onLessLikeThis: (FeedItem) -> Unit,
    onBlockSource: (FeedItem) -> Unit,
    onShowFiltered: () -> Unit,
) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
            if (error != null) {
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            if (items.isEmpty() && !refreshing) {
                item {
                    Text(
                        "Pull down to load today's stories. The first load checks every article for paywalls, ads and clickbait, so it can take a moment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items, key = { it.article.id }) { item ->
                StoryCard(item, onOpen, onThumb, onLessLikeThis, onBlockSource)
            }
            if (filteredCount > 0) {
                item {
                    Text(
                        "$filteredCount stories filtered out (paywalls, clickbait, AI) — review",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onShowFiltered).padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryCard(
    item: FeedItem,
    onOpen: (FeedItem) -> Unit,
    onThumb: (FeedItem, Boolean) -> Unit,
    onLessLikeThis: (FeedItem) -> Unit,
    onBlockSource: (FeedItem) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val lead = item.reader.blocks.firstNotNullOfOrNull { (it as? Block.Image)?.url }
    Card(Modifier.fillMaxWidth().clickable { onOpen(item) }, shape = RoundedCornerShape(12.dp)) {
        Column {
            if (lead != null) {
                AsyncImage(
                    model = lead,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )
            }
            Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        item.article.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                    )
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("More like this") }, onClick = { menu = false; onThumb(item, true) })
                            DropdownMenuItem(text = { Text("Less like this") }, onClick = { menu = false; onLessLikeThis(item) })
                            DropdownMenuItem(text = { Text("Hide ${item.article.sourceName}") }, onClick = { menu = false; onBlockSource(item) })
                        }
                    }
                }
                val date = item.article.publishedAtMillis.takeIf { it > 0 }?.let {
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))
                }
                Text(
                    listOfNotNull(item.article.sourceName, date, "${readMinutes(item)} min read").joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.substitutedFrom?.let {
                    Text(
                        "Free version — the $it story is paywalled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

private fun readMinutes(item: FeedItem): Int = (item.reader.wordCount / 230).coerceAtLeast(1)
