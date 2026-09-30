package app.quarterhour.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.quarterhour.core.news.FeedItem
import app.quarterhour.core.reader.Block
import app.quarterhour.ui.theme.ReaderBody
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

/** Clean, native article view: text and photos only, nothing else from the publisher's page. */
@Composable
fun ReaderScreen(
    item: FeedItem,
    onDwell: (seconds: Long) -> Unit,
    onThumb: (Boolean) -> Unit,
) {
    var voted by remember { mutableStateOf<Boolean?>(null) }

    DisposableEffect(item.article.id) {
        val start = System.currentTimeMillis()
        onDispose { onDwell((System.currentTimeMillis() - start) / 1000) }
    }

    val reader = item.reader
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(reader.title.ifBlank { item.article.title }, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                val date = (reader.publishedAtMillis ?: item.article.publishedAtMillis.takeIf { it > 0 })
                    ?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) }
                Text(
                    listOfNotNull(reader.siteName ?: item.article.sourceName, reader.byline, date).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.substitutedFrom?.let {
                    Text(
                        "Shown from ${item.article.sourceName} because ${it}'s version is paywalled.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        items(reader.blocks) { block -> BlockView(block) }
        item {
            Column(Modifier.padding(20.dp)) {
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text("Was this worth your time?", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { voted = true; onThumb(true) }, enabled = voted == null) {
                        Icon(Icons.Default.ThumbUp, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Yes")
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = { voted = false; onThumb(false) }, enabled = voted == null) {
                        Icon(Icons.Default.ThumbDown, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("No")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Original article: ${reader.url}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BlockView(block: Block) {
    val side = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    when (block) {
        is Block.Heading -> Text(
            block.text,
            style = if (block.level <= 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            modifier = side.padding(top = 10.dp),
        )
        is Block.Paragraph -> Text(block.text, style = ReaderBody, modifier = side)
        is Block.Quote -> Text(
            block.text,
            style = ReaderBody.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = side.padding(start = 16.dp),
        )
        is Block.ListItem -> Text(
            (block.ordinal?.let { "$it. " } ?: "• ") + block.text,
            style = ReaderBody,
            modifier = side.padding(start = 8.dp),
        )
        is Block.Image -> Column(Modifier.padding(vertical = 10.dp)) {
            AsyncImage(
                model = block.url,
                contentDescription = block.caption,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
            block.caption?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
        }
        // Publisher video players carry pre-roll ads, so only the still frame is shown.
        is Block.VideoPoster -> Column(Modifier.padding(vertical = 10.dp)) {
            AsyncImage(
                model = block.imageUrl,
                contentDescription = "Still from the article's video",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
            )
            Text(
                "Video in the original article",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
}
