package app.quarterhour.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.quarterhour.core.social.Media
import app.quarterhour.core.social.MediaType
import app.quarterhour.core.social.SocialPost
import coil.compose.AsyncImage

@Composable
fun SocialScreen(
    posts: List<SocialPost>,
    hasAccounts: Boolean,
    loading: Boolean,
    error: String?,
    onLoad: () -> Unit,
    onConnect: () -> Unit,
    onSeen: (SocialPost) -> Unit,
    onThumb: (SocialPost, Boolean) -> Unit,
) {
    LaunchedEffect(hasAccounts) { if (hasAccounts) onLoad() }

    when {
        !hasAccounts -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Your 15 a day", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Connect Bluesky or Mastodon and QuarterHour picks the 15 best photos and short videos from the people you follow — no ads, no sponsored posts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onConnect) { Text("Connect an account") }
        }
        loading && posts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Text(
                    if (posts.isEmpty()) "Nothing with photos or video today." else "Today's ${posts.size}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            itemsIndexed(posts, key = { _, p -> p.id }) { index, post ->
                LaunchedEffect(post.id) { onSeen(post) }
                PostCard(index + 1, posts.size, post, onThumb)
            }
        }
    }
}

@Composable
private fun PostCard(n: Int, total: Int, post: SocialPost, onThumb: (SocialPost, Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = post.avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(post.authorName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(post.authorHandle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text("$n/$total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (post.media.size == 1) {
            MediaView(post.media.first(), Modifier.fillMaxWidth())
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(post.media) { m -> MediaView(m, Modifier.width(300.dp)) }
            }
        }
        if (post.text.isNotBlank()) {
            Text(post.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp), maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(start = 4.dp, bottom = 4.dp)) {
            IconButton(onClick = { onThumb(post, true) }) { Icon(Icons.Default.ThumbUp, contentDescription = "More like this") }
            IconButton(onClick = { onThumb(post, false) }) { Icon(Icons.Default.ThumbDown, contentDescription = "Less like this") }
        }
    }
}

@Composable
private fun MediaView(media: Media, modifier: Modifier) {
    val ratio = (media.aspectRatio ?: 1f).coerceIn(0.56f, 1.9f)
    when (media.type) {
        MediaType.IMAGE -> AsyncImage(
            model = media.url,
            contentDescription = media.alt,
            contentScale = ContentScale.Crop,
            modifier = modifier.aspectRatio(ratio),
        )
        MediaType.VIDEO -> VideoPlayer(media, modifier.aspectRatio(ratio))
    }
}

/** Muted, looping, tap-to-play video. Released as soon as it scrolls away. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPlayer(media: Media, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember(media.url) {
        ExoPlayer.Builder(context).build().apply {
            val item = MediaItem.Builder()
                .setUri(media.url)
                .apply { if (".m3u8" in media.url) setMimeType(MimeTypes.APPLICATION_M3U8) }
                .build()
            setMediaItem(item)
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = false
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true; setBackgroundColor(android.graphics.Color.BLACK) } },
        modifier = modifier.background(Color.Black),
    )
}
